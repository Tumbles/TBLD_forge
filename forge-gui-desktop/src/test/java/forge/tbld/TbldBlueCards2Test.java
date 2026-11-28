package forge.tbld;

import forge.game.GameEntityCounterTable;
import forge.game.GameObject;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
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

/** Blue TBLD cards, second batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlueCards2Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Faerie Miscreant", "Flux Channeler", "Followed Footsteps", "Force Spike", "Forgotten Creation", "Frazzle",
            "Freed from the Real", "Frogify", "Future Sight", "Geralfs Masterpiece", "Geralfs Mindcrusher", "Ghostly Wings",
            "Glyph Keeper", "Grafted Identity", "Hard Evidence", "High Fae Trickster", "Homunculus Horde", "Hope-Ender Coatl",
            "Horribly Awry", "Hunters Mark", "Illusion of Choice", "Illusionary Armor", "Inspiration from Beyond",
            "Isperias Skywatch", "Jump", "Lay Claim", "Lock and Load", "Logic Knot", "Lucid Dreams", "Lullmage Mentor",
            "Lunar Force", "Magus of the Bazaar", "Mana Leak", "Mana Severance", "Mass Manipulation", "Maximize Altitude",
            "Meeting of Minds", "Memory Erosion", "Memory Lapse", "Memorys Journey", "Merfolk Looter", "Merfolk Trickster",
            "Merrow Commerce", "Metallic Rebuke", "Micromancer", "Minister of Inquiries", "Miscalculation", "Mist Intruder",
            "Mnemonic Deluge", "Monastery Siege");

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

    private boolean unblockable(final Card attacker, final Card blocker) {
        return !CombatUtil.canBlock(current(attacker), current(blocker));
    }

    private static boolean isClue(final Card c) {
        return c.getType().hasSubtype("Clue") && c.isEnchantment();
    }

    private boolean countered(final SpellAbility spell) {
        return in(spell.getHostCard(), ZoneType.Graveyard);
    }

    private static int pt(final Card c) {
        return c.getNetPower() * 100 + c.getNetToughness();
    }

    @Test
    public void faerieMiscreant() {
        newGame();
        final Card f = enter("Faerie Miscreant", me);
        AssertJUnit.assertTrue(f.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("no other Rogue with flying", 0, count(me, ZoneType.Hand));
        enter("Faerie Miscreant", me);
        AssertJUnit.assertEquals("another Rogue with flying: draw", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void fluxChanneler() {
        newGame();
        islands(4);
        creature("Flux Channeler", me);
        final Card f = creature("Carrion Feeder", me);
        current(f).addCounterInternal(CounterEnumType.P1P1, 1, me, false, new GameEntityCounterTable(), null);
        choices(me).prefer = c -> c == current(f);
        cast(hand("Concentrate", me));
        AssertJUnit.assertEquals("a noncreature spell: proliferate", 2, p1p1(current(f)));
        cast(hand("Ornithopter", me));
        AssertJUnit.assertEquals("a creature spell: no proliferate", 2, p1p1(current(f)));
    }

    @Test
    public void followedFootsteps() {
        newGame();
        islands(4);
        final Card c = creature("Carrion Feeder", me);
        cast(hand("Followed Footsteps", me), c);
        AssertJUnit.assertTrue(tokens(me).isEmpty());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("a copy at your upkeep", 1, tokens(me).size());
        AssertJUnit.assertTrue(named(tokens(me).get(0), "Carrion Feeder"));
    }

    @Test
    public void forceSpike() {
        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Force Spike", me), orn);
        AssertJUnit.assertTrue("can't pay 1", countered(orn));
        AssertJUnit.assertEquals("countered: 1 damage to its controller", 19, opp.getLife());
        lands("Mountain", 1, opp);
        choices(opp).payUnless = true;
        final SpellAbility orn2 = oppCasts("Ornithopter");
        cast(hand("Force Spike", me), orn2);
        AssertJUnit.assertTrue("paid 1", in(orn2.getHostCard(), ZoneType.Battlefield));
        AssertJUnit.assertEquals("not countered: no damage", 19, opp.getLife());
    }

    @Test
    public void forgottenCreation() {
        newGame();
        final Card fc = creature("Forgotten Creation", me);
        AssertJUnit.assertTrue("skulk: greater power can't block", unblockable(fc, creature("Woolly Thoctar", opp)));
        AssertJUnit.assertFalse(unblockable(fc, creature("Ornithopter", opp)));
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        choices(me).confirm = true;
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("discarded the hand", 2, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals("drew that many, then the draw step", 3, count(me, ZoneType.Hand));
    }

    @Test
    public void frazzle() {
        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Frazzle", me), orn);
        AssertJUnit.assertTrue(countered(orn));
        lands("Island", 4, opp);
        final SpellAbility conc = oppCasts("Concentrate");
        AssertJUnit.assertFalse("nonblue only", canTargetSpell(hand("Frazzle", me), me, conc));
        resolve();
    }

    @Test
    public void freedFromTheReal() {
        newGame();
        islands(5);
        final Card c = creature("Carrion Feeder", me);
        final Card fr = hand("Freed from the Real", me);
        cast(fr, c);
        activate(fr, "Tap enchanted");
        AssertJUnit.assertTrue(current(c).isTapped());
        activate(fr, "Untap enchanted");
        AssertJUnit.assertFalse(current(c).isTapped());
    }

    @Test
    public void frogify() {
        newGame();
        islands(2);
        final Card f = creature("Carrion Feeder", opp);
        final Card o = creature("Ornithopter", opp);
        cast(hand("Frogify", me), f);
        cast(hand("Frogify", me), o);
        for (final Card c : List.of(current(f), current(o))) {
            AssertJUnit.assertEquals("base 1/1", 101, pt(c));
            AssertJUnit.assertTrue("a blue Frog creature", c.isCreature() && c.getType().hasCreatureType("Frog") && c.isBlue());
        }
        AssertJUnit.assertFalse("loses other creature types", current(f).getType().hasCreatureType("Zombie"));
        AssertJUnit.assertFalse("loses other card types", current(o).isArtifact());
        AssertJUnit.assertFalse("loses all abilities", current(o).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertTrue("no longer \"can't block\"", canBlock(current(f)));
        AssertJUnit.assertTrue(current(f).getAllSpellAbilities().stream().noneMatch(sa -> sa.getDescription().contains("+1/+1")));
    }

    @Test
    public void futureSight() {
        newGame();
        islands(5);
        enter("Future Sight", me);
        library(me, "Concentrate", "Island");
        final Card top = me.getCardsIn(ZoneType.Library).get(0);
        resolve();
        AssertJUnit.assertTrue("the top card is revealed", current(top).mayPlayerLook(opp));
        final SpellAbility land = current(top).getAllPossibleAbilities(me, true).stream()
                .filter(SpellAbility::isLandAbility).findFirst().orElseThrow(() -> new AssertionError("can't play the land"));
        me.playLand(current(top), land);
        AssertJUnit.assertTrue("played a land from the top", in(top, ZoneType.Battlefield));
        final Card next = me.getCardsIn(ZoneType.Library).get(0);
        AssertJUnit.assertTrue(named(next, "Concentrate"));
        resolve();
        cast(me, alternative(next, sa -> sa.isSpell() && sa.getMayPlay() != null), null);
        AssertJUnit.assertTrue("cast a spell from the top", in(next, ZoneType.Graveyard));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
    }

    @Test
    public void geralfsMasterpiece() {
        newGame();
        final Card gm = creature("Geralfs Masterpiece", me);
        AssertJUnit.assertTrue(gm.hasKeyword(Keyword.FLYING));
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        resolve();
        AssertJUnit.assertEquals("-3/-3 for each of two cards", 707, pt(current(gm)));

        newGame();
        islands(4);
        final Card g = grave("Geralfs Masterpiece", me);
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        activate(g, "Return");
        AssertJUnit.assertTrue("returned from the graveyard", in(g, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(g).isTapped());
        AssertJUnit.assertEquals("discarded three", 3, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals("an empty hand: 13/13", 1313, pt(current(g)));
    }

    @Test
    public void geralfsMindcrusher() {
        newGame();
        library(opp, "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar",
                "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        choices(me).targets = List.of(opp);
        final Card m = enter("Geralfs Mindcrusher", me);
        AssertJUnit.assertEquals("mills eight", 8, count(opp, ZoneType.Graveyard));
        kill(m);
        AssertJUnit.assertTrue("undying", in(m, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, p1p1(current(m)));
        AssertJUnit.assertEquals("entered again: eight more", 16, count(opp, ZoneType.Graveyard));
        kill(m);
        AssertJUnit.assertTrue("had a +1/+1 counter: stays dead", in(m, ZoneType.Graveyard));
    }

    @Test
    public void ghostlyWings() {
        newGame();
        islands(2);
        final Card c = creature("Carrion Feeder", me);
        final Card gw = hand("Ghostly Wings", me);
        cast(gw, c);
        AssertJUnit.assertEquals("+2/+1", 302, pt(current(c)));
        AssertJUnit.assertTrue(current(c).hasKeyword(Keyword.FLYING));
        hand("Woolly Thoctar", me);
        activate(gw, "Return enchanted");
        AssertJUnit.assertTrue("returned to its owner's hand", in(c, ZoneType.Hand));
        AssertJUnit.assertEquals("discarded a card; the aura went too", 2, count(me, ZoneType.Graveyard));
    }

    @Test
    public void glyphKeeper() {
        newGame();
        final Card g = creature("Glyph Keeper", me);
        AssertJUnit.assertTrue(g.hasKeyword(Keyword.FLYING));
        lands("Mountain", 2, opp);
        final SpellAbility first = oppCasts("Searing Touch", g);
        resolve();
        AssertJUnit.assertTrue("the first time this turn: countered", countered(first));
        AssertJUnit.assertEquals(0, current(g).getDamage());
        oppCasts("Searing Touch", current(g));
        resolve();
        AssertJUnit.assertEquals("the second time: not countered", 2, current(g).getDamage());

        // Embalm 5UU
        newGame();
        islands(7);
        final Card e = grave("Glyph Keeper", me);
        activate(e, "Embalm");
        AssertJUnit.assertTrue(in(e, ZoneType.Exile));
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card t = tokens(me).get(0);
        AssertJUnit.assertTrue("a copy", named(t, "Glyph Keeper") && t.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertTrue("a white Zombie Sphinx", t.isWhite() && !t.isBlue()
                && t.getType().hasCreatureType("Zombie") && t.getType().hasCreatureType("Sphinx"));
    }

    @Test
    public void graftedIdentity() {
        newGame();
        islands(3);
        final Card mine = creature("Carrion Feeder", me);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Grafted Identity", me), t);
        AssertJUnit.assertTrue("sacrificed a creature", in(mine, ZoneType.Graveyard));
        AssertJUnit.assertEquals(me, current(t).getController());
    }

    @Test
    public void hardEvidence() {
        newGame();
        islands(1);
        cast(hand("Hard Evidence", me));
        AssertJUnit.assertEquals(2, tokens(me).size());
        final Card crab = tokens(me).stream().filter(c -> c.getType().hasCreatureType("Crab")).findFirst().orElseThrow();
        AssertJUnit.assertTrue("a 0/3 blue Crab", crab.isCreature() && crab.isBlue() && pt(crab) == 3);
        AssertJUnit.assertTrue("investigate", tokens(me).stream().anyMatch(TbldBlueCards2Test::isClue));
    }

    @Test
    public void highFaeTrickster() {
        newGame();
        islands(8);
        playUntil(opp, PhaseType.MAIN1);
        final Card h = hand("High Fae Trickster", me);
        AssertJUnit.assertTrue("flash", canCast(h, me));
        cast(h);
        AssertJUnit.assertTrue(current(h).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(402, pt(current(h)));
        final Card conc = hand("Concentrate", me);
        resolve();
        AssertJUnit.assertTrue("a sorcery at flash speed", canCast(conc, me));
        cast(conc);
        AssertJUnit.assertTrue(in(conc, ZoneType.Graveyard));
    }

    @Test
    public void homunculusHorde() {
        newGame();
        islands(3);
        creature("Homunculus Horde", me);
        cast(hand("Divination", me), me);
        AssertJUnit.assertEquals("the second card drawn: a copy", 1, tokens(me).size());
        AssertJUnit.assertTrue(named(tokens(me).get(0), "Homunculus Horde"));
        me.drawCard();
        resolve();
        AssertJUnit.assertEquals("only the second card", 1, tokens(me).size());
    }

    @Test
    public void hopeEnderCoatl() {
        newGame();
        islands(3);
        final SpellAbility orn = oppCasts("Ornithopter");
        final Card c = hand("Hope-Ender Coatl", me);
        cast(c);
        AssertJUnit.assertTrue("when cast: countered, they can't pay 1", countered(orn));
        AssertJUnit.assertTrue(in(c, ZoneType.Battlefield));
        AssertJUnit.assertTrue("devoid", current(c).isColorless());
        AssertJUnit.assertTrue(current(c).hasKeyword(Keyword.FLYING));

        newGame();
        islands(3);
        lands("Mountain", 1, opp);
        choices(opp).payUnless = true;
        final SpellAbility orn2 = oppCasts("Ornithopter");
        cast(hand("Hope-Ender Coatl", me));
        AssertJUnit.assertTrue("they paid 1", in(orn2.getHostCard(), ZoneType.Battlefield));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("flash", canCast(hand("Hope-Ender Coatl", me), me));
    }

    @Test
    public void horriblyAwry() {
        newGame();
        islands(2);
        lands("Forest", 1, opp);
        lands("Mountain", 1, opp);
        lands("Plains", 1, opp);
        final SpellAbility t = oppCasts("Woolly Thoctar");
        final Card ha = hand("Horribly Awry", me);
        cast(ha, t);
        AssertJUnit.assertTrue("devoid", current(ha).isColorless());
        AssertJUnit.assertTrue("any creature spell: mana value 3, exiled", in(t.getHostCard(), ZoneType.Exile));
        lands("Mountain", 1, opp);
        final SpellAbility st = oppCasts("Searing Touch", me);
        AssertJUnit.assertFalse("creature spells only", canTargetSpell(hand("Horribly Awry", me), me, st));
        resolve();
    }

    @Test
    public void huntersMark() {
        newGame();
        final List<Card> isl = islands(3);
        final Card hm = hand("Hunters Mark", me);
        cast(hm, opp);
        AssertJUnit.assertEquals("enchants the opponent", opp, current(hm).getEntityAttachedTo());
        final int hand = count(opp, ZoneType.Hand);
        cast(hand("Divination", me), opp);
        AssertJUnit.assertEquals("2U for U: it targets the enchanted player", hand + 2, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue(isl.stream().allMatch(c -> current(c).isTapped()));
        untapAll(me);
        cast(hand("Divination", me), me);
        AssertJUnit.assertTrue("targeting you: full cost", isl.stream().allMatch(c -> current(c).isTapped()));
    }

    /** A stand-in vote ("each player votes for a player"): no TBLD card that votes is scripted yet. */
    private void vote(final Card host) {
        final SpellAbility v = AbilityFactory.getAbility("DB$ Vote | Defined$ Player | VotePlayer$ Player", current(host));
        v.setActivatingPlayer(me);
        AbilityUtils.resolve(v);
    }

    @Test
    public void illusionOfChoice() {
        newGame();
        islands(1);
        final Card ic = hand("Illusion of Choice", me);
        cast(ic);
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
        vote(ic);
        AssertJUnit.assertEquals("you choose every player's vote", 2, choices(me).votedFor.size());
        AssertJUnit.assertTrue(choices(me).votedFor.contains(opp));
        AssertJUnit.assertTrue(choices(opp).votedFor.isEmpty());
        playUntil(opp, PhaseType.MAIN1);
        choices(me).votedFor.clear();
        vote(ic);
        AssertJUnit.assertEquals("this turn only", List.of(me), choices(me).votedFor);
        AssertJUnit.assertEquals(List.of(opp), choices(opp).votedFor);
    }

    @Test
    public void illusionaryArmor() {
        newGame();
        islands(3);
        final Card c = creature("Carrion Feeder", me);
        final Card ia = hand("Illusionary Armor", me);
        cast(ia, c);
        AssertJUnit.assertEquals("+4/+4", 505, pt(current(c)));
        lands("Mountain", 1, opp);
        oppCasts("Searing Touch", current(c));
        resolve();
        AssertJUnit.assertTrue("targeted: sacrificed", in(ia, ZoneType.Graveyard));
        AssertJUnit.assertTrue("so 2 damage killed the 1/1", in(c, ZoneType.Graveyard));
    }

    @Test
    public void inspirationFromBeyond() {
        newGame();
        islands(10);
        library(me, "Woolly Thoctar", "Concentrate", "Ornithopter");
        choices(me).prefer = c -> named(c, "Concentrate");
        final Card ib = hand("Inspiration from Beyond", me);
        cast(ib);
        AssertJUnit.assertEquals("returned the sorcery", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Concentrate"));
        AssertJUnit.assertEquals("milled three, kept two, and the spell", 3, count(me, ZoneType.Graveyard));
        cast(me, alternative(ib, SpellAbility::isFlashback), null);
        AssertJUnit.assertTrue("flashback 5UU", in(ib, ZoneType.Exile));
        AssertJUnit.assertEquals("milled three more", 5, count(me, ZoneType.Graveyard));
    }

    @Test
    public void isperiasSkywatch() {
        newGame();
        final Card t = creature("Woolly Thoctar", opp);
        final Card s = enter("Isperias Skywatch", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertTrue("detained", current(t).isDetained());
        AssertJUnit.assertFalse(canBlock(current(t)));
        lands("Mountain", 1, opp);
        choices(opp).payUnless = false;
        final SpellAbility st = oppCasts("Searing Touch", current(s));
        resolve();
        AssertJUnit.assertTrue("ward 2", countered(st));
        AssertJUnit.assertEquals(0, current(s).getDamage());
        islands(2);
        lands("Island", 4, opp);
        final int hand = count(opp, ZoneType.Hand);
        final SpellAbility conc = oppCasts("Concentrate");
        activate(s, "Counter target", conc);
        AssertJUnit.assertTrue("countered: they can't pay 2", countered(conc));
        AssertJUnit.assertEquals("they drew nothing", hand, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue("sacrificed", in(s, ZoneType.Graveyard));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("detained until your next turn: still", current(t).isDetained());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertFalse(current(t).isDetained());
    }

    @Test
    public void jump() {
        newGame();
        islands(1);
        final Card c = creature("Carrion Feeder", me);
        cast(hand("Jump", me), c);
        AssertJUnit.assertTrue(current(c).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("this turn", current(c).hasKeyword(Keyword.FLYING));
    }

    @Test
    public void layClaim() {
        newGame();
        islands(7);
        final Card m = lands("Mountain", 1, opp).get(0);
        cast(hand("Lay Claim", me), m);
        AssertJUnit.assertEquals("any permanent", me, current(m).getController());

        newGame();
        islands(2);
        final Card lc = hand("Lay Claim", me);
        activate(lc, "Cycling");
        AssertJUnit.assertTrue(in(lc, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 2", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void lockAndLoad() {
        newGame();
        islands(7);
        cast(hand("Concentrate", me));
        cast(hand("Lock and Load", me));
        AssertJUnit.assertEquals("three, then one and one for the other sorcery", 5, count(me, ZoneType.Hand));

        newGame();
        islands(4);
        final Card ll = hand("Lock and Load", me);
        cast(me, alternative(ll, SpellAbility::isPlotting), null);
        AssertJUnit.assertTrue("plotted for 3U", in(ll, ZoneType.Exile) && current(ll).isPlotted());
        playUntil(me, PhaseType.MAIN1);
        me.getCardsIn(ZoneType.Battlefield).forEach(c -> c.setTapped(true));
        final int hand = count(me, ZoneType.Hand);
        cast(me, alternative(me, ll, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue("cast without paying its mana cost", in(ll, ZoneType.Graveyard));
        AssertJUnit.assertEquals(hand + 1, count(me, ZoneType.Hand));
    }

    @Test
    public void logicKnot() {
        newGame();
        islands(2);
        grave("Woolly Thoctar", me);
        grave("Woolly Thoctar", me);
        lands("Mountain", 1, opp);
        final SpellAbility orn = oppCasts("Ornithopter");
        choices(opp).payUnless = true;
        final Card lk = hand("Logic Knot", me);
        lk.getFirstSpellAbility().setXManaCostPaid(2);
        final SpellAbility spell = put(me, lk.getFirstSpellAbility(), targeting(orn));
        AssertJUnit.assertEquals("delve: XUU with X = 2 paid by two Islands and two cards", 2, count(me, ZoneType.Exile));
        lands("Island", 2, opp);
        final SpellAbility leak = oppCasts("Mana Leak", spell);
        resolve();
        AssertJUnit.assertTrue("Mana Leak resolved", in(leak.getHostCard(), ZoneType.Graveyard));
        AssertJUnit.assertTrue("can't be countered", in(lk, ZoneType.Graveyard));
        AssertJUnit.assertTrue("they couldn't pay 2", countered(orn));
    }

    @Test
    public void lucidDreams() {
        newGame();
        islands(5);
        grave("Ornithopter", me);
        grave("Illness in the Ranks", me);
        grave("Concentrate", me);
        grave("Woolly Thoctar", me);
        cast(hand("Lucid Dreams", me));
        AssertJUnit.assertEquals("artifact, creature, enchantment, sorcery", 4, count(me, ZoneType.Hand));
    }

    @Test
    public void lullmageMentor() {
        newGame();
        islands(2);
        final Card lm = creature("Lullmage Mentor", me);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Mana Leak", me), orn);
        AssertJUnit.assertTrue(countered(orn));
        AssertJUnit.assertEquals("a spell you control countered a spell", 1, tokens(me).size());
        final Card m = tokens(me).get(0);
        AssertJUnit.assertTrue("1/1 blue Merfolk with hexproof", m.getType().hasCreatureType("Merfolk") && m.isBlue()
                && pt(m) == 101 && m.hasKeyword(Keyword.HEXPROOF));
        for (int i = 0; i < 5; i++) {
            creature("Merfolk Looter", me);
        }
        final SpellAbility orn2 = oppCasts("Ornithopter");
        activate(lm, "Counter target spell", orn2);
        AssertJUnit.assertTrue("tapped seven Merfolk: countered", countered(orn2));
        AssertJUnit.assertEquals(7, me.getCreaturesInPlay().stream().filter(Card::isTapped).count());
        AssertJUnit.assertEquals("an ability you control countered a spell", 2, tokens(me).size());
    }

    @Test
    public void lunarForce() {
        newGame();
        final Card lf = enter("Lunar Force", me);
        AssertJUnit.assertEquals("mana value 4", 4, lf.getCMC());
        cast(hand("Ornithopter", me));
        AssertJUnit.assertTrue("your own spell: nothing", in(lf, ZoneType.Battlefield));
        final SpellAbility orn = oppCasts("Ornithopter");
        resolve();
        AssertJUnit.assertTrue("an opponent cast a spell: countered", countered(orn));
        AssertJUnit.assertTrue("sacrificed", in(lf, ZoneType.Graveyard));
    }

    @Test
    public void magusOfTheBazaar() {
        newGame();
        final Card m = creature("Magus of the Bazaar", me);
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        activate(m, "Draw two");
        AssertJUnit.assertTrue(current(m).isTapped());
        AssertJUnit.assertEquals("two, drew two, discarded three", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void manaLeak() {
        newGame();
        islands(4);
        lands("Mountain", 2, opp);
        choices(opp).payUnless = true;
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Mana Leak", me), orn);
        AssertJUnit.assertTrue("can't pay 3", countered(orn));
        lands("Mountain", 1, opp);
        final SpellAbility orn2 = oppCasts("Ornithopter");
        cast(hand("Mana Leak", me), orn2);
        AssertJUnit.assertTrue("paid 3", in(orn2.getHostCard(), ZoneType.Battlefield));
    }

    @Test
    public void manaSeverance() {
        newGame();
        islands(1);
        library(me, "Island", "Swamp", "Mountain");
        choices(me).prefer = c -> c instanceof Card && ((Card) c).isLand();
        cast(hand("Mana Severance", me));
        AssertJUnit.assertEquals("every land exiled", 3, count(me, ZoneType.Exile));
        AssertJUnit.assertTrue(me.getCardsIn(ZoneType.Library).stream().noneMatch(Card::isLand));
        AssertJUnit.assertEquals(10, count(me, ZoneType.Library));
    }

    @Test
    public void massManipulation() {
        newGame();
        islands(8);
        final Card a = creature("Woolly Thoctar", opp);
        final Card b = creature("Woolly Thoctar", opp);
        final Card c = creature("Ornithopter", opp);
        final Card mm = hand("Mass Manipulation", me);
        mm.getFirstSpellAbility().setXManaCostPaid(2);
        cast(mm, a, b);
        AssertJUnit.assertEquals("X = 2", me, current(a).getController());
        AssertJUnit.assertEquals(me, current(b).getController());
        AssertJUnit.assertEquals(opp, current(c).getController());
    }

    @Test
    public void maximizeAltitude() {
        newGame();
        islands(2);
        final Card c = creature("Carrion Feeder", me);
        playUntil(opp, PhaseType.MAIN1);
        final Card ma = hand("Maximize Altitude", me);
        AssertJUnit.assertTrue("flash", canCast(ma, me));
        cast(ma, c);
        AssertJUnit.assertEquals("+1/+2", 203, pt(current(c)));
        AssertJUnit.assertTrue(current(c).hasKeyword(Keyword.FLYING));
        hand("Woolly Thoctar", me);
        cast(me, withOptionalCosts(current(ma)), targeting(c));
        AssertJUnit.assertEquals("jump-start", 305, pt(current(c)));
        AssertJUnit.assertTrue(in(ma, ZoneType.Exile));
        AssertJUnit.assertEquals("discarded a card", 1, count(me, ZoneType.Graveyard));
    }

    @Test
    public void meetingOfMinds() {
        newGame();
        islands(2);
        final List<Card> cs = List.of(creature("Carrion Feeder", me), creature("Carrion Feeder", me), creature("Carrion Feeder", me));
        playUntil(me, PhaseType.MAIN2); // the AI convokes only after combat
        cast(hand("Meeting of Minds", me));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("convoke tapped the creatures", cs.stream().allMatch(c -> current(c).isTapped()));
    }

    @Test
    public void memoryErosion() {
        newGame();
        enter("Memory Erosion", me);
        cast(hand("Ornithopter", me));
        AssertJUnit.assertEquals("your own spell: nothing", 0, count(me, ZoneType.Graveyard));
        oppCasts("Ornithopter");
        resolve();
        AssertJUnit.assertEquals("an opponent cast a spell: they mill three", 3, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void memoryLapse() {
        newGame();
        islands(2);
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Memory Lapse", me), orn);
        AssertJUnit.assertTrue("on top of its owner's library", named(opp.getCardsIn(ZoneType.Library).get(0), "Ornithopter"));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void memorysJourney() {
        newGame();
        islands(2);
        final Card[] g = new Card[5];
        for (int i = 0; i < 5; i++) {
            g[i] = grave(i % 2 == 0 ? "Ornithopter" : "Carrion Feeder", opp);
        }
        final int lib = count(opp, ZoneType.Library);
        final Card mj = hand("Memorys Journey", me);
        cast(me, mj.getFirstSpellAbility(), targeting(opp, g[0], g[1], g[2], g[3], g[4]));
        AssertJUnit.assertEquals("up to four", lib + 4, count(opp, ZoneType.Library));
        AssertJUnit.assertTrue(in(g[4], ZoneType.Graveyard));
        lands("Forest", 1, me);
        cast(me, alternative(mj, SpellAbility::isFlashback), targeting(opp, current(g[4])));
        AssertJUnit.assertTrue("flashback G", in(mj, ZoneType.Exile));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void merfolkLooter() {
        newGame();
        final Card l = creature("Merfolk Looter", me);
        AssertJUnit.assertTrue("untapped: hexproof", current(l).hasKeyword(Keyword.HEXPROOF));
        hand("Woolly Thoctar", me);
        activate(l, "Draw a card");
        AssertJUnit.assertTrue(current(l).isTapped());
        AssertJUnit.assertFalse("tapped: no hexproof", current(l).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertEquals("drew, then discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
    }

    @Test
    public void merfolkTrickster() {
        newGame();
        islands(2);
        playUntil(opp, PhaseType.MAIN1);
        final Card o = creature("Ornithopter", opp);
        final Card mt = hand("Merfolk Trickster", me);
        AssertJUnit.assertTrue("flash", canCast(mt, me));
        choices(me).targets = List.of(o);
        cast(mt);
        AssertJUnit.assertTrue(current(o).isTapped());
        AssertJUnit.assertFalse("loses all abilities", current(o).hasKeyword(Keyword.FLYING));
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertTrue("this turn", current(o).hasKeyword(Keyword.FLYING));
    }

    @Test
    public void merrowCommerce() {
        newGame();
        enter("Merrow Commerce", me);
        final Card l = creature("Merfolk Looter", me);
        final Card f = creature("Carrion Feeder", me);
        l.setTapped(true);
        f.setTapped(true);
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertFalse("your end step: Merfolk untap", current(l).isTapped());
        AssertJUnit.assertTrue("not a Merfolk", current(f).isTapped());
    }

    @Test
    public void metallicRebuke() {
        newGame();
        final List<Card> isl = islands(1);
        final List<Card> arts = lands("Reapers Talisman", 2, me);
        final SpellAbility orn = oppCasts("Ornithopter");
        lands("Mountain", 2, opp);
        choices(opp).payUnless = true;
        cast(hand("Metallic Rebuke", me), orn);
        AssertJUnit.assertTrue("they can't pay 3", countered(orn));
        AssertJUnit.assertTrue("improvise tapped the artifacts", arts.stream().allMatch(c -> current(c).isTapped()));
        AssertJUnit.assertTrue(current(isl.get(0)).isTapped());
    }

    @Test
    public void micromancer() {
        newGame();
        library(me, "Jump", "Concentrate", "Lunar Force");
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Jump");
        enter("Micromancer", me);
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("a sorcery with mana value 1", named(me.getCardsIn(ZoneType.Hand).get(0), "Jump"));
    }

    @Test
    public void ministerOfInquiries() {
        newGame();
        final Card m = creature("Minister of Inquiries", me);
        AssertJUnit.assertEquals("EE", 2, me.getCounters(CounterEnumType.ENERGY));
        activate(m, "mills three", opp);
        AssertJUnit.assertEquals(3, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals("paid E", 1, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertTrue(current(m).isTapped());
    }

    @Test
    public void miscalculation() {
        newGame();
        islands(2);
        lands("Mountain", 1, opp);
        choices(opp).payUnless = true;
        final SpellAbility orn = oppCasts("Ornithopter");
        cast(hand("Miscalculation", me), orn);
        AssertJUnit.assertTrue("they can't pay 2: exiled", in(orn.getHostCard(), ZoneType.Exile));

        newGame();
        islands(2);
        final Card mc = hand("Miscalculation", me);
        activate(mc, "Cycling");
        AssertJUnit.assertTrue(in(mc, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling 2", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void mistIntruder() {
        newGame();
        final Card mi = creature("Mist Intruder", me);
        AssertJUnit.assertTrue("devoid", mi.isColorless());
        AssertJUnit.assertTrue(mi.hasKeyword(Keyword.FLYING));
        attack(me, null, mi);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals("ingest: the top two exiled", 2, count(opp, ZoneType.Exile));
        lands("Mountain", 1, opp);
        choices(opp).payUnless = false;
        final SpellAbility st = oppCasts("Searing Touch", current(mi));
        resolve();
        AssertJUnit.assertTrue("ward 2", countered(st));
        AssertJUnit.assertTrue(in(mi, ZoneType.Battlefield));
    }

    @Test
    public void mnemonicDeluge() {
        newGame();
        islands(9);
        final Card conc = grave("Concentrate", opp);
        choices(me).confirm = true;
        final Card md = hand("Mnemonic Deluge", me);
        cast(md, conc);
        AssertJUnit.assertTrue("exiled the sorcery", in(conc, ZoneType.Exile));
        AssertJUnit.assertTrue("exiled itself", in(md, ZoneType.Exile));
        AssertJUnit.assertEquals("cast three copies for free", 9, count(me, ZoneType.Hand));
    }

    @Test
    public void monasterySiege() {
        newGame();
        choices(me).modes.add("Khans");
        enter("Monastery Siege", me);
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("Khans: drew an additional card, then discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));

        newGame();
        choices(me).modes.add("Dragons");
        enter("Monastery Siege", me);
        final Card c = creature("Carrion Feeder", me);
        lands("Mountain", 2, opp);
        choices(opp).payUnless = false;
        final SpellAbility atCreature = oppCasts("Searing Touch", current(c));
        resolve();
        AssertJUnit.assertTrue("Dragons: your permanents have ward 3", countered(atCreature));
        AssertJUnit.assertTrue(in(c, ZoneType.Battlefield));
        final SpellAbility atYou = oppCasts("Searing Touch", me);
        resolve();
        AssertJUnit.assertTrue("and so do you", countered(atYou));
        AssertJUnit.assertEquals(20, me.getLife());
    }
}
