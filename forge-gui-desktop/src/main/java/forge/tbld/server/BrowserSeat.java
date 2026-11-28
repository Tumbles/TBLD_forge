package forge.tbld.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import forge.ImageKeys;
import forge.card.GamePieceType;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.deck.CardPool;
import forge.game.GameEntityView;
import forge.game.GameLogEntry;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.card.CounterType;
import forge.game.combat.CombatView;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.game.tbld.TbldEmblems;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.Selection;
import forge.gamemodes.match.YieldUpdate;
import forge.gamemodes.net.IRemote;
import forge.gamemodes.net.event.IdentifiableNetEvent;
import forge.gamemodes.net.event.NetEvent;
import forge.gamemodes.net.ProtocolGuiGame;
import forge.gamemodes.net.ProtocolMethod;
import forge.gamemodes.net.event.GuiGameEvent;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.IGameController;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.player.PlayerControllerHuman;
import forge.trackable.TrackableCollection;
import forge.util.ITriggerEvent;
import forge.util.collect.FCollection;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * One player's seat in one match. The engine's calls on this player's GUI arrive here as
 * {@link GuiGameEvent}s; we send each to the browser as JSON, together with a snapshot of the game
 * state as this player is allowed to see it. Calls that need an answer block until the browser
 * replies. Actions from the browser are passed to this player's {@link IGameController}.
 */
public final class BrowserSeat implements IRemote {

    /** Calls that are answered on the server, without asking the browser. */
    private static final java.util.Set<ProtocolMethod> AUTO_ANSWERED = java.util.EnumSet.of(
            ProtocolMethod.assignCombatDamage, ProtocolMethod.assignGenericAmount,
            ProtocolMethod.sideboard, ProtocolMethod.manipulateCardList);

    /** Calls that only update state and have no meaning to the browser on their own. */
    private static final java.util.Set<ProtocolMethod> STATE_ONLY = java.util.EnumSet.of(
            ProtocolMethod.setGameView, ProtocolMethod.applyDelta, ProtocolMethod.applyYieldUpdate,
            ProtocolMethod.applyMacroState);

    /** Logs prompts, buttons and selections to standard output. Set with -Dtbld.debug=true. */
    private static final boolean DEBUG = Boolean.getBoolean("tbld.debug");

    private final int seatIndex;
    private final Supplier<Channel> channel;
    private final ProtocolGuiGame gui;
    private volatile PlayerView player;
    /** Set when the match is abandoned; the seat then ignores the engine and answers nothing. */
    private volatile boolean retired;
    /** Pass priority automatically when the player has nothing to play or activate. Set by the browser. */
    private volatile boolean autoPass;
    /** With {@link #autoPass}: a mana ability the player could activate also counts as something to do. */
    private volatile boolean autoPassCountsMana;
    /** Keep priority after casting a spell or activating an ability. Off, priority passes. Set by the browser. */
    private volatile boolean holdPriority = true;
    /**
     * The phases in which this player stops to get priority while the stack is empty: on their own
     * turn, and on an opponent's turn. In the other phases they pass. Null until the browser sends
     * them; until then the player stops in every phase.
     */
    private volatile Set<PhaseType> stopsOwnTurn;
    private volatile Set<PhaseType> stopsOpponentTurn;
    /** The phase stops could not be applied yet, because the game view had no players. */
    private volatile boolean stopsPending;

    /** The phases that a player can choose to stop in. */
    static final List<PhaseType> STOP_PHASES = List.of(PhaseType.UPKEEP, PhaseType.DRAW, PhaseType.MAIN1,
            PhaseType.COMBAT_BEGIN, PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.COMBAT_DECLARE_BLOCKERS,
            PhaseType.COMBAT_FIRST_STRIKE_DAMAGE, PhaseType.COMBAT_DAMAGE, PhaseType.COMBAT_END,
            PhaseType.MAIN2, PhaseType.END_OF_TURN);
    private final ExecutorService actions;

    /** Questions waiting for the browser's answer, by request id. */
    private final Map<Integer, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();
    /** The messages that carried those questions, resent when the browser reconnects. */
    private final Map<Integer, String> pendingMessages = new ConcurrentHashMap<>();
    private volatile String lastPrompt;
    private volatile String lastButtons;
    private volatile String lastState;
    private volatile String lastSelection;

    /** Objects in the last snapshot, by id, for resolving the browser's clicks. */
    private volatile Map<Integer, CardView> visibleCards = Map.of();
    private volatile Map<Integer, PlayerView> visiblePlayers = Map.of();
    private volatile Map<Integer, StackItemView> visibleStack = Map.of();

    /** The triggered abilities this player chose "Always yield" for, by Forge's yield key: the source's name and the ability's text. */
    private final Map<String, String[]> alwaysYields = new ConcurrentHashMap<>();

