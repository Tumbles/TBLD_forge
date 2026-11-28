package forge.tbld;

import forge.ai.AITest;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.http.HttpClient;

/**
 * Two scripted players play a complete game through the web server: lands, creatures, spells
 * with targets and mana payment, attacks, blocks, creatures dying, and a player losing.
 */
public class TbldFullGameTest extends AITest {

    @Test(timeOut = 300000)
    public void scriptedPlayersPlayAGameToTheEnd() throws Exception {
        final TbldWebServer server = new TbldWebServer(2);
        final int port = server.start(0);
        TbldBot attacker = null;
        TbldBot defender = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            attacker = new TbldBot(http, port, 1, true);
            defender = new TbldBot(http, port, 2, false);

            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !(attacker.gameOver && defender.gameOver)) {
                Thread.sleep(250);
            }
            final String report = "\n" + attacker.summary() + "\n" + defender.summary();

            AssertJUnit.assertTrue("errors: " + attacker.errors + defender.errors + report,
                    attacker.errors.isEmpty() && defender.errors.isEmpty());
            AssertJUnit.assertTrue("game did not finish" + report, attacker.gameOver && defender.gameOver);
            AssertJUnit.assertTrue("nobody lost on life" + report, attacker.myLife <= 0 || defender.myLife <= 0);
            AssertJUnit.assertTrue("seat 1 played fewer than 3 lands" + report, attacker.maxLands >= 3);
            AssertJUnit.assertTrue("seat 2 played fewer than 3 lands" + report, defender.maxLands >= 3);
            AssertJUnit.assertTrue("seat 1 cast no creature" + report, attacker.castCreature);
            AssertJUnit.assertTrue("seat 2 cast no creature" + report, defender.castCreature);
            AssertJUnit.assertTrue("no attack" + report, attacker.sawAttack);
            AssertJUnit.assertTrue("seat 2 was never shown a blocker it had chosen, before confirming the blocks" + report,
                    defender.sawPlannedBlock);
            AssertJUnit.assertTrue("seat 1 never saw a declared block" + report, attacker.sawBlock);
            AssertJUnit.assertTrue("selecting a blocked attacker with no creature chosen changed something" + report,
                    defender.attackerClickIgnored);
            AssertJUnit.assertTrue("selecting a blocking creature did not just take its block back" + report,
                    defender.unassignWorked);
            AssertJUnit.assertTrue("no creature died" + report, attacker.creatureDied || defender.creatureDied);
            AssertJUnit.assertTrue("no instant or sorcery resolved" + report, attacker.spellResolved || defender.spellResolved);
            AssertJUnit.assertTrue("a text showed an object id: " + attacker.textsWithIds + defender.textsWithIds,
                    attacker.textsWithIds.isEmpty() && defender.textsWithIds.isEmpty());
            AssertJUnit.assertFalse("a player saw the other's hand", attacker.sawOpponentHand || defender.sawOpponentHand);
        } finally {
            if (attacker != null) {
                attacker.close();
            }
            if (defender != null) {
                defender.close();
            }
            server.stop();
        }
    }
}
