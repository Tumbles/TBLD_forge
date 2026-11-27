package forge.tbld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.AITest;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

/**
 * Serves the web client, plays a few turns with two scripted players (seat 2 takes one
 * mulligan; seat 1 has auto-pass on), and then restarts the match.
 */
public class TbldWebServerTest extends AITest {

    private static final int TARGET_TURN = 5;

    @Test(timeOut = 240000)
    public void mulliganAndRestart() throws Exception {
        final TbldWebServer server = new TbldWebServer(2);
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            final HttpResponse<String> page = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            AssertJUnit.assertEquals(200, page.statusCode());
            AssertJUnit.assertTrue(page.body().contains("TBLD"));

            bot1 = new TbldBot(http, port, 1, false, 0);
            bot1.setAutoPass(true);
            bot2 = new TbldBot(http, port, 2, false, 1);

            long deadline = System.currentTimeMillis() + 120000;
            while (System.currentTimeMillis() < deadline && bot1.maxTurn < TARGET_TURN && !bot1.gameOver) {
                Thread.sleep(250);
            }
            String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("game did not progress" + report, bot1.maxTurn >= TARGET_TURN || bot1.gameOver);
            AssertJUnit.assertTrue("seat 1 played no land" + report, bot1.maxLands >= 1);

            // The test deck's Mountains are TBLD Mountain version 69: named Mountain, with that version's picture
            int mountains = 0;
            for (final JsonElement pe : bot1.state.getAsJsonArray("players")) {
                for (final JsonElement ce : pe.getAsJsonObject().getAsJsonArray("battlefield")) {
                    final JsonObject c = ce.getAsJsonObject();
                    if (c.has("name") && "Mountain".equals(c.get("name").getAsString())) {
                        mountains++;
                        AssertJUnit.assertEquals("TBLD", c.get("set").getAsString());
                        AssertJUnit.assertEquals("/pics/TBLD%20Mountain69.jpg", c.has("img") ? c.get("img").getAsString() : "");
                    }
                }
            }
            AssertJUnit.assertTrue("no Mountain on the battlefield" + report, mountains > 0);

            // Starting emblems: whoever took turn 1 has Player One, the other player Player Two, shown with their
            // pictures from before the mulligan
            AssertJUnit.assertTrue("no turn 1 seen" + report, bot1.turnOnePlayer >= 0);
            AssertJUnit.assertEquals("Player One emblem owner" + report, Integer.valueOf(bot1.turnOnePlayer), bot1.commandOwners.get("Player One"));
            AssertJUnit.assertNotNull("no Player Two emblem" + report, bot1.commandOwners.get("Player Two"));
            AssertJUnit.assertFalse("one player has both emblems" + report,
                    bot1.commandOwners.get("Player Two").equals(bot1.commandOwners.get("Player One")));
            // (the command zones also hold effects, such as Searing Touch's)
            AssertJUnit.assertTrue("seat 2 did not see both emblems" + report, bot2.commandOwners.keySet().containsAll(Set.of("Player One", "Player Two")));
            for (final String name : bot1.commandOwners.keySet()) {
                AssertJUnit.assertFalse("an effect's name shows a card id: " + name, name.matches(".*\\(\\d+\\).*"));
            }
            final JsonObject one = bot1.commandCards.get("Player One");
            AssertJUnit.assertEquals("/pics/TBLD%20Player%20One.jpg", one.has("img") ? one.get("img").getAsString() : "");
            AssertJUnit.assertEquals("Emblem — Book of Lore", one.get("type").getAsString());
            AssertJUnit.assertTrue(one.get("text").getAsString().startsWith("You skip your first draw step."));
            AssertJUnit.assertTrue("no emblem shown during the mulligan" + report, bot2.sawCommandBeforeKeep);
            AssertJUnit.assertFalse("a player saw the other's hand", bot1.sawOpponentHand || bot2.sawOpponentHand);

            // London mulligan: the cards go to the bottom only after the hand is kept
            AssertJUnit.assertTrue("seat 2 was not asked to put a card on the bottom" + report, bot2.returnPrompts > 0);
            AssertJUnit.assertFalse("seat 2 was asked to put a card on the bottom before keeping" + report, bot2.returnPromptBeforeKeep);
            AssertJUnit.assertTrue("seat 2 never had 6 cards in hand and 34 in library" + report, bot2.sawMulliganResult);

            // Auto-pass: seat 1 is never asked for priority when it has nothing to do, but still is when it has
            AssertJUnit.assertEquals("seat 1 (auto-pass on) got priority prompts with nothing to play" + report, 0, bot1.idlePriorityPrompts);
            AssertJUnit.assertTrue("seat 1 (auto-pass on) got no priority prompts at all" + report, bot1.busyPriorityPrompts > 0);
            AssertJUnit.assertTrue("seat 2 (auto-pass off) never got a priority prompt with nothing to play" + report, bot2.idlePriorityPrompts > 0);

            // Restart
            bot1.restart();
            deadline = System.currentTimeMillis() + 60000;
            while (System.currentTimeMillis() < deadline && !(bot1.statesAfterReset > 0 && bot2.statesAfterReset > 0)) {
                Thread.sleep(250);
            }
            report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("no new game after restart" + report, bot1.statesAfterReset > 0 && bot2.statesAfterReset > 0);
            AssertJUnit.assertTrue("new game did not start at turn 0 or 1; first turn " + bot1.firstTurnAfterReset,
                    bot1.firstTurnAfterReset <= 1);
            AssertJUnit.assertTrue("errors after restart: " + bot1.errors + bot2.errors, bot1.errors.isEmpty() && bot2.errors.isEmpty());
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
