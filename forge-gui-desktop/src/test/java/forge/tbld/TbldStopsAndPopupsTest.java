package forge.tbld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.AITest;
import forge.deck.Deck;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;

/**
 * The web client's phase stops and the choices it shows as popups, played through the web server
 * by two scripted players.
 */
public class TbldStopsAndPopupsTest extends AITest {

    /** A seat with stops only in its own main phases gets priority with an empty stack nowhere else. */
    @Test(timeOut = 240000)
    public void phaseStopsDecideWhereAPlayerGetsPriority() throws Exception {
        final TbldWebServer server = new TbldWebServer(2);
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, true);
            bot1.setStops(List.of("MAIN1", "MAIN2"), List.of());
            bot2 = new TbldBot(http, port, 2, false);

            final long deadline = System.currentTimeMillis() + 150000;
            while (System.currentTimeMillis() < deadline && bot1.maxTurn < 6 && !bot1.gameOver) {
                Thread.sleep(250);
            }
            final String report = "\n" + bot1.summary() + "\n  priority phases: " + bot1.priorityPhases
                    + "\n" + bot2.summary() + "\n  priority phases: " + bot2.priorityPhases;
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("game did not progress" + report, bot1.maxTurn >= 6 || bot1.gameOver);
            final Set<String> allowed = Set.of("own:MAIN1", "own:MAIN2");
            for (final String p : bot1.priorityPhases) {
                AssertJUnit.assertTrue("seat 1 got priority with an empty stack outside its stops: " + p + report,
                        p.endsWith(":stack") || allowed.contains(p));
            }
            AssertJUnit.assertTrue("seat 1 never got priority in its main phases" + report,
                    bot1.priorityPhases.stream().anyMatch(allowed::contains));
            // Without stops, seat 2 gets priority in every phase, including the other player's turn
            AssertJUnit.assertTrue("seat 2 (no stops set) never got priority on the other player's turn" + report,
                    bot2.priorityPhases.stream().anyMatch(p -> p.startsWith("opponent:") && !p.endsWith(":stack")));
        } finally {
            if (bot1 != null) {
                bot1.close();
            }
            if (bot2 != null) {
                bot2.close();
            }
            server.stop();
        }
    }

    /**
     * Scry 2 (Twist of Fate) is asked as a choice of cards to put on the bottom, with each card's face so
     * the browser can show it, and the answer decides which card is drawn next.
     */
    @Test(timeOut = 240000)
    public void scryIsAskedWithTheCardsAndTheAnswerIsApplied() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, () -> {
            final Deck d = new Deck("Scry test deck");
            d.getMain().add("Swamp|TBLD|1", 20);
            d.getMain().add("Twist of Fate", 20);
            return d;
        });
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, true);
            bot2 = new TbldBot(http, port, 2, false);

            final long deadline = System.currentTimeMillis() + 150000;
            while (System.currentTimeMillis() < deadline && !bot1.gameOver
                    && !(bot1.scryTopDrawn || bot2.scryTopDrawn) && bot1.maxTurn < 14) {
                Thread.sleep(250);
            }
            final String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());

            JsonObject scry = null;
            for (final TbldBot b : List.of(bot1, bot2)) {
                for (final JsonObject d : b.dialogs) {
                    if ("order".equals(d.get("m").getAsString()) && d.getAsJsonArray("a").get(0).getAsString().contains("bottom")) {
                        scry = d;
                        break;
                    }
                }
            }
            AssertJUnit.assertNotNull("nobody was asked which cards to put on the bottom" + report, scry);
            final JsonArray cards = scry.getAsJsonArray("a").get(4).getAsJsonArray();
            AssertJUnit.assertEquals("scry 2 offered " + cards.size() + " cards" + report, 2, cards.size());
            for (final JsonElement e : cards) {
                final JsonObject c = e.getAsJsonObject();
                AssertJUnit.assertTrue("a scry card came without its face: " + c, c.has("name") && c.has("type") && c.has("img"));
            }
            // Each bot answers its first scry by putting the first card on the bottom and keeping the second
            AssertJUnit.assertTrue("no scrying player drew the card kept on top" + report,
                    bot1.scryTopDrawn || bot2.scryTopDrawn);
            AssertJUnit.assertFalse("a card put on the bottom was drawn" + report, bot1.scryBottomDrawn || bot2.scryBottomDrawn);
        } finally {
            if (bot1 != null) {
                bot1.close();
            }
            if (bot2 != null) {
                bot2.close();
            }
            server.stop();
        }
    }
}
