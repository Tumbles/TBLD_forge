package forge.tbld;

import forge.ai.AITest;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Serves the web client, plays a few turns with two scripted players (seat 2 takes one
 * mulligan), and then restarts the match.
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
            bot2 = new TbldBot(http, port, 2, false, 1);

            long deadline = System.currentTimeMillis() + 120000;
            while (System.currentTimeMillis() < deadline && bot1.maxTurn < TARGET_TURN && !bot1.gameOver) {
                Thread.sleep(250);
            }
            String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("game did not progress" + report, bot1.maxTurn >= TARGET_TURN || bot1.gameOver);
            AssertJUnit.assertTrue("seat 1 played no land" + report, bot1.maxLands >= 1);
            AssertJUnit.assertFalse("a player saw the other's hand", bot1.sawOpponentHand || bot2.sawOpponentHand);

            // London mulligan: the cards go to the bottom only after the hand is kept
            AssertJUnit.assertTrue("seat 2 was not asked to put a card on the bottom" + report, bot2.returnPrompts > 0);
            AssertJUnit.assertFalse("seat 2 was asked to put a card on the bottom before keeping" + report, bot2.returnPromptBeforeKeep);
            AssertJUnit.assertTrue("seat 2 never had 6 cards in hand and 34 in library" + report, bot2.sawMulliganResult);

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
