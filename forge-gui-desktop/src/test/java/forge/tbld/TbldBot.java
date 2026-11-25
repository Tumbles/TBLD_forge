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
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private int attackerClickedTurn = -1;
    private final Set<Integer> blockersClicked = new HashSet<>();
    private int blockTurn = -1;

    // What happened in the game, for the test's assertions
    final List<String> errors = new CopyOnWriteArrayList<>();
    final List<String> actions = new CopyOnWriteArrayList<>();
    volatile boolean gameOver;
    volatile int maxTurn;
    volatile int maxLands;
    volatile boolean castCreature;
    volatile boolean sawAttack;
    volatile boolean sawBlock;
    volatile boolean creatureDied;
    volatile boolean spellResolved;
    volatile boolean sawOpponentHand;
    volatile int myLife = Integer.MIN_VALUE;
    volatile int opponentLife = Integer.MIN_VALUE;

    TbldBot(final HttpClient http, final int port, final int seat, final boolean aggressive) throws Exception {
        this.seat = seat;
        this.aggressive = aggressive;
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
                handle(JsonParser.parseString(text).getAsJsonObject());
            } catch (final RuntimeException e) {
                errors.add("seat " + seat + ": " + e + " on " + text);
            }
        }
        webSocket.request(1);
        return null;
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
            case "state" -> observe(msg.getAsJsonObject("state"));
            case "call" -> {
                final String m = msg.get("m").getAsString();
                final JsonArray a = msg.getAsJsonArray("a");
                if (msg.has("rid")) {
                    answer(msg.get("rid").getAsInt(), m, a);
                    return;
                }
                switch (m) {
                    case "showPromptMessage" -> prompt = a.get(1).isJsonNull() ? "" : a.get(1).getAsString();
                    case "updateButtons" -> {
                        buttons = a;
                        spentButtons = null;
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
        final int me = s.get("me").getAsInt();
        for (final JsonElement pe : s.getAsJsonArray("players")) {
            final JsonObject p = pe.getAsJsonObject();
            final boolean mine = p.get("id").getAsInt() == me;
            if (mine) {
                myLife = p.get("life").getAsInt();
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
                        sawBlock = true;
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
            act("ok", -1, "keep");
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
                if (attackerClickedTurn != turn()) {
                    attackerClickedTurn = turn();
                    act("card", attacker.get("id").getAsInt(), "choose attacker to block");
                    return;
                }
                for (final JsonElement ce : me.getAsJsonArray("battlefield")) {
                    final JsonObject c = ce.getAsJsonObject();
                    final int id = c.get("id").getAsInt();
                    if (isCreature(c) && !c.get("tapped").getAsBoolean() && !c.has("blocking") && blockersClicked.add(id)) {
                        act("card", id, "block with " + c.get("name").getAsString());
                        return;
                    }
                }
            }
            if (okEnabled) {
                act("ok", -1, "done blocking");
            }
            return;
        }

        // Choosing targets: the opponent if allowed, otherwise an opposing card, otherwise anything offered
        final JsonObject sel = selection;
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
            if (hand.isJsonArray() && !hand.getAsJsonArray().isEmpty()) {
                act("card", hand.getAsJsonArray().get(0).getAsJsonObject().get("id").getAsInt(), "discard");
            }
            return;
        }

        // Priority
        final boolean priority = "OK".equals(label1) && okEnabled && !label2.isEmpty();
        if (priority && myTurn() && state.get("phase").getAsString().toLowerCase().contains("main")
                && state.getAsJsonArray("stack").isEmpty() && me.get("hand").isJsonArray()) {
            final Set<Integer> playable = idSet("actionable");
            final JsonArray hand = me.getAsJsonArray("hand");
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
                    if ((pass == 1 && creature || pass == 2 && !land && !creature) && attempted.add("cast:" + turn() + ":" + id)) {
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
        final JsonElement v;
        switch (m) {
            case "confirm", "showConfirmDialog" -> v = new JsonPrimitive(true);
            case "showOptionDialog" -> v = new JsonPrimitive(0);
            case "showInputDialog" -> v = new JsonPrimitive("");
            case "getChoices" -> v = indices(a.get(1).getAsInt());
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
        final List<String> last = actions.subList(Math.max(0, actions.size() - 25), actions.size());
        return "seat " + seat + ": turn " + maxTurn + ", life " + myLife + ", lands " + maxLands
                + ", prompt=" + prompt.replace('\n', ' ') + ", buttons=" + buttons
                + "\n  last actions: " + String.join(" | ", last);
    }
}
