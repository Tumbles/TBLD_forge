package forge.tbld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
        // Seat 2's Hydras cost five: more Forests, so a run of few lands doesn't keep them all in hand
        d.getMain().add("Forest|TBLD|11", seat == 1 ? 17 : 24);
        d.getMain().add(seat == 1 ? "Jungleborn Pioneer" : "Ironscale Hydra", seat == 1 ? 23 : 16);
        return d;
    }

    /** Crackling Cyclops, and Searing Touch to cast for its trigger. */
    private static Deck cyclopsDeck() {
        final Deck d = new Deck("Cast trigger test deck");
        d.getMain().add("Mountain|TBLD|69", 17);
        d.getMain().add("Crackling Cyclops", 12);
        d.getMain().add("Searing Touch", 11);
        return d;
    }

    /**
     * Without "Hold priority" a player passes priority after casting a spell, but not when the cast
     * put a trigger on the stack: Crackling Cyclops's trigger gives its controller priority over it.
     */
    @Test(timeOut = 300000)
    public void castTriggerGivesPriorityWithoutHoldPriority() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, TbldWebTriggersTest::cyclopsDeck);
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, true);
            bot1.setPref("holdPriority", new JsonPrimitive(false));
            bot2 = new TbldBot(http, port, 2, false);
            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !(bot1.gameOver && bot2.gameOver)
                    && bot1.triggerAfterCast < 1) {
                Thread.sleep(100);
            }
            final String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("seat 1 never got priority over Crackling Cyclops's trigger" + report, bot1.triggerAfterCast >= 1);
            AssertJUnit.assertEquals("seat 1 (hold priority off) got priority over its own spell" + report, 0, bot1.heldAfterCast);
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
     * "Always yield" (as on MTGO) to Crackling Cyclops's trigger: switched on at the trigger, it passes priority over it
     * then and every later time, and with hold priority off the player also passes over their own spell once the
     * trigger has resolved. The snapshot lists the ability so it can be switched off.
     */
    @Test(timeOut = 300000)
    public void alwaysYieldPassesOverATrigger() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, TbldWebTriggersTest::cyclopsDeck);
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, true);
            bot1.setPref("holdPriority", new JsonPrimitive(false));
            bot1.alwaysYieldOwnTriggers = true;
            bot2 = new TbldBot(http, port, 2, false);
            final long deadline = System.currentTimeMillis() + 240000;
            // Until a cast whose trigger was already yielded to has run its course (the next prompt after it)
            while (System.currentTimeMillis() < deadline && !(bot1.gameOver && bot2.gameOver)
                    && !(bot1.promptsAfterCastAtFirstAutoYield >= 0 && bot1.promptsAfterCast > bot1.promptsAfterCastAtFirstAutoYield)) {
                Thread.sleep(100);
            }
            final String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertFalse("seat 1 never switched Always yield on" + report, bot1.yieldSetOn.isEmpty());
            AssertJUnit.assertTrue("the snapshot did not list Crackling Cyclops as always yielded to" + report,
                    bot1.alwaysYieldsListed.contains("Crackling Cyclops"));
            AssertJUnit.assertFalse("no later Cyclops trigger was yielded to" + report, bot1.autoYieldedTriggers.isEmpty());
            AssertJUnit.assertTrue("the cast with a yielded trigger never finished" + report,
                    bot1.promptsAfterCast > bot1.promptsAfterCastAtFirstAutoYield);
            AssertJUnit.assertEquals("seat 1 got priority over a trigger it always yields to" + report, 0, bot1.promptsOverYieldedTrigger);
            AssertJUnit.assertEquals("seat 1 (hold priority off) got priority over its own spell" + report, 0, bot1.heldAfterCast);
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

    /** Jungleborn Pioneers and their Merfolk, for Stand Together's two targets. */
    private static Deck standTogetherDeck() {
        final Deck d = new Deck("Two targets test deck");
        d.getMain().add("Forest|TBLD|11", 17);
        d.getMain().add("Jungleborn Pioneer", 12);
        d.getMain().add("Stand Together", 11);
        return d;
    }

    /** A stack item with two targets, as the snapshot names them by id. */
    private static boolean twoTargetsOnStack(final TbldBot bot) {
        final JsonObject s = bot.state;
        if (s == null || !s.has("stack")) {
            return false;
        }
        for (final JsonElement e : s.getAsJsonArray("stack")) {
            final JsonObject item = e.getAsJsonObject();
            if (item.has("targetCards") && item.getAsJsonArray("targetCards").size() >= 2) {
                return true;
            }
        }
        return false;
    }

    /** Stand Together's second target is on its sub-ability; the stack item still lists both, so both get a line. */
    @Test(timeOut = 300000)
    public void everyTargetOfASpellIsListed() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, TbldWebTriggersTest::standTogetherDeck);
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, false);
            bot2 = new TbldBot(http, port, 2, false);
            boolean seen = false;
            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !(bot1.gameOver && bot2.gameOver) && !seen) {
                seen = twoTargetsOnStack(bot1) || twoTargetsOnStack(bot2);
                Thread.sleep(50);
            }
            final String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("Stand Together never showed two targets on the stack" + report, seen);
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

    /** Forests and Servants of the Conduit, which give their controller energy. */
    private static Deck energyDeck() {
        final Deck d = new Deck("Energy test deck");
        d.getMain().add("Forest|TBLD|11", 17);
        d.getMain().add("Servant of the Conduit", 23);
        return d;
    }

    /** A player with energy, as the snapshot gives a player's counters by name. */
    private static boolean energyShown(final TbldBot bot) {
        final JsonObject s = bot.state;
        if (s == null || !s.has("players")) {
            return false;
        }
        for (final JsonElement pe : s.getAsJsonArray("players")) {
            final JsonObject p = pe.getAsJsonObject();
            if (p.has("counters") && p.getAsJsonObject("counters").has("Energy")
                    && p.getAsJsonObject("counters").get("Energy").getAsInt() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Servant of the Conduit's energy is in the snapshot, so the client can show it. */
    @Test(timeOut = 300000)
    public void playerEnergyIsInTheSnapshot() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, TbldWebTriggersTest::energyDeck);
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, false);
            bot2 = new TbldBot(http, port, 2, false);
            boolean seen = false;
            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !(bot1.gameOver && bot2.gameOver) && !seen) {
                seen = energyShown(bot1) || energyShown(bot2);
                Thread.sleep(50);
            }
            final String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());
            AssertJUnit.assertTrue("no player's energy ever showed in the snapshot" + report, seen);
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
