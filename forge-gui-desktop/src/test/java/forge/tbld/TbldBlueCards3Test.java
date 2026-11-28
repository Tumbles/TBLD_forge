package forge.tbld;

import forge.ai.ComputerUtil;
import forge.deck.DeckFormat;
import forge.game.GameObject;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

/** Blue TBLD cards, third batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlueCards3Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Moonsnare Specialist", "Muddle the Mixture", "Mulldrifter", "Murk Strider", "Mystic Reflection",
            "Mystical Teachings", "Neutralizing Blast", "New Perspectives", "Ninja of the Deep Hours", "Novijen Sages",
            "Nyxborn Triton", "Ordeal of Thassa", "Out of Air", "Persistent Petitioners", "Pestermite",
            "Phantasmal Dreadmaw", "Phantom Carriage", "Pieces of the Puzzle", "Precognitive Perception",
            "Prophet of Distortion", "Prosperous Thief", "Psychic Surgery", "Quasiduplicate", "Qumulox", "Radical Idea",
            "Reason", "Retraction Helix", "Rewind", "Rise and Shine", "River Herald Scout", "Riverwise Augur",
            "Roil Cartographer", "Rooftop Storm", "Ruination Guide", "Sage of Hours", "Sailor of Means", "Salvage Drone",
            "Scholar of the Ages", "Scroll Thief", "Sea-Dasher Octopus", "Seagraf Skaab", "Shipwreck Dowser",
            "Shoal Serpent", "Shoreline Looter", "Show and Tell", "Shriekgeist", "Skaab Ruinator", "Sleep of the Dead",
            "Slimebind", "Sower of Temptation");

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

    private boolean canCast(final Card spell, final Player p) {
        final SpellAbility sa = current(spell).getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canPlay();
    }

    private static boolean canTargetSpell(final Card spell, final Player p, final SpellAbility target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTargetSpellAbility(target);
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

    /** The opponent targets the permanent with Searing Touch and doesn't pay ward: true if it was countered. */
    private boolean wardCounters(final Card target) {
        lands("Mountain", 1, opp);
        choices(opp).payUnless = false;
        final SpellAbility st = oppCasts("Searing Touch", current(target));
        resolve();
        return countered(st) && current(target).getDamage() == 0;
    }

    private long treasures(final Player p) {
        return tokens(p).stream().filter(c -> c.getType().hasSubtype("Treasure")).count();
    }

    /** Attacks with the creature, then puts the ninja in at declare blockers by its ninjutsu. */
    private Card ninjutsu(final String ninja, final Card attacker) {
        choices(me).attackers = List.of(attacker);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        choices(me).attackers = List.of();
        final Card n = hand(ninja, me); // not earlier: the AI would cast it
        activate(n, "Ninjutsu");
        AssertJUnit.assertTrue("ninjutsu returns the attacker", in(attacker, ZoneType.Hand));
        AssertJUnit.assertTrue("tapped and attacking", current(n).isAttacking() && current(n).isTapped());
        return n;
    }

    @Test
    public void moonsnareSpecialist() {
        newGame();
        final Card t = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(t);
        final Card m = enter("Moonsnare Specialist", me);
        AssertJUnit.assertTrue("returned up to one target creature", in(t, ZoneType.Hand));
        AssertJUnit.assertTrue(m.hasKeyword(Keyword.HEXPROOF));

        newGame();
        islands(3);
        final Card o = creature("Ornithopter", opp);
        choices(me).targets = List.of(o);
        ninjutsu("Moonsnare Specialist", creature("Carrion Feeder", me));
        AssertJUnit.assertTrue("ninjutsu 2U: it entered, so a creature returned", in(o, ZoneType.Hand));
    }

    @Test
    public void muddleTheMixture() {
        newGame();
        islands(2);
        lands("Island", 4, opp);
        final SpellAbility conc = oppCasts("Concentrate");
        cast(hand("Muddle the Mixture", me), conc);
        AssertJUnit.assertTrue(countered(conc));
        final SpellAbility orn = oppCasts("Ornithopter");
        AssertJUnit.assertFalse("sorcery spells only", canTargetSpell(hand("Muddle the Mixture", me), me, orn));
        resolve();

        newGame();
        islands(2);
        library(me, "Jump", "Mana Leak", "Concentrate");
        choices(me).prefer = c -> true;
        final Card m = hand("Muddle the Mixture", me);
        activate(m, "Transmute");
        AssertJUnit.assertTrue("transmute exiles it from the hand", in(m, ZoneType.Exile));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("found the card with mana value 2", named(handCard(me, 0), "Mana Leak"));
    }

    @Test
    public void mulldrifter() {
        newGame();
        islands(5);
        final Card m = hand("Mulldrifter", me);
        cast(m);
        AssertJUnit.assertTrue(current(m).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("draw two", 2, count(me, ZoneType.Hand));

        newGame();
        islands(3);
        final Card e = hand("Mulldrifter", me);
        cast(me, alternative(e, SpellAbility::isEvoke), null);
        AssertJUnit.assertTrue("evoke 2U: sacrificed", in(e, ZoneType.Graveyard));
        AssertJUnit.assertEquals("still drew two", 2, count(me, ZoneType.Hand));
    }

    @Test
    public void murkStrider() {
        newGame();
        islands(4);
        final Card t = creature("Woolly Thoctar", opp);
        addCardToZone("Woolly Thoctar", opp, ZoneType.Exile);
        addCardToZone("Woolly Thoctar", opp, ZoneType.Exile);
        choices(me).confirm = true;
        choices(me).targets = List.of(t);
        final Card ms = hand("Murk Strider", me);
        cast(ms);
        AssertJUnit.assertTrue("devoid", current(ms).isColorless());
        AssertJUnit.assertEquals(602, pt(current(ms)));
        AssertJUnit.assertEquals("two exiled cards to their graveyard", 2, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Exile));
        AssertJUnit.assertTrue("then returned a creature", in(t, ZoneType.Hand));

        newGame();
        islands(4);
        final Card t2 = creature("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(t2);
        playUntil(opp, PhaseType.MAIN1);
        final Card ms2 = hand("Murk Strider", me);
        AssertJUnit.assertTrue("flash", canCast(ms2, me));
        cast(ms2);
        AssertJUnit.assertTrue("no exiled cards: nothing returned", in(t2, ZoneType.Battlefield));
    }

    @Test
    public void mysticReflection() {
        newGame();
        islands(2);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Mystic Reflection", me), t);
        playUntil(me, PhaseType.MAIN1); // not only this turn
        final Card o = hand("Ornithopter", me);
        cast(o);
        AssertJUnit.assertTrue("entered as a copy", named(current(o), "Woolly Thoctar"));
        final Card o2 = hand("Ornithopter", me);
        cast(o2);
        AssertJUnit.assertTrue("only the next time", named(current(o2), "Ornithopter"));

        // Foretell U
        newGame();
        final List<Card> isl = islands(2);
        final Card f = hand("Mystic Reflection", me);
        cast(me, current(f).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow(), null);
        AssertJUnit.assertTrue(in(f, ZoneType.Exile) && current(f).isForetold());
        final Card t2 = creature("Woolly Thoctar", opp);
        playUntil(me, PhaseType.MAIN1);
        current(isl.get(0)).setTapped(true);
        cast(me, alternative(f, SpellAbility::isForetold), targeting(t2));
        AssertJUnit.assertTrue("cast for U", in(f, ZoneType.Graveyard));
    }

    @Test
    public void mysticalTeachings() {
        newGame();
        islands(4);
        library(me, "Searing Touch", "Concentrate");
        choices(me).prefer = c -> true;
        final Card mt = hand("Mystical Teachings", me);
        cast(mt);
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("a flash card", named(handCard(me, 0), "Searing Touch"));
        lands("Swamp", 1, me);
        islands(5);
        library(me, "Pestermite");
        cast(me, alternative(mt, SpellAbility::isFlashback), null);
        AssertJUnit.assertTrue("flashback 5B", in(mt, ZoneType.Exile));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Hand));
    }

    @Test
    public void neutralizingBlast() {
        newGame();
        islands(1);
        lands("Forest", 1, opp);
        lands("Mountain", 1, opp);
        lands("Plains", 1, opp);
        final SpellAbility t = oppCasts("Woolly Thoctar");
        cast(hand("Neutralizing Blast", me), t);
        AssertJUnit.assertTrue(countered(t));
        final SpellAbility orn = oppCasts("Ornithopter");
        AssertJUnit.assertFalse("multicolored only", canTargetSpell(hand("Neutralizing Blast", me), me, orn));
        resolve();
    }

    private SpellAbility freeCycling(final Card c) {
        return current(c).getAllPossibleAbilities(me, true).stream()
                .filter(sa -> sa.isCycling() && sa.getPayCosts().getTotalMana().isZero()).findFirst().orElse(null);
    }

    @Test
    public void newPerspectives() {
        newGame();
        islands(5);
        cast(hand("New Perspectives", me));
        AssertJUnit.assertEquals("draw three", 3, count(me, ZoneType.Hand));
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        final Card sk = hand("Seagraf Skaab", me);
        resolve();
        AssertJUnit.assertNull("six cards: pay cycling costs", freeCycling(sk));
        hand("Woolly Thoctar", me);
        resolve();
        final SpellAbility free = freeCycling(sk);
        AssertJUnit.assertNotNull("seven cards: cycle for 0", free);
        cast(me, free, null);
        AssertJUnit.assertTrue(in(sk, ZoneType.Graveyard));
        AssertJUnit.assertEquals("discarded and drew", 7, count(me, ZoneType.Hand));
    }

    @Test
    public void ninjaOfTheDeepHours() {
        newGame();
        islands(2);
        ninjutsu("Ninja of the Deep Hours", creature("Carrion Feeder", me));
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals("the returned attacker and a drawn card", 2, count(me, ZoneType.Hand));
    }

    @Test
    public void novijenSages() {
        newGame();
        islands(1);
        final Card s = enter("Novijen Sages", me);
        AssertJUnit.assertEquals("graft 4", 4, p1p1(s));
        choices(me).confirm = true;
        final Card o = enter("Ornithopter", me);
        AssertJUnit.assertEquals("moved a counter", 3, p1p1(current(s)));
        AssertJUnit.assertEquals(1, p1p1(current(o)));
        activate(s, "Draw a card");
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("removed two counters", 2, p1p1(current(s)) + p1p1(current(o)));
    }

    @Test
    public void nyxbornTriton() {
        newGame();
        final Card t = creature("Nyxborn Triton", me);
        AssertJUnit.assertEquals(203, pt(t));
        AssertJUnit.assertTrue("ward 1", wardCounters(t));

        newGame();
        islands(3);
        final Card c = creature("Carrion Feeder", me);
        final Card tr = hand("Nyxborn Triton", me);
        cast(me, alternative(tr, SpellAbility::isBestow), targeting(c));
        AssertJUnit.assertEquals("bestow 2U", current(c), current(tr).getAttachedTo());
        AssertJUnit.assertEquals("+2/+3", 304, pt(current(c)));
        AssertJUnit.assertTrue("and ward 1", wardCounters(c));
    }

    @Test
    public void ordealOfThassa() {
        newGame();
        islands(2);
        final Card c = creature("Carrion Feeder", me);
        final Card o = hand("Ordeal of Thassa", me);
        cast(o, c);
        attack(me, null, c);
        AssertJUnit.assertEquals(1, p1p1(current(c)));
        playUntil(me, PhaseType.MAIN1);
        attack(me, null, c);
        AssertJUnit.assertEquals(2, p1p1(current(c)));
        AssertJUnit.assertTrue(in(o, ZoneType.Battlefield));
        playUntil(me, PhaseType.MAIN1);
        final int hand = count(me, ZoneType.Hand);
        attack(me, null, c);
        AssertJUnit.assertEquals(3, p1p1(current(c)));
        AssertJUnit.assertTrue("three counters: sacrificed", in(o, ZoneType.Graveyard));
        AssertJUnit.assertEquals("scry 2", 1, me.getScryThisTurn());
        AssertJUnit.assertEquals("then draw two", hand + 2, count(me, ZoneType.Hand));
    }

    @Test
    public void outOfAir() {
        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Out of Air", me), orn);
        AssertJUnit.assertTrue("a creature spell: UU", countered(orn));

        newGame();
        final List<Card> isl = islands(4);
        lands("Island", 4, opp);
        final SpellAbility conc = oppCasts("Concentrate");
        cast(hand("Out of Air", me), conc);
        AssertJUnit.assertTrue(countered(conc));
        AssertJUnit.assertTrue("otherwise 2UU", isl.stream().allMatch(c -> current(c).isTapped()));
    }

    @Test
    public void persistentPetitioners() {
        newGame();
        islands(1);
        final Card p = creature("Persistent Petitioners", me);
        AssertJUnit.assertTrue("any number in a deck", DeckFormat.canHaveAnyNumberOf(p.getPaperCard()));
        activate(p, "mills two", opp);
        AssertJUnit.assertEquals(2, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(p).isTapped());

        newGame();
        library(opp, "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        final List<Card> ps = List.of(creature("Persistent Petitioners", me), creature("Persistent Petitioners", me),
                creature("Persistent Petitioners", me), creature("Persistent Petitioners", me));
        activate(ps.get(0), "mills twelve", opp);
        AssertJUnit.assertEquals(12, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertTrue("tapped four Advisors", ps.stream().allMatch(c -> current(c).isTapped()));
    }

    @Test
    public void pestermite() {
        newGame();
        final Card t = creature("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(t);
        final Card p = enter("Pestermite", me);
        AssertJUnit.assertTrue(p.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertTrue("tapped target permanent", current(t).isTapped());
        final Card isl = islands(1).get(0);
        isl.setTapped(true);
        choices(me).targets = List.of(isl);
        enter("Pestermite", me);
        AssertJUnit.assertFalse("or untapped it", current(isl).isTapped());

        newGame();
        islands(2);
        library(me, "Spellstutter Sprite", "Woolly Thoctar");
        choices(me).prefer = c -> true;
        final Card fc = hand("Pestermite", me);
        activate(fc, "cycling");
        AssertJUnit.assertTrue(in(fc, ZoneType.Graveyard));
        AssertJUnit.assertTrue("Faeriecycling 2", named(handCard(me, 0), "Spellstutter Sprite"));
        playUntil(opp, PhaseType.MAIN1);
        islands(3);
        AssertJUnit.assertTrue("flash", canCast(hand("Pestermite", me), me));
    }

    @Test
    public void phantasmalDreadmaw() {
        newGame();
        islands(3);
        final Card d = hand("Phantasmal Dreadmaw", me);
        AssertJUnit.assertEquals("mana value 6", 6, d.getCMC());
        cast(d);
        AssertJUnit.assertTrue("2/U 2/U 2/U paid with UUU", in(d, ZoneType.Battlefield));
        AssertJUnit.assertTrue(current(d).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertEquals(606, pt(current(d)));
        lands("Mountain", 1, opp);
        oppCasts("Searing Touch", current(d));
        resolve();
        AssertJUnit.assertTrue("targeted: sacrificed", in(d, ZoneType.Graveyard));
    }

    @Test
    public void phantomCarriage() {
        newGame();
        library(me, "Concentrate");
        choices(me).prefer = c -> named(c, "Concentrate");
        final Card pc = creature("Phantom Carriage", me);
        AssertJUnit.assertEquals("searched a card into the graveyard", 1, count(me, ZoneType.Graveyard));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Graveyard).get(0), "Concentrate"));
        AssertJUnit.assertTrue(pc.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertFalse("can't block creatures without flying", CombatUtil.canBlock(creature("Woolly Thoctar", opp), pc));
        AssertJUnit.assertTrue(CombatUtil.canBlock(creature("Ornithopter", opp), pc));
        islands(5);
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("flash", canCast(hand("Phantom Carriage", me), me));
    }

    @Test
    public void piecesOfThePuzzle() {
        newGame();
        islands(3);
        library(me, "Woolly Thoctar", "Ornithopter", "Island", "Concentrate", "Divination", "Jump");
        choices(me).prefer = c -> named(c, "Concentrate") || named(c, "Divination") || named(c, "Jump");
        final Card p = hand("Pieces of the Puzzle", me);
        cast(p);
        AssertJUnit.assertEquals("up to two sorcery cards", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(me.getCardsIn(ZoneType.Hand).stream().allMatch(Card::isSorcery));
        AssertJUnit.assertEquals("the rest and the spell", 5, count(me, ZoneType.Graveyard));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Library).get(0), "Woolly Thoctar"));
    }

    @Test
    public void precognitivePerception() {
        newGame();
        islands(5);
        cast(hand("Precognitive Perception", me));
        AssertJUnit.assertEquals("main phase: scry 3", 1, me.getScryThisTurn());
        AssertJUnit.assertEquals("then draw three", 3, count(me, ZoneType.Hand));

        newGame();
        islands(5);
        playUntil(opp, PhaseType.MAIN1);
        final int hand = count(me, ZoneType.Hand);
        final Card pp = hand("Precognitive Perception", me);
        AssertJUnit.assertTrue("flash", canCast(pp, me));
        cast(pp);
        AssertJUnit.assertEquals("not your main phase: no scry", 0, me.getScryThisTurn());
        AssertJUnit.assertEquals("draw three", hand + 3, count(me, ZoneType.Hand));
    }

    @Test
    public void prophetOfDistortion() {
        newGame();
        islands(3);
        lands("Wastes", 1, me);
        final Card p = creature("Prophet of Distortion", me);
        AssertJUnit.assertTrue("devoid", p.isColorless());
        library(me, "Concentrate", "Jump");
        activate(p, "Scan");
        AssertJUnit.assertEquals("scan: exiled one of the top two", 1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals("then drew", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("ward 1", wardCounters(p));
    }

    @Test
    public void prosperousThief() {
        newGame();
        final Card t = creature("Prosperous Thief", me);
        final Card rogue = creature("Roil Cartographer", me);
        final Card other = creature("Carrion Feeder", me);
        attack(me, null, t, rogue, other);
        AssertJUnit.assertEquals("a Treasure for each Rogue", 2, treasures(me));

        newGame();
        islands(2);
        ninjutsu("Prosperous Thief", creature("Carrion Feeder", me));
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals("ninjutsu 1U", 17, opp.getLife());
        AssertJUnit.assertEquals(1, treasures(me));
    }

    @Test
    public void psychicSurgery() {
        newGame();
        islands(16);
        final Card ps = enter("Psychic Surgery", me);
        final boolean[] chose = new boolean[1];
        choices(me).prefer = c -> {
            chose[0] = true;
            return true;
        };
        final int lib = count(opp, ZoneType.Library);
        activate(ps, "shuffles", opp);
        AssertJUnit.assertEquals("an opponent shuffled: one of the top two exiled", 1, count(opp, ZoneType.Exile));
        AssertJUnit.assertTrue("your choice", chose[0]);
        AssertJUnit.assertEquals(lib - 1, count(opp, ZoneType.Library));
        activate(ps, "shuffles", me);
        AssertJUnit.assertEquals("you shuffled: nothing", 0, count(me, ZoneType.Exile));
    }

    @Test
    public void quasiduplicate() {
        newGame();
        islands(3);
        final Card c = creature("Carrion Feeder", me);
        final Card q = hand("Quasiduplicate", me);
        final SpellAbility s = q.getFirstSpellAbility();
        s.setActivatingPlayer(me);
        AssertJUnit.assertFalse("a creature you control", s.canTarget(creature("Woolly Thoctar", opp)));
        cast(q, c);
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue("a copy", named(tokens(me).get(0), "Carrion Feeder"));
        islands(3);
        hand("Woolly Thoctar", me);
        cast(me, withOptionalCosts(current(q)), targeting(c));
        AssertJUnit.assertEquals("jump-start", 2, tokens(me).size());
        AssertJUnit.assertTrue(in(q, ZoneType.Exile));
        AssertJUnit.assertEquals("discarded a card", 1, count(me, ZoneType.Graveyard));
    }

    @Test
    public void qumulox() {
        newGame();
        islands(6);
        creature("Ornithopter", me);
        final SpellAbility one = hand("Qumulox", me).getFirstSpellAbility();
        one.setActivatingPlayer(me);
        AssertJUnit.assertFalse("one artifact: 7 mana, six lands can't pay", ComputerUtil.handlePlayingSpellAbility(me, one, null));

        newGame();
        islands(6);
        creature("Ornithopter", me);
        creature("Ornithopter", me);
        playUntil(opp, PhaseType.MAIN1);
        final Card q = hand("Qumulox", me);
        AssertJUnit.assertTrue("flash", canCast(q, me));
        cast(q);
        AssertJUnit.assertTrue("affinity for artifacts: 6UU for 4UU", in(q, ZoneType.Battlefield));
        AssertJUnit.assertTrue(current(q).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(504, pt(current(q)));
    }

    @Test
    public void radicalIdea() {
        newGame();
        islands(2);
        library(me, "Concentrate", "Jump");
        final Card ri = hand("Radical Idea", me);
        cast(ri);
        AssertJUnit.assertEquals("scan", 1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals("then draw", 1, count(me, ZoneType.Hand));
        islands(2);
        cast(me, withOptionalCosts(current(ri)), null);
        AssertJUnit.assertTrue("jump-start", in(ri, ZoneType.Exile));
        AssertJUnit.assertEquals("discarded, scanned, drew", 3, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void reason() {
        newGame();
        islands(4);
        final SpellAbility orn = oppCasts("Ornithopter");
        choices(me).modes.add("Counter target");
        choices(me).targets = List.of(orn);
        final Card r = hand("Reason", me);
        cast(r);
        AssertJUnit.assertTrue("flash creature: entered", in(r, ZoneType.Battlefield));
        AssertJUnit.assertTrue("countered a nonflash spell", countered(orn));
        lands("Mountain", 1, opp);
        final SpellAbility st = oppCasts("Searing Touch", me);
        final SpellAbility counter = AbilityFactory.getAbility(current(r).getSVar("DBCounter"), current(r));
        counter.setActivatingPlayer(me);
        AssertJUnit.assertFalse("a flash spell can't be targeted", counter.canTargetSpellAbility(st));
        resolve();

        newGame();
        islands(1);
        final Card c = creature("Carrion Feeder", me);
        hand("Concentrate", me);
        choices(me).modes.add("Put a +1/+1");
        choices(me).targets = List.of(c);
        final Card e = hand("Reason", me);
        cast(me, alternative(e, SpellAbility::isEvoke), null);
        AssertJUnit.assertTrue("evoke U: sacrificed", in(e, ZoneType.Graveyard));
        AssertJUnit.assertEquals("exiled a nonland card from the hand", 1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals(1, p1p1(current(c)));
        AssertJUnit.assertTrue("a flying counter", current(c).hasKeyword(Keyword.FLYING));
    }

    @Test
    public void retractionHelix() {
        newGame();
        islands(1);
        final Card c = creature("Carrion Feeder", me);
        final Card t = creature("Woolly Thoctar", opp);
        final Card land = lands("Mountain", 1, opp).get(0);
        cast(hand("Retraction Helix", me), c);
        final SpellAbility ab = ability(current(c), "Return target nonland");
        ab.setActivatingPlayer(me);
        AssertJUnit.assertFalse("nonland permanents only", ab.canTarget(land));
        activate(c, "Return target nonland", t);
        AssertJUnit.assertTrue(in(t, ZoneType.Hand));
        AssertJUnit.assertTrue(current(c).isTapped());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("this turn", current(c).getAllSpellAbilities().stream()
                .noneMatch(sa -> sa.getDescription().contains("Return target")));
    }

    @Test
    public void rewind() {
        newGame();
        final List<Card> isl = islands(4);
        final SpellAbility orn = oppCasts("Ornithopter");
        choices(me).prefer = c -> true;
        cast(hand("Rewind", me), orn);
        AssertJUnit.assertTrue(countered(orn));
        AssertJUnit.assertTrue("untapped four lands", isl.stream().noneMatch(c -> current(c).isTapped()));
    }

    @Test
    public void riseAndShine() {
        newGame();
        islands(8);
        final Card a = lands("Reapers Talisman", 1, me).get(0);
        final Card b = lands("Reapers Talisman", 1, me).get(0);
        cast(hand("Rise and Shine", me), a);
        AssertJUnit.assertTrue(current(a).isCreature() && current(a).isArtifact());
        AssertJUnit.assertEquals("0/0 with four +1/+1 counters", 404, pt(current(a)));
        AssertJUnit.assertFalse(current(b).isCreature());
        final Card o = hand("Rise and Shine", me);
        cast(me, alternative(o, sa -> sa.isAlternativeCost(AlternativeCost.Overload)), null);
        AssertJUnit.assertTrue("overload 4UU: each", current(b).isCreature());
        AssertJUnit.assertEquals(404, pt(current(b)));
        AssertJUnit.assertEquals("already a creature: no more counters", 4, p1p1(current(a)));
    }

    @Test
    public void riverHeraldScout() {
        newGame();
        library(me, "Island");
        final Card c = creature("Carrion Feeder", me);
        choices(me).targets = List.of(c);
        enter("River Herald Scout", me);
        AssertJUnit.assertTrue("explore: a land to the hand", named(handCard(me, 0), "Island"));
        AssertJUnit.assertEquals(0, p1p1(current(c)));
        library(me, "Concentrate");
        enter("River Herald Scout", me);
        AssertJUnit.assertEquals("a nonland: a +1/+1 counter on the explorer", 1, p1p1(current(c)));
    }

    @Test
    public void riverwiseAugur() {
        newGame();
        final int lib = count(me, ZoneType.Library);
        enter("Riverwise Augur", me);
        AssertJUnit.assertEquals("drew three, put two back", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, me.getNumDrawnThisTurn());
        AssertJUnit.assertEquals(lib - 1, count(me, ZoneType.Library));
    }

    @Test
    public void roilCartographer() {
        newGame();
        final Card rc = creature("Roil Cartographer", me);
        for (int i = 0; i < 6; i++) {
            enter("Island", me);
        }
        AssertJUnit.assertEquals("landfall: E each", 6, me.getCounters(CounterEnumType.ENERGY));
        activate(rc, "Draw three");
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertTrue(current(rc).isTapped());
    }

    private SpellAbility freeSpell(final Card c) {
        return current(c).getAllPossibleAbilities(me, true).stream()
                .filter(sa -> sa.isSpell() && sa.getPayCosts().getTotalMana().isZero()).findFirst().orElse(null);
    }

    @Test
    public void rooftopStorm() {
        newGame();
        enter("Rooftop Storm", me);
        final Card z = hand("Carrion Feeder", me);
        final Card w = hand("Woolly Thoctar", me);
        resolve();
        AssertJUnit.assertNull("not a Zombie", freeSpell(w));
        cast(me, freeSpell(z), null);
        AssertJUnit.assertTrue("a Zombie spell for 0", in(z, ZoneType.Battlefield));
    }

    @Test
    public void ruinationGuide() {
        newGame();
        final Card g = creature("Ruination Guide", me);
        final Card o = creature("Ornithopter", me);
        final Card f = creature("Carrion Feeder", me);
        AssertJUnit.assertTrue("devoid", g.isColorless());
        AssertJUnit.assertEquals("other colorless creatures +1/+0", 102, pt(current(o)));
        AssertJUnit.assertEquals(101, pt(current(f)));
        AssertJUnit.assertEquals(302, pt(current(g)));
        attack(me, null, g);
        AssertJUnit.assertEquals("ingest", 2, count(opp, ZoneType.Exile));
    }

    @Test
    public void sageOfHours() {
        newGame();
        islands(3);
        final Card s = creature("Sage of Hours", me);
        for (int i = 0; i < 3; i++) {
            cast(hand("Jump", me), s);
        }
        AssertJUnit.assertEquals("heroic", 3, p1p1(current(s)));
        final int turn = game.getPhaseHandler().getTurn();
        activate(s, "extra turn");
        AssertJUnit.assertEquals(0, p1p1(current(s)));
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("took the next turn", turn + 1, game.getPhaseHandler().getTurn());
    }

    @Test
    public void sailorOfMeans() {
        newGame();
        choices(me).modes.add("Treasure");
        final Card s = creature("Sailor of Means", me);
        AssertJUnit.assertEquals("enters: a Treasure", 1, treasures(me));
        choices(me).modes.add("Draw a card");
        hand("Woolly Thoctar", me);
        attack(me, null, s);
        AssertJUnit.assertEquals("combat damage: drew, then discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
    }

    @Test
    public void salvageDrone() {
        newGame();
        final Card d = creature("Salvage Drone", me);
        AssertJUnit.assertTrue("devoid", d.isColorless());
        attack(me, null, d);
        AssertJUnit.assertEquals("ingest", 2, count(opp, ZoneType.Exile));
        choices(me).payUnless = true;
        kill(d);
        AssertJUnit.assertEquals("dies: drew a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("put two exiled cards into their graveyard", 2, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals("so nothing exiled from the hand", 0, count(me, ZoneType.Exile));

        newGame();
        final Card d2 = creature("Salvage Drone", me);
        hand("Woolly Thoctar", me);
        kill(d2);
        AssertJUnit.assertEquals("drew, then exiled a card from the hand", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Exile));
    }

    @Test
    public void scholarOfTheAges() {
        newGame();
        final Card a = grave("Concentrate", me);
        final Card b = grave("Divination", me);
        final Card o = grave("Ornithopter", me);
        choices(me).targets = List.of(o, a, b);
        enter("Scholar of the Ages", me);
        AssertJUnit.assertTrue("two sorcery cards", in(a, ZoneType.Hand) && in(b, ZoneType.Hand));
        AssertJUnit.assertTrue(in(o, ZoneType.Graveyard));
    }

    @Test
    public void scrollThief() {
        newGame();
        final Card t = creature("Scroll Thief", me);
        attack(me, null, t);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals("dealt damage to a player: draw", 1, count(me, ZoneType.Hand));
        damage(current(t), opp, 1, false);
        AssertJUnit.assertEquals("any damage", 2, count(me, ZoneType.Hand));
    }

    @Test
    public void seaDasherOctopus() {
        newGame();
        islands(2);
        final Card host = creature("Carrion Feeder", me);
        playUntil(opp, PhaseType.MAIN1);
        final Card oct = hand("Sea-Dasher Octopus", me);
        cast(me, alternative(oct, SpellAbility::isMutate), targeting(host));
        AssertJUnit.assertEquals("mutate 1U, at flash speed: one creature", 1, me.getCreaturesInPlay().size());
        final Card merged = me.getCreaturesInPlay().get(0);
        AssertJUnit.assertTrue(merged.isMutated());
        playUntil(me, PhaseType.MAIN1);
        final int hand = count(me, ZoneType.Hand);
        attack(me, null, merged);
        AssertJUnit.assertEquals("combat damage to a player: draw", hand + 1, count(me, ZoneType.Hand));
    }

    @Test
    public void seagrafSkaab() {
        newGame();
        islands(1);
        final Card s = hand("Seagraf Skaab", me);
        activate(s, "Cycling");
        AssertJUnit.assertTrue(in(s, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 1", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(103, pt(creature("Seagraf Skaab", me)));
    }

    @Test
    public void shipwreckDowser() {
        newGame();
        final Card s = grave("Concentrate", me);
        final Card o = grave("Ornithopter", me);
        choices(me).targets = List.of(o, s);
        final Card d = creature("Shipwreck Dowser", me);
        AssertJUnit.assertTrue("a sorcery card", in(s, ZoneType.Hand));
        AssertJUnit.assertTrue("not an artifact creature", in(o, ZoneType.Graveyard));
        final Card t = grave("Reapers Talisman", me);
        choices(me).targets = List.of(t);
        enter("Shipwreck Dowser", me);
        AssertJUnit.assertTrue("a noncreature artifact card", in(t, ZoneType.Hand));
        islands(1);
        cast(hand("Jump", me), d);
        AssertJUnit.assertEquals("prowess", 303, pt(current(d)));
    }

    @Test
    public void shoalSerpent() {
        newGame();
        final Card s = creature("Shoal Serpent", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.DEFENDER));
        enter("Island", me);
        AssertJUnit.assertFalse("landfall: loses defender", current(s).hasKeyword(Keyword.DEFENDER));
        attack(me, null, s);
        AssertJUnit.assertEquals(14, opp.getLife());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertTrue("this turn", current(s).hasKeyword(Keyword.DEFENDER));
    }

    @Test
    public void shorelineLooter() {
        newGame();
        final Card l = creature("Shoreline Looter", me);
        AssertJUnit.assertFalse("can't be blocked", CombatUtil.canBlock(l, creature("Woolly Thoctar", opp)));
        hand("Woolly Thoctar", me);
        attack(me, null, l);
        AssertJUnit.assertEquals("drew, then discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));

        newGame();
        final Card l2 = creature("Shoreline Looter", me);
        for (int i = 0; i < 7; i++) {
            grave("Woolly Thoctar", me);
        }
        attack(me, null, l2);
        AssertJUnit.assertEquals("threshold: no discard", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(7, count(me, ZoneType.Graveyard));
    }

    @Test
    public void showAndTell() {
        newGame();
        islands(3);
        final Card w = hand("Woolly Thoctar", me);
        final Card c = hand("Concentrate", me);
        final Card o = hand("Ornithopter", opp);
        choices(me).prefer = x -> true;
        choices(opp).prefer = x -> true;
        cast(hand("Show and Tell", me));
        AssertJUnit.assertTrue("each player put a card into play", in(w, ZoneType.Battlefield) && in(o, ZoneType.Battlefield));
        AssertJUnit.assertTrue("not a sorcery", in(c, ZoneType.Hand));

        newGame();
        islands(3);
        final Card st = hand("Show and Tell", me);
        activate(st, "Cycling");
        AssertJUnit.assertTrue(in(st, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 3", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void shriekgeist() {
        newGame();
        library(opp, "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar",
                "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        final Card s = creature("Shriekgeist", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        final Card spirit = creature("Phantom Carriage", me);
        final Card other = creature("Carrion Feeder", me);
        attack(me, null, s, spirit, other);
        AssertJUnit.assertEquals(14, opp.getLife());
        AssertJUnit.assertEquals("Spirits dealt 5: they mill 15", 15, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void skaabRuinator() {
        newGame();
        islands(3);
        for (int i = 0; i < 3; i++) {
            grave("Carrion Feeder", me);
        }
        final Card r = hand("Skaab Ruinator", me);
        cast(r);
        AssertJUnit.assertTrue(in(r, ZoneType.Battlefield));
        AssertJUnit.assertEquals("exiled three creature cards", 3, count(me, ZoneType.Exile));
        AssertJUnit.assertTrue(current(r).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(506, pt(current(r)));
        kill(r);
        AssertJUnit.assertTrue(in(r, ZoneType.Graveyard));
        untapAll(me);
        for (int i = 0; i < 3; i++) {
            grave("Carrion Feeder", me);
        }
        cast(me, alternative(r, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue("cast from the graveyard", in(r, ZoneType.Battlefield));
        AssertJUnit.assertEquals(6, count(me, ZoneType.Exile));
    }

    @Test
    public void sleepOfTheDead() {
        newGame();
        islands(1);
        final Card t = creature("Woolly Thoctar", opp);
        final Card s = hand("Sleep of the Dead", me);
        cast(s, t);
        AssertJUnit.assertTrue(current(t).isTapped());
        AssertJUnit.assertEquals("a stun counter", 1, current(t).getCounters(CounterEnumType.STUN));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("didn't untap", current(t).isTapped());
        AssertJUnit.assertEquals(0, current(t).getCounters(CounterEnumType.STUN));

        playUntil(me, PhaseType.MAIN1);
        for (int i = 0; i < 3; i++) {
            grave("Woolly Thoctar", me);
        }
        final Card t2 = creature("Woolly Thoctar", opp);
        cast(me, alternative(s, SpellAbility::isEscape), targeting(t2));
        AssertJUnit.assertTrue("escape U", current(t2).isTapped());
        AssertJUnit.assertEquals("exiled three other cards", 3, count(me, ZoneType.Exile));
    }

    @Test
    public void slimebind() {
        newGame();
        islands(1);
        final Card t = creature("Woolly Thoctar", opp);
        playUntil(opp, PhaseType.MAIN1);
        final Card s = hand("Slimebind", me);
        AssertJUnit.assertTrue("flash", canCast(s, me));
        cast(s, t);
        AssertJUnit.assertEquals("-4/-0", 204, pt(current(t)));
    }

    @Test
    public void sowerOfTemptation() {
        newGame();
        final Card t = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(t);
        final Card s = enter("Sower of Temptation", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(me, current(t).getController());
        kill(s);
        AssertJUnit.assertEquals("while it remains in play", opp, current(t).getController());
    }
}
