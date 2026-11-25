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
 * Plays a game between two scripted browser clients over the web server. Each client keeps its
 * opening hand, answers every question with the first legal option, and presses the OK button
 * (pass priority, done attacking, ...) whenever it is enabled.
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

            final Bot bot1 = new Bot(http, port, 1);
            final Bot bot2 = new Bot(http, port, 2);

            final long deadline = System.currentTimeMillis() + 180000;
            while (System.currentTimeMillis() < deadline
                    && bot1.maxTurn.get() < TARGET_TURN && !bot1.gameOver && !bot2.gameOver) {
                Thread.sleep(250);
            }

            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("game did not progress; reached turn " + bot1.maxTurn.get(),
                    bot1.maxTurn.get() >= TARGET_TURN || bot1.gameOver);
            AssertJUnit.assertTrue("seat 1 received no prompts", bot1.calls.get() > 0);
            AssertJUnit.assertTrue("seat 2 received no prompts", bot2.calls.get() > 0);
            AssertJUnit.assertTrue("seat 1 saw its own hand", bot1.sawOwnHand);
            AssertJUnit.assertFalse("seat 1 saw seat 2's hand", bot1.sawOpponentHand);
            AssertJUnit.assertFalse("seat 2 saw seat 1's hand", bot2.sawOpponentHand);
        } finally {
            server.stop();
        }
    }

    private static final class Bot implements WebSocket.Listener {
        final int seat;
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

        Bot(final HttpClient http, final int port, final int seat) throws Exception {
            this.seat = seat;
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
                    if (System.currentTimeMillis() - lastMessage > 2000 && lastButtons != null) {
                        respondToButtons(lastButtons);
                    }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();
        }

        private void respondToButtons(final JsonArray a) {
            if (a.get(3).getAsBoolean()) {
                act("ok", -1);
            } else if (prompt.toLowerCase().contains("discard") && !myHand.isEmpty()) {
                act("card", myHand.get(0).getAsJsonObject().get("id").getAsInt());
            }
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
            final String t = msg.get("t").getAsString();
            if ("state".equals(t)) {
                final JsonObject s = msg.getAsJsonObject("state");
                if (s.has("turn")) {
                    maxTurn.accumulateAndGet(s.get("turn").getAsInt(), Math::max);
                    gameOver = s.get("gameOver").getAsBoolean();
                    final int me = s.get("me").getAsInt();
                    for (final JsonElement pe : s.getAsJsonArray("players")) {
                        final JsonObject p = pe.getAsJsonObject();
                        final boolean mine = p.get("id").getAsInt() == me;
                        final JsonElement hand = p.get("hand");
                        if (mine && hand.isJsonArray()) {
                            myHand = hand.getAsJsonArray();
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
            final JsonArray a = msg.getAsJsonArray("a");
            if (!msg.has("rid")) {
                if ("showPromptMessage".equals(m)) {
                    prompt = a.get(1).isJsonNull() ? "" : a.get(1).getAsString();
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
