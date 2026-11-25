package forge.tbld.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import forge.card.MagicColor;
import forge.deck.CardPool;
import forge.game.GameEntityView;
import forge.game.GameLogEntry;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.card.CounterType;
import forge.game.combat.CombatView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.Selection;
import forge.gamemodes.net.IRemote;
import forge.gamemodes.net.event.IdentifiableNetEvent;
import forge.gamemodes.net.event.NetEvent;
import forge.gamemodes.net.ProtocolGuiGame;
import forge.gamemodes.net.ProtocolMethod;
import forge.gamemodes.net.event.GuiGameEvent;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.IGameController;
import forge.item.PaperCard;
import forge.trackable.TrackableCollection;
import forge.util.ITriggerEvent;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            // As in Forge's desktop client: a single playable ability is used without asking,
            // and only playable abilities are offered.
            final Object[] a = g.getObjects();
            final List<SpellAbilityView> playable = new ArrayList<>();
            for (final Object o : asList(a[1])) {
                if (o instanceof SpellAbilityView sav && sav.canPlay()) {
                    playable.add(sav);
                }
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
                    break;
                }
            }
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
                final List<Object> source = asList(a[4]);
                final List<Object> ordered = none ? source : pick(source, v);
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

    private JsonObject cardRef(final CardView cv) {
        final JsonObject j = new JsonObject();
        j.addProperty("card", cv.getId());
        final PlayerView me = player;
        if (me == null || cv.canBeShownTo(me)) {
            final CardStateView s = cv.getCurrentState();
            j.addProperty("name", s == null ? "?" : s.getName());
        } else {
            j.addProperty("name", "(hidden card)");
        }
        return j;
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
        final CardStateView s = cv.getCurrentState();
        if (s != null) {
            j.addProperty("name", s.getName());
            j.addProperty("cost", s.getManaCost() == null ? "" : s.getManaCost().toString());
            j.addProperty("type", s.getType() == null ? "" : s.getType().toString());
            j.addProperty("text", s.getOracleText());
            if (s.isCreature()) {
                j.addProperty("power", s.getPower());
                j.addProperty("toughness", s.getToughness());
            }
        }
        j.addProperty("tapped", cv.isTapped());
        j.addProperty("sick", cv.isSick());
        j.addProperty("damage", cv.getDamage());
        j.addProperty("token", cv.isToken());
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
            }
            if (combat.isBlocking(cv)) {
                j.addProperty("blocking", true);
            }
        }
        return j;
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
        j.addProperty("activePlayer", gv.getPlayerTurn() == null ? -1 : gv.getPlayerTurn().getId());
        j.addProperty("gameOver", gv.isGameOver());

        final JsonArray ps = new JsonArray();
        if (gv.getPlayers() != null) {
            for (final PlayerView pv : gv.getPlayers()) {
                players.put(pv.getId(), pv);
                final JsonObject p = new JsonObject();
                p.addProperty("id", pv.getId());
                p.addProperty("name", pv.getName());
                p.addProperty("life", pv.getLife());
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
                final JsonObject mana = new JsonObject();
                final String[] names = { "W", "U", "B", "R", "G" };
                for (int i = 0; i < 5; i++) {
                    final int amount = pv.getMana(MagicColor.WUBRG[i]);
                    if (amount > 0) {
                        mana.addProperty(names[i], amount);
                    }
                }
                final int colorless = pv.getMana(MagicColor.COLORLESS);
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
                if (si.getSourceCard() != null) {
                    s.add("source", cardRef(si.getSourceCard()));
                }
                st.add(s);
            }
        }
        j.add("stack", st);

        final JsonArray log = new JsonArray();
        if (gv.getGameLog() != null) {
            final List<GameLogEntry> entries = gv.getGameLog().getAllEntries();
            // Newest first; getAllEntries is in chronological order
            for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - 30); i--) {
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
