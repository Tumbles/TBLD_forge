package forge.tbld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.ai.AITest;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays a game between two scripted browser clients over the web server. Each client answers
 * every question with the first legal option and presses the OK button (pass priority, done
 * attacking, ...) whenever it is enabled. Seat 2 takes one mulligan. Then seat 1 restarts the
 * match, and the test checks that a new game begins.
 */
public class TbldWebServerTest extends AITest {

    private static final int TARGET_TURN = 5;

    @Test(timeOut = 240000)
    public void twoBrowserClientsPlayAGame() throws Exception {
        final TbldWebServer server = new TbldWebServer(2);
        final int port = server.start(0);
        try {
            final HttpClient http = HttpClient.newHttpClient();
            final HttpResponse<String> page = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            AssertJUnit.assertEquals(200, page.statusCode());
            AssertJUnit.assertTrue(page.body().contains("TBLD"));

            final Bot bot1 = new Bot(http, port, 1, 0);
            final Bot bot2 = new Bot(http, port, 2, 1);

            final long deadline = System.currentTimeMillis() + 180000;
            while (System.currentTimeMillis() < deadline
                    && bot1.maxTurn.get() < TARGET_TURN && !bot1.gameOver && !bot2.gameOver) {
                Thread.sleep(250);
            }

            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("game did not progress; reached turn " + bot1.maxTurn.get() + "\n" + bot1 + "\n" + bot2,
                    bot1.maxTurn.get() >= TARGET_TURN || bot1.gameOver);
            AssertJUnit.assertTrue("seat 1 received no prompts", bot1.calls.get() > 0);
            AssertJUnit.assertTrue("seat 2 received no prompts", bot2.calls.get() > 0);
            AssertJUnit.assertTrue("seat 1 saw its own hand", bot1.sawOwnHand);
            AssertJUnit.assertTrue("seat 1 played no land; lands on battlefield " + bot1.landsInPlay, bot1.landsInPlay >= 2);
            AssertJUnit.assertFalse("seat 1 saw seat 2's hand", bot1.sawOpponentHand);
            AssertJUnit.assertFalse("seat 2 saw seat 1's hand", bot2.sawOpponentHand);

            // London mulligan: the cards go to the bottom only after the hand is kept
            AssertJUnit.assertTrue("seat 2 was not asked to put a card on the bottom", bot2.returnPrompts.get() > 0);
            AssertJUnit.assertFalse("seat 2 was asked to put a card on the bottom before keeping", bot2.returnPromptBeforeKeep);
            AssertJUnit.assertTrue("seat 2 never had 6 cards in hand and 34 in library; turn:hand/library seen " + bot2.handLibrarySeen + "\n" + bot2.mulliganLog, bot2.sawMulliganResult);

            // Restart
            bot1.restart();
            final long restartDeadline = System.currentTimeMillis() + 60000;
            while (System.currentTimeMillis() < restartDeadline
                    && !(bot1.statesAfterReset.get() > 0 && bot2.statesAfterReset.get() > 0)) {
                Thread.sleep(250);
            }
            AssertJUnit.assertTrue("seat 1 got no new game after restart", bot1.statesAfterReset.get() > 0);
            AssertJUnit.assertTrue("seat 2 got no new game after restart", bot2.statesAfterReset.get() > 0);
            AssertJUnit.assertTrue("new game did not start at turn 0 or 1; first turn " + bot1.firstTurnAfterReset,
                    bot1.firstTurnAfterReset <= 1);
            AssertJUnit.assertTrue("errors after restart: " + bot1.errors + bot2.errors, bot1.errors.isEmpty() && bot2.errors.isEmpty());
        } finally {
            server.stop();
        }
    }

    private static final class Bot implements WebSocket.Listener {
        final int seat;
        final int mulligansToTake;
        int mulligansTaken;
        volatile boolean keepPressed;
        volatile boolean returnPromptBeforeKeep;
        volatile boolean sawMulliganResult;
        final AtomicInteger returnPrompts = new AtomicInteger();
        volatile boolean resetReceived;
        final AtomicInteger statesAfterReset = new AtomicInteger();
        volatile int firstTurnAfterReset = Integer.MAX_VALUE;
        /** Seat 1 plays a Mountain in each of its main phases. */
        volatile int landsInPlay;
        volatile int landTurn = -1;
        volatile boolean myMainPhase;
        volatile int turn;
        final List<String> handLibrarySeen = new CopyOnWriteArrayList<>();
        /** Every message up to the first turn, for diagnosing the mulligan. */
        final List<String> mulliganLog = new CopyOnWriteArrayList<>();
        final java.util.Deque<String> recent = new java.util.concurrent.ConcurrentLinkedDeque<>();

