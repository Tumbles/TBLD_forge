package forge.tbld;

import forge.ai.ComputerUtil;
import forge.game.GameActionUtil;
import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

/** Blue TBLD cards, fourth batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlueCards4Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Spectral Arcanist", "Spectral Interference", "Spellstutter Sprite", "Sphinx of Enlightenment",
            "Sphinx of the Second Sun", "Spyglass Siren", "Starlit Mantle", "Stasis", "Stealth Mission", "Stern Dismissal",
            "Stifle", "Stitched Assistant", "Stitchers Apprentice", "Stitchwing Skaab", "Stormbound Geist",
            "Strategic Planning", "Sultai Skullkeeper", "Summary Dismissal", "Surtland Elementalist", "Tempest Harvester",
            "Thirst for Knowledge", "Thirst for Meaning", "Thought Collapse", "Thought Harvester", "Thousand-Faced Shadow",
            "Timestream Navigator", "Tolarian Kraken", "Tome Scour", "Train of Thought", "Treasure Mage", "Trinket Mage",
            "Triskaidekaphile", "Triton Fortune Hunter", "Triton Shorestalker", "Twiddle", "Unsummon", "Vesuvan Duplimancy",
            "Vodalian Mindsinger", "Void Snare", "War-Wing Siren", "Wavecrash Triton", "Welcome to the Fold");

    private List<Card> islands(final int n) {
        return lands("Island", n, me);
    }

    private static boolean named(final Object c, final String name) {
        return c instanceof Card && ((Card) c).getName().equals(name);
    }

    /** The opponent casts the card from their hand, leaving it on the stack. */
    private SpellAbility oppCasts(final String name, final GameObject... targets) {
        return put(opp, hand(name, opp).getFirstSpellAbility(), targeting(targets));
    }

    private static boolean canTargetSpell(final Card spell, final Player p, final SpellAbility target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTargetSpellAbility(target);
    }

    private static boolean canTarget(final Card spell, final Player p, final GameObject target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTarget(target);
    }

    private boolean countered(final SpellAbility spell) {
        return in(spell.getHostCard(), ZoneType.Graveyard);
    }

    private static int pt(final Card c) {
        return c.getNetPower() * 100 + c.getNetToughness();
    }

    private Card handCard(final Player p, final int i) {
        return p.getCardsIn(ZoneType.Hand).get(i);
    }

    private long tapped(final Iterable<Card> cards) {
        long n = 0;
        for (final Card c : cards) {
            if (current(c).isTapped()) {
                n++;
            }
        }
        return n;
    }

    private boolean owns(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().anyMatch(c -> named(c, name));
    }

    /** Counts the cards looked at by surveil. */
    public static final class Surveils {
        int total;

        @com.google.common.eventbus.Subscribe
        public void onSurveil(final forge.game.event.GameEventSurveil e) {
            total += e.toLibrary() + e.toGraveyard();
        }
    }

    private Surveils surveils() {
        final Surveils s = new Surveils();
        game.subscribeToEvents(s);
        return s;
    }

    /** The opponent activates the creature's ability, leaving it on the stack. */
    private SpellAbility oppActivates(final Card c, final String text) {
        put(opp, ability(current(c), text), targeting());
        return game.getStack().peekAbility();
    }

    /** The card enters the battlefield and its triggered abilities wait on the stack. */
    private SpellAbility entersWithTrigger(final String name, final Player p) {
        final Card c = addCardToZone(name, p, ZoneType.Hand);
        game.getAction().moveToPlay(c, null, null);
        game.copyLastState();
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        AssertJUnit.assertFalse("a triggered ability is waiting", game.getStack().isEmpty());
        return game.getStack().peekAbility();
    }

    /** Attacks with the creatures, then puts the ninja in at declare blockers by its ninjutsu. */
    private Card ninjutsu(final String ninja, final Card... attackers) {
        choices(me).attackers = List.of(attackers);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        choices(me).attackers = List.of();
        final Card n = hand(ninja, me); // not earlier: the AI would cast it
        activate(n, "Ninjutsu");
        AssertJUnit.assertTrue("tapped and attacking", current(n).isAttacking() && current(n).isTapped());
        return n;
    }

    @Test
    public void spectralArcanist() {
        newGame();
        final Card t = creature("Carrion Feeder", me);
        final Card jump = grave("Jump", me);
        final Card conc = grave("Concentrate", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(t);
        choices(me).prefer = c -> named(c, "Jump");
        final Card a = enter("Spectral Arcanist", me);
        AssertJUnit.assertTrue(a.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(302, pt(a));
        AssertJUnit.assertTrue("cast Jump without paying", current(t).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertTrue("exiled instead of going to the graveyard", in(jump, ZoneType.Exile));
        AssertJUnit.assertTrue("one Spirit: mana value 4 is too much", in(conc, ZoneType.Graveyard));

        newGame();
        final Card t2 = creature("Carrion Feeder", me);
        final Card oppJump = grave("Jump", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(t2);
        enter("Spectral Arcanist", me);
        AssertJUnit.assertTrue("from any graveyard", in(oppJump, ZoneType.Exile));
        AssertJUnit.assertTrue(current(t2).hasKeyword(Keyword.FLYING));

        newGame();
        final Card t3 = creature("Carrion Feeder", me);
        final Card j3 = grave("Jump", me);
        choices(me).confirm = false;
        choices(me).targets = List.of(t3);
        enter("Spectral Arcanist", me);
        AssertJUnit.assertTrue("may", in(j3, ZoneType.Graveyard));
        AssertJUnit.assertFalse(current(t3).hasKeyword(Keyword.FLYING));

        // The number of Spirits: Arcanist and a Geist make two, so mana value 3 is too much; with two Geists it is not
        newGame();
        creature("Stormbound Geist", me);
        grave("Divination", me);
        choices(me).confirm = true;
        enter("Spectral Arcanist", me);
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        newGame();
        creature("Stormbound Geist", me);
        creature("Stormbound Geist", me);
        final Card div = grave("Divination", me);
        choices(me).confirm = true;
        enter("Spectral Arcanist", me);
        AssertJUnit.assertEquals("Divination for free", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(in(div, ZoneType.Exile));
    }

    @Test
    public void spectralInterference() {
        newGame();
        islands(2);
        lands("Swamp", 1, opp);
        final SpellAbility feeder = oppCasts("Carrion Feeder");
        cast(hand("Spectral Interference", me), feeder);
        AssertJUnit.assertTrue("creature spell, can't pay 4", countered(feeder));

        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Spectral Interference", me), orn);
        AssertJUnit.assertTrue("artifact spell", countered(orn));

        newGame();
        islands(2);
        final List<Card> oppLands = lands("Swamp", 5, opp);
        choices(opp).payUnless = true;
        final SpellAbility paid = oppCasts("Carrion Feeder");
        cast(hand("Spectral Interference", me), paid);
        AssertJUnit.assertTrue("paid 4: not countered", in(paid.getHostCard(), ZoneType.Battlefield));
        AssertJUnit.assertEquals("1 for the spell, 4 for the cost", 5, tapped(oppLands));

        newGame();
        islands(2);
        lands("Mountain", 1, opp);
        final SpellAbility touch = oppCasts("Searing Touch", me);
        AssertJUnit.assertFalse("only artifact or creature spells", canTargetSpell(hand("Spectral Interference", me), me, touch));
        AssertJUnit.assertTrue("flash", hand("Spectral Interference", me).hasFlashSupertype());
        resolve();
    }

    @Test
    public void spellstutterSprite() {
        newGame();
        islands(2);
        lands("Island", 3, opp);
        final SpellAbility div = oppCasts("Divination", opp);
        choices(me).targets = List.of(div);
        final Card s = hand("Spellstutter Sprite", me);
        cast(s);
        AssertJUnit.assertTrue("flash: cast with a spell on the stack", in(s, ZoneType.Battlefield));
        AssertJUnit.assertTrue(current(s).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(101, pt(current(s)));
        AssertJUnit.assertEquals("one Faerie: mana value 3 is too much, Divination resolves", 2, count(opp, ZoneType.Hand));

        newGame();
        islands(2);
        lands("Island", 3, opp);
        creature("Faerie Miscreant", me);
        creature("Faerie Miscreant", me);
        final SpellAbility div2 = oppCasts("Divination", opp);
        choices(me).targets = List.of(div2);
        cast(hand("Spellstutter Sprite", me));
        AssertJUnit.assertEquals("three Faeries: countered", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue(countered(div2));

        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        choices(me).targets = List.of(orn);
        cast(hand("Spellstutter Sprite", me));
        AssertJUnit.assertTrue("mana value 0 or less than 1 Faerie", countered(orn));
    }

    @Test
    public void sphinxOfEnlightenment() {
        newGame();
        choices(me).targets = List.of(opp);
        final Card s = enter("Sphinx of Enlightenment", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(505, pt(s));
        AssertJUnit.assertEquals("the opponent draws a card", 1, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals("I draw three", 3, count(me, ZoneType.Hand));
    }

    @Test
    public void sphinxOfTheSecondSun() {
        newGame();
        final Card s = creature("Sphinx of the Second Sun", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(606, pt(s));
        final Card land = lands("Island", 1, me).get(0);
        playUntil(opp, PhaseType.MAIN1);
        playUntil(me, PhaseType.MAIN1); // the first turn has no draw step, additional ones included
        playUntil(me, PhaseType.MAIN2);
        land.setTapped(true);
        final int drawn = me.getNumDrawnThisTurn();
        final int turn = game.getPhaseHandler().getTurn();
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertEquals("still the same turn", turn, game.getPhaseHandler().getTurn());
        AssertJUnit.assertFalse("the additional beginning phase untapped", current(land).isTapped());
        AssertJUnit.assertEquals("and drew", drawn + 1, me.getNumDrawnThisTurn());

        newGame();
        islands(2);
        final Card k = hand("Sphinx of the Second Sun", me);
        cast(me, alternative(k, sa -> sa.getDescription().startsWith("Suspend")), null);
        AssertJUnit.assertTrue("suspend 6 for UU", in(k, ZoneType.Exile));
        AssertJUnit.assertEquals(6, current(k).getCounters(CounterEnumType.TIME));
    }

    @Test
    public void spyglassSiren() {
        newGame();
        final Card siren = enter("Spyglass Siren", me);
        AssertJUnit.assertTrue(siren.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(101, pt(siren));
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card map = tokens(me).get(0);
        AssertJUnit.assertTrue(map.isArtifact() && map.getType().hasSubtype("Map"));

        final Card c = creature("Carrion Feeder", me);
        final Card isl = islands(1).get(0);
        library(me, "Island");
        activate(map, "explores", c);
        AssertJUnit.assertTrue("explore: a land to the hand", named(handCard(me, 0), "Island"));
        AssertJUnit.assertTrue("paid 1", current(isl).isTapped());
        AssertJUnit.assertTrue("sacrificed", tokens(me).isEmpty());

        newGame();
        enter("Spyglass Siren", me);
        final Card c2 = creature("Carrion Feeder", me);
        islands(1);
        library(me, "Concentrate");
        activate(tokens(me).get(0), "explores", c2);
        AssertJUnit.assertEquals("a nonland: a +1/+1 counter", 1, p1p1(current(c2)));
    }

    @Test
    public void starlitMantle() {
        newGame();
        islands(1);
        final Card c = creature("Carrion Feeder", me);
        final Card m = hand("Starlit Mantle", me);
        AssertJUnit.assertTrue("flash", m.hasFlashSupertype());
        final Card theirs = creature("Carrion Feeder", opp);
        AssertJUnit.assertFalse("a creature you control", canTarget(m, me, theirs));
        AssertJUnit.assertTrue(canTarget(m, me, c));
        cast(m, c);
        AssertJUnit.assertTrue(in(m, ZoneType.Battlefield));
        AssertJUnit.assertTrue("hexproof this turn", current(c).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertEquals("+1/+1", 202, pt(current(c)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("only this turn", current(c).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertEquals(202, pt(current(c)));
    }

    @Test
    public void stasis() {
        newGame();
        islands(2);
        final Card st = hand("Stasis", me);
        cast(st);
        final Card spare = islands(1).get(0);
        final Card oppLand = lands("Mountain", 1, opp).get(0);
        oppLand.setTapped(true);
        choices(me).payUnless = true;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("paid U", in(st, ZoneType.Battlefield));
        AssertJUnit.assertTrue(current(spare).isTapped());
        AssertJUnit.assertTrue("untap steps skipped: the opponent's land is still tapped", current(oppLand).isTapped());
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("nothing left to pay with", in(st, ZoneType.Graveyard));
    }

    @Test
    public void stealthMission() {
        newGame();
        islands(1);
        final Surveils sv = surveils();
        final Card c = creature("Carrion Feeder", me);
        final Card blocker = creature("Woolly Thoctar", opp);
        AssertJUnit.assertTrue(CombatUtil.canBlock(current(c), current(blocker)));
        cast(hand("Stealth Mission", me), c);
        AssertJUnit.assertEquals(1, p1p1(current(c)));
        AssertJUnit.assertEquals(202, pt(current(c)));
        AssertJUnit.assertFalse("can't be blocked this turn", CombatUtil.canBlock(current(c), current(blocker)));
        AssertJUnit.assertEquals("surveil 4", 4, sv.total);
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("only this turn", CombatUtil.canBlock(current(c), current(blocker)));
    }

    @Test
    public void sternDismissal() {
        newGame();
        islands(2);
        final Card mine = creature("Carrion Feeder", me);
        final Card theirs = creature("Carrion Feeder", opp);
        final Card ench = enter("Stasis", opp);
        final Card art = enter("Gilded Lotus", opp);
        final Card myEnch = enter("Stasis", me);
        final Card sd = hand("Stern Dismissal", me);
        AssertJUnit.assertTrue("flash", sd.hasFlashSupertype());
        AssertJUnit.assertFalse("you don't control", canTarget(sd, me, mine));
        AssertJUnit.assertFalse("you don't control", canTarget(sd, me, myEnch));
        AssertJUnit.assertFalse("creature or enchantment", canTarget(sd, me, art));
        cast(sd, theirs);
        AssertJUnit.assertTrue(in(theirs, ZoneType.Hand));
        cast(hand("Stern Dismissal", me), ench);
        AssertJUnit.assertTrue(in(ench, ZoneType.Hand));
        AssertJUnit.assertEquals("owner's hand", 2, count(opp, ZoneType.Hand));
    }

    @Test
    public void stifle() {
        newGame();
        islands(1);
        lands("Island", 3, opp);
        final Card tri = creature("Triskaidekaphile", opp);
        final SpellAbility ab = oppActivates(tri, "Draw a card");
        cast(hand("Stifle", me), ab);
        AssertJUnit.assertEquals("activated ability countered", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue("Stifle goes to the graveyard", owns(me, ZoneType.Graveyard, "Stifle"));

        // A triggered ability
        newGame();
        islands(1);
        final SpellAbility trig = entersWithTrigger("Sultai Skullkeeper", opp);
        AssertJUnit.assertTrue(trig.isTrigger());
        cast(hand("Stifle", me), trig);
        AssertJUnit.assertEquals("trigger countered: nothing milled", 0, count(opp, ZoneType.Graveyard));

        // Jump-start: from the graveyard by discarding a card
        newGame();
        islands(2);
        lands("Island", 6, opp);
        final Card tri2 = creature("Triskaidekaphile", opp);
        final Card st = grave("Stifle", me);
        final Card pitch = hand("Woolly Thoctar", me);
        final SpellAbility ab2 = oppActivates(tri2, "Draw a card");
        cast(me, withOptionalCosts(current(st)), targeting(ab2));
        AssertJUnit.assertTrue("jump-start exiles it", in(st, ZoneType.Exile));
        AssertJUnit.assertTrue("discarded a card", in(pitch, ZoneType.Graveyard));
        AssertJUnit.assertEquals("countered", 0, count(opp, ZoneType.Hand));
    }

    @Test
    public void stitchedAssistant() {
        newGame();
        final Surveils sv = surveils();
        final Card feeder = creature("Carrion Feeder", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Carrion Feeder");
        final Card a = enter("Stitched Assistant", me);
        AssertJUnit.assertEquals(201, pt(a));
        AssertJUnit.assertTrue("exploited the Feeder", in(feeder, ZoneType.Graveyard));
        AssertJUnit.assertEquals("surveil 2", 2, sv.total);
        AssertJUnit.assertEquals("then draw", 1, count(me, ZoneType.Hand));

        newGame();
        final Surveils sv2 = surveils();
        choices(me).confirm = false;
        enter("Stitched Assistant", me);
        AssertJUnit.assertEquals("no exploit: no surveil", 0, sv2.total);
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));

        newGame();
        final Surveils sv3 = surveils();
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Stitched Assistant");
        final Card a3 = enter("Stitched Assistant", me);
        AssertJUnit.assertTrue("it can exploit itself", in(a3, ZoneType.Graveyard));
        AssertJUnit.assertEquals(2, sv3.total);
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void stitchersApprentice() {
        newGame();
        final Card a = creature("Stitchers Apprentice", me);
        AssertJUnit.assertEquals(102, pt(a));
        choices(me).prefer = c -> c instanceof Card && ((Card) c).isToken();
        activate(a, "Create a 2/2");
        AssertJUnit.assertTrue(current(a).isTapped());
        AssertJUnit.assertTrue("the Homunculus was there to sacrifice", tokens(me).isEmpty());
        AssertJUnit.assertTrue(in(a, ZoneType.Battlefield));

        newGame();
        final Card b = creature("Stitchers Apprentice", me);
        choices(me).prefer = c -> c == b || (c instanceof Card && ((Card) c).getId() == b.getId());
        activate(b, "Create a 2/2");
        AssertJUnit.assertTrue("sacrificed the Apprentice", in(b, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card tok = tokens(me).get(0);
        AssertJUnit.assertEquals(202, pt(tok));
        AssertJUnit.assertTrue("blue", tok.getColor().hasBlue());
        AssertJUnit.assertTrue(tok.getType().hasSubtype("Homunculus"));
    }

    @Test
    public void stitchwingSkaab() {
        newGame();
        final Card sk = grave("Stitchwing Skaab", me);
        islands(2);
        hand("Woolly Thoctar", me);
        final SpellAbility one = ability(current(sk), "from your graveyard");
        one.setActivatingPlayer(me);
        AssertJUnit.assertFalse("two cards to discard", one.canPlay());
        hand("Woolly Thoctar", me);
        activate(sk, "from your graveyard");
        AssertJUnit.assertTrue("returned", in(sk, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(sk).isTapped());
        AssertJUnit.assertEquals("discarded two", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(sk).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(301, pt(current(sk)));
    }

    @Test
    public void stormboundGeist() {
        newGame();
        final Card g = creature("Stormbound Geist", me);
        AssertJUnit.assertTrue(g.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(202, pt(g));
        final Card ground = creature("Carrion Feeder", opp);
        final Card flyer = creature("Ornithopter", opp);
        AssertJUnit.assertFalse("blocks only creatures with flying", CombatUtil.canBlock(current(ground), current(g)));
        AssertJUnit.assertTrue(CombatUtil.canBlock(current(flyer), current(g)));
        kill(g);
        AssertJUnit.assertTrue("undying", in(g, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, p1p1(current(g)));
        AssertJUnit.assertEquals(303, pt(current(g)));
        kill(g);
        AssertJUnit.assertTrue("not a second time", in(g, ZoneType.Graveyard));
    }

    @Test
    public void strategicPlanning() {
        newGame();
        islands(2);
        library(me, "Island", "Concentrate", "Divination");
        choices(me).prefer = c -> named(c, "Concentrate");
        cast(hand("Strategic Planning", me));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("the chosen card", named(handCard(me, 0), "Concentrate"));
        AssertJUnit.assertTrue("the rest to the graveyard", owns(me, ZoneType.Graveyard, "Divination"));
        AssertJUnit.assertTrue(owns(me, ZoneType.Graveyard, "Island"));
        AssertJUnit.assertEquals("two cards and the spell", 3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void sultaiSkullkeeper() {
        newGame();
        final Card s = enter("Sultai Skullkeeper", me);
        AssertJUnit.assertEquals(201, pt(s));
        AssertJUnit.assertEquals("mill five", 5, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void summaryDismissal() {
        newGame();
        islands(4);
        lands("Island", 4, opp);
        lands("Swamp", 1, opp);
        final Card tri = creature("Triskaidekaphile", opp);
        final SpellAbility orn = oppCasts("Ornithopter");
        final SpellAbility ab = oppActivates(tri, "Draw a card");
        final SpellAbility trig = entersWithTrigger("Sultai Skullkeeper", opp);
        final Card sd = hand("Summary Dismissal", me);
        AssertJUnit.assertTrue("flash", sd.hasFlashSupertype());
        AssertJUnit.assertTrue("no cycling", current(sd).getAllSpellAbilities().stream().noneMatch(SpellAbility::isCycling));
        cast(sd);
        AssertJUnit.assertTrue("other spells are exiled, not countered", in(orn.getHostCard(), ZoneType.Exile));
        AssertJUnit.assertEquals("abilities are countered", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals("the trigger too", 0, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertTrue(ab != null && trig != null);
        AssertJUnit.assertTrue("itself is not exiled", in(sd, ZoneType.Graveyard));
        AssertJUnit.assertTrue(game.getStack().isEmpty());
    }

    /** The card's spells, one for each way of paying its additional cost ("reveal a Giant card or pay 2"). */
    private List<SpellAbility> additionalCostSpells(final Card c) {
        final SpellAbility base = current(c).getFirstSpellAbility();
        base.setActivatingPlayer(c.getController());
        return GameActionUtil.getAdditionalCostSpell(base);
    }

    private static boolean reveals(final SpellAbility sa) {
        return sa.getPayCosts().hasSpecificCostType(forge.game.cost.CostReveal.class);
    }

    @Test
    public void surtlandElementalist() {
        newGame();
        final List<Card> seven = islands(7);
        final Card s = hand("Surtland Elementalist", me);
        final SpellAbility pay2 = additionalCostSpells(s).stream().filter(x -> !reveals(x)).findFirst().orElseThrow();
        AssertJUnit.assertFalse("4UU plus 2 is 8 mana", ComputerUtil.handlePlayingSpellAbility(me, pay2, null));
        AssertJUnit.assertEquals("nothing paid", 0, tapped(seven));
        final List<Card> one = islands(1);
        cast(me, additionalCostSpells(s).stream().filter(x -> !reveals(x)).findFirst().orElseThrow(), null);
        AssertJUnit.assertTrue(in(s, ZoneType.Battlefield));
        AssertJUnit.assertEquals(808, pt(current(s)));
        AssertJUnit.assertEquals("paid 4UU and 2", 8, tapped(seven) + tapped(one));

        newGame();
        final List<Card> six = islands(6);
        final Card giant = hand("Brine Giant", me);
        final Card s2 = hand("Surtland Elementalist", me);
        cast(me, additionalCostSpells(s2).stream().filter(SpellAbility::isSpell).filter(TbldBlueCards4Test::reveals).findFirst().orElseThrow(), null);
        AssertJUnit.assertTrue("revealed a Giant instead of paying 2", in(s2, ZoneType.Battlefield));
        AssertJUnit.assertEquals("4UU", 6, tapped(six));
        AssertJUnit.assertTrue(in(giant, ZoneType.Hand));

        newGame();
        islands(6);
        final Card s3 = hand("Surtland Elementalist", me);
        hand("Ornithopter", me);
        for (final SpellAbility sa : additionalCostSpells(s3)) {
            AssertJUnit.assertFalse("no Giant to reveal: 8 mana, and only 6", ComputerUtil.handlePlayingSpellAbility(me, sa, null));
        }
        AssertJUnit.assertTrue(in(s3, ZoneType.Hand));

        // Attack trigger
        newGame();
        final Card att = creature("Surtland Elementalist", me);
        final Card jump = hand("Jump", me);
        final Card orn = hand("Woolly Thoctar", me); // not castable by the AI itself
        choices(me).confirm = true;
        choices(me).targets = List.of(att);
        attack(me, null, att);
        AssertJUnit.assertTrue("cast a sorcery from the hand for free", in(jump, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(att).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertTrue("sorcery spells only", in(orn, ZoneType.Hand));
        AssertJUnit.assertEquals(12, opp.getLife());
    }

    @Test
    public void tempestHarvester() {
        newGame();
        final Card h = enter("Tempest Harvester", me);
        AssertJUnit.assertEquals(201, pt(h));
        h.setSickness(false);
        AssertJUnit.assertEquals("EE", 2, me.getCounters(CounterEnumType.ENERGY));
        final Card orn = hand("Ornithopter", me);
        choices(me).prefer = c -> c == orn || (c instanceof Card && ((Card) c).getId() == orn.getId());
        activate(h, "Draw a card");
        AssertJUnit.assertEquals("paid E", 1, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals("drew a card", 1, me.getNumDrawnThisTurn());
        AssertJUnit.assertTrue("then discarded", in(orn, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(current(h).isTapped());
        untapAll(me);
        activate(h, "Draw a card");
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));
        untapAll(me);
        final SpellAbility again = ability(current(h), "Draw a card");
        again.setActivatingPlayer(me);
        AssertJUnit.assertFalse("no energy left", again.canPlay());
    }

    @Test
    public void thirstForKnowledge() {
        newGame();
        islands(3);
        library(me, "Island", "Ornithopter", "Island");
        cast(hand("Thirst for Knowledge", me));
        AssertJUnit.assertEquals(3, me.getNumDrawnThisTurn());
        AssertJUnit.assertEquals("an artifact discarded: one card", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(owns(me, ZoneType.Graveyard, "Ornithopter"));
        AssertJUnit.assertEquals("the artifact and the spell", 2, count(me, ZoneType.Graveyard));

        newGame();
        islands(3);
        library(me, "Island", "Stasis", "Island");
        cast(hand("Thirst for Knowledge", me));
        AssertJUnit.assertEquals("an enchantment is no artifact: two cards", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void thirstForMeaning() {
        newGame();
        islands(3);
        library(me, "Island", "Stasis", "Island");
        cast(hand("Thirst for Meaning", me));
        AssertJUnit.assertEquals(3, me.getNumDrawnThisTurn());
        AssertJUnit.assertEquals("an enchantment discarded: one card", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(owns(me, ZoneType.Graveyard, "Stasis"));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Graveyard));

        newGame();
        islands(3);
        library(me, "Island", "Ornithopter", "Island");
        cast(hand("Thirst for Meaning", me));
        AssertJUnit.assertEquals("an artifact is no enchantment: two cards", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void thoughtCollapse() {
        newGame();
        islands(4);
        final Card t = creature("Carrion Feeder", me);
        cast(hand("Jump", me), t); // spells cast this turn don't change what is milled
        final SpellAbility orn = oppCasts("Ornithopter");
        final Card tc = hand("Thought Collapse", me);
        AssertJUnit.assertTrue("flash", tc.hasFlashSupertype());
        cast(tc, orn);
        AssertJUnit.assertTrue(countered(orn));
        AssertJUnit.assertEquals("Ornithopter and three milled", 4, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals("its controller mills", 1 + 1, count(me, ZoneType.Graveyard));

        newGame();
        islands(2);
        final Card c = hand("Thought Collapse", me);
        activate(c, "Cycling");
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 2", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void thoughtHarvester() {
        newGame();
        islands(1);
        final Card h = creature("Thought Harvester", me);
        AssertJUnit.assertTrue("devoid", h.isColorless());
        AssertJUnit.assertTrue(h.hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertEquals(204, pt(h));
        choices(me).targets = List.of(opp);
        cast(hand("Ornithopter", me));
        AssertJUnit.assertEquals("a colorless spell: the opponent exiles the top card", 1, count(opp, ZoneType.Exile));
        final Card t = creature("Carrion Feeder", me);
        cast(hand("Jump", me), t);
        AssertJUnit.assertEquals("a blue spell: nothing", 1, count(opp, ZoneType.Exile));
        choices(me).targets = List.of(me);
        cast(hand("Ornithopter", me));
        AssertJUnit.assertEquals("target player: me", 1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Exile));
        choices(me).targets = List.of(opp);
        oppCasts("Ornithopter");
        resolve();
        AssertJUnit.assertEquals("whenever you cast", 1, count(opp, ZoneType.Exile));
    }

    @Test
    public void thousandFacedShadow() {
        newGame();
        islands(4);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Woolly Thoctar", me);
        choices(me).targets = List.of(b);
        final Card n = ninjutsu("Thousand-Faced Shadow", a, b);
        AssertJUnit.assertTrue("returned the unblocked attacker", in(a, ZoneType.Hand));
        AssertJUnit.assertTrue(current(n).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(101, pt(current(n)));
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card copy = tokens(me).get(0);
        AssertJUnit.assertEquals("a copy of the other attacker", "Woolly Thoctar", copy.getName());
        AssertJUnit.assertEquals(604, pt(copy));
        AssertJUnit.assertTrue("tapped and attacking", copy.isTapped() && copy.isAttacking());

        newGame();
        islands(1);
        creature("Ornithopter", me);
        final Card c = hand("Thousand-Faced Shadow", me);
        cast(c);
        AssertJUnit.assertTrue("cast for U", in(c, ZoneType.Battlefield));
        AssertJUnit.assertTrue("not attacking: no copy", tokens(me).isEmpty());
    }

    @Test
    public void timestreamNavigator() {
        newGame();
        final Card nav = creature("Timestream Navigator", me);
        AssertJUnit.assertEquals(101, pt(nav));
        lands("Island", 8, me);
        resolve();
        AssertJUnit.assertFalse("nine permanents", me.hasBlessing());
        final SpellAbility ex = ability(current(nav), "extra turn");
        ex.setActivatingPlayer(me);
        AssertJUnit.assertFalse("no city's blessing", ex.canPlay());
        lands("Island", 1, me);
        resolve();
        AssertJUnit.assertTrue("ten permanents: Ascend", me.hasBlessing());
        AssertJUnit.assertTrue(ex.canPlay());
        final int turn = game.getPhaseHandler().getTurn();
        activate(nav, "extra turn");
        AssertJUnit.assertTrue("on the bottom of the library", in(nav, ZoneType.Library));
        AssertJUnit.assertEquals(nav.getId(), me.getCardsIn(ZoneType.Library).getLast().getId());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("took the next turn", turn + 1, game.getPhaseHandler().getTurn());
    }

    @Test
    public void tolarianKraken() {
        newGame();
        final Card k = creature("Tolarian Kraken", me);
        AssertJUnit.assertEquals(406, pt(k));
        AssertJUnit.assertTrue("hexproof while untapped", current(k).hasKeyword(Keyword.HEXPROOF));
        k.setTapped(true);
        resolve();
        AssertJUnit.assertFalse("not while tapped", current(k).hasKeyword(Keyword.HEXPROOF));
        k.setTapped(false);

        final Card theirs = creature("Carrion Feeder", opp);
        opp.drawCards(1);
        resolve();
        AssertJUnit.assertFalse("the opponent's draw doesn't trigger it", current(theirs).isTapped());
        choices(me).confirm = true;
        choices(me).targets = List.of(theirs);
        me.drawCards(1);
        resolve();
        AssertJUnit.assertTrue("tapped it", current(theirs).isTapped());

        final Card mine = creature("Carrion Feeder", me);
        mine.setTapped(true);
        choices(me).targets = List.of(mine);
        me.drawCards(1);
        resolve();
        AssertJUnit.assertFalse("or untapped one", current(mine).isTapped());

        final Card other = creature("Carrion Feeder", opp);
        choices(me).confirm = false;
        choices(me).targets = List.of(other);
        me.drawCards(1);
        resolve();
        AssertJUnit.assertFalse("you may", current(other).isTapped());
    }

    @Test
    public void tomeScour() {
        newGame();
        islands(1);
        cast(hand("Tome Scour", me), opp);
        AssertJUnit.assertEquals("target player mills five", 5, count(opp, ZoneType.Graveyard));

        newGame();
        islands(2);
        library(me, "Mana Leak", "Concentrate", "Jump");
        choices(me).prefer = c -> true;
        final Card t = hand("Tome Scour", me);
        activate(t, "Transmute");
        AssertJUnit.assertTrue("transmute exiles it from the hand", in(t, ZoneType.Exile));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("the card with mana value 1", named(handCard(me, 0), "Jump"));
        AssertJUnit.assertEquals("paid UU", 2, tapped(me.getCardsIn(ZoneType.Battlefield)));
    }

    @Test
    public void trainOfThought() {
        newGame();
        final List<Card> isl = islands(6);
        choices(me).number = 0;
        cast(hand("Train of Thought", me));
        AssertJUnit.assertEquals("no replicate: one card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(2, tapped(isl));

        newGame();
        final List<Card> isl2 = islands(6);
        choices(me).number = 2;
        cast(hand("Train of Thought", me));
        AssertJUnit.assertEquals("replicated twice: three cards", 3, me.getNumDrawnThisTurn());
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("1U and two times 1U", 6, tapped(isl2));
    }

    @Test
    public void treasureMage() {
        newGame();
        library(me, "Gilded Lotus", "Darksteel Forge", "Island");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        final Card m = enter("Treasure Mage", me);
        AssertJUnit.assertEquals(202, pt(m));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("mana value 6 or greater", named(handCard(me, 0), "Darksteel Forge"));
        AssertJUnit.assertTrue("mana value 5 stays", owns(me, ZoneType.Library, "Gilded Lotus"));

        newGame();
        library(me, "Gilded Lotus", "Ornithopter", "Island");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        enter("Treasure Mage", me);
        AssertJUnit.assertEquals("nothing with mana value 6 or greater", 0, count(me, ZoneType.Hand));

        newGame();
        library(me, "Darksteel Forge");
        choices(me).confirm = false;
        enter("Treasure Mage", me);
        AssertJUnit.assertEquals("may", 0, count(me, ZoneType.Hand));
    }

    @Test
    public void trinketMage() {
        newGame();
        library(me, "Gilded Lotus", "Darksteel Forge", "Arcbound Worker");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        final Card m = enter("Trinket Mage", me);
        AssertJUnit.assertEquals(202, pt(m));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("mana value 1", named(handCard(me, 0), "Arcbound Worker"));

        newGame();
        library(me, "Gilded Lotus", "Ornithopter");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        enter("Trinket Mage", me);
        AssertJUnit.assertTrue("mana value 0 is 1 or less", named(handCard(me, 0), "Ornithopter"));

        newGame();
        library(me, "Gilded Lotus", "Darksteel Forge");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        enter("Trinket Mage", me);
        AssertJUnit.assertEquals("nothing with mana value 1 or less", 0, count(me, ZoneType.Hand));

        newGame();
        library(me, "Ornithopter");
        choices(me).confirm = false;
        enter("Trinket Mage", me);
        AssertJUnit.assertEquals("may", 0, count(me, ZoneType.Hand));
    }

    @Test
    public void triskaidekaphile() {
        newGame();
        final Card t = creature("Triskaidekaphile", me);
        AssertJUnit.assertEquals(103, pt(t));
        for (int i = 0; i < 9; i++) {
            hand("Woolly Thoctar", me);
        }
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("no maximum hand size", 9, count(me, ZoneType.Hand));

        newGame();
        creature("Triskaidekaphile", me);
        for (int i = 0; i < 13; i++) {
            hand("Woolly Thoctar", me);
        }
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("exactly thirteen cards at my upkeep: I win", game.isGameOver() && me.getOutcome().hasWon());

        newGame();
        creature("Triskaidekaphile", me);
        for (int i = 0; i < 12; i++) {
            hand("Woolly Thoctar", me);
        }
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertFalse("twelve", game.isGameOver());

        newGame();
        creature("Triskaidekaphile", me);
        for (int i = 0; i < 14; i++) {
            hand("Woolly Thoctar", me);
        }
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertFalse("fourteen", game.isGameOver());

        newGame();
        final Card t2 = creature("Triskaidekaphile", me);
        final List<Card> isl = islands(3);
        activate(t2, "Draw a card");
        AssertJUnit.assertEquals("2U: draw a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, tapped(isl));
    }

    @Test
    public void tritonFortuneHunter() {
        newGame();
        islands(3);
        final Card h = creature("Triton Fortune Hunter", me);
        AssertJUnit.assertEquals(202, pt(h));
        cast(hand("Jump", me), h);
        AssertJUnit.assertEquals("Jump draws a card, heroic draws two", 3, count(me, ZoneType.Hand));
        final Card other = creature("Carrion Feeder", me);
        cast(hand("Jump", me), other);
        AssertJUnit.assertEquals("a spell that targets another creature: only Jump's own draw", 4, count(me, ZoneType.Hand));
    }

    @Test
    public void tritonShorestalker() {
        newGame();
        final Card s = creature("Triton Shorestalker", me);
        AssertJUnit.assertTrue(s.isCreature() && !s.isEnchantment());
        AssertJUnit.assertEquals(101, pt(s));
        final Card blocker = creature("Woolly Thoctar", opp);
        AssertJUnit.assertFalse("can't be blocked", CombatUtil.canBlock(current(s), current(blocker)));
        final Card plain = creature("Carrion Feeder", me);
        AssertJUnit.assertTrue(CombatUtil.canBlock(current(plain), current(blocker)));

        newGame();
        islands(1);
        final Card c = hand("Triton Shorestalker", me);
        activate(c, "Cycling");
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 1", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void twiddle() {
        newGame();
        islands(3);
        final Card land = lands("Swamp", 1, me).get(0);
        land.setTapped(true);
        final Card art = enter("Gilded Lotus", opp);
        final Card cr = creature("Carrion Feeder", opp);
        final Card ench = enter("Stasis", opp);
        final Card tw = hand("Twiddle", me);
        AssertJUnit.assertFalse("artifact, creature or land", canTarget(tw, me, ench));
        cast(tw, land);
        AssertJUnit.assertFalse("untapped my land", current(land).isTapped());
        cast(hand("Twiddle", me), cr);
        AssertJUnit.assertTrue("tapped their creature", current(cr).isTapped());
        cast(hand("Twiddle", me), art);
        AssertJUnit.assertTrue("tapped their artifact", current(art).isTapped());

        newGame();
        islands(1);
        final Card cr2 = creature("Carrion Feeder", opp);
        final Card jt = grave("Twiddle", me);
        final Card pitch = hand("Woolly Thoctar", me);
        cast(me, withOptionalCosts(current(jt)), targeting(cr2));
        AssertJUnit.assertTrue("jump-start exiles it", in(jt, ZoneType.Exile));
        AssertJUnit.assertTrue("discarded a card", in(pitch, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(cr2).isTapped());
    }

    @Test
    public void unsummon() {
        newGame();
        islands(1);
        final Card t = creature("Woolly Thoctar", opp);
        final Card u = hand("Unsummon", me);
        AssertJUnit.assertTrue("flash", u.hasFlashSupertype());
        AssertJUnit.assertFalse("creatures only", canTarget(u, me, enter("Gilded Lotus", opp)));
        cast(u, t);
        AssertJUnit.assertTrue("returned to its owner's hand", in(t, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Hand));

        newGame();
        islands(2);
        final Card c = hand("Unsummon", me);
        activate(c, "Cycling");
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 2", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void vesuvanDuplimancy() {
        newGame();
        islands(3);
        enter("Vesuvan Duplimancy", me);
        final Card c = creature("Carrion Feeder", me);
        cast(hand("Jump", me), c);
        AssertJUnit.assertEquals("a copy of the creature", 1, tokens(me).size());
        AssertJUnit.assertEquals("Carrion Feeder", tokens(me).get(0).getName());
        final Card art = enter("Gilded Lotus", me);
        cast(hand("Twiddle", me), art);
        AssertJUnit.assertEquals("a copy of the artifact", 2, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).stream().anyMatch(x -> x.getName().equals("Gilded Lotus")));
        final Card theirs = creature("Carrion Feeder", opp);
        cast(hand("Jump", me), theirs);
        AssertJUnit.assertEquals("a creature I don't control: no copy", 2, tokens(me).size());
        lands("Mountain", 1, opp);
        oppCasts("Searing Touch", c);
        resolve();
        AssertJUnit.assertEquals("their spell: no copy", 2, tokens(me).size());
    }

    @Test
    public void vodalianMindsinger() {
        // Not kicked: 2/2, so only a creature with power 1 or less
        newGame();
        final Card feeder = creature("Carrion Feeder", opp);
        final Card big = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(big, feeder);
        final Card m = enter("Vodalian Mindsinger", me);
        AssertJUnit.assertEquals(202, pt(m));
        AssertJUnit.assertEquals(0, p1p1(m));
        AssertJUnit.assertEquals("gained control of the creature with less power", me, current(feeder).getController());
        AssertJUnit.assertEquals(opp, current(big).getController());
        kill(m);
        AssertJUnit.assertEquals("no Merfolk Wizard left: control returns", opp, current(feeder).getController());

        // Kicked once (1R): two counters
        newGame();
        lands("Island", 2, me);
        lands("Mountain", 2, me);
        final Card three = creature("Spectral Arcanist", opp);
        final Card five = creature("Brine Giant", opp);
        choices(me).targets = List.of(five, three);
        final Card k1 = hand("Vodalian Mindsinger", me);
        final List<forge.game.spellability.OptionalCostValue> opts = GameActionUtil.getOptionalCostValues(k1.getFirstSpellAbility());
        AssertJUnit.assertEquals("kicker 1R and kicker 1G", 2, opts.size());
        cast(me, GameActionUtil.addOptionalCosts(k1.getFirstSpellAbility(), List.of(opts.get(0))), null);
        AssertJUnit.assertEquals("two counters for one kick", 2, p1p1(current(k1)));
        AssertJUnit.assertEquals(404, pt(current(k1)));
        AssertJUnit.assertEquals("power 3 is less than 4", me, current(three).getController());
        AssertJUnit.assertEquals("power 5 is not", opp, current(five).getController());

        // Kicked twice (1R and 1G): four counters
        newGame();
        lands("Island", 3, me);
        lands("Mountain", 2, me);
        lands("Forest", 2, me);
        final Card five2 = creature("Brine Giant", opp);
        choices(me).payUnless = true; // Brine Giant has ward 1
        choices(me).targets = List.of(five2);
        final Card k2 = hand("Vodalian Mindsinger", me);
        cast(me, withOptionalCosts(k2), null);
        AssertJUnit.assertEquals("two counters for each kick", 4, p1p1(current(k2)));
        AssertJUnit.assertEquals(606, pt(current(k2)));
        AssertJUnit.assertEquals("power 5 is less than 6", me, current(five2).getController());

        // Control lasts as long as I control a Merfolk Wizard, not only this card
        newGame();
        final Card f3 = creature("Carrion Feeder", opp);
        final Card other = creature("Tempest Harvester", me);
        choices(me).targets = List.of(f3);
        final Card m3 = enter("Vodalian Mindsinger", me);
        AssertJUnit.assertEquals(me, current(f3).getController());
        kill(m3);
        AssertJUnit.assertEquals("another Merfolk Wizard: still mine", me, current(f3).getController());
        kill(other);
        AssertJUnit.assertEquals("no Merfolk Wizard: it goes back", opp, current(f3).getController());
    }

    @Test
    public void voidSnare() {
        newGame();
        islands(1);
        final Card t = creature("Woolly Thoctar", opp);
        final Card vs = hand("Void Snare", me);
        AssertJUnit.assertFalse("nonland", canTarget(vs, me, lands("Island", 1, opp).get(0)));
        cast(vs, t);
        AssertJUnit.assertTrue(in(t, ZoneType.Hand));
        AssertJUnit.assertTrue("no buyback", in(vs, ZoneType.Graveyard));

        newGame();
        islands(1);
        final Card ench = enter("Stasis", opp);
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        final Card vs2 = hand("Void Snare", me);
        cast(me, withOptionalCosts(vs2), targeting(ench));
        AssertJUnit.assertTrue("any nonland permanent", in(ench, ZoneType.Hand));
        AssertJUnit.assertTrue("buyback: back in my hand", in(vs2, ZoneType.Hand));
        AssertJUnit.assertEquals("discarded two cards", 2, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void warWingSiren() {
        newGame();
        islands(2);
        final Card s = hand("War-Wing Siren", me);
        AssertJUnit.assertTrue("flash", s.hasFlashSupertype());
        cast(s);
        AssertJUnit.assertTrue(in(s, ZoneType.Battlefield));
        AssertJUnit.assertTrue(current(s).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(103, pt(current(s)));
        islands(2);
        cast(hand("Jump", me), current(s));
        AssertJUnit.assertEquals("heroic", 1, p1p1(current(s)));
        cast(hand("Jump", me), current(s));
        AssertJUnit.assertEquals(2, p1p1(current(s)));
        final Card other = creature("Carrion Feeder", me);
        islands(1);
        cast(hand("Jump", me), other);
        AssertJUnit.assertEquals("a spell that targets another creature", 2, p1p1(current(s)));
    }

    @Test
    public void wavecrashTriton() {
        newGame();
        islands(3);
        final Card w = creature("Wavecrash Triton", me);
        AssertJUnit.assertEquals(204, pt(w));
        final Card mine = creature("Carrion Feeder", me);
        final Card theirs = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(mine, theirs);
        cast(hand("Jump", me), w);
        AssertJUnit.assertTrue("tapped target creature an opponent controls", current(theirs).isTapped());
        AssertJUnit.assertEquals("and a stun counter", 1, current(theirs).getCounters(CounterEnumType.STUN));
        AssertJUnit.assertFalse("not my own creature", current(mine).isTapped());
        AssertJUnit.assertEquals(0, current(mine).getCounters(CounterEnumType.STUN));
        final Card other = creature("Carrion Feeder", opp);
        cast(hand("Jump", me), mine);
        AssertJUnit.assertFalse("a spell that targets another creature", current(other).isTapped());
        AssertJUnit.assertEquals(1, current(theirs).getCounters(CounterEnumType.STUN));
    }

    @Test
    public void welcomeToTheFold() {
        newGame();
        islands(4);
        final Card feeder = creature("Carrion Feeder", opp);
        cast(hand("Welcome to the Fold", me), feeder);
        AssertJUnit.assertEquals("toughness 1", me, current(feeder).getController());

        newGame();
        islands(4);
        final Card orn = creature("Ornithopter", opp);
        cast(hand("Welcome to the Fold", me), orn);
        AssertJUnit.assertEquals("toughness 2", me, current(orn).getController());

        newGame();
        islands(4);
        final Card tri = creature("Triskaidekaphile", opp);
        final Card w = hand("Welcome to the Fold", me);
        cast(w, tri);
        AssertJUnit.assertEquals("toughness 3 is too much", opp, current(tri).getController());
        AssertJUnit.assertTrue(in(w, ZoneType.Graveyard));

        // Madness XU: with X = 3, toughness 3 is enough, toughness 4 is not
        newGame();
        islands(4);
        final Card tri2 = creature("Triskaidekaphile", opp);
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Welcome to the Fold");
        choices(me).xValue = 3;
        choices(me).targets = List.of(tri2);
        final Card w2 = hand("Welcome to the Fold", me);
        discard(me, w2);
        AssertJUnit.assertTrue("cast for its madness cost", in(w2, ZoneType.Graveyard));
        AssertJUnit.assertEquals("paid 3 and U", 4, tapped(me.getCardsIn(ZoneType.Battlefield)));
        AssertJUnit.assertEquals("toughness 3 or less", me, current(tri2).getController());

        newGame();
        islands(4);
        final Card thoc = creature("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Welcome to the Fold");
        choices(me).xValue = 3;
        choices(me).targets = List.of(thoc);
        discard(me, hand("Welcome to the Fold", me));
        AssertJUnit.assertEquals("toughness 4 is more than X", opp, current(thoc).getController());

        newGame();
        islands(4);
        final Card feeder2 = creature("Carrion Feeder", opp);
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Welcome to the Fold");
        choices(me).xValue = 0;
        choices(me).targets = List.of(feeder2);
        discard(me, hand("Welcome to the Fold", me));
        AssertJUnit.assertEquals("madness: toughness 1 is more than X = 0", opp, current(feeder2).getController());
    }
}