    /** {@code channel} supplies this seat's current browser connection, or null while disconnected. */
    public BrowserSeat(final int seatIndex, final Supplier<Channel> channel) {
        this.seatIndex = seatIndex;
        this.channel = channel;
        this.gui = new ProtocolGuiGame(this);
        this.actions = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "tbld-seat-" + seatIndex + "-actions");
            t.setDaemon(true);
            return t;
        });
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public IGuiGame getGui() {
        return gui;
    }

    public PlayerView getPlayer() {
        return player;
    }

    /** Sends a newly connected browser everything it needs to show the current situation. */
    public void resend() {
        for (final String m : new String[] { lastState, lastSelection, lastPrompt, lastButtons }) {
            if (m != null) {
                write(m);
            }
        }
        for (final String m : pendingMessages.values()) {
            write(m);
        }
    }

    /** Detaches this seat from its match: unanswered questions get no answer, and later engine calls are ignored. */
    public void retire() {
        retired = true;
        actions.shutdown();
        for (final CompletableFuture<JsonElement> f : pending.values()) {
            f.complete(JsonNull.INSTANCE);
        }
    }

    /** This seat's controller in the engine, or null before the match has started. */
    public IGameController getController() {
        final PlayerView me = player;
        return me == null ? null : gui.getGameController(me);
    }

    private void write(final String json) {
        if (retired) {
            return;
        }
        final Channel c = channel.get();
        if (c != null && c.isActive()) {
            c.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    // ------------------------------------------------------------------ engine -> browser

    @Override
    public void send(final NetEvent event) {
        if (!retired && event instanceof GuiGameEvent g) {
            handleCall(g, null);
        }
    }

    @Override
    public Object sendAndWait(final IdentifiableNetEvent event) {
        if (retired || !(event instanceof GuiGameEvent original)) {
            return null;
        }
        GuiGameEvent g = original;
        if (g.getMethod() == ProtocolMethod.getAbilityToPlay) {
            // As in Forge's desktop client (CMatchUI.getAbilityToPlay). Without a click (a[2] is null)
            // the engine is putting an ability on the stack itself, such as a triggered ability, which
            // never counts as playable: a single ability is used as it is. For a click, a single
            // playable ability is used without asking, and only playable abilities are offered.
            final Object[] a = g.getObjects();
            final boolean clicked = a[2] != null;
            final List<SpellAbilityView> playable = new ArrayList<>();
            for (final Object o : asList(a[1])) {
                if (o instanceof SpellAbilityView sav && (!clicked || sav.canPlay())) {
                    playable.add(sav);
                }
            }
            if (!clicked && playable.size() == 1) {
                return playable.get(0);
            }
            if (playable.isEmpty()) {
                return null;
            }
            if (playable.size() == 1 && !playable.get(0).promptIfOnlyPossibleAbility()) {
                return playable.get(0);
            }
            g = new GuiGameEvent(ProtocolMethod.getAbilityToPlay, a[0], playable, a[2]);
        }
        final Object auto = autoAnswer(g);
        if (auto != null || AUTO_ANSWERED.contains(g.getMethod())) {
            return auto;
        }
        final int rid = g.getId();
        final CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pending.put(rid, future);
        if (retired) {
            future.complete(JsonNull.INSTANCE);
        }
        handleCall(g, rid);
        try {
            return decodeReply(g, future.get());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (final ExecutionException e) {
            return null;
        } finally {
            pending.remove(rid);
            pendingMessages.remove(rid);
        }
    }

    private void handleCall(final GuiGameEvent g, final Integer rid) {
        final ProtocolMethod method = g.getMethod();
        final Object[] args = g.getObjects();

        if (method == ProtocolMethod.openView && args.length > 0 && args[0] instanceof TrackableCollection<?> players) {
            for (final Object o : players) {
                if (o instanceof PlayerView pv) {
                    player = pv;
                    applyPrefsLater();
                    break;
                }
            }
        }

        if (stopsPending) {
            applyPrefsLater();
        }

        final JsonObject state = new JsonObject();
        state.addProperty("t", "state");
        state.add("state", snapshot());
        final String stateJson = state.toString();
        lastState = stateJson;
        write(stateJson);

        if (STATE_ONLY.contains(method)) {
            return;
        }

        final JsonObject call = new JsonObject();
        call.addProperty("t", "call");
        call.addProperty("m", method.name());
        final JsonArray encoded = new JsonArray();
        for (final Object a : args) {
            encoded.add(encode(a));
        }
        call.add("a", encoded);
        if (rid != null) {
            call.addProperty("rid", rid);
        }
        final String callJson = call.toString();
        if (DEBUG && (method == ProtocolMethod.setSelection || method == ProtocolMethod.updateButtons
                || method == ProtocolMethod.showPromptMessage)) {
            System.out.println("[tbld] seat " + seatIndex + " " + (callJson.length() > 300 ? callJson.substring(0, 300) : callJson));
        }
        if (rid != null) {
            pendingMessages.put(rid, callJson);
        } else if (method == ProtocolMethod.showPromptMessage) {
            lastPrompt = callJson;
        } else if (method == ProtocolMethod.updateButtons || method == ProtocolMethod.awaitNextInput) {
            lastButtons = callJson;
        } else if (method == ProtocolMethod.setSelection) {
            lastSelection = callJson;
        }
        write(callJson);
    }

    /** Answers that are computed on the server. Returns null if the browser must be asked. */
    private Object autoAnswer(final GuiGameEvent g) {
        final Object[] a = g.getObjects();
        switch (g.getMethod()) {
            case assignCombatDamage: {
                // All damage to the first blocker in the damage assignment order, which is always legal.
                @SuppressWarnings("unchecked")
                final List<CardView> blockers = (List<CardView>) a[1];
                final Map<CardView, Integer> result = new LinkedHashMap<>();
                if (blockers != null && !blockers.isEmpty()) {
                    result.put(blockers.get(0), (Integer) a[2]);
                }
                return result;
            }
            case assignGenericAmount: {
                @SuppressWarnings("unchecked")
                final Map<Object, Integer> targets = (Map<Object, Integer>) a[1];
                final int amount = (Integer) a[2];
                final boolean atLeastOne = (Boolean) a[3];
                final Map<Object, Integer> result = new LinkedHashMap<>();
                int remaining = amount;
                for (final Object t : targets.keySet()) {
                    final int give = atLeastOne ? 1 : 0;
                    result.put(t, give);
                    remaining -= give;
                }
                if (!result.isEmpty()) {
                    final Object first = result.keySet().iterator().next();
                    result.put(first, result.get(first) + remaining);
                }
                return result;
            }
            case sideboard: {
                final CardPool main = (CardPool) a[1];
                final List<PaperCard> result = new ArrayList<>();
                if (main != null) {
                    result.addAll(main.toFlatList());
                }
                return result;
            }
            case manipulateCardList: {
                final List<CardView> result = new ArrayList<>();
                if (a[1] instanceof Iterable<?> it) {
                    for (final Object o : it) {
                        result.add((CardView) o);
                    }
                }
                return result;
            }
            default:
                return null;
        }
    }

    private Object decodeReply(final GuiGameEvent g, final JsonElement v) {
        final Object[] a = g.getObjects();
        final boolean none = v == null || v.isJsonNull();
        switch (g.getMethod()) {
            case showConfirmDialog:
            case confirm:
                return !none && v.getAsBoolean();
            case showOptionDialog:
                return none ? -1 : v.getAsInt();
            case showInputDialog:
                return none ? null : v.getAsString();
            case getChoices:
                return pick(asList(a[3]), v);
            case chooseEntitiesForEffect:
                return pick(asList(a[1]), v);
            case chooseSingleEntityForEffect: {
                final List<Object> picked = pick(asList(a[1]), v);
                return picked.isEmpty() ? null : picked.get(0);
            }
            case getAbilityToPlay: {
                final List<Object> picked = pick(asList(a[1]), v);
                return picked.isEmpty() ? null : picked.get(0);
            }
            case order: {
                // The browser answers with indices into the source list followed by the destination list
                final List<Object> source = asList(a[4]);
                final List<Object> all = new ArrayList<>(source);
                all.addAll(asList(a[5]));
                final List<Object> ordered = none ? source : pick(all, v);
                return new IGuiGame.OrderResult<>(ordered, false);
            }
            default:
                return null;
        }
    }

    private static List<Object> asList(final Object o) {
        final List<Object> result = new ArrayList<>();
        if (o instanceof Iterable<?> it) {
            for (final Object x : it) {
                result.add(x);
            }
        }
        return result;
    }

    /** Elements of {@code options} at the indices in {@code v}, which is an index or an array of indices. */
    private static List<Object> pick(final List<Object> options, final JsonElement v) {
        final List<Object> result = new ArrayList<>();
        if (v == null || v.isJsonNull()) {
            return result;
        }
        if (v.isJsonArray()) {
            for (final JsonElement e : v.getAsJsonArray()) {
                final int i = e.getAsInt();
                if (i >= 0 && i < options.size()) {
                    result.add(options.get(i));
                }
            }
        } else {
            final int i = v.getAsInt();
            if (i >= 0 && i < options.size()) {
                result.add(options.get(i));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ browser -> engine

    /**
     * Handles one message from the browser. Actions run on this seat's own thread, because an action
     * can make the engine ask this player a question, and the answer arrives on the same connection.
     */
    public void receive(final JsonObject msg) {
        final String t = msg.has("t") ? msg.get("t").getAsString() : "";
        if ("pref".equals(t)) {
            if (msg.has("autoPass")) {
                // "off", "manaOnly" (pass when only mana abilities could be used) or "noActions"
                // (pass only when not even a mana ability could be used); true and false mean manaOnly and off
                final JsonElement mode = msg.get("autoPass");
                final String m = mode.isJsonPrimitive() && mode.getAsJsonPrimitive().isBoolean()
                        ? (mode.getAsBoolean() ? "manaOnly" : "off") : mode.getAsString();
                autoPass = !"off".equals(m);
                autoPassCountsMana = "noActions".equals(m);
            }
            if (msg.has("holdPriority")) {
                holdPriority = msg.get("holdPriority").getAsBoolean();
            }
            if (msg.has("stops") && msg.get("stops").isJsonObject()) {
                final JsonObject stops = msg.getAsJsonObject("stops");
                stopsOwnTurn = phases(stops.get("own"));
                stopsOpponentTurn = phases(stops.get("opponent"));
            }
            applyPrefsLater();
            return;
        }
        if ("reply".equals(t)) {
            final CompletableFuture<JsonElement> f = pending.get(msg.get("rid").getAsInt());
            if (f != null) {
                f.complete(msg.get("v"));
            }
            return;
        }
        if ("act".equals(t) && !retired) {
            actions.execute(() -> {
                try {
                    act(msg);
                } catch (final RuntimeException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * Applies this seat's preferences to its controller, on the seat's own thread: turning auto-pass
     * on can pass the priority the player holds right now. Before the match starts there is no
     * controller yet; the preferences are applied when the engine opens the player's view.
     */
    private void applyPrefsLater() {
        if (retired) {
            return;
        }
        actions.execute(() -> {
            final IGameController controller = retired ? null : getController();
            if (controller != null) {
                setPref(controller, FPref.YIELD_HOLD_PRIORITY, String.valueOf(holdPriority));
                // "Yield all" passes everything until the end of the turn: an opponent's spell or attack does not
                // end it (Forge's defaults do); the player switches it off to respond
                setPref(controller, FPref.YIELD_INTERRUPT_ON_OPPONENT_SPELL, "false");
                setPref(controller, FPref.YIELD_INTERRUPT_ON_ATTACKERS, "false");
                // Forge's "auto-pass if no actions": pass priority when nothing can be played or activated.
                // Forge ignores mana abilities there; the TBLD pref counts them, and is set first so the
                // sitting prompt is judged with both.
                setPref(controller, FPref.YIELD_AUTO_PASS_COUNTS_MANA_ABILITIES, String.valueOf(autoPassCountsMana));
                setPref(controller, FPref.YIELD_AUTO_PASS_NO_ACTIONS, String.valueOf(autoPass));
                applyStops(controller);
            }
        });
    }

    /**
     * Sets one of the controller's preferences, only if it changes: a change can make Forge rescan
     * the player's possible actions from this thread while the game runs, so unchanged values are
     * not sent again each time the browser sends its settings.
     */
    private static void setPref(final IGameController controller, final FPref pref, final String value) {
        if (!value.equals(controller.getYieldController().getStringPref(pref))) {
            controller.applyYieldUpdate(new YieldUpdate.SetYieldPref(pref, value));
        }
    }

    /**
     * Tells the controller which phases to pass in, on every player's turn. Forge passes priority in
     * a skipped phase only while the stack is empty, so the player can still respond to spells.
     */
    private void applyStops(final IGameController controller) {
        final Set<PhaseType> own = stopsOwnTurn;
        final Set<PhaseType> opponent = stopsOpponentTurn;
        if (own == null || opponent == null) {
            return;
        }
        final GameView gv = gui.getGameView();
        final PlayerView me = player;
        if (gv == null || me == null || gv.getPlayers() == null || gv.getPlayers().isEmpty()) {
            stopsPending = true;
            return;
        }
        stopsPending = false;
        for (final PlayerView turnPlayer : gv.getPlayers()) {
            final Set<PhaseType> stops = turnPlayer.getId() == me.getId() ? own : opponent;
            for (final PhaseType phase : STOP_PHASES) {
                controller.applyYieldUpdate(new YieldUpdate.SkipPhase(turnPlayer, phase, !stops.contains(phase)));
            }
        }
    }

    /** Always yield covers every card of the source's name with the same ability. */
    private static String nameKey(final StackItemView si) {
        return PlayerControllerHuman.yieldNameKey(si.getSourceCard() == null ? "" : si.getSourceCard().getName(), si.getKey());
    }

    /** The phases named in a JSON array. Unknown names are ignored. */
    private static Set<PhaseType> phases(final JsonElement names) {
        final Set<PhaseType> result = EnumSet.noneOf(PhaseType.class);
        if (names != null && names.isJsonArray()) {
            for (final JsonElement e : names.getAsJsonArray()) {
                try {
                    result.add(PhaseType.valueOf(e.getAsString()));
                } catch (final IllegalArgumentException | UnsupportedOperationException | IllegalStateException ex) {
                    // not a phase
                }
            }
        }
        return result;
    }

    private void act(final JsonObject msg) {
        final IGameController controller = retired ? null : getController();
        if (controller == null) {
            return;
        }
        final String m = msg.get("m").getAsString();
        final int id = msg.has("id") ? msg.get("id").getAsInt() : -1;
        switch (m) {
            case "ok" -> controller.selectButtonOk();
            case "cancel" -> controller.selectButtonCancel();
            case "card" -> {
                final CardView cv = visibleCards.get(id);
                if (cv != null) {
                    controller.selectCard(cv, null, LEFT_CLICK);
                }
            }
            case "player" -> {
                final PlayerView pv = visiblePlayers.get(id);
                if (pv != null) {
                    controller.selectPlayer(pv, LEFT_CLICK);
                }
            }
            case "stack" -> {
                final StackItemView si = visibleStack.get(id);
                if (si != null) {
                    controller.selectStackItem(si);
                }
            }
            case "mana" -> controller.useMana((byte) id);
            case "yieldAll" -> {
                // "Yield all": pass priority for the rest of the turn (End Turn turns it on too); id 0 stops yielding.
                // Turning it on while the player holds priority passes it now.
                if (id == 1) {
                    controller.applyYieldUpdate(new YieldUpdate.SetAutoPassUntilEndOfTurn(player, true));
                } else if (controller.getYieldController().autoPassUntilEndOfTurn() && controller instanceof PlayerControllerHuman pch) {
                    pch.autoPassCancel();
                }
            }
            case "alwaysYield" -> {
                // "Always yield" (as on MTGO): pass priority whenever this source's triggered ability is on top of
                // the stack, until switched off. Forge's per-card auto-yield; switching it on while the player holds
                // priority over that ability passes now.
                final String key = msg.has("key") ? msg.get("key").getAsString() : "";
                final boolean on = !msg.has("on") || msg.get("on").getAsBoolean();
                if (on) {
                    // Only for a triggered ability the player can see on the stack
                    for (final StackItemView si : visibleStack.values()) {
                        if (si.isTrigger() && key.equals(nameKey(si))) {
                            alwaysYields.put(key, new String[] {
                                    si.getSourceCard() == null ? "" : si.getSourceCard().getName(), si.getText() });
                            controller.applyYieldUpdate(new YieldUpdate.CardAutoYield(key, true));
                            break;
                        }
                    }
                } else if (alwaysYields.remove(key) != null) {
                    controller.applyYieldUpdate(new YieldUpdate.CardAutoYield(key, false));
                }
            }
            case "undo" -> controller.undoLastAction();
            case "concede" -> controller.concede();
            default -> { }
        }
    }

    private static final ITriggerEvent LEFT_CLICK = new ITriggerEvent() {
        @Override public int getButton() { return 1; }
        @Override public int getX() { return 0; }
        @Override public int getY() { return 0; }
    };

    // ------------------------------------------------------------------ encoding

    private JsonElement encode(final Object o) {
        if (o == null) {
            return JsonNull.INSTANCE;
        }
        if (o instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (o instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (o instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (o instanceof Enum<?> e) {
            return new JsonPrimitive(e.name());
        }
        if (o instanceof CardView cv) {
            return cardRef(cv);
        }
        if (o instanceof PlayerView pv) {
            final JsonObject j = new JsonObject();
            j.addProperty("player", pv.getId());
            j.addProperty("name", pv.getName());
            return j;
        }
        if (o instanceof SpellAbilityView sa) {
            final JsonObject j = new JsonObject();
            j.addProperty("sa", sa.getId());
            j.addProperty("text", sa.getDescription());
            return j;
        }
        if (o instanceof StackItemView si) {
            final JsonObject j = new JsonObject();
            j.addProperty("stack", si.getId());
            j.addProperty("text", si.getText());
            return j;
        }
        if (o instanceof GameEntityView ge) {
            final JsonObject j = new JsonObject();
            j.addProperty("entity", ge.getId());
            j.addProperty("name", ge.getName());
            return j;
        }
        if (o instanceof Selection sel) {
            final JsonObject j = new JsonObject();
            j.add("cards", ids(sel.cards()));
            j.addProperty("min", sel.min());
            j.addProperty("max", sel.max());
            j.add("targets", encode(sel.targets()));
            j.add("chosen", encode(sel.chosen()));
            j.add("actionable", ids(sel.actionable()));
            return j;
        }
        if (o instanceof PaperCard pc) {
            return new JsonPrimitive(pc.getName());
        }
        if (o instanceof DelayedReveal dr) {
            final JsonObject j = new JsonObject();
            j.add("reveal", encode(dr.getCards()));
            j.addProperty("message", dr.getMessagePrefix());
            return j;
        }
        if (o instanceof Map<?, ?> map) {
            final JsonArray arr = new JsonArray();
            for (final Map.Entry<?, ?> e : map.entrySet()) {
                final JsonArray pair = new JsonArray();
                pair.add(encode(e.getKey()));
                pair.add(encode(e.getValue()));
                arr.add(pair);
            }
            return arr;
        }
        if (o instanceof Iterable<?> it) {
            final JsonArray arr = new JsonArray();
            for (final Object x : it) {
                arr.add(encode(x));
            }
            return arr;
        }
        if (o instanceof byte[]) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(o.toString());
    }

    private static JsonArray ids(final List<? extends CardView> cards) {
        final JsonArray arr = new JsonArray();
        if (cards != null) {
            for (final CardView cv : cards) {
                arr.add(cv.getId());
            }
        }
        return arr;
    }

    /** A card named in a call or a stack item: its id, and its face if this player may see it. */
    private JsonObject cardRef(final CardView cv) {
        final JsonObject j = new JsonObject();
        j.addProperty("card", cv.getId());
        final PlayerView me = player;
        if (me == null || cv.canBeShownTo(me)) {
            final CardStateView s = cv.getCurrentState();
            j.addProperty("name", s == null ? "?" : shownName(s.getName()));
            addFace(j, cv);
        } else {
            j.addProperty("name", "(hidden card)");
            j.addProperty("hidden", true);
        }
        return j;
    }

    /**
     * A card's name as players see it. Forge names an effect after the card that made it, card id
     * included ("Searing Touch (76)'s Effect"); the id is left out.
     */
    static String shownName(final String name) {
        return name == null ? null : name.replaceAll(" \\(\\d+\\)(?='s )", "");
    }

    /** What is printed on the card: name, cost, type, text, power and toughness, colors and a picture. */
    private static void addFace(final JsonObject j, final CardView cv) {
        final CardStateView s = cv.getCurrentState();
        if (s == null) {
            return;
        }
        j.addProperty("name", shownName(s.getName()));
        j.addProperty("cost", s.getManaCost() == null ? "" : s.getManaCost().toString());
        String type = s.getType() == null ? "" : s.getType().toString();
        if (cv.isEmblem() && type.isBlank()) {
            type = TbldEmblems.typeLine(s.getName());
        }
        j.addProperty("type", type);
        // Effects and emblems made during the game have no printed text; their abilities describe them
        final String oracle = s.getOracleText();
        j.addProperty("text", oracle == null || oracle.isBlank() ? s.getAbilityText() : oracle);
        if (s.isCreature()) {
            j.addProperty("power", s.getPower());
            j.addProperty("toughness", s.getToughness());
        }
        j.addProperty("colors", colors(s));
        j.addProperty("set", s.getSetCode() == null ? "" : s.getSetCode());
        j.addProperty("token", cv.isToken());
        // An effect (such as "can't gain life this turn") is shown with the art of the card that made it
        final boolean effect = cv.getGamePieceType() == GamePieceType.EFFECT && !cv.isEmblem();
        if (effect) {
            j.addProperty("effect", true);
        }
        final String img = effect ? sourceImageUrl(s.getImageKey()) : imageUrl(cv, s);
        if (img != null) {
            j.addProperty("img", img);
        }
    }

    /**
     * The picture of the card an effect was made by. Forge gives an effect its source's image key,
     * such as "c:Searing Touch|TBLD|1"; keys of other kinds (tokens and the like) have no picture here.
     */
    static String sourceImageUrl(final String imageKey) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.CARD_PREFIX)) {
            return null;
        }
        final String[] parts = imageKey.substring(ImageKeys.CARD_PREFIX.length()).split("\\|");
        if (parts[0].isBlank()) {
            return null;
        }
        if (parts.length > 1 && "TBLD".equalsIgnoreCase(parts[1])) {
            return TbldPictures.urlFor(parts[0], artIndex(imageKey));
        }
        return "https://api.scryfall.com/cards/named?format=image&version=normal&exact="
                + URLEncoder.encode(parts[0], StandardCharsets.UTF_8);
    }

    private static String colors(final CardStateView s) {
        final StringBuilder b = new StringBuilder();
        if (s.getColors() != null) {
            if (s.getColors().hasWhite()) {
                b.append('W');
            }
            if (s.getColors().hasBlue()) {
                b.append('U');
            }
            if (s.getColors().hasBlack()) {
                b.append('B');
            }
            if (s.getColors().hasRed()) {
                b.append('R');
            }
            if (s.getColors().hasGreen()) {
                b.append('G');
            }
        }
        return b.toString();
    }

    /**
     * Where the browser loads the card's picture from, or null to have the browser draw the card.
     * TBLD cards come from this server ({@link TbldPictures}), for the card's version; official
     * cards from Scryfall by name. Tokens get the picture of Brett's token of the same name and
     * colors ({@link TbldPictures#tokenUrlFor}). Face-down cards have no picture.
     */
    static String imageUrl(final CardView cv, final CardStateView s) {
        if (cv.isFaceDown()) {
            return null;
        }
        if (cv.isToken()) {
            return TbldPictures.tokenUrlFor(s.getName(), s.isCreature() ? s.getPower() + "/" + s.getToughness() : null, colors(s));
        }
        final String set = s.getSetCode();
        if (set == null || set.isEmpty()) {
            return null;
        }
        if ("TBLD".equalsIgnoreCase(set)) {
            return TbldPictures.urlFor(s.getName(), artIndex(s.getImageKey()));
        }
        return "https://api.scryfall.com/cards/named?format=image&version=normal&exact="
                + URLEncoder.encode(s.getName(), StandardCharsets.UTF_8);
    }

    /** The art index in a card image key such as "c:Mountain|TBLD|69", or 0. */
    static int artIndex(final String imageKey) {
        if (imageKey == null) {
            return 0;
        }
        final String[] parts = imageKey.split("\\|");
        if (parts.length < 3) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[2].replaceAll("[^0-9].*$", ""));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private JsonObject cardJson(final CardView cv, final CombatView combat, final Map<Integer, CardView> seen) {
        seen.put(cv.getId(), cv);
        final JsonObject j = new JsonObject();
        j.addProperty("id", cv.getId());
        final PlayerView me = player;
        if (me != null && !cv.canBeShownTo(me)) {
            j.addProperty("hidden", true);
            return j;
        }
        addFace(j, cv);
        j.addProperty("tapped", cv.isTapped());
        j.addProperty("sick", cv.isSick());
        j.addProperty("damage", cv.getDamage());
        j.addProperty("faceDown", cv.isFaceDown());
        if (cv.getCounters() != null && !cv.getCounters().isEmpty()) {
            final JsonObject counters = new JsonObject();
            for (final CounterType ct : cv.getCounters().elementSet()) {
                counters.addProperty(ct.getName(), cv.getCounters().count(ct));
            }
            j.add("counters", counters);
        }
        if (cv.getAttachedTo() != null) {
            j.addProperty("attachedTo", cv.getAttachedTo().getId());
        }
        if (combat != null) {
            if (combat.isAttacking(cv)) {
                final GameEntityView def = combat.getDefender(cv);
                j.addProperty("attacking", def == null ? "" : def.getName());
                final JsonArray blockedBy = new JsonArray();
                for (final CardView blocker : shownBlockers(combat, cv)) {
                    blockedBy.add(cardRef(blocker).get("name"));
                }
                if (!blockedBy.isEmpty()) {
                    j.add("blockedBy", blockedBy);
                }
            }
            // The attackers this creature blocks: the first one's name, and every one's id (for the block lines)
            final JsonArray blockingIds = new JsonArray();
            for (final CardView attacker : combat.getAttackers()) {
                if (shownBlockers(combat, attacker).contains(cv)) {
                    if (blockingIds.isEmpty()) {
                        j.addProperty("blocking", cardRef(attacker).get("name").getAsString());
                    }
                    blockingIds.add(attacker.getId());
                }
            }
            if (!blockingIds.isEmpty()) {
                j.add("blockingIds", blockingIds);
            }
        }
        return j;
    }

    /**
     * The blockers of {@code attacker} that this seat's player sees: declared blockers, and, while
     * blockers are being chosen, the ones this player has chosen so far. Until blockers are
     * declared, Forge's {@link CombatView} lists the chosen ones only as planned blockers.
     */
    private List<CardView> shownBlockers(final CombatView combat, final CardView attacker) {
        final List<CardView> result = new ArrayList<>();
        final FCollection<CardView> declared = combat.getBlockers(attacker);
        if (declared != null) {
            result.addAll(declared);
        }
        final FCollection<CardView> planned = combat.getPlannedBlockers(attacker);
        final PlayerView me = player;
        if (planned != null && me != null) {
            for (final CardView b : planned) {
                if (!result.contains(b) && b.getController() != null && b.getController().getId() == me.getId()) {
                    result.add(b);
                }
            }
        }
        return result;
    }

    private JsonArray zone(final Collection<CardView> cards, final CombatView combat, final Map<Integer, CardView> seen) {
        final JsonArray arr = new JsonArray();
        if (cards != null) {
            for (final CardView cv : cards) {
                arr.add(cardJson(cv, combat, seen));
            }
        }
        return arr;
    }

    private static int size(final Collection<?> c) {
        return c == null ? 0 : c.size();
    }

    /** The game state as this seat's player is allowed to see it. */
    private JsonObject snapshot() {
        final JsonObject j = new JsonObject();
        final GameView gv = gui.getGameView();
        final PlayerView me = player;
        j.addProperty("me", me == null ? -1 : me.getId());
        if (gv == null) {
            return j;
        }
        final Map<Integer, CardView> cards = new LinkedHashMap<>();
        final Map<Integer, PlayerView> players = new LinkedHashMap<>();
        final Map<Integer, StackItemView> stack = new LinkedHashMap<>();
        final CombatView combat = gv.getCombat();

        j.addProperty("turn", gv.getTurn());
        j.addProperty("phase", gv.getPhase() == null ? "" : gv.getPhase().nameForUi);
        j.addProperty("phaseId", gv.getPhase() == null ? "" : gv.getPhase().name());
        j.addProperty("activePlayer", gv.getPlayerTurn() == null ? -1 : gv.getPlayerTurn().getId());
        j.addProperty("gameOver", gv.isGameOver());
        // This player is passing priority until the end of the turn ("Yield all", or End Turn)
        final IGameController controller = getController();
        j.addProperty("yieldAll", controller != null && controller.getYieldController().autoPassUntilEndOfTurn());

        final JsonArray ps = new JsonArray();
        if (gv.getPlayers() != null) {
            for (final PlayerView pv : gv.getPlayers()) {
                players.put(pv.getId(), pv);
                final JsonObject p = new JsonObject();
                p.addProperty("id", pv.getId());
                p.addProperty("name", pv.getName());
                p.addProperty("life", pv.getLife());
                // Player counters (energy, poison, ...), by name ("Energy")
                if (pv.getCounters() != null && !pv.getCounters().isEmpty()) {
                    final JsonObject counters = new JsonObject();
                    for (final CounterType ct : pv.getCounters().elementSet()) {
                        counters.addProperty(ct.getName(), pv.getCounters().count(ct));
                    }
                    p.add("counters", counters);
                }
                p.addProperty("library", size(pv.getCards(ZoneType.Library)));
                final boolean mine = me != null && pv.getId() == me.getId();
                if (mine) {
                    p.add("hand", zone(pv.getCards(ZoneType.Hand), combat, cards));
                } else {
                    p.addProperty("hand", size(pv.getCards(ZoneType.Hand)));
                }
                p.add("battlefield", zone(pv.getCards(ZoneType.Battlefield), combat, cards));
                p.add("graveyard", zone(pv.getCards(ZoneType.Graveyard), combat, cards));
                p.add("exile", zone(pv.getCards(ZoneType.Exile), combat, cards));
                // Emblems (TBLD starting emblems) and lasting effects
                p.add("command", zone(pv.getCards(ZoneType.Command), combat, cards));
                final JsonObject mana = new JsonObject();
                final String[] names = { "W", "U", "B", "R", "G" };
                for (int i = 0; i < 5; i++) {
                    final int amount = pv.getMana(MagicColor.WUBRG[i]);
                    if (amount > 0) {
                        mana.addProperty(names[i], amount);
                    }
                }
                // The pool keys colorless mana by ManaAtom.COLORLESS, not MagicColor.COLORLESS (which is 0)
                final int colorless = pv.getMana((byte) ManaAtom.COLORLESS);
                if (colorless > 0) {
                    mana.addProperty("C", colorless);
                }
                p.add("mana", mana);
                ps.add(p);
            }
        }
        j.add("players", ps);

        final JsonArray st = new JsonArray();
        if (gv.getStack() != null) {
            for (final StackItemView si : gv.getStack()) {
                stack.put(si.getId(), si);
                final JsonObject s = new JsonObject();
                s.addProperty("id", si.getId());
                s.addProperty("text", si.getText());
                s.addProperty("ability", si.isAbility());
                s.addProperty("trigger", si.isTrigger());
                // A triggered ability can be set to "Always yield"; the key names it (its source and its ability)
                if (si.isTrigger() && si.getKey() != null) {
                    final String nameKey = nameKey(si);
                    s.addProperty("yieldKey", nameKey);
                    s.addProperty("alwaysYield", controller != null && controller.shouldAutoYield(nameKey));
                }
                if (si.getActivatingPlayer() != null) {
                    s.addProperty("controller", si.getActivatingPlayer().getId());
                }
                if (si.getSourceCard() != null) {
                    s.add("source", cardRef(si.getSourceCard()));
                }
                // Target names for the text, and ids so the client can draw a line to each target. A spell's
                // later targets ("and another target creature", as on Stand Together) are on its sub-abilities.
                final JsonArray targets = new JsonArray();
                final JsonArray targetCards = new JsonArray();
                final JsonArray targetPlayers = new JsonArray();
                final Set<Integer> cardIds = new HashSet<>();
                final Set<Integer> playerIds = new HashSet<>();
                for (StackItemView part = si; part != null; part = part.getSubInstance()) {
                    if (part.getTargetCards() != null) {
                        for (final CardView t : part.getTargetCards()) {
                            if (cardIds.add(t.getId())) {
                                targets.add(cardRef(t).get("name"));
                                targetCards.add(t.getId());
                            }
                        }
                    }
                    if (part.getTargetPlayers() != null) {
                        for (final PlayerView t : part.getTargetPlayers()) {
                            if (playerIds.add(t.getId())) {
                                targets.add(t.getName());
                                targetPlayers.add(t.getId());
                            }
                        }
                    }
                }
                if (!targets.isEmpty()) {
                    s.add("targets", targets);
                    s.add("targetCards", targetCards);
                    s.add("targetPlayers", targetPlayers);
                }
                st.add(s);
            }
        }
        j.add("stack", st);

        // The triggered abilities this player always yields to, so the settings can list them and switch them off.
        // Forge forgets them when a game ends.
        final JsonArray yields = new JsonArray();
        for (final Map.Entry<String, String[]> e : alwaysYields.entrySet()) {
            if (controller != null && controller.shouldAutoYield(e.getKey())) {
                final JsonObject y = new JsonObject();
                y.addProperty("key", e.getKey());
                y.addProperty("name", e.getValue()[0]);
                y.addProperty("text", e.getValue()[1]);
                yields.add(y);
            }
        }
        j.add("alwaysYields", yields);

        final JsonArray log = new JsonArray();
        if (gv.getGameLog() != null) {
            // The last 100 entries, oldest first (the browser shows them in its log popup)
            final List<GameLogEntry> entries = gv.getGameLog().getAllEntries();
            for (int i = Math.max(0, entries.size() - 100); i < entries.size(); i++) {
                log.add(entries.get(i).message());
            }
        }
        j.add("log", log);

        visibleCards = cards;
        visiblePlayers = players;
        visibleStack = stack;
        return j;
    }
}
