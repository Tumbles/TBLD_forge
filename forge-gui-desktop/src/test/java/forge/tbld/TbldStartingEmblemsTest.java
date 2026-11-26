package forge.tbld;

import com.google.common.collect.Lists;
import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilMana;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.tbld.TbldEmblems;
import forge.game.zone.ZoneType;
import forge.player.LobbyPlayerHuman;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/** TBLD starting emblems: who gets which, and what each one does. */
public class TbldStartingEmblemsTest extends AITest {

    /** A game of {@code n} AI players with starting emblems on, in the first player's first main phase. */
    private Game game(final int n) {
        initAndCreateGame(); // loads the card database
        final List<RegisteredPlayer> players = Lists.newArrayList();
        for (int i = 1; i <= n; i++) {
            players.add(new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("p" + i, null)));
        }
        return start(players);
    }

    private static Game start(final List<RegisteredPlayer> players) {
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setTbldStartingEmblems(true);
        final Game game = new Game(players, rules, new Match(rules, players, "Test"));
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    private static List<String> commandNames(final Player p) {
        final List<String> names = new ArrayList<>();
        for (final Card c : p.getCardsIn(ZoneType.Command)) {
            names.add(c.getName());
        }
        return names;
    }

    private static SpellAbility ability(final Card emblem, final Player p) {
        final SpellAbility sa = emblem.getSpellAbilities().getFirst();
        sa.setActivatingPlayer(p);
        return sa;
    }

    @Test
    public void emblemsFollowTurnOrder() {
        for (final int n : new int[] { 2, 3, 6 }) {
            final Game game = game(n);
            final Player first = game.getPlayers().get(1);
            TbldEmblems.giveStartingEmblems(game, first);
            Player p = first;
            for (int place = 0; place < n; place++) {
                final List<String> expected = place < 5 ? List.of(TbldEmblems.NAMES.get(place)) : List.of();
                AssertJUnit.assertEquals(n + " players, place " + (place + 1), expected, commandNames(p));
                for (final Card c : p.getCardsIn(ZoneType.Command)) {
                    AssertJUnit.assertTrue(c.isEmblem());
                    AssertJUnit.assertEquals("TBLD", c.getSetCode());
                }
                p = game.getNextPlayerAfter(p);
            }
        }
        // Giving them again for another starting player replaces them
        final Game game = game(2);
        final Player a = game.getPlayers().get(0);
        final Player b = game.getPlayers().get(1);
        TbldEmblems.giveStartingEmblems(game, a);
        TbldEmblems.giveStartingEmblems(game, b);
        AssertJUnit.assertEquals(List.of("Player Two"), commandNames(a));
        AssertJUnit.assertEquals(List.of("Player One"), commandNames(b));
        AssertJUnit.assertEquals("Emblem — Book of Lore", TbldEmblems.typeLine("Player One"));
    }

    @Test
    public void playerOneSkipsOnlyTheFirstDrawStep() {
        final Game game = game(2);
        final Player one = game.getPlayers().get(0);
        final Player two = game.getPlayers().get(1);
        TbldEmblems.giveStartingEmblems(game, one);
        fillLibrary(one, 10);
        fillLibrary(two, 10);

        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, one);
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertEquals("Player One drew in their first draw step", 0, one.getCardsIn(ZoneType.Hand).size());

        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, two);
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertEquals("Player Two skipped a draw", 1, two.getCardsIn(ZoneType.Hand).size());

        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, one);
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertEquals("Player One skipped a second draw step", 1, one.getCardsIn(ZoneType.Hand).size());
    }

    @Test
    public void playerOneDrawsTwoAndExilesOne() {
        final Game game = game(2);
        final Player one = game.getPlayers().get(0);
        final Player two = game.getPlayers().get(1);
        final Card emblem = TbldEmblems.give(game, one, 0);
        addCards("Mountain", 3, one);
        fillLibrary(one, 5);
        final SpellAbility sa = ability(emblem, one);
        AssertJUnit.assertFalse("can be countered", sa.isCounterableBy(null));

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, two);
        AssertJUnit.assertFalse("usable on the opponent's turn", sa.canPlay());
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, one);
        AssertJUnit.assertTrue("not usable on its owner's turn", sa.canPlay());

        AssertJUnit.assertTrue(ComputerUtil.playStack(sa, one, game));
        AssertJUnit.assertTrue("the emblem was not exiled", one.getCardsIn(ZoneType.Command).isEmpty());
        AssertJUnit.assertEquals(3, one.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count());
        game.getStack().resolveStack();
        AssertJUnit.assertEquals(1, one.getCardsIn(ZoneType.Hand).size());
        AssertJUnit.assertEquals(1, one.getCardsIn(ZoneType.Exile).size());
        AssertJUnit.assertEquals(3, one.getCardsIn(ZoneType.Library).size());
    }

    @Test
    public void playerTwoAddsColorlessOnce() {
        final Game game = game(2);
        final Player two = game.getPlayers().get(1);
        final Card emblem = TbldEmblems.give(game, two, 1);
        final SpellAbility mana = emblem.getManaAbilities().getFirst();
        mana.setActivatingPlayer(two);
        AssertJUnit.assertTrue(mana.canPlay());
        AssertJUnit.assertTrue("an AI player may spend it", ComputerUtilMana.getAvailableManaSources(two, true).contains(emblem));

        AssertJUnit.assertTrue(ComputerUtil.playNoStack(two, mana, game, false));
        AssertJUnit.assertEquals(1, two.getManaPool().totalMana());
        AssertJUnit.assertTrue("the emblem was not exiled", two.getCardsIn(ZoneType.Command).isEmpty());
    }

    @Test
    public void autoPayNeverSpendsAPersonsEmblem() {
        initAndCreateGame();
        final List<RegisteredPlayer> players = Lists.newArrayList(
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerHuman("person")),
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("ai", null)));
        final Game game = start(players);
        final Player person = game.getPlayers().get(0);
        AssertJUnit.assertFalse(person.isAI());
        final Card emblem = TbldEmblems.give(game, person, 1);
        AssertJUnit.assertFalse(ComputerUtilMana.getAvailableManaSources(person, true).contains(emblem));
    }

    @Test
    public void playerThreeTradesTheNextNormalTurnForAnExtraTurn() {
        final Game game = game(2);
        final Player one = game.getPlayers().get(0);
        final Player two = game.getPlayers().get(1);
        final Card emblem = TbldEmblems.give(game, one, 2);
        addCards("Mountain", 2, one);
        fillLibrary(one, 10);
        fillLibrary(two, 10);
        final SpellAbility sa = ability(emblem, one);
        AssertJUnit.assertFalse(sa.isCounterableBy(null));
        AssertJUnit.assertTrue(sa.canPlay());
        AssertJUnit.assertTrue(ComputerUtil.playStack(sa, one, game));
        game.getStack().resolveStack();
        AssertJUnit.assertEquals(List.of("Player Three's Effect"), commandNames(one));

        final List<Player> turns = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // Until the next turn begins, even if the same player takes it
            final int turn = game.getPhaseHandler().getTurn();
            do {
                game.getPhaseHandler().mainLoopStep();
            } while (!game.isGameOver() && game.getPhaseHandler().getTurn() == turn);
            turns.add(game.getPhaseHandler().getPlayerTurn());
        }
        // The extra turn, the opponent's turn, the skipped turn (so the opponent again), then back to normal
        AssertJUnit.assertEquals(List.of(one, two, two, one), turns);
        AssertJUnit.assertTrue("the effect stayed after skipping a turn", one.getCardsIn(ZoneType.Command).isEmpty());
    }

    @Test
    public void playerFourCountersASpellAndCantBeCountered() {
        final Game game = game(2);
        final Player one = game.getPlayers().get(0);
        final Player two = game.getPlayers().get(1);
        final Card emblem = TbldEmblems.give(game, one, 3);

        // The opponent casts Ornithopter
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, two);
        final Card thopter = addCardToZone("Ornithopter", two, ZoneType.Hand);
        final SpellAbility spell = thopter.getFirstSpellAbility();
        spell.setActivatingPlayer(two);
        AssertJUnit.assertTrue(ComputerUtil.playStack(spell, two, game));

        final SpellAbility counter = ability(emblem, one);
        counter.getTargets().add(game.getStack().peekAbility());
        AssertJUnit.assertTrue("not usable on the opponent's turn", counter.canPlay());
        AssertJUnit.assertTrue(ComputerUtil.playStack(counter, one, game));
        AssertJUnit.assertTrue("the emblem was not exiled", one.getCardsIn(ZoneType.Command).isEmpty());

        // Stifle can't counter the emblem's ability (the stack holds a copy of the activated ability)
        final SpellAbility onStack = game.getStack().peekAbility();
        AssertJUnit.assertEquals("Player Four", onStack.getHostCard().getName());
        final Card stifle = addCardToZone("Stifle", two, ZoneType.Hand);
        final SpellAbility stifleSa = stifle.getFirstSpellAbility();
        stifleSa.setActivatingPlayer(two);
        stifleSa.getTargets().add(onStack);
        AbilityUtils.resolve(stifleSa);
        AssertJUnit.assertNotNull("Stifle countered the emblem's ability", game.getStack().getInstanceMatchingSpellAbilityID(onStack));
        AssertJUnit.assertEquals(2, game.getStack().size());

        game.getStack().resolveStack();
        AssertJUnit.assertTrue(game.getStack().isEmpty());
        AssertJUnit.assertEquals(List.of("Ornithopter"), two.getCardsIn(ZoneType.Graveyard).stream().map(Card::getName).toList());
    }

    @Test
    public void playerFiveDrawsAnExtraCardAndHasNoMaximumHandSize() {
        final Game game = game(2);
        final Player one = game.getPlayers().get(0);
        TbldEmblems.give(game, one, 4);
        fillLibrary(one, 10);
        game.getAction().checkStaticAbilities();
        AssertJUnit.assertTrue(one.isUnlimitedHandSize());

        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, one);
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertEquals(2, one.getCardsIn(ZoneType.Hand).size());
    }
}
