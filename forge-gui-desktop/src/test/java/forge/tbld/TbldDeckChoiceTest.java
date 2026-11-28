package forge.tbld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.ai.AITest;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.tbld.server.TbldTestDecks;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A seat chooses its test deck: by naming it when it connects, and by asking for another one, which
 * starts a new match. The server tells each seat the decks it offers and which one the seat plays.
 */
public class TbldDeckChoiceTest extends AITest {

    /** Records every message a seat receives, answers each question with its first option and presses OK when it can. */
    private static final class Seat implements WebSocket.Listener {
        final List<JsonObject> messages = new CopyOnWriteArrayList<>();
        private final StringBuilder partial = new StringBuilder();
        final WebSocket ws;

        Seat(final HttpClient http, final String uri) throws Exception {
            ws = http.newWebSocketBuilder().buildAsync(URI.create(uri), this).get(10, TimeUnit.SECONDS);
        }

        @Override
        public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
            partial.append(data);
            if (last) {
                final JsonObject m = JsonParser.parseString(partial.toString()).getAsJsonObject();
                messages.add(m);
                partial.setLength(0);
                // Questions take the first option
                if (m.has("rid")) {
                    final JsonObject reply = new JsonObject();
                    reply.addProperty("t", "reply");
                    reply.add("rid", m.get("rid"));
                    reply.addProperty("v", 0);
                    webSocket.sendText(reply.toString(), true);
                } else if (m.has("m") && "updateButtons".equals(m.get("m").getAsString())
                        && m.getAsJsonArray("a").size() > 3 && m.getAsJsonArray("a").get(3).getAsBoolean()) {
                    // Play first, keep the hand, pass priority
                    final JsonObject ok = new JsonObject();
                    ok.addProperty("t", "act");
                    ok.addProperty("m", "ok");
                    webSocket.sendText(ok.toString(), true);
                }
            }
            webSocket.request(1);
            return null;
        }

        JsonObject await(final int from, final Predicate<JsonObject> p) throws InterruptedException {
            final long deadline = System.currentTimeMillis() + 60000;
            while (System.currentTimeMillis() < deadline) {
                for (int i = from; i < messages.size(); i++) {
                    if (p.test(messages.get(i))) {
                        return messages.get(i);
                    }
                }
                Thread.sleep(100);
            }
            final StringBuilder seen = new StringBuilder();
            for (final JsonObject m : messages) {
                seen.append("\n").append(m.has("m") ? m.get("t").getAsString() + " " + m.get("m").getAsString() + " " + m.get("a") : m.get("t").getAsString());
            }
            AssertJUnit.fail("message not received; got" + seen);
            return null;
        }

        int indexOf(final JsonObject m) {
            return messages.indexOf(m);
        }
    }

    private static boolean isType(final JsonObject m, final String t) {
        return m.has("t") && t.equals(m.get("t").getAsString());
    }

    /** The names of the cards in this seat's own hand in a state message, or null if it shows none. */
    private static Set<String> ownHand(final JsonObject m) {
        if (!isType(m, "state") || !m.has("state") || !m.getAsJsonObject("state").has("players")) {
            return null;
        }
        for (final JsonElement pe : m.getAsJsonObject("state").getAsJsonArray("players")) {
            final JsonObject p = pe.getAsJsonObject();
            if (p.has("hand") && p.get("hand").isJsonArray() && !p.getAsJsonArray("hand").isEmpty()) {
                final Set<String> names = new TreeSet<>();
                for (final JsonElement ce : p.getAsJsonArray("hand")) {
                    names.add(ce.getAsJsonObject().get("name").getAsString());
                }
                return names;
            }
        }
        return null;
    }

    private static Set<String> cardNames(final Deck d) {
        final Set<String> names = new TreeSet<>();
        for (final Map.Entry<PaperCard, Integer> e : d.getMain()) {
            names.add(e.getKey().getName());
        }
        return names;
    }

    @Test(timeOut = 180000)
    public void seatChoosesItsDeck() throws Exception {
        final Set<String> ur = cardNames(TbldTestDecks.urSpells());
        final Set<String> wb = cardNames(TbldTestDecks.wbDrain());
        final Set<String> shared = new TreeSet<>(ur);
        shared.retainAll(wb);
        AssertJUnit.assertTrue("the two decks share cards: " + shared, shared.isEmpty());

        final TbldWebServer server = new TbldWebServer(2);
        final int port = server.start(0);
        Seat one = null;
        Seat two = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            one = new Seat(http, "ws://localhost:" + port + "/ws?seat=1&deck=ur-spells");
            final JsonObject hello1 = one.await(0, m -> isType(m, "hello"));
            AssertJUnit.assertEquals("ur-spells", hello1.get("deck").getAsString());
            AssertJUnit.assertEquals(TbldTestDecks.ALL.size(), hello1.getAsJsonArray("decks").size());
            AssertJUnit.assertEquals("Blue-red spells", hello1.getAsJsonArray("decks").get(3).getAsJsonObject().get("label").getAsString());
            two = new Seat(http, "ws://localhost:" + port + "/ws?seat=2&deck=no-such-deck");
            final JsonObject hello2 = two.await(0, m -> isType(m, "hello"));
            AssertJUnit.assertEquals("an unknown deck leaves the seat's default", "green", hello2.get("deck").getAsString());

            // Seat 1's opening hand comes from the deck it named
            final JsonObject first = one.await(0, m -> ownHand(m) != null);
            AssertJUnit.assertTrue("hand " + ownHand(first) + " is not from the blue-red deck", ur.containsAll(ownHand(first)));

            // Choosing another deck starts a new match with it
            final JsonObject choose = new JsonObject();
            choose.addProperty("t", "deck");
            choose.addProperty("id", "wb-drain");
            one.ws.sendText(choose.toString(), true).get(10, TimeUnit.SECONDS);
            final JsonObject reset = one.await(0, m -> isType(m, "reset"));
            two.await(0, m -> isType(m, "reset"));
            final JsonObject next = one.await(one.indexOf(reset) + 1, m -> ownHand(m) != null);
            AssertJUnit.assertTrue("hand " + ownHand(next) + " is not from the white-black deck", wb.containsAll(ownHand(next)));

            // A browser that reconnects is told the deck it plays
            one.ws.abort();
            one = new Seat(http, "ws://localhost:" + port + "/ws?seat=1");
            AssertJUnit.assertEquals("wb-drain", one.await(0, m -> isType(m, "hello")).get("deck").getAsString());
        } finally {
            if (one != null) {
                one.ws.abort();
            }
            if (two != null) {
                two.ws.abort();
            }
            server.stop();
        }
    }
}
