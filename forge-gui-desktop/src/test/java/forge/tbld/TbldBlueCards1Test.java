package forge.tbld;

import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.CombatUtil;
import forge.game.cost.CostDiscard;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

/** Blue TBLD cards, first batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlueCards1Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Annul", "Aqueous Form", "Arcane Artisan", "Arcane Flight", "Armored Skaab", "As Foretold", "Augur of Bolas",
            "Augury Raven", "Baral Chief of Compliance", "Battlefield Thaumaturge", "Bazaar Trademage", "Beguiler of Wills",
            "Benthic Infiltrator", "Boomerang", "Breakthrough", "Brine Giant", "Capsize", "Chart a Course", "Circular Logic",
            "Citywatch Sphinx", "Concentrate", "Condescend", "Confirm Suspicions", "Confound", "Curious Inquiry", "Cyclonic Rift",
            "Deceiver Exarch", "Deep-Sea Kraken", "Deepfathom Skulker", "Deeproot Waters", "Deprive", "Depth Defiler",
            "Devious Cover-Up", "Disdainful Stroke", "Distant Melody", "Divination", "Domineer", "Dream Halls", "Dream Twist",
            "Drowned Secrets", "Drowner of Hope", "Echo of Eons", "Eel Umbra", "Eldrazi Skyspawner", "Essence Capture",
            "Essence Scatter", "Exclude", "Exhaustion");

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

    /** Puts the triggered abilities waiting to be put on the stack there, without resolving anything. */
    private void triggers() {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private boolean canCast(final Card spell, final Player p) {
        final SpellAbility sa = current(spell).getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canPlay();
    }

    private boolean canTarget(final Card spell, final Player p, final Card target) {
        final SpellAbility sa = current(spell).getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTarget(current(target));
    }

    private static boolean canTargetSpell(final Card spell, final Player p, final SpellAbility target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTargetSpellAbility(target);
    }

    private boolean canTargetWith(final Card c, final String ability, final Card target) {
        final SpellAbility sa = ability(current(c), ability);
        sa.setActivatingPlayer(c.getController());
        return sa.canTarget(current(target));
    }

    private boolean unblockable(final Card attacker, final Card blocker) {
        return !CombatUtil.canBlock(current(attacker), current(blocker));
    }

    private SpellAbility foretell(final Card c) {
        return current(c).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow();
    }

    private static boolean isClue(final Card c) {
        return c.getType().hasSubtype("Clue") && c.isEnchantment();
    }

    @Test
    public void annul() {
        newGame();
        islands(3);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Annul", me), orn);
        AssertJUnit.assertTrue("an artifact spell", in(orn.getHostCard(), ZoneType.Graveyard));
        lands("Swamp", 2, opp);
        final SpellAbility ench = oppCasts("Illness in the Ranks");
        cast(hand("Annul", me), ench);
        AssertJUnit.assertTrue("an enchantment spell", in(ench.getHostCard(), ZoneType.Graveyard));
        final SpellAbility feeder = oppCasts("Carrion Feeder");
        AssertJUnit.assertFalse("not a creature spell", canTargetSpell(hand("Annul", me), me, feeder));
        resolve();

        // Any activated ability, not only an artifact's or enchantment's
        newGame();
        islands(1);
        final Card f = creature("Carrion Feeder", opp);
        creature("Ornithopter", opp);
        put(opp, ability(f, "+1/+1 counter"), null);
        cast(hand("Annul", me), game.getStack().peekAbility());
        AssertJUnit.assertTrue(in(f, ZoneType.Battlefield));
        AssertJUnit.assertEquals("the ability was countered", 0, p1p1(current(f)));
    }

    @Test
    public void aqueousForm() {
        newGame();
        islands(1);
        final Card a = creature("Carrion Feeder", me);
        final Card blocker = creature("Woolly Thoctar", opp);
        cast(hand("Aqueous Form", me), a);
        AssertJUnit.assertTrue(unblockable(a, blocker));
        library(me, "Ornithopter", "Sign in Blood");
        final int lib = count(me, ZoneType.Library);
        attack(me, null, a);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals("scan: one of the top two exiled", 1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals(lib - 1, count(me, ZoneType.Library));
    }

    @Test
    public void arcaneArtisan() {
        newGame();
        islands(3);
        final Card art = creature("Arcane Artisan", me);
        library(me, "Carrion Feeder");
        activate(art, "Target player draws", me);
        AssertJUnit.assertEquals("drew, then exiled it", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue("a token copy of the creature", named(tokens(me).get(0), "Carrion Feeder"));
        kill(art);
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("left play: its tokens exiled at the next end step", tokens(me).isEmpty());
    }

    @Test
    public void arcaneFlight() {
        newGame();
        islands(1);
        final Card c = creature("Carrion Feeder", me);
        cast(hand("Arcane Flight", me), c);
        AssertJUnit.assertEquals("scry 2", 1, me.getScryThisTurn());
        AssertJUnit.assertEquals(2, current(c).getNetPower());
        AssertJUnit.assertEquals(2, current(c).getNetToughness());
        AssertJUnit.assertTrue(current(c).hasKeyword(Keyword.FLYING));
    }

    @Test
    public void armoredSkaab() {
        newGame();
        choices(me).targets = List.of(opp);
        final Card s = enter("Armored Skaab", me);
        AssertJUnit.assertEquals("enters: mill three", 3, count(opp, ZoneType.Graveyard));
        kill(s);
        AssertJUnit.assertEquals("dies: mill three", 6, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void asForetold() {
        newGame();
        final Card af = enter("As Foretold", me);
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("a time counter at the upkeep", 1, current(af).getCounters(CounterEnumType.TIME));
        final Card leak = hand("Mana Leak", me);
        final Card st = hand("Searing Touch", me);
        resolve(); // statics see the new cards
        AssertJUnit.assertTrue("mana value 2 > X", current(leak).getAllPossibleAbilities(me, true).stream().noneMatch(sa -> sa.getMayPlay() != null));
        cast(me, alternative(st, sa -> sa.getMayPlay() != null), targeting(opp));
        AssertJUnit.assertEquals("cast for 0", 18, opp.getLife());
        final Card st2 = hand("Searing Touch", me);
        resolve();
        AssertJUnit.assertTrue("once each turn", current(st2).getAllPossibleAbilities(me, true).stream().noneMatch(sa -> sa.getMayPlay() != null));
    }

    @Test
    public void augurOfBolas() {
        newGame();
        library(me, "Sign in Blood", "Woolly Thoctar", "Carrion Feeder");
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Sign in Blood");
        enter("Augur of Bolas", me);
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("the sorcery to the hand", named(me.getCardsIn(ZoneType.Hand).get(0), "Sign in Blood"));
        AssertJUnit.assertEquals("the rest into the graveyard", 2, count(me, ZoneType.Graveyard));
    }

    @Test
    public void auguryRaven() {
        newGame();
        islands(4);
        final Card r = hand("Augury Raven", me);
        cast(r);
        AssertJUnit.assertTrue(current(r).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("enters: scry 2", 1, me.getScryThisTurn());
        current(r).setSickness(false);
        attack(me, null, r);
        AssertJUnit.assertEquals("attacks: scry 2", 2, me.getScryThisTurn());
        AssertJUnit.assertEquals(17, opp.getLife());

        // Foretell U (the picture's cost; the XML says 1U)
        newGame();
        final List<Card> isl = islands(2);
        final Card f = hand("Augury Raven", me);
        cast(me, foretell(f), null);
        AssertJUnit.assertTrue(in(f, ZoneType.Exile) && current(f).isForetold());
        playUntil(me, PhaseType.MAIN1);
        current(isl.get(0)).setTapped(true);
        cast(me, alternative(f, SpellAbility::isForetold), null);
        AssertJUnit.assertTrue("cast for U", in(f, ZoneType.Battlefield));
    }

    @Test
    public void baralChiefOfCompliance() {
        newGame();
        islands(3);
        creature("Baral Chief of Compliance", me);
        cast(hand("Concentrate", me));
        AssertJUnit.assertEquals("2UU cast with three lands", 3, count(me, ZoneType.Hand));

        newGame();
        islands(1);
        creature("Baral Chief of Compliance", me);
        final SpellAbility orn = oppCasts("Ornithopter");
        hand("Woolly Thoctar", me);
        choices(me).confirm = true;
        cast(hand("Mana Leak", me), orn);
        AssertJUnit.assertTrue("Mana Leak (1U) cast for U", in(orn.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertEquals("drew, then discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Graveyard));
    }

    @Test
    public void battlefieldThaumaturge() {
        newGame();
        islands(2);
        final Card bt = creature("Battlefield Thaumaturge", me);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Capsize", me), t);
        AssertJUnit.assertTrue("1UU cast for UU: it targets a creature", in(t, ZoneType.Hand));
        AssertJUnit.assertEquals("prowess", 3, current(bt).getNetPower());
        islands(1);
        final Card other = creature("Carrion Feeder", me);
        cast(hand("Arcane Flight", me), bt);
        AssertJUnit.assertTrue("heroic", current(other).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertTrue(current(bt).hasKeyword(Keyword.HEXPROOF));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("this turn", current(other).hasKeyword(Keyword.HEXPROOF));
    }

    @Test
    public void bazaarTrademage() {
        newGame();
        islands(3);
        playUntil(opp, PhaseType.MAIN1);
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        final Card bt = hand("Bazaar Trademage", me);
        AssertJUnit.assertTrue("flash", canCast(bt, me));
        cast(bt);
        AssertJUnit.assertTrue(current(bt).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("2 + 2 drawn - 3 discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void beguilerOfWills() {
        newGame();
        final Card b = creature("Beguiler of Wills", me);
        creature("Carrion Feeder", me);
        final Card small = creature("Carrion Feeder", opp);
        final Card big = creature("Woolly Thoctar", opp);
        AssertJUnit.assertFalse("power 6, two creatures", canTargetWith(b, "Gain control", big));
        activate(b, "Gain control", small);
        AssertJUnit.assertEquals(me, current(small).getController());
    }

    @Test
    public void benthicInfiltrator() {
        newGame();
        final Card bi = creature("Benthic Infiltrator", me);
        AssertJUnit.assertTrue("devoid", bi.isColorless());
        final Card blocker = creature("Woolly Thoctar", opp);
        AssertJUnit.assertTrue(unblockable(bi, blocker));
        attack(me, null, bi);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals("ingest: the top two exiled", 2, count(opp, ZoneType.Exile));
    }

    @Test
    public void boomerang() {
        newGame();
        islands(4);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Boomerang", me), t);
        AssertJUnit.assertTrue("a permanent", in(t, ZoneType.Hand));
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Boomerang", me), current(orn.getHostCard()));
        AssertJUnit.assertTrue("a spell", in(orn.getHostCard(), ZoneType.Hand));
        AssertJUnit.assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void breakthrough() {
        newGame();
        islands(2);
        hand("Carrion Feeder", me);
        final Card b = hand("Breakthrough", me);
        b.getFirstSpellAbility().setXManaCostPaid(1);
        cast(b);
        AssertJUnit.assertEquals("drew four, kept X = 1", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(5, count(me, ZoneType.Graveyard));
    }

    @Test
    public void brineGiant() {
        newGame();
        islands(4);
        enter("Illness in the Ranks", me);
        enter("Illness in the Ranks", me);
        final Card g = hand("Brine Giant", me);
        cast(g);
        AssertJUnit.assertTrue("affinity for enchantments: 5U for 3U", in(g, ZoneType.Battlefield));
        lands("Mountain", 1, opp);
        choices(opp).payUnless = false;
        final SpellAbility st = oppCasts("Searing Touch", current(g));
        resolve();
        AssertJUnit.assertTrue("ward 1", in(st.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, current(g).getDamage());
    }

    @Test
    public void capsize() {
        newGame();
        islands(6);
        final Card t = creature("Woolly Thoctar", opp);
        final Card cap = hand("Capsize", me);
        cast(me, withOptionalCosts(cap), targeting(t));
        AssertJUnit.assertTrue(in(t, ZoneType.Hand));
        AssertJUnit.assertTrue("buyback", in(cap, ZoneType.Hand));
        islands(3);
        final Card t2 = creature("Carrion Feeder", opp);
        cast(cap, t2);
        AssertJUnit.assertTrue(in(t2, ZoneType.Hand));
        AssertJUnit.assertTrue("no buyback", in(cap, ZoneType.Graveyard));
    }

    @Test
    public void chartACourse() {
        newGame();
        islands(2);
        cast(hand("Chart a Course", me));
        AssertJUnit.assertEquals("drew two, discarded one", 1, count(me, ZoneType.Hand));

        newGame();
        islands(2);
        final Card a = creature("Carrion Feeder", me);
        attack(me, null, a);
        cast(hand("Chart a Course", me));
        AssertJUnit.assertEquals("attacked: no discard", 2, count(me, ZoneType.Hand));
    }

    @Test
    public void circularLogic() {
        newGame();
        islands(3);
        grave("Woolly Thoctar", me);
        grave("Woolly Thoctar", me);
        final SpellAbility orn = oppCasts("Ornithopter");
        lands("Mountain", 1, opp);
        choices(opp).payUnless = true;
        cast(hand("Circular Logic", me), orn);
        AssertJUnit.assertTrue("1 for each of two cards: one land can't pay", in(orn.getHostCard(), ZoneType.Graveyard));

        // Madness U
        newGame();
        islands(1);
        grave("Woolly Thoctar", me);
        final SpellAbility orn2 = oppCasts("Ornithopter");
        choices(me).confirm = true;
        discard(me, hand("Circular Logic", me));
        AssertJUnit.assertTrue("cast for its madness cost", in(orn2.getHostCard(), ZoneType.Graveyard));
    }

    @Test
    public void citywatchSphinx() {
        newGame();
        final Card s = creature("Citywatch Sphinx", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        kill(s);
        AssertJUnit.assertEquals("dies: surveil", 1, me.getSurveilThisTurn());

        newGame();
        islands(1);
        final Card c = hand("Citywatch Sphinx", me);
        activate(c, "Cycling");
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 1", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void concentrate() {
        newGame();
        islands(4);
        cast(hand("Concentrate", me));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
    }

    @Test
    public void condescend() {
        newGame();
        islands(3);
        final SpellAbility orn = oppCasts("Ornithopter");
        lands("Mountain", 1, opp);
        choices(opp).payUnless = true;
        final Card c = hand("Condescend", me);
        c.getFirstSpellAbility().setXManaCostPaid(2);
        cast(c, orn);
        AssertJUnit.assertTrue("X = 2: one land can't pay", in(orn.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertEquals("scry 2", 1, me.getScryThisTurn());
    }

    @Test
    public void confirmSuspicions() {
        newGame();
        islands(5);
        final SpellAbility orn = oppCasts("Ornithopter");
        final SpellAbility spell = put(me, hand("Confirm Suspicions", me).getFirstSpellAbility(), targeting(orn));
        lands("Island", 2, opp);
        oppCasts("Mana Leak", spell);
        resolve();
        AssertJUnit.assertTrue("can't be countered", in(orn.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertEquals("investigate three times", 3, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).stream().allMatch(TbldBlueCards1Test::isClue));
    }

    @Test
    public void confound() {
        newGame();
        islands(2);
        final Card c = creature("Carrion Feeder", me);
        lands("Mountain", 2, opp);
        final SpellAbility atCreature = oppCasts("Searing Touch", c);
        final SpellAbility atMe = oppCasts("Searing Touch", me);
        final Card conf = hand("Confound", me);
        AssertJUnit.assertFalse("targets a player, not a permanent", canTargetSpell(conf, me, atMe));
        cast(conf, atCreature);
        AssertJUnit.assertTrue(in(c, ZoneType.Battlefield));
        AssertJUnit.assertTrue(in(atCreature.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(18, me.getLife());
    }

    @Test
    public void curiousInquiry() {
        newGame();
        islands(1);
        final Card a = creature("Carrion Feeder", me);
        cast(hand("Curious Inquiry", me), a);
        AssertJUnit.assertEquals(3, current(a).getNetPower());
        AssertJUnit.assertEquals(2, current(a).getNetToughness());
        attack(me, null, a);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertEquals("investigate", 1, tokens(me).size());
        AssertJUnit.assertTrue(isClue(tokens(me).get(0)));
    }

    @Test
    public void cyclonicRift() {
        newGame();
        islands(2);
        final Card t = creature("Woolly Thoctar", opp);
        final Card mine = creature("Carrion Feeder", me);
        final Card rift = hand("Cyclonic Rift", me);
        AssertJUnit.assertFalse("only permanents you don't control", canTarget(rift, me, mine));
        cast(rift, t);
        AssertJUnit.assertTrue(in(t, ZoneType.Hand));

        // Overload 6U
        newGame();
        islands(7);
        final Card a = creature("Woolly Thoctar", opp);
        final Card b = creature("Ornithopter", opp);
        final Card land = lands("Mountain", 1, opp).get(0);
        final Card mine2 = creature("Carrion Feeder", me);
        final Card rift2 = hand("Cyclonic Rift", me);
        cast(me, alternative(rift2, sa -> sa.isAlternativeCost(AlternativeCost.Overload)), null);
        AssertJUnit.assertTrue(in(a, ZoneType.Hand) && in(b, ZoneType.Hand));
        AssertJUnit.assertTrue("nonland", in(land, ZoneType.Battlefield));
        AssertJUnit.assertTrue("you don't control", in(mine2, ZoneType.Battlefield));
    }

    @Test
    public void deceiverExarch() {
        newGame();
        islands(3);
        playUntil(opp, PhaseType.MAIN1);
        final Card t1 = creature("Woolly Thoctar", opp);
        final Card t2 = creature("Carrion Feeder", opp);
        final Card de = hand("Deceiver Exarch", me);
        AssertJUnit.assertTrue("flash", canCast(de, me));
        choices(me).modes.add("Tap two");
        choices(me).targets = List.of(t1, t2);
        cast(de);
        AssertJUnit.assertTrue(current(t1).isTapped() && current(t2).isTapped());

        newGame();
        final List<Card> isl = islands(3);
        choices(me).modes.add("Untap two");
        choices(me).targets = List.of(isl.get(0), isl.get(1));
        cast(hand("Deceiver Exarch", me));
        AssertJUnit.assertEquals(2, isl.stream().filter(c -> !current(c).isTapped()).count());
    }

    @Test
    public void deepSeaKraken() {
        newGame();
        islands(3);
        final Card k = hand("Deep-Sea Kraken", me);
        cast(me, alternative(k, sa -> sa.getDescription().startsWith("Suspend")), null);
        AssertJUnit.assertTrue("suspend 9 for 2U", in(k, ZoneType.Exile));
        AssertJUnit.assertEquals(9, current(k).getCounters(CounterEnumType.TIME));
        lands("Mountain", 1, opp);
        oppCasts("Searing Touch", me);
        resolve();
        AssertJUnit.assertEquals("an opponent cast a spell", 8, current(k).getCounters(CounterEnumType.TIME));

        newGame();
        final Card k2 = creature("Deep-Sea Kraken", me);
        AssertJUnit.assertTrue(unblockable(k2, creature("Woolly Thoctar", opp)));
        AssertJUnit.assertFalse("no hexproof on the picture", k2.hasKeyword(Keyword.HEXPROOF));
    }

    @Test
    public void deepfathomSkulker() {
        newGame();
        islands(3);
        lands("Wastes", 1, me);
        final Card s = creature("Deepfathom Skulker", me);
        AssertJUnit.assertTrue("devoid", s.isColorless());
        final Card a = creature("Carrion Feeder", me);
        final Card blocker = creature("Woolly Thoctar", opp);
        activate(s, "can't be blocked", a);
        AssertJUnit.assertTrue(unblockable(a, blocker));
        choices(me).confirm = true;
        attack(me, null, a);
        AssertJUnit.assertEquals("a creature dealt combat damage: draw", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void deeprootWaters() {
        newGame();
        islands(2);
        enter("Deeproot Waters", me);
        cast(hand("Augur of Bolas", me));
        AssertJUnit.assertEquals("a Merfolk spell", 1, tokens(me).size());
        final Card m = tokens(me).get(0);
        AssertJUnit.assertTrue(m.getType().hasCreatureType("Merfolk") && m.isBlue());
        AssertJUnit.assertTrue(m.getNetPower() == 1 && m.getNetToughness() == 1);
        AssertJUnit.assertTrue(m.hasKeyword(Keyword.HEXPROOF));
        islands(4);
        cast(hand("Concentrate", me));
        AssertJUnit.assertEquals("not a Merfolk", 1, tokens(me).size());
    }

    @Test
    public void deprive() {
        newGame();
        islands(3);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Deprive", me), orn);
        AssertJUnit.assertTrue("exiled", in(orn.getHostCard(), ZoneType.Exile));
        AssertJUnit.assertEquals("returned a land", 2, count(me, ZoneType.Battlefield));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Island"));
    }

    @Test
    public void depthDefiler() {
        newGame();
        islands(5);
        lands("Wastes", 1, me);
        final Card t = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(t, opp);
        final Card d = hand("Depth Defiler", me);
        cast(me, withOptionalCosts(d), null);
        AssertJUnit.assertTrue("devoid", current(d).isColorless());
        AssertJUnit.assertTrue(in(d, ZoneType.Battlefield));
        AssertJUnit.assertFalse("kicked: both (returned, then discarded)", in(t, ZoneType.Battlefield));
        AssertJUnit.assertEquals("returned one, drew two, discarded one", 2, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Graveyard));

        newGame();
        islands(5);
        final Card t2 = creature("Woolly Thoctar", opp);
        choices(me).modes.add("draws two");
        choices(me).targets = List.of(me);
        cast(hand("Depth Defiler", me));
        AssertJUnit.assertTrue("not kicked: one", in(t2, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
    }

    @Test
    public void deviousCoverUp() {
        newGame();
        islands(4);
        final Card g1 = grave("Carrion Feeder", me);
        final Card g2 = grave("Woolly Thoctar", me);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(me, hand("Devious Cover-Up", me).getFirstSpellAbility(), targeting(orn, g1, g2));
        AssertJUnit.assertTrue("countered and exiled", in(orn.getHostCard(), ZoneType.Exile));
        AssertJUnit.assertTrue(in(g1, ZoneType.Library) && in(g2, ZoneType.Library));
    }

    @Test
    public void disdainfulStroke() {
        newGame();
        islands(2);
        lands("Island", 5, opp);
        final Card dh = hand("Drowner of Hope", opp);
        final SpellAbility spell = put(opp, dh.getFirstSpellAbility(), null);
        triggers();
        AssertJUnit.assertEquals("the spell and its cast trigger", 2, game.getStack().size());
        final Card ds = hand("Disdainful Stroke", me);
        cast(ds, spell);
        AssertJUnit.assertTrue("mana value 5", in(dh, ZoneType.Graveyard));
        AssertJUnit.assertTrue("its trigger was countered", tokens(opp).isEmpty());
        final SpellAbility orn = oppCasts("Ornithopter");
        AssertJUnit.assertFalse("mana value 0", canTargetSpell(hand("Disdainful Stroke", me), me, orn));
    }

    @Test
    public void distantMelody() {
        newGame();
        islands(4);
        creature("Carrion Feeder", me);
        creature("Carrion Feeder", me);
        creature("Woolly Thoctar", me);
        cast(hand("Distant Melody", me));
        AssertJUnit.assertEquals("two Zombies", 2, count(me, ZoneType.Hand));
    }

    @Test
    public void divination() {
        newGame();
        final List<Card> isl = islands(3);
        cast(hand("Divination", me), me);
        AssertJUnit.assertEquals(2, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(isl.stream().allMatch(c -> current(c).isTapped()));
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertEquals("next end step: untap a permanent", 1, isl.stream().filter(c -> !current(c).isTapped()).count());
    }

    @Test
    public void domineer() {
        newGame();
        islands(2);
        final Card o = creature("Ornithopter", opp);
        final Card d = hand("Domineer", me);
        AssertJUnit.assertFalse("artifact creatures only", canTarget(d, me, creature("Woolly Thoctar", opp)));
        cast(d, o);
        AssertJUnit.assertEquals(me, current(o).getController());
    }

    @Test
    public void dreamHalls() {
        newGame();
        enter("Dream Halls", me);
        hand("Carrion Feeder", me);
        hand("Divination", me);
        final Card conc = hand("Concentrate", me);
        cast(me, alternative(conc, sa -> sa.getPayCosts().hasSpecificCostType(CostDiscard.class)), null);
        AssertJUnit.assertTrue("discarded the blue card", me.getCardsIn(ZoneType.Graveyard).stream().anyMatch(c -> named(c, "Divination")));
        AssertJUnit.assertEquals("Feeder and three drawn", 4, count(me, ZoneType.Hand));

        // Its controller: the opponent may too
        final Card leak = hand("Mana Leak", opp);
        hand("Concentrate", opp);
        alternative(opp, leak, sa -> sa.getPayCosts().hasSpecificCostType(CostDiscard.class));
    }

    @Test
    public void dreamTwist() {
        newGame();
        islands(1);
        final Card dt = hand("Dream Twist", me);
        cast(dt, opp);
        AssertJUnit.assertEquals(3, count(opp, ZoneType.Graveyard));
        untapAll(me);
        cast(me, alternative(dt, SpellAbility::isFlashback), targeting(opp));
        AssertJUnit.assertEquals("flashback U", 6, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(dt, ZoneType.Exile));
    }

    @Test
    public void drownedSecrets() {
        newGame();
        islands(4);
        enter("Drowned Secrets", me);
        choices(me).targets = List.of(opp);
        cast(hand("Concentrate", me));
        AssertJUnit.assertEquals("a blue spell", 2, count(opp, ZoneType.Graveyard));
        lands("Swamp", 2, me);
        cast(hand("Sign in Blood", me), me);
        AssertJUnit.assertEquals("not a blue spell", 2, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void drownerOfHope() {
        newGame();
        islands(5);
        final Card d = hand("Drowner of Hope", me);
        cast(d);
        AssertJUnit.assertTrue("devoid", current(d).isColorless());
        AssertJUnit.assertEquals("two Spawn when cast", 2, tokens(me).size());
        final Card spawn = tokens(me).get(0);
        AssertJUnit.assertTrue(spawn.getType().hasCreatureType("Eldrazi") && spawn.isColorless() && spawn.getNetPower() == 1);
        final Card t = creature("Woolly Thoctar", opp);
        activate(d, "Tap target", t);
        AssertJUnit.assertTrue(current(t).isTapped());
        AssertJUnit.assertEquals("sacrificed an Eldrazi", 1, tokens(me).size());
        mana(tokens(me).get(0), null);
        AssertJUnit.assertTrue("Sacrifice this: Add C", tokens(me).isEmpty());
        AssertJUnit.assertEquals(1, me.getManaPool().totalMana());
    }

    @Test
    public void echoOfEons() {
        newGame();
        islands(6);
        hand("Carrion Feeder", me);
        grave("Woolly Thoctar", me);
        hand("Carrion Feeder", opp);
        grave("Woolly Thoctar", opp);
        final Card e = hand("Echo of Eons", me);
        cast(e);
        AssertJUnit.assertEquals(7, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(7, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals("only Echo of Eons", 1, count(me, ZoneType.Graveyard));
        untapAll(me);
        cast(me, alternative(e, SpellAbility::isFlashback), null);
        AssertJUnit.assertTrue("flashback 2U", in(e, ZoneType.Exile));
        AssertJUnit.assertEquals(7, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Graveyard));
    }

    @Test
    public void eelUmbra() {
        newGame();
        islands(2);
        final Card c = creature("Carrion Feeder", me);
        playUntil(opp, PhaseType.MAIN1);
        final Card eel = hand("Eel Umbra", me);
        AssertJUnit.assertTrue("flash", canCast(eel, me));
        cast(eel, c);
        AssertJUnit.assertEquals(3, current(c).getNetPower());
        AssertJUnit.assertEquals(2, current(c).getNetToughness());
        kill(c);
        AssertJUnit.assertTrue("umbra armor", in(c, ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, current(c).getDamage());
        AssertJUnit.assertTrue(in(eel, ZoneType.Graveyard));
    }

    @Test
    public void eldraziSkyspawner() {
        newGame();
        final Card s = creature("Eldrazi Skyspawner", me);
        AssertJUnit.assertTrue("devoid", s.isColorless());
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("a Spawn when it enters (the picture)", 1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).getType().hasCreatureType("Spawn"));
        attack(me, null, s);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals("ingest", 2, count(opp, ZoneType.Exile));
    }

    @Test
    public void essenceCapture() {
        newGame();
        islands(2);
        final Card mine = creature("Carrion Feeder", me);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(me, hand("Essence Capture", me).getFirstSpellAbility(), targeting(orn, mine));
        AssertJUnit.assertTrue(in(orn.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, p1p1(current(mine)));
        lands("Swamp", 1, opp);
        final SpellAbility ench = oppCasts("Illness in the Ranks");
        AssertJUnit.assertFalse("creature spells only", canTargetSpell(hand("Essence Capture", me), me, ench));
    }

    @Test
    public void essenceScatter() {
        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Essence Scatter", me), orn);
        AssertJUnit.assertTrue(in(orn.getHostCard(), ZoneType.Graveyard));
        islands(2);
        final Card c = hand("Essence Scatter", me);
        activate(c, "Cycling");
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 2", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void exclude() {
        newGame();
        islands(3);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Exclude", me), orn);
        AssertJUnit.assertTrue("exiled", in(orn.getHostCard(), ZoneType.Exile));
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void exhaustion() {
        newGame();
        islands(3);
        final Card t = creature("Woolly Thoctar", opp);
        t.setTapped(true);
        final Card u = creature("Carrion Feeder", opp);
        final List<Card> ol = lands("Mountain", 2, opp);
        ol.get(0).setTapped(true);
        cast(hand("Exhaustion", me), opp);
        AssertJUnit.assertEquals(1, current(t).getCounters(CounterEnumType.STUN));
        AssertJUnit.assertEquals(1, current(ol.get(0)).getCounters(CounterEnumType.STUN));
        AssertJUnit.assertEquals("untapped", 0, current(u).getCounters(CounterEnumType.STUN));
        AssertJUnit.assertEquals(0, current(ol.get(1)).getCounters(CounterEnumType.STUN));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("the stun counter kept it tapped", current(t).isTapped());
        AssertJUnit.assertEquals(0, current(t).getCounters(CounterEnumType.STUN));
    }
}