        @Override
        public String toString() {
            return "seat " + seat + ": prompt=" + prompt + " buttons=" + lastButtons + " mulligans=" + mulligansTaken
                    + " kept=" + keepPressed + " returnPrompts=" + returnPrompts + " hand=" + myHand.size()
                    + "\n  recent calls: " + String.join("\n    ", recent);
        }
        final WebSocket ws;
        final StringBuilder partial = new StringBuilder();
        final AtomicInteger maxTurn = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final List<String> errors = new CopyOnWriteArrayList<>();
        volatile boolean gameOver;
        volatile boolean sawOwnHand;
        volatile boolean sawOpponentHand;
        volatile String prompt = "";
        volatile JsonArray lastButtons;
        volatile JsonArray myHand = new JsonArray();
        volatile long lastMessage = System.currentTimeMillis();

        Bot(final HttpClient http, final int port, final int seat, final int mulligansToTake) throws Exception {
            this.seat = seat;
            this.mulligansToTake = mulligansToTake;
            this.ws = http.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port + "/ws?seat=" + seat), this)
                    .get(10, TimeUnit.SECONDS);
            // A click can arrive before the engine is waiting for input; repeat it if nothing happens.
            final Thread watchdog = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                        return;
                    }
                    final JsonArray buttons = lastButtons;
                    if (System.currentTimeMillis() - lastMessage > 2000 && buttons != null) {
                        respondToButtons(buttons);
                        respondToPrompt();
                    }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();
        }

        private synchronized void respondToButtons(final JsonArray a) {
            if (seat == 1 && myMainPhase && landTurn != turn && a.get(3).getAsBoolean()) {
                for (final JsonElement c : myHand) {
                    final JsonObject card = c.getAsJsonObject();
                    if (card.has("name") && "Mountain".equals(card.get("name").getAsString())) {
                        landTurn = turn;
                        act("card", card.get("id").getAsInt());
                        return;
                    }
                }
            }
            final String label1 = a.get(1).isJsonNull() ? "" : a.get(1).getAsString();
            final String label2 = a.get(2).isJsonNull() ? "" : a.get(2).getAsString();
            if ("Keep".equals(label1) && "Mulligan".equals(label2)) {
                if (mulligansTaken < mulligansToTake) {
                    mulligansTaken++;
                    lastButtons = null;
                    act("cancel", -1);
                } else {
                    keepPressed = true;
                    act("ok", -1);
                }
                return;
            }
            if (a.get(3).getAsBoolean()) {
                act("ok", -1);
            }
        }

        /** Picks a card for a prompt that needs one. Called when the prompt arrives, which is after its buttons. */
        private synchronized void respondToPrompt() {
            final JsonArray a = lastButtons;
            if (a == null || a.get(3).getAsBoolean()) {
                return;
            }
            final String p = prompt.toLowerCase();
            if ((p.contains("discard") || p.contains("return")) && !myHand.isEmpty()) {
                act("card", myHand.get(0).getAsJsonObject().get("id").getAsInt());
            }
        }

        void restart() {
            final JsonObject o = new JsonObject();
            o.addProperty("t", "restart");
            sendJson(o);
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

        private synchronized void sendJson(final JsonObject o) {
            ws.sendText(o.toString(), true).join();
        }

        private void act(final String m, final int id) {
            if (turn < 1) {
                mulliganLog.add(">> " + m + " " + id);
            }
            final JsonObject o = new JsonObject();
            o.addProperty("t", "act");
            o.addProperty("m", m);
            if (id >= 0) {
                o.addProperty("id", id);
            }
            // Answer off the WebSocket thread, as a person would after a moment
            CompletableFuture.runAsync(() -> sendJson(o), CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
        }

        private void reply(final int rid, final JsonElement v) {
            final JsonObject o = new JsonObject();
            o.addProperty("t", "reply");
            o.addProperty("rid", rid);
            o.add("v", v);
            CompletableFuture.runAsync(() -> sendJson(o), CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
        }

        private static JsonArray firstIndices(final int n) {
            final JsonArray arr = new JsonArray();
            for (int i = 0; i < n; i++) {
                arr.add(i);
            }
            return arr;
        }

        private void handle(final JsonObject msg) {
            lastMessage = System.currentTimeMillis();
            if (turn < 1 && mulliganLog.size() < 200) {
                final String text = msg.toString();
                mulliganLog.add(text.length() > 250 ? text.substring(0, 250) : text);
            }
            final String t = msg.get("t").getAsString();
            if ("reset".equals(t)) {
                resetReceived = true;
                lastButtons = null;
                return;
            }
            if ("state".equals(t)) {
                final JsonObject s = msg.getAsJsonObject("state");
                if (s.has("turn") && resetReceived) {
                    if (statesAfterReset.getAndIncrement() == 0) {
                        firstTurnAfterReset = s.get("turn").getAsInt();
                    }
                }
                if (s.has("turn")) {
                    turn = s.get("turn").getAsInt();
                    myMainPhase = s.get("activePlayer").getAsInt() == s.get("me").getAsInt()
                            && s.get("phase").getAsString().toLowerCase().contains("main")
                            && s.getAsJsonArray("stack").isEmpty();
                    maxTurn.accumulateAndGet(s.get("turn").getAsInt(), Math::max);
                    gameOver = s.get("gameOver").getAsBoolean();
                    final int me = s.get("me").getAsInt();
                    for (final JsonElement pe : s.getAsJsonArray("players")) {
                        final JsonObject p = pe.getAsJsonObject();
                        final boolean mine = p.get("id").getAsInt() == me;
                        final JsonElement hand = p.get("hand");
                        if (mine && !resetReceived) {
                            int lands = 0;
                            for (final JsonElement c : p.getAsJsonArray("battlefield")) {
                                if ("Mountain".equals(c.getAsJsonObject().get("name").getAsString())) {
                                    lands++;
                                }
                            }
                            landsInPlay = Math.max(landsInPlay, lands);
                        }
                        if (mine && hand.isJsonArray()) {
                            myHand = hand.getAsJsonArray();
                            if (mulligansToTake > 0 && keepPressed) {
                                final String pair = s.get("turn").getAsInt() + ":" + myHand.size() + "/" + p.get("library").getAsInt();
                                if (handLibrarySeen.size() < 40 && !handLibrarySeen.contains(pair)) {
                                    handLibrarySeen.add(pair);
                                }
                                if (myHand.size() == 6 && p.get("library").getAsInt() == 34) {
                                    sawMulliganResult = true;
                                }
                            }
                            if (!myHand.isEmpty()) {
                                sawOwnHand = true;
                            }
                        }
                        if (!mine && hand.isJsonArray()) {
                            sawOpponentHand = true;
                        }
                    }
                }
                return;
            }
            if (!"call".equals(t)) {
                return;
            }
            calls.incrementAndGet();
            final String m = msg.get("m").getAsString();
            recent.addLast(msg.toString().length() > 300 ? msg.toString().substring(0, 300) : msg.toString());
            while (recent.size() > 12) {
                recent.pollFirst();
            }
            final JsonArray a = msg.getAsJsonArray("a");
            if (!msg.has("rid")) {
                if ("showPromptMessage".equals(m)) {
                    prompt = a.get(1).isJsonNull() ? "" : a.get(1).getAsString();
                    if (prompt.contains("Return ") && prompt.contains("bottom of your library")) {
                        returnPrompts.incrementAndGet();
                        if (!keepPressed) {
                            returnPromptBeforeKeep = true;
                        }
                    }
                    respondToPrompt();
                } else if ("updateButtons".equals(m)) {
                    lastButtons = a;
                    respondToButtons(a);
                }
                return;
            }
            final int rid = msg.get("rid").getAsInt();
            switch (m) {
                case "confirm", "showConfirmDialog" -> reply(rid, new com.google.gson.JsonPrimitive(true));
                case "showOptionDialog" -> reply(rid, new com.google.gson.JsonPrimitive(0));
                case "showInputDialog" -> reply(rid, new com.google.gson.JsonPrimitive(""));
                case "getChoices" -> reply(rid, firstIndices(Math.max(a.get(1).getAsInt(), Math.min(1, a.get(2).getAsInt()))));
                case "chooseEntitiesForEffect" -> reply(rid, firstIndices(Math.max(a.get(2).getAsInt(), Math.min(1, a.get(3).getAsInt()))));
                case "chooseSingleEntityForEffect", "getAbilityToPlay" -> reply(rid, new com.google.gson.JsonPrimitive(0));
                default -> reply(rid, JsonNull.INSTANCE);
            }
        }
    }
}
