package forge.tbld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.AITest;
import forge.deck.Deck;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.http.HttpClient;
import java.util.function.IntFunction;

/**
 * A person's abilities work in a web game: their triggered abilities reach the stack and resolve
 * (Jungleborn Pioneer makes its Merfolk token), and a replacement effect applies in combat
 * (Ironscale Hydra gets +1/+1 counters instead of combat damage). Until 2026-11-27 the web seat
 * dropped every triggered ability a person controlled.
 */
public class TbldWebTriggersTest extends AITest {

    /** Seat 1 attacks with Jungleborn Pioneers and their Merfolk; seat 2 blocks with Ironscale Hydras. */
    private static Deck deck(final int seat) {
        final Deck d = new Deck("Trigger test deck " + seat);
        d.getMain().add("Forest|TBLD|11", 17);
        d.getMain().add(seat == 1 ? "Jungleborn Pioneer" : "Ironscale Hydra", 23);
        return d;
    }

    private boolean merfolk;
    private boolean hydraCounter;
    private boolean triggerLogged;

    private void look(final TbldBot bot) {
        final JsonObject s = bot.state;
        if (s == null) {
            return;
        }
        for (final JsonElement pe : s.getAsJsonArray("players")) {
            final JsonObject p = pe.getAsJsonObject();
            if (!p.has("battlefield") || !p.get("battlefield").isJsonArray()) {
                continue;
            }
            for (final JsonElement ce : p.getAsJsonArray("battlefield")) {
                final JsonObject c = ce.getAsJsonObject();
                final String name = c.has("name") ? c.get("name").getAsString() : "";
                if (name.contains("Merfolk") && c.has("token") && c.get("token").getAsBoolean()) {
                    merfolk = true;
                }
                if (name.equals("Ironscale Hydra") && c.has("counters") && c.getAsJsonObject("counters").size() > 0
                        && (!c.has("damage") || c.get("damage").getAsInt() == 0)) {
                    hydraCounter = true;
                }
            }
        }
        if (s.has("log")) {
            for (final JsonElement l : s.getAsJsonArray("log")) {
                if (l.getAsString().contains("triggered Jungleborn Pioneer")) {
                    triggerLogged = true;
                }
            }
        }
    }

    @Test(timeOut = 300000)
    public void triggersAndReplacementsWorkForPeople() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, (IntFunction<Deck>) TbldWebTriggersTest::deck);
        final int port = server.start(0);
        TbldBot attacker = null;
        TbldBot defender = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            attacker = new TbldBot(http, port, 1, true);
            defender = new TbldBot(http, port, 2, false);
            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !(attacker.gameOver && defender.gameOver)
                    && !(merfolk && hydraCounter && triggerLogged)) {
                look(attacker);
                look(defender);
                Thread.sleep(100);
            }
            final String report = "\n" + attacker.summary() + "\n" + defender.summary();
            AssertJUnit.assertTrue("errors: " + attacker.errors + defender.errors + report,
                    attacker.errors.isEmpty() && defender.errors.isEmpty());
            AssertJUnit.assertTrue("Jungleborn Pioneer's trigger never went on the stack" + report, triggerLogged);
            AssertJUnit.assertTrue("no Merfolk token appeared" + report, merfolk);
            AssertJUnit.assertTrue("no Ironscale Hydra got a counter instead of combat damage" + report, hydraCounter);
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
