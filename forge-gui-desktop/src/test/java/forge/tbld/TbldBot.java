package forge.tbld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A scripted player that plays through the web server's WebSocket protocol, the same way the
 * browser client does. After each burst of messages it looks at the latest prompt, buttons,
 * selection and state, and takes one action.
 *
 * <p>Policy: keep any opening hand; play a Mountain each turn; cast the playable creatures and
 * spells in hand, aiming spells at the opponent; an aggressive bot attacks with every creature
 * that can; a defensive bot never attacks and blocks the first attacker with every untapped
 * creature.
 */
final class TbldBot implements WebSocket.Listener {

    final int seat;
    final boolean aggressive;
    private final int mulligansToTake;
    private int mulligansTaken;
    private final WebSocket ws;
    private final StringBuilder partial = new StringBuilder();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "tbld-bot-timer");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingDecision;
    /** The buttons that the last action responded to, restored if nothing happens after it. */
    private volatile JsonArray spentButtons;
    private volatile long lastMessage = System.currentTimeMillis();

    // Latest view of the game
    volatile JsonObject state;
    volatile String prompt = "";
    volatile JsonArray buttons;
    volatile JsonObject selection;

    // Memory that keeps the bot from repeating itself
    private int landTurn = -1;
    private final Set<String> attempted = new HashSet<>();
    private final Set<Integer> blockersClicked = new HashSet<>();
    private int blockTurn = -1;
    /** The turn in which this bot last pressed OK to confirm its blocks. */
    private int blocksConfirmedTurn = -1;
    /** Steps of the one-time check of clicks on a blocked attacker and a blocking creature. */
    private int probe;
    private int probeBlocker = -1;

    // What happened in the game, for the test's assertions
    final List<String> errors = new CopyOnWriteArrayList<>();
    /** Texts the server sent with an object id ("Mountain (45)"): players don't see ids except in the game log. */
    final List<String> textsWithIds = new CopyOnWriteArrayList<>();
    final List<String> actions = new CopyOnWriteArrayList<>();
    volatile boolean gameOver;
    volatile int maxTurn;
    volatile int maxLands;
    volatile boolean castCreature;
    volatile boolean sawAttack;
    /** An opposing creature was shown blocking, which happens only once blockers are declared. */
    volatile boolean sawBlock;
    /** While choosing blockers, one of this player's creatures was shown blocking. */
    volatile boolean sawPlannedBlock;
    /** Selecting an attacker with no creature chosen left the existing block alone and chose nothing. */
    volatile boolean attackerClickIgnored;
    /** Selecting a blocking creature took its block back and did not select it. */
    volatile boolean unassignWorked;
    volatile boolean creatureDied;
    volatile boolean spellResolved;
    volatile boolean sawOpponentHand;
    volatile int myLife = Integer.MIN_VALUE;
    volatile boolean keepPressed;
    volatile int returnPrompts;
    volatile boolean returnPromptBeforeKeep;
    volatile boolean sawMulliganResult;
    volatile boolean resetReceived;
    volatile int statesAfterReset;
    volatile int firstTurnAfterReset = Integer.MAX_VALUE;
    volatile int opponentLife = Integer.MIN_VALUE;
    /** Priority prompts shown while this player had no card to play and no ability to activate. */
    volatile int idlePriorityPrompts;
    /** Priority prompts shown while this player had something to play or activate. */
    volatile int busyPriorityPrompts;
    /** Each priority prompt's phase, as "own:PHASE" or "opponent:PHASE", with ":stack" if the stack was not empty. */
    final List<String> priorityPhases = new CopyOnWriteArrayList<>();
    /** The questions this player was asked in a dialog: method and arguments. */
    final List<JsonObject> dialogs = new CopyOnWriteArrayList<>();
    /** The first scry of two or more cards: the card put on the bottom and the one kept on top. */
    volatile int scryBottom = -1;
    volatile int scryTop = -1;
    volatile boolean scryTopDrawn;
    volatile boolean scryBottomDrawn;
    /** The player whose turn turn 1 was, or -1 before turn 1. */
    volatile int turnOnePlayer = -1;
    /** Each card seen in a command zone, by name: the id of the player it belonged to, and the card itself. */
    final Map<String, Integer> commandOwners = new ConcurrentHashMap<>();
    final Map<String, JsonObject> commandCards = new ConcurrentHashMap<>();
    /** A command zone held a card before this player kept a hand. */
    volatile boolean sawCommandBeforeKeep;

    // Optional behaviour, set before the game starts
    /** With five lands or fewer, tap a land for mana before casting a spell that isn't a creature. */
    volatile boolean floatManaFirst;
    /** Cast nothing until this player controls this many lands. */
    volatile int castFromLands;
    /** Use "Yield all" on opponents' turns (see {@link #decide}). */
    volatile boolean yieldScript;

    // Spells, payment and priority
    /** Spells cast with mana already in the pool. */
    volatile int floatCasts;
    private volatile boolean floatCastPending;
    /** Payment prompts shown while casting a spell with mana already in the pool. */
    volatile int paymentPromptsAfterFloat;
    private volatile boolean castPending;
    /** Priority prompts right after a cast, with this player's own spell on top of the stack. */
    volatile int heldAfterCast;
    /** Priority prompts right after a cast, with this player's own triggered ability on top of the stack. */
    volatile int triggerAfterCast;
    /** Priority prompts right after a cast. */
    volatile int promptsAfterCast;
    /** Each optional-cost question: its arguments, and the mana this player had (untapped lands and pool). */
    final List<JsonObject> optionalCostAsks = new CopyOnWriteArrayList<>();
    /** Idle priority prompts while this player had no mana ability to use (no untapped land, no Player Two emblem). */
    volatile int idleWithoutManaSource;

    // "Yield all"
    volatile int yieldTurn = -1;
    volatile int yieldOffTurn = -1;
    /** Priority prompts in the turn this player yielded all, after yielding. */
    volatile int promptsWhileYielding;
    /** The game said this player was yielding, in the turn they yielded. */
    volatile boolean sawYieldOn;
    /** Some state after the yielding turn said this player was not yielding. */
    volatile boolean sawYieldEnd;
    /** A later state in the yielding turn still said this player was not yielding. */
    volatile boolean yieldEndedEarly;
    /** Priority prompts in the turn this player switched "Yield all" off again. */
    volatile int promptsAfterYieldOff;
    /** A stack item that targeted this player carried this player's id in its targetPlayers. */
    volatile boolean sawTargetIdOnStack;

    // "Always yield"
    /** Switch "Always yield" on for each of this player's triggered abilities when it first gets priority over one. */
    volatile boolean alwaysYieldOwnTriggers;
    /** Stack ids of the triggered abilities this player switched "Always yield" on at. */
    final Set<Integer> yieldSetOn = ConcurrentHashMap.newKeySet();
    /** Stack ids of triggered abilities that were always yielded to from the moment they were seen. */
    final Set<Integer> autoYieldedTriggers = ConcurrentHashMap.newKeySet();
    /** {@link #promptsAfterCast} when the first of {@link #autoYieldedTriggers} was seen. */
    volatile int promptsAfterCastAtFirstAutoYield = -1;
    /** Priority prompts with a triggered ability this player always yields to on top of the stack. */
    volatile int promptsOverYieldedTrigger;
    /** The snapshot's list of always-yielded abilities named this source. */
    final Set<String> alwaysYieldsListed = ConcurrentHashMap.newKeySet();

    TbldBot(final HttpClient http, final int port, final int seat, final boolean aggressive) throws Exception {
        this(http, port, seat, aggressive, 0);
    }

    TbldBot(final HttpClient http, final int port, final int seat, final boolean aggressive, final int mulligansToTake) throws Exception {
        this.seat = seat;
        this.aggressive = aggressive;
        this.mulligansToTake = mulligansToTake;
        this.ws = http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws?seat=" + seat), this)
                .get(10, TimeUnit.SECONDS);
        // If nothing has happened for a while, look again: an action may have arrived before the engine was ready for it.
        timer.scheduleWithFixedDelay(() -> {
            if (!gameOver && System.currentTimeMillis() - lastMessage > 1500) {
                synchronized (this) {
                    if (buttons == null) {
                        buttons = spentButtons;
                    }
                }
                decide();
            }
        }, 1500, 1000, TimeUnit.MILLISECONDS);
    }

    /** Turns the server's auto-pass on or off for this seat, as the browser's checkbox does. */
    void setAutoPass(final boolean on) {
        final JsonObject o = new JsonObject();
        o.addProperty("t", "pref");
        o.addProperty("autoPass", on);
        send(o);
    }

    /** Sends one preference, as the browser's settings do: autoPass ("off", "manaOnly", "noActions") or holdPriority. */
    void setPref(final String key, final JsonElement value) {
        final JsonObject o = new JsonObject();
        o.addProperty("t", "pref");
        o.add(key, value);
        send(o);
    }

    /** Sets the phases this seat stops in, as the browser's phase bar does. */
    void setStops(final List<String> own, final List<String> opponent) {
        final JsonObject stops = new JsonObject();
        final JsonArray o = new JsonArray();
        own.forEach(o::add);
        final JsonArray p = new JsonArray();
        opponent.forEach(p::add);
        stops.add("own", o);
        stops.add("opponent", p);
        final JsonObject msg = new JsonObject();
        msg.addProperty("t", "pref");
        msg.add("stops", stops);
        send(msg);
    }

    void restart() {
        final JsonObject o = new JsonObject();
        o.addProperty("t", "restart");
        send(o);
    }

    void close() {
        timer.shutdownNow();
        ws.abort();
    }

    @Override
    public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
        partial.append(data);
        if (last) {
            final String text = partial.toString();
            partial.setLength(0);
            try {
                final JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
                findIds(msg);
                handle(msg);
            } catch (final RuntimeException e) {
                errors.add("seat " + seat + ": " + e + " on " + text);
            }
        }
        webSocket.request(1);
        return null;
    }

    private static final Pattern OBJECT_ID = Pattern.compile("(?<=\\S) [(\\[]H?\\d+[)\\]]");

    /** Notes every text of the message that shows an object id, except the game log and the keys that name an ability. */
    private void findIds(final JsonElement e) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            if (OBJECT_ID.matcher(e.getAsString()).find() && textsWithIds.size() < 20) {
                textsWithIds.add("seat " + seat + ": " + e.getAsString());
            }
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(this::findIds);
        } else if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().stream()
                    .filter(f -> !List.of("log", "key", "yieldKey").contains(f.getKey()))
                    .forEach(f -> findIds(f.getValue()));
        }
    }

    @Override
    public void onError(final WebSocket webSocket, final Throwable error) {
        errors.add("seat " + seat + ": " + error);
    }

    // ------------------------------------------------------------------ receiving

    private synchronized void handle(final JsonObject msg) {
        lastMessage = System.currentTimeMillis();
        final String t = msg.get("t").getAsString();
        switch (t) {
            case "reset" -> {
                resetReceived = true;
                gameOver = false;
                buttons = null;
                spentButtons = null;
                selection = null;
                landTurn = -1;
                attempted.clear();
            }
            case "state" -> {
                final JsonObject s = msg.getAsJsonObject("state");
                if (resetReceived && s.has("turn") && statesAfterReset++ == 0) {
                    firstTurnAfterReset = s.get("turn").getAsInt();
                }
                observe(s);
            }
            case "call" -> {
                final String m = msg.get("m").getAsString();
                final JsonArray a = msg.getAsJsonArray("a");
                if (msg.has("rid")) {
                    answer(msg.get("rid").getAsInt(), m, a);
                    return;
                }
                switch (m) {
                    case "showPromptMessage" -> {
                        prompt = a.get(1).isJsonNull() ? "" : a.get(1).getAsString();
                        if (floatCastPending && prompt.toLowerCase().contains("pay mana cost")) {
                            paymentPromptsAfterFloat++;
                        }
                        if (prompt.contains("Return ") && prompt.contains("bottom of your library")) {
                            returnPrompts++;
                            if (!keepPressed) {
                                returnPromptBeforeKeep = true;
                            }
                        }
                    }
                    case "updateButtons" -> {
                        buttons = a;
                        spentButtons = null;
                        // A priority prompt sends its selection and message before its buttons
                        if (prompt.startsWith("Priority") && !a.get(1).isJsonNull() && "OK".equals(a.get(1).getAsString())) {
                            if (idSet("actionable").isEmpty()) {
                                idlePriorityPrompts++;
                                if (myLands(true) == 0 && !hasCommandCard("Player Two")) {
                                    idleWithoutManaSource++;
                                }
                            } else {
                                busyPriorityPrompts++;
                            }
                            notePriorityPrompt();
                            final JsonObject s = state;
                            if (s != null && s.has("phaseId")) {
                                priorityPhases.add((myTurn() ? "own:" : "opponent:") + s.get("phaseId").getAsString()
                                        + (s.getAsJsonArray("stack").isEmpty() ? "" : ":stack"));
                            }
                        }
                    }
                    case "awaitNextInput" -> {
                        buttons = null;
                        spentButtons = null;
                    }
                    case "setSelection" -> selection = a.get(0).isJsonObject() ? a.get(0).getAsJsonObject() : null;
                    default -> { }
                }
            }
            default -> { }
        }
        // Decide once this burst of messages is over
        if (pendingDecision != null) {
            pendingDecision.cancel(false);
        }
        pendingDecision = timer.schedule(this::decide, 80, TimeUnit.MILLISECONDS);
    }

    private void observe(final JsonObject s) {
        state = s;
        if (!s.has("turn")) {
            return;
        }
        maxTurn = Math.max(maxTurn, s.get("turn").getAsInt());
        gameOver = s.get("gameOver").getAsBoolean();
        if (turnOnePlayer < 0 && s.get("turn").getAsInt() == 1 && s.has("activePlayer")) {
            turnOnePlayer = s.get("activePlayer").getAsInt();
        }
        if (s.has("stack")) {
            for (final JsonElement se : s.getAsJsonArray("stack")) {
                final JsonObject item = se.getAsJsonObject();
                if (item.has("targetPlayers")) {
                    for (final JsonElement t : item.getAsJsonArray("targetPlayers")) {
                        if (t.getAsInt() == s.get("me").getAsInt()) {
                            sawTargetIdOnStack = true;
                        }
                    }
                }
                final int sid = item.get("id").getAsInt();
                if (item.has("alwaysYield") && item.get("alwaysYield").getAsBoolean() && !yieldSetOn.contains(sid)
                        && autoYieldedTriggers.add(sid) && promptsAfterCastAtFirstAutoYield < 0) {
                    promptsAfterCastAtFirstAutoYield = promptsAfterCast;
                }
            }
        }
        if (s.has("alwaysYields")) {
            for (final JsonElement y : s.getAsJsonArray("alwaysYields")) {
                alwaysYieldsListed.add(y.getAsJsonObject().get("name").getAsString());
            }
        }
        if (yieldTurn >= 0 && s.has("yieldAll")) {
            final boolean yielding = s.get("yieldAll").getAsBoolean();
            if (s.get("turn").getAsInt() == yieldTurn && yielding) {
                sawYieldOn = true;
            } else if (s.get("turn").getAsInt() > yieldTurn && !yielding) {
                sawYieldEnd = true;
            }
        }
        final int me = s.get("me").getAsInt();
        for (final JsonElement pe : s.getAsJsonArray("players")) {
            final JsonObject p = pe.getAsJsonObject();
            final boolean mine = p.get("id").getAsInt() == me;
            if (p.has("command")) {
                for (final JsonElement ce : p.getAsJsonArray("command")) {
                    final JsonObject c = ce.getAsJsonObject();
                    if (c.has("name")) {
                        commandOwners.putIfAbsent(c.get("name").getAsString(), p.get("id").getAsInt());
                        commandCards.put(c.get("name").getAsString(), c);
                        if (!keepPressed) {
                            sawCommandBeforeKeep = true;
                        }
                    }
                }
            }
            if (mine) {
                myLife = p.get("life").getAsInt();
                if (p.get("hand").isJsonArray()) {
                    for (final JsonElement ce : p.getAsJsonArray("hand")) {
                        final int id = ce.getAsJsonObject().get("id").getAsInt();
                        if (id == scryTop) {
                            scryTopDrawn = true;
                        }
                        if (id == scryBottom) {
                            scryBottomDrawn = true;
                        }
                    }
                }
                if (keepPressed && p.get("hand").isJsonArray() && p.getAsJsonArray("hand").size() == 6
                        && p.get("library").getAsInt() == 34) {
                    sawMulliganResult = true;
                }
            } else {
                opponentLife = p.get("life").getAsInt();
                if (p.get("hand").isJsonArray()) {
                    sawOpponentHand = true;
                }
            }
            int lands = 0;
            for (final JsonElement ce : p.getAsJsonArray("battlefield")) {
                final JsonObject c = ce.getAsJsonObject();
                if (isLand(c)) {
                    lands++;
                }
                if (isCreature(c)) {
                    if (mine) {
                        castCreature = true;
                    }
                    if (c.has("attacking")) {
                        sawAttack = true;
                    }
                    if (c.has("blocking")) {
                        if (!mine) {
                            sawBlock = true;
                        } else if (prompt.toLowerCase().contains("block") && blocksConfirmedTurn != turn()) {
                            sawPlannedBlock = true;
                        }
                    }
                }
            }
            if (mine) {
                maxLands = Math.max(maxLands, lands);
            }
            for (final JsonElement ce : p.getAsJsonArray("graveyard")) {
                final JsonObject c = ce.getAsJsonObject();
                if (isCreature(c)) {
                    creatureDied = true;
                } else if (c.has("type") && c.get("type").getAsString().matches(".*(Instant|Sorcery).*")) {
                    spellResolved = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ deciding

    /** Bookkeeping for each priority prompt: what followed a cast, and prompts while yielding. */
    private void notePriorityPrompt() {
        final JsonObject s = state;
        if (s != null && s.has("stack") && !s.getAsJsonArray("stack").isEmpty()) {
            final JsonObject top = s.getAsJsonArray("stack").get(0).getAsJsonObject();
            if (top.has("alwaysYield") && top.get("alwaysYield").getAsBoolean()) {
                promptsOverYieldedTrigger++;
            }
        }
        if (castPending) {
            castPending = false;
            floatCastPending = false;
            promptsAfterCast++;
            if (s != null && s.has("stack")) {
                final JsonArray st = s.getAsJsonArray("stack");
                final JsonObject top = st.isEmpty() ? null : st.get(0).getAsJsonObject();
                if (top != null && top.has("controller") && top.get("controller").getAsInt() == s.get("me").getAsInt()) {
                    if (top.has("trigger") && top.get("trigger").getAsBoolean()) {
                        triggerAfterCast++;
                    } else {
                        heldAfterCast++;
                    }
                }
            }
        }
        if (yieldTurn >= 0 && turn() == yieldTurn) {
            promptsWhileYielding++;
        }
        if (yieldOffTurn >= 0 && turn() == yieldOffTurn) {
            promptsAfterYieldOff++;
        }
    }

    /** This player's lands on the battlefield, or only the untapped ones. */
    private int myLands(final boolean untappedOnly) {
        final JsonObject me = me();
        int n = 0;
        if (me != null) {
            for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                final JsonObject c = ce.getAsJsonObject();
                if (isLand(c) && !(untappedOnly && c.has("tapped") && c.get("tapped").getAsBoolean())) {
                    n++;
                }
            }
        }
        return n;
    }

    /** One of this player's untapped lands, or -1. */
    private int untappedLand() {
        final JsonObject me = me();
        if (me != null) {
            for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                final JsonObject c = ce.getAsJsonObject();
                if (isLand(c) && c.has("tapped") && !c.get("tapped").getAsBoolean()) {
                    return c.get("id").getAsInt();
                }
            }
        }
        return -1;
    }

    /** The mana in a player's pool. */
    private static int pool(final JsonObject p) {
        int n = 0;
        if (p != null && p.has("mana") && p.get("mana").isJsonObject()) {
            for (final Map.Entry<String, JsonElement> e : p.getAsJsonObject("mana").entrySet()) {
                n += e.getValue().getAsInt();
            }
        }
        return n;
    }

    private boolean hasCommandCard(final String name) {
        final JsonObject me = me();
        if (me != null && me.has("command")) {
            for (final JsonElement ce : me.getAsJsonArray("command")) {
                final JsonObject c = ce.getAsJsonObject();
                if (c.has("name") && name.equals(c.get("name").getAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLand(final JsonObject c) {
        return c.has("type") && c.get("type").getAsString().contains("Land");
    }

    private static boolean isCreature(final JsonObject c) {
        return c.has("type") && c.get("type").getAsString().contains("Creature");
    }

    private int turn() {
        final JsonObject s = state;
        return s == null || !s.has("turn") ? 0 : s.get("turn").getAsInt();
    }

    private boolean myTurn() {
        final JsonObject s = state;
        return s != null && s.has("activePlayer") && s.get("activePlayer").getAsInt() == s.get("me").getAsInt();
    }

    private JsonObject me() {
        return player(true);
    }

    /** This player's card on the battlefield with the given id, or null. */
    private JsonObject myCard(final int id) {
        final JsonObject me = me();
        if (me != null) {
            for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                if (ce.getAsJsonObject().get("id").getAsInt() == id) {
                    return ce.getAsJsonObject();
                }
            }
        }
        return null;
    }

    private JsonObject opponent() {
        return player(false);
    }

    private JsonObject player(final boolean mine) {
        final JsonObject s = state;
        if (s == null || !s.has("players")) {
            return null;
        }
        final int me = s.get("me").getAsInt();
        for (final JsonElement pe : s.getAsJsonArray("players")) {
            final JsonObject p = pe.getAsJsonObject();
            if ((p.get("id").getAsInt() == me) == mine) {
                return p;
            }
        }
        return null;
    }

    private Set<Integer> idSet(final String key) {
        final Set<Integer> result = new HashSet<>();
        final JsonObject sel = selection;
        if (sel != null && sel.has(key)) {
            for (final JsonElement e : sel.getAsJsonArray(key)) {
                if (e.isJsonPrimitive()) {
                    result.add(e.getAsInt());
                } else if (e.isJsonObject() && e.getAsJsonObject().has("card")) {
                    result.add(e.getAsJsonObject().get("card").getAsInt());
                }
            }
        }
        return result;
    }

    private synchronized void decide() {
        if (gameOver || buttons == null || state == null) {
            return;
        }
        final JsonArray b = buttons;
        final String label1 = b.get(1).isJsonNull() ? "" : b.get(1).getAsString();
        final String label2 = b.get(2).isJsonNull() ? "" : b.get(2).getAsString();
        final boolean okEnabled = b.get(3).getAsBoolean();
        final String p = prompt;
        final String pl = p.toLowerCase();
        final JsonObject me = me();
        final JsonObject opp = opponent();
        if (me == null || opp == null) {
            return;
        }

        if ("Keep".equals(label1) && "Mulligan".equals(label2)) {
            if (mulligansTaken < mulligansToTake) {
                mulligansTaken++;
                act("cancel", -1, "mulligan");
            } else {
                keepPressed = true;
                act("ok", -1, "keep");
            }
            return;
        }
        if ("Auto".equals(label1) && okEnabled) {
            act("ok", -1, "auto-pay");
            return;
        }
        if ("Decline".equals(label2)) {
            act("cancel", -1, "decline yield suggestion");
            return;
        }
        if (pl.contains("pay mana cost") && "Cancel".equals(label2) && b.get(4).getAsBoolean()) {
            act("cancel", -1, "cancel unpayable spell");
            return;
        }

        if (pl.contains("select creatures to attack")) {
            if (aggressive) {
                final Set<Integer> candidates = idSet("actionable");
                for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                    final JsonObject c = ce.getAsJsonObject();
                    final int id = c.get("id").getAsInt();
                    if (candidates.contains(id) && !c.has("attacking") && attempted.add("attack:" + turn() + ":" + id)) {
                        act("card", id, "attack with " + c.get("name").getAsString());
                        return;
                    }
                }
            }
            act("ok", -1, "done attacking");
            return;
        }

        if (pl.contains("block")) {
            if (blockTurn != turn()) {
                blockTurn = turn();
                blockersClicked.clear();
            }
            JsonObject attacker = null;
            for (final JsonElement ce : opp.getAsJsonArray("battlefield")) {
                if (ce.getAsJsonObject().has("attacking")) {
                    attacker = ce.getAsJsonObject();
                    break;
                }
            }
            if (attacker != null && !aggressive) {
                final Set<Integer> chosen = idSet("chosen");
                // Once per game, after the first block: selecting an attacker with no creature chosen
                // must change nothing, and selecting the blocking creature must take its block back
                // without selecting it
                if (probe == 0 && chosen.isEmpty()) {
                    for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                        final JsonObject c = ce.getAsJsonObject();
                        if (c.has("blocking")) {
                            probe = 1;
                            probeBlocker = c.get("id").getAsInt();
                            act("card", attacker.get("id").getAsInt(), "select attacker with no creature chosen");
                            return;
                        }
                    }
                }
                if (probe == 1) {
                    final JsonObject c = myCard(probeBlocker);
                    attackerClickIgnored = c != null && c.has("blocking") && chosen.isEmpty();
                    probe = 2;
                    act("card", probeBlocker, "select the blocking creature");
                    return;
                }
                if (probe == 2) {
                    final JsonObject c = myCard(probeBlocker);
                    unassignWorked = c != null && !c.has("blocking") && !chosen.contains(probeBlocker);
                    probe = 3;
                }
                // Choose a creature, then the attacker it blocks
                if (!chosen.isEmpty()) {
                    if (attempted.add("assign:" + turn() + ":" + chosen)) {
                        act("card", attacker.get("id").getAsInt(), "block " + attacker.get("name").getAsString());
                        return;
                    }
                } else {
                    for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                        final JsonObject c = ce.getAsJsonObject();
                        final int id = c.get("id").getAsInt();
                        if (isCreature(c) && !c.get("tapped").getAsBoolean() && !c.has("blocking") && blockersClicked.add(id)) {
                            act("card", id, "choose blocker " + c.get("name").getAsString());
                            return;
                        }
                    }
                }
            }
            if (okEnabled) {
                blocksConfirmedTurn = turn();
                act("ok", -1, "done blocking");
            }
            return;
        }

        // Choosing targets: the opponent if allowed, otherwise an opposing card, otherwise anything offered
        final JsonObject sel = selection;
        // Valid cards come in the selection's cards (the targets are players and stack items): a spell that
        // targets only creatures (or an Aura) offers no players. Each card is tried once a turn; if none
        // works, the spell is cancelled.
        if (sel != null && pl.contains("target") && (!sel.has("targets") || sel.getAsJsonArray("targets").isEmpty())
                && sel.has("cards") && !sel.getAsJsonArray("cards").isEmpty()) {
            final Set<Integer> chosen = idSet("chosen");
            final Set<Integer> mine = new HashSet<>();
            for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                mine.add(ce.getAsJsonObject().get("id").getAsInt());
            }
            Integer pick = null;
            for (final boolean opposing : new boolean[] {true, false}) {
                for (final JsonElement e : sel.getAsJsonArray("cards")) {
                    final int id = e.getAsInt();
                    if (pick == null && mine.contains(id) != opposing && !chosen.contains(id) && !attempted.contains("target:" + turn() + ":" + id)) {
                        pick = id;
                    }
                }
            }
            if (pick != null) {
                attempted.add("target:" + turn() + ":" + pick);
                act("card", pick, "target card");
            } else if (okEnabled) {
                act("ok", -1, "ok");
            } else {
                act("cancel", -1, "cancel targeting");
            }
            return;
        }
        if (sel != null && sel.has("targets") && !sel.getAsJsonArray("targets").isEmpty()) {
            JsonObject pick = null;
            for (final JsonElement e : sel.getAsJsonArray("targets")) {
                final JsonObject o = e.getAsJsonObject();
                if (o.has("player") && o.get("player").getAsInt() == opp.get("id").getAsInt()) {
                    pick = o;
                    break;
                }
            }
            if (pick == null) {
                pick = sel.getAsJsonArray("targets").get(0).getAsJsonObject();
            }
            if (pick.has("player")) {
                act("player", pick.get("player").getAsInt(), "target player");
            } else if (pick.has("card")) {
                act("card", pick.get("card").getAsInt(), "target card");
            } else if (okEnabled) {
                act("ok", -1, "ok");
            }
            return;
        }

        if ((pl.contains("discard") || pl.contains("return")) && !okEnabled) {
            final JsonElement hand = me.get("hand");
            final Set<Integer> chosen = idSet("chosen");
            if (hand.isJsonArray()) {
                for (final JsonElement c : hand.getAsJsonArray()) {
                    final int id = c.getAsJsonObject().get("id").getAsInt();
                    if (!chosen.contains(id)) {
                        act("card", id, "discard");
                        return;
                    }
                }
            }
            return;
        }

        // Priority
        final boolean priority = "OK".equals(label1) && okEnabled && !label2.isEmpty();
        // "Always yield" to this player's own triggered ability on top of the stack, as a person would from its tile
        if (priority && alwaysYieldOwnTriggers && !state.getAsJsonArray("stack").isEmpty()) {
            final JsonObject top = state.getAsJsonArray("stack").get(0).getAsJsonObject();
            if (top.has("yieldKey") && !top.get("alwaysYield").getAsBoolean() && top.has("controller")
                    && top.get("controller").getAsInt() == state.get("me").getAsInt() && yieldSetOn.add(top.get("id").getAsInt())) {
                if (actions.size() < 2000) {
                    actions.add("t" + turn() + " always yield to " + top.get("text").getAsString());
                }
                final JsonObject o = new JsonObject();
                o.addProperty("t", "act");
                o.addProperty("m", "alwaysYield");
                o.addProperty("key", top.get("yieldKey").getAsString());
                o.addProperty("on", true);
                // Switching it on passes the priority this player holds, so these buttons are spent
                spentButtons = buttons;
                buttons = null;
                send(o);
                return;
            }
        }
        // "Yield all" script, on an opponent's turn: first yield for the rest of a turn; on a later turn,
        // yield and switch it straight off again
        if (priority && yieldScript && !myTurn() && turn() >= 3) {
            if (yieldTurn < 0) {
                yieldTurn = turn();
                act("yieldAll", 1, "yield all");
                return;
            }
            if (yieldOffTurn < 0 && turn() >= yieldTurn + 2) {
                yieldOffTurn = turn();
                act("yieldAll", 1, "yield all");
                act("yieldAll", 0, "stop yielding");
                return;
            }
        }
        if (priority && myTurn() && state.get("phase").getAsString().toLowerCase().contains("main")
                && state.getAsJsonArray("stack").isEmpty() && me.get("hand").isJsonArray()) {
            final Set<Integer> playable = idSet("actionable");
            final JsonArray hand = me.getAsJsonArray("hand");
            final int lands = myLands(false);
            // A land first, then creatures, then other spells
            for (int pass = 0; pass < 3; pass++) {
                for (final JsonElement ce : hand) {
                    final JsonObject c = ce.getAsJsonObject();
                    final int id = c.get("id").getAsInt();
                    if (!playable.contains(id)) {
                        continue;
                    }
                    final boolean land = isLand(c);
                    final boolean creature = isCreature(c);
                    if (pass == 0 && land && landTurn != turn()) {
                        landTurn = turn();
                        act("card", id, "play " + c.get("name").getAsString());
                        return;
                    }
                    if (!land && lands < castFromLands) {
                        continue;
                    }
                    // Tap a land for mana first, so the spell is cast with mana already in the pool
                    if (pass == 2 && !land && !creature && floatManaFirst && lands <= 5 && pool(me) == 0
                            && attempted.add("float:" + turn() + ":" + id)) {
                        final int source = untappedLand();
                        if (source >= 0) {
                            act("card", source, "tap a land for mana");
                            return;
                        }
                    }
                    if ((pass == 1 && creature || pass == 2 && !land && !creature) && attempted.add("cast:" + turn() + ":" + id)) {
                        if (pool(me) > 0) {
                            floatCasts++;
                            floatCastPending = true;
                        }
                        castPending = true;
                        act("card", id, "cast " + c.get("name").getAsString());
                        return;
                    }
                }
            }
        }
        if (okEnabled) {
            act("ok", -1, "pass");
        }
    }

    // ------------------------------------------------------------------ answering questions

    private void answer(final int rid, final String m, final JsonArray a) {
        final JsonObject asked = new JsonObject();
        asked.addProperty("m", m);
        asked.add("a", a);
        dialogs.add(asked);
        final boolean optionalCost = "getChoices".equals(m) && !a.get(0).isJsonNull() && a.get(0).getAsString().toLowerCase().contains("optional cost");
        if (optionalCost) {
            final JsonObject ask = new JsonObject();
            ask.add("a", a);
            // The Player Two emblem's mana counts too
            ask.addProperty("mana", myLands(true) + pool(me()) + (hasCommandCard("Player Two") ? 1 : 0));
            optionalCostAsks.add(ask);
        }
        final JsonElement v;
        switch (m) {
            case "order" -> {
                // Scry: the first time, put the first card on the bottom; later, keep everything on top
                final String title = a.get(0).isJsonNull() ? "" : a.get(0).getAsString();
                final JsonArray source = a.get(4).getAsJsonArray();
                if (title.contains("bottom") && source.size() >= 2) {
                    final JsonArray chosen = new JsonArray();
                    if (scryTop < 0) {
                        scryBottom = source.get(0).getAsJsonObject().get("card").getAsInt();
                        scryTop = source.get(1).getAsJsonObject().get("card").getAsInt();
                        chosen.add(0);
                    }
                    v = chosen;
                } else {
                    v = JsonNull.INSTANCE;
                }
            }
            case "confirm", "showConfirmDialog" -> v = new JsonPrimitive(true);
            case "showOptionDialog" -> v = new JsonPrimitive(0);
            case "showInputDialog" -> v = new JsonPrimitive("");
            case "getChoices" -> {
                // A bot that floats mana for its spell declines optional costs (the last choice, "No buyback"):
                // the emblem's mana can make buyback affordable, but Auto payment never spends the emblem
                if (floatManaFirst && optionalCost) {
                    final JsonArray last = new JsonArray();
                    last.add(a.get(3).getAsJsonArray().size() - 1);
                    v = last;
                } else {
                    v = indices(a.get(1).getAsInt());
                }
            }
            case "chooseEntitiesForEffect" -> v = indices(Math.max(1, a.get(2).getAsInt()));
            case "chooseSingleEntityForEffect", "getAbilityToPlay" -> v = new JsonPrimitive(0);
            default -> v = JsonNull.INSTANCE;
        }
        actions.add("answer " + m + " " + v);
        final JsonObject o = new JsonObject();
        o.addProperty("t", "reply");
        o.addProperty("rid", rid);
        o.add("v", v);
        timer.schedule(() -> send(o), 20, TimeUnit.MILLISECONDS);
    }

    private static JsonArray indices(final int n) {
        final JsonArray arr = new JsonArray();
        for (int i = 0; i < n; i++) {
            arr.add(i);
        }
        return arr;
    }

    private void act(final String m, final int id, final String description) {
        if (actions.size() < 2000) {
            actions.add("t" + turn() + " " + description);
        }
        final JsonObject o = new JsonObject();
        o.addProperty("t", "act");
        o.addProperty("m", m);
        if (id >= 0) {
            o.addProperty("id", id);
        }
        // Once acted on, the current buttons are spent until the engine sends new ones
        spentButtons = buttons;
        buttons = null;
        send(o);
    }

    private synchronized void send(final JsonObject o) {
        ws.sendText(o.toString(), true).join();
    }

    String summary() {
        // A copy: the bot may still be adding actions, which would break a view of the live list
        final List<String> all = List.copyOf(actions);
        final List<String> last = all.subList(Math.max(0, all.size() - 25), all.size());
        return "seat " + seat + ": turn " + maxTurn + ", life " + myLife + ", lands " + maxLands
                + ", priority prompts idle/busy " + idlePriorityPrompts + "/" + busyPriorityPrompts
                + ", prompt=" + prompt.replace('\n', ' ') + ", buttons=" + buttons
                + "\n  last actions: " + String.join(" | ", last);
    }
}
