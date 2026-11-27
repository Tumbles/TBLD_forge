package forge.tbld;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import forge.ai.AITest;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.net.http.HttpClient;
import java.util.List;

/**
 * Paying from floating mana, the optional-cost (buyback) question, "Hold priority", the three
 * auto-pass settings, "Yield all", and the first land of a player's first turn entering tapped.
 */
public class TbldPriorityAndPaymentTest extends AITest {

    private static Deck deck(final String spell, final int lands) {
        final Deck d = new Deck("Priority test deck");
        d.getMain().add("Mountain|TBLD|69", lands);
        d.getMain().add(spell, 40 - lands);
        return d;
    }

    /**
     * Seat 1 taps a land before each Searing Touch while it has five lands or fewer, and has "Hold
     * priority" off. Seat 2 casts nothing until it has six lands, enough for buyback ({4}{R} more),
     * and holds priority (the default).
     */
    @Test(timeOut = 300000)
    public void floatingManaBuybackAndHoldPriority() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, () -> deck("Searing Touch", 27));
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, false);
            bot1.floatManaFirst = true;
            bot1.setPref("holdPriority", new JsonPrimitive(false));
            bot2 = new TbldBot(http, port, 2, false);
            bot2.castFromLands = 6;

            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !bot1.gameOver && bot1.maxTurn < 24
                    && !(bot1.floatCasts >= 2 && bot2.heldAfterCast >= 1 && bot1.promptsAfterCast >= 2 && buybackAsked(bot2) != null)) {
                Thread.sleep(250);
            }
            final String report = "\n" + bot1.summary() + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());

            // Floating mana that covers the cost is spent without a payment prompt
            AssertJUnit.assertTrue("seat 1 never cast a spell with mana in its pool" + report, bot1.floatCasts >= 1);
            AssertJUnit.assertEquals("seat 1 was asked to pay although its pool covered the cost" + report, 0, bot1.paymentPromptsAfterFloat);

            // The optional-cost question comes only when the player could pay for an optional cost
            for (final TbldBot b : List.of(bot1, bot2)) {
                for (final JsonObject ask : b.optionalCostAsks) {
                    AssertJUnit.assertTrue("seat " + b.seat + " was asked about buyback with only " + ask.get("mana") + " mana" + report,
                            ask.get("mana").getAsInt() >= 6);
                }
            }
            // ... and is one choice from a list that ends with not paying it
            final JsonObject ask = buybackAsked(bot2);
            AssertJUnit.assertNotNull("seat 2 (six lands) was never asked about buyback" + report, ask);
            final JsonArray a = ask.getAsJsonArray("a");
            AssertJUnit.assertEquals("the buyback question is a single choice: " + a, 1, a.get(1).getAsInt());
            AssertJUnit.assertEquals("the buyback question is a single choice: " + a, 1, a.get(2).getAsInt());
            final JsonArray options = a.get(3).getAsJsonArray();
            AssertJUnit.assertEquals("buyback options: " + options, 2, options.size());
            AssertJUnit.assertTrue("buyback options: " + options, options.get(0).getAsString().startsWith("Buyback"));
            AssertJUnit.assertEquals("buyback options: " + options, "No buyback", options.get(1).getAsString());

            // Without "Hold priority", casting passes priority; with it, the caster gets priority back
            AssertJUnit.assertTrue("seat 1 cast nothing" + report, bot1.promptsAfterCast >= 1);
            AssertJUnit.assertEquals("seat 1 (hold priority off) got priority over its own spell" + report, 0, bot1.heldAfterCast);
            AssertJUnit.assertTrue("seat 2 (hold priority on) never got priority over its own spell" + report, bot2.heldAfterCast >= 1);

            // Stack items name their targets by id, so the browser can draw a line to each
            AssertJUnit.assertTrue("seat 2 never saw its id among a stack item's targets" + report, bot2.sawTargetIdOnStack);

            // Searing Touch's "can't gain life" effect is marked as an effect, with Searing Touch's picture
            final JsonObject effect = bot1.commandCards.get("Searing Touch's Effect");
            AssertJUnit.assertNotNull("seat 1 never saw Searing Touch's effect; command zone: " + bot1.commandCards.keySet() + report, effect);
            AssertJUnit.assertTrue("the effect is not marked as one: " + effect, effect.has("effect") && effect.get("effect").getAsBoolean());
            AssertJUnit.assertEquals("/pics/TBLD%20Searing%20Touch.jpg", effect.has("img") ? effect.get("img").getAsString() : "");
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

    private static JsonObject buybackAsked(final TbldBot b) {
        for (final JsonObject ask : b.optionalCostAsks) {
            if (ask.getAsJsonArray("a").get(3).getAsJsonArray().toString().contains("No buyback")) {
                return ask;
            }
        }
        return null;
    }

    /**
     * With creatures that cost four, players often hold untapped lands with nothing to cast. Seat 1
     * auto-passes only when it has no actions at all (an untapped land is one), seat 2 when it has
     * only mana abilities. Seat 1 also uses "Yield all" on two of seat 2's turns.
     */
    @Test(timeOut = 300000)
    public void autoPassSettingsAndYieldAll() throws Exception {
        final TbldWebServer server = new TbldWebServer(2, () -> deck("Hill Giant", 24));
        final int port = server.start(0);
        TbldBot bot1 = null;
        TbldBot bot2 = null;
        try {
            final HttpClient http = HttpClient.newHttpClient();
            bot1 = new TbldBot(http, port, 1, false);
            bot1.setPref("autoPass", new JsonPrimitive("noActions"));
            bot1.yieldScript = true;
            bot2 = new TbldBot(http, port, 2, true);
            bot2.setPref("autoPass", new JsonPrimitive("manaOnly"));

            final long deadline = System.currentTimeMillis() + 240000;
            while (System.currentTimeMillis() < deadline && !bot1.gameOver
                    && !(bot1.yieldOffTurn >= 0 && bot1.maxTurn > bot1.yieldOffTurn) && bot1.maxTurn < 20) {
                Thread.sleep(250);
            }
            final String report = "\n" + bot1.summary() + "\n  yield turns " + bot1.yieldTurn + ", " + bot1.yieldOffTurn
                    + "\n" + bot2.summary();
            AssertJUnit.assertTrue("errors: " + bot1.errors + bot2.errors + report, bot1.errors.isEmpty() && bot2.errors.isEmpty());

            // "Only mana abilities": never a prompt with nothing but mana abilities to use
            AssertJUnit.assertEquals("seat 2 (auto-pass if only mana abilities) got idle priority prompts" + report, 0, bot2.idlePriorityPrompts);
            AssertJUnit.assertTrue("seat 2 got no priority prompts at all" + report, bot2.busyPriorityPrompts > 0);
            // "No actions": stops while a land could be tapped, and passes when nothing at all could be used
            AssertJUnit.assertTrue("seat 1 (auto-pass if no actions) never stopped with only mana abilities" + report, bot1.idlePriorityPrompts > 0);
            AssertJUnit.assertEquals("seat 1 got priority with no mana ability to use" + report, 0, bot1.idleWithoutManaSource);

            // "Yield all" passes everything for the rest of the turn, and ends with it
            AssertJUnit.assertTrue("seat 1 never yielded" + report, bot1.yieldTurn >= 0);
            AssertJUnit.assertTrue("the game never said seat 1 was yielding" + report, bot1.sawYieldOn);
            AssertJUnit.assertEquals("seat 1 got priority while yielding" + report, 0, bot1.promptsWhileYielding);
            AssertJUnit.assertTrue("seat 1 was still yielding after the turn ended" + report, bot1.sawYieldEnd);
            // Switched off again, it stops passing
            AssertJUnit.assertTrue("seat 1 never switched yield all off" + report, bot1.yieldOffTurn >= 0);
            AssertJUnit.assertTrue("seat 1 got no priority after switching yield all off" + report, bot1.promptsAfterYieldOff > 0);
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

    /** The first land a player plays in their first turn enters tapped; later lands don't. */
    @Test
    public void firstLandOfTheFirstTurnEntersTapped() {
        final Game game = initAndCreateGame();
        game.getRules().setTbldFirstLandEntersTapped(true);
        final Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        while (p.getTurn() < 1) {
            p.incrementTurn();
        }
        p.resetLandsPlayedThisTurn();
        AssertJUnit.assertTrue("first land of the first turn", play(p, "Mountain").isTapped());
        p.addMaxLandPlays(game.getNextTimestamp(), 1); // an extra land drop
        AssertJUnit.assertFalse("second land of the first turn", play(p, "Mountain").isTapped());
        p.incrementTurn();
        p.resetLandsPlayedThisTurn();
        AssertJUnit.assertFalse("first land of the second turn", play(p, "Mountain").isTapped());

        // Without the TBLD rule, the first land enters untapped
        final Game plain = initAndCreateGame();
        final Player q = plain.getPlayers().get(1);
        plain.getPhaseHandler().devModeSet(PhaseType.MAIN1, q);
        while (q.getTurn() < 1) {
            q.incrementTurn();
        }
        q.resetLandsPlayedThisTurn();
        AssertJUnit.assertFalse("first land without the TBLD rule", play(q, "Mountain").isTapped());
    }

    private Card play(final Player p, final String land) {
        final Card c = addCardToZone(land, p, ZoneType.Hand);
        final SpellAbility sa = c.getAllPossibleAbilities(p, false).stream()
                .filter(SpellAbility::isLandAbility).findFirst().orElseThrow();
        return p.playLand(c, sa);
    }
}
