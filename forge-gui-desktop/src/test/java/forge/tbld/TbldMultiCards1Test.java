package forge.tbld;

import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Predicate;

/** Multicolour TBLD cards, first batch: each card's abilities played in an engine game and checked against its text. */
public class TbldMultiCards1Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Abzan Ascendancy", "Agony Warp", "Anax and Cymede", "Ascended Lawmage", "Auger Spree", "Azorius Skyguard",
            "Azors Elocutors", "Basilica Bell-Haunt", "Battlewise Hoplite", "Beamsplitter Mage", "Beetleback Chief", "Biovisionary",
            "Blightning", "Blood Spatter Analysis", "Bloodtithe Harvester", "Boltwing Marauder", "Borborygmos Enraged",
            "Boros Challenger", "Butcher of the Horde", "Campus Renovation", "Casualties of War", "Chromanticore",
            "Collective Blessing", "Conduit Goblin", "Corpsejack Menace", "Cruel Celebrant", "Cryptborn Horror",
            "Cyclops Superconductor", "Despark", "Djinn Illuminatus", "Doom Foretold", "Double Cleave", "Dramatic Rescue",
            "Dreg Mangler", "Drown in Filth", "Ecstatic Electromancer", "Eladamris Call", "Elas il-Kor Sadistic Pilgrim",
            "Electrosiphon", "Elite Guardmage", "Epic Experiment", "Faithful Mending", "Fathom Feeder", "Firemane Avenger",
            "Fireminds Research", "Flayer Drone", "Forerunner of Slaughter", "Fusion Elemental", "Gelectrode", "Ghor-Clan Rampager",
            "Gift of Orzhova");

    private static boolean named(final Object c, final String name) {
        return c instanceof Card && ((Card) c).getName().equals(name);
    }

    private static Predicate<GameEntity> is(final Card... cards) {
        return e -> e instanceof Card && java.util.Arrays.stream(cards).anyMatch(c -> c.getId() == ((Card) e).getId());
    }

    private boolean canActivate(final Card c, final String text) {
        final SpellAbility sa = ability(current(c), text);
        sa.setActivatingPlayer(c.getController());
        return sa.canPlay();
    }

    private boolean canTarget(final Card spell, final Player p, final Card target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTarget(current(target));
    }

    private Card find(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name + " in " + z));
    }

    private static boolean hasAll(final Card c, final Keyword... kws) {
        return java.util.Arrays.stream(kws).allMatch(c::hasKeyword);
    }

    /** The opponent casts Blightning at me, leaving it on the stack. */
    private Card oppBlightning() {
        lands("Swamp", 1, opp);
        lands("Mountain", 2, opp);
        final Card b = hand("Blightning", opp);
        put(opp, b.getFirstSpellAbility(), targeting(me));
        return b;
    }

    @Test
    public void abzanAscendancy() {
        newGame();
        final Card a = creature("Woolly Thoctar", me);
        final Card b = creature("Carrion Feeder", me);
        final Card o = creature("Carrion Feeder", opp);
        enter("Abzan Ascendancy", me);
        AssertJUnit.assertEquals(1, p1p1(current(a)));
        AssertJUnit.assertEquals(1, p1p1(current(b)));
        AssertJUnit.assertEquals("only your creatures", 0, p1p1(current(o)));
        kill(b);
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card spirit = tokens(me).get(0);
        AssertJUnit.assertTrue(spirit.getType().hasCreatureType("Spirit") && spirit.isWhite() && spirit.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(1, spirit.getNetPower());
        kill(spirit);
        AssertJUnit.assertTrue("not for tokens", tokens(me).isEmpty());
        kill(o);
        AssertJUnit.assertTrue("not for the opponent's", tokens(me).isEmpty() && tokens(opp).isEmpty());
    }

    @Test
    public void agonyWarp() {
        newGame();
        lands("Island", 1, me);
        lands("Swamp", 1, me);
        final Card a = creature("Woolly Thoctar", opp);
        final Card b = creature("Woolly Thoctar", opp);
        cast(hand("Agony Warp", me), a, b);
        AssertJUnit.assertEquals(4, current(a).getNetPower());
        AssertJUnit.assertEquals(4, current(a).getNetToughness());
        AssertJUnit.assertEquals(6, current(b).getNetPower());
        AssertJUnit.assertEquals(2, current(b).getNetToughness());
        AssertJUnit.assertEquals("investigate", 1, tokens(me).size());
        final Card clue = tokens(me).get(0);
        AssertJUnit.assertTrue("a Clue enchantment", clue.isEnchantment() && clue.getType().hasSubtype("Clue"));
    }

    @Test
    public void anaxAndCymede() {
        newGame();
        lands("Forest", 1, me);
        final Card ac = creature("Anax and Cymede", me);
        final Card other = creature("Carrion Feeder", me);
        AssertJUnit.assertTrue(hasAll(ac, Keyword.FIRST_STRIKE, Keyword.VIGILANCE));
        cast(hand("Giant Growth", me), ac);
        AssertJUnit.assertEquals("3 + 3 + 2", 8, current(ac).getNetPower());
        AssertJUnit.assertEquals("2 + 3 + 1", 6, current(ac).getNetToughness());
        AssertJUnit.assertEquals(3, current(other).getNetPower());
        AssertJUnit.assertEquals(2, current(other).getNetToughness());
    }

    @Test
    public void ascendedLawmage() {
        newGame();
        lands("Plains", 2, me);
        lands("Island", 2, me);
        final Card law = hand("Ascended Lawmage", me);
        put(me, law.getFirstSpellAbility(), null);
        lands("Island", 2, opp);
        put(opp, hand("Mana Leak", opp).getFirstSpellAbility(), targeting(game.getStack().peekAbility()));
        resolve();
        AssertJUnit.assertTrue("can't be countered", in(law, ZoneType.Battlefield));
        AssertJUnit.assertTrue(hasAll(current(law), Keyword.FLYING, Keyword.HEXPROOF));
        AssertJUnit.assertFalse("hexproof", canTarget(hand("Auger Spree", opp), opp, law));
        lands("Plains", 1, me);
        lands("Island", 1, me);
        final Card bl = oppBlightning();
        choices(opp).payUnless = false;
        activate(law, "Counter target", game.getStack().peekAbility());
        AssertJUnit.assertTrue("sacrificed", in(law, ZoneType.Graveyard));
        AssertJUnit.assertTrue("countered", in(bl, ZoneType.Graveyard));
        AssertJUnit.assertEquals(20, me.getLife());
    }

    @Test
    public void augerSpree() {
        newGame();
        lands("Swamp", 1, me);
        lands("Mountain", 1, me);
        final Card t = creature("Azors Elocutors", opp);
        cast(hand("Auger Spree", me), t);
        AssertJUnit.assertEquals(10, current(t).getNetPower());
        AssertJUnit.assertEquals(1, current(t).getNetToughness());
    }

    @Test
    public void azoriusSkyguard() {
        newGame();
        final Card sky = creature("Azorius Skyguard", me);
        AssertJUnit.assertTrue(hasAll(sky, Keyword.FLYING, Keyword.FIRST_STRIKE));
        final Card theirs = creature("Woolly Thoctar", opp);
        final Card mine = creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(5, current(theirs).getNetPower());
        AssertJUnit.assertEquals(4, current(theirs).getNetToughness());
        AssertJUnit.assertEquals(1, current(mine).getNetPower());
        lands("Plains", 1, me);
        lands("Island", 1, me);
        final Card bl = oppBlightning();
        choices(opp).payUnless = false;
        activate(sky, "Counter target", game.getStack().peekAbility());
        AssertJUnit.assertTrue(in(sky, ZoneType.Graveyard) && in(bl, ZoneType.Graveyard));
        AssertJUnit.assertEquals(20, me.getLife());
        AssertJUnit.assertEquals(6, current(theirs).getNetPower());
    }

    @Test
    public void azorsElocutors() {
        newGame();
        final Card az = creature("Azors Elocutors", me);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(1, current(az).getCounters(CounterEnumType.FILIBUSTER));
        damage(creature("Carrion Feeder", opp), me, 1, false);
        AssertJUnit.assertEquals("dealt damage: one removed", 0, current(az).getCounters(CounterEnumType.FILIBUSTER));
        current(az).setCounters(CounterEnumType.FILIBUSTER, 4);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("five counters: win", game.isGameOver() && me.getOutcome().hasWon());
    }

    @Test
    public void basilicaBellHaunt() {
        newGame();
        hand("Woolly Thoctar", opp);
        enter("Basilica Bell-Haunt", me);
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals(23, me.getLife());
    }

    @Test
    public void battlewiseHoplite() {
        newGame();
        lands("Forest", 1, me);
        final Card h = creature("Battlewise Hoplite", me);
        cast(hand("Giant Growth", me), h);
        AssertJUnit.assertEquals(1, p1p1(current(h)));
        AssertJUnit.assertEquals(6, current(h).getNetPower());
        AssertJUnit.assertEquals("scry 1", 1, me.getScryThisTurn());
    }

    @Test
    public void beamsplitterMage() {
        newGame();
        lands("Forest", 1, me);
        final Card mage = creature("Beamsplitter Mage", me);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        final Card c = creature("Woolly Thoctar", me);
        choices(me).prefer = is(a, b);
        cast(hand("Giant Growth", me), mage);
        AssertJUnit.assertEquals(5, current(mage).getNetPower());
        AssertJUnit.assertEquals("copied for up to two", 4, current(a).getNetPower());
        AssertJUnit.assertEquals(4, current(b).getNetPower());
        AssertJUnit.assertEquals("not a third", 6, current(c).getNetPower());
    }

    @Test
    public void beetlebackChief() {
        newGame();
        lands("Swamp", 2, me);
        choices(me).confirm = true;
        final Card bc = enter("Beetleback Chief", me);
        AssertJUnit.assertTrue(bc.hasKeyword(Keyword.MENACE));
        AssertJUnit.assertEquals("paid 1B/R: two Goblins", 2, tokens(me).size());
        final Card g = tokens(me).get(0);
        AssertJUnit.assertTrue(g.getType().hasCreatureType("Goblin") && g.isBlack() && g.hasKeyword(Keyword.MENACE));
        AssertJUnit.assertEquals(1, g.getNetPower());
        AssertJUnit.assertEquals(0, me.getLandsInPlay().stream().filter(l -> !l.isTapped()).count());
        newGame();
        lands("Swamp", 2, me);
        choices(me).confirm = false;
        enter("Beetleback Chief", me);
        AssertJUnit.assertTrue("not paid", tokens(me).isEmpty());
    }

    @Test
    public void biovisionary() {
        newGame();
        for (int i = 0; i < 3; i++) {
            creature("Biovisionary", opp);
        }
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertFalse("three are not enough", game.isGameOver());
        newGame();
        for (int i = 0; i < 4; i++) {
            creature("Biovisionary", opp);
        }
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("four: wins at each end step", game.isGameOver() && opp.getOutcome().hasWon());
    }

    @Test
    public void blightning() {
        newGame();
        lands("Swamp", 2, me);
        lands("Mountain", 1, me);
        for (int i = 0; i < 3; i++) {
            hand("Woolly Thoctar", opp);
        }
        cast(hand("Blightning", me), opp);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Hand));
    }

    @Test
    public void bloodSpatterAnalysis() {
        newGame();
        final Card t = creature("Azors Elocutors", opp);
        final Card dies = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(dies);
        final Card bsa = enter("Blood Spatter Analysis", me);
        AssertJUnit.assertTrue("3 damage", in(dies, ZoneType.Graveyard));
        AssertJUnit.assertEquals("a creature died", 1, current(bsa).getCounters(CounterEnumType.BLOODSTAIN));
        AssertJUnit.assertEquals("milled", 1, count(me, ZoneType.Graveyard));
        current(bsa).setCounters(CounterEnumType.BLOODSTAIN, 4);
        final Card back = grave("Carrion Feeder", me);
        choices(me).targets = List.of(back);
        kill(t);
        AssertJUnit.assertTrue("five counters: sacrificed", in(bsa, ZoneType.Graveyard));
        AssertJUnit.assertTrue("a creature card returned", in(back, ZoneType.Hand));
    }

    @Test
    public void bloodtitheHarvester() {
        newGame();
        final Card bh = creature("Bloodtithe Harvester", me);
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).getType().hasSubtype("Blood"));
        final Card t = creature("Woolly Thoctar", opp);
        AssertJUnit.assertTrue(ability(current(bh), "Target creature gets").getRestrictions().isSorcerySpeed());
        activate(bh, "Target creature gets", t);
        AssertJUnit.assertTrue(in(bh, ZoneType.Graveyard));
        AssertJUnit.assertEquals("twice one Blood", 4, current(t).getNetPower());
        AssertJUnit.assertEquals(2, current(t).getNetToughness());
    }

    @Test
    public void boltwingMarauder() {
        newGame();
        final Card bm = creature("Boltwing Marauder", me);
        AssertJUnit.assertTrue(bm.hasKeyword(Keyword.FLYING));
        choices(me).targets = List.of(bm);
        creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(5, current(bm).getNetPower());
        creature("Carrion Feeder", opp);
        AssertJUnit.assertEquals("only under your control", 5, current(bm).getNetPower());
    }

    @Test
    public void borborygmosEnraged() {
        newGame();
        final Card b = creature("Borborygmos Enraged", me);
        AssertJUnit.assertTrue(b.hasKeyword(Keyword.TRAMPLE));
        library(me, "Swamp", "Woolly Thoctar", "Mountain");
        attack(me, null, b);
        AssertJUnit.assertEquals(13, opp.getLife());
        AssertJUnit.assertEquals("lands into the hand", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("the rest into the graveyard", 1, count(me, ZoneType.Graveyard));
        activate(b, "3 damage", opp);
        AssertJUnit.assertEquals(10, opp.getLife());
        AssertJUnit.assertEquals("discarded a land", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void borosChallenger() {
        newGame();
        final Card bc = creature("Boros Challenger", me);
        final Card small = creature("Carrion Feeder", me);
        choices(me).targets = List.of(small);
        attack(me, null, bc, small);
        AssertJUnit.assertEquals("mentor", 1, p1p1(current(small)));
        lands("Mountain", 1, me);
        lands("Plains", 1, me);
        activate(bc, "gets +1/+1");
        AssertJUnit.assertEquals(3, current(bc).getNetPower());
        AssertJUnit.assertEquals(5, current(bc).getNetToughness());
    }

    @Test
    public void butcherOfTheHorde() {
        newGame();
        final Card b = creature("Butcher of the Horde", me);
        AssertJUnit.assertTrue(b.hasKeyword(Keyword.FLYING));
        final Card f = creature("Carrion Feeder", me);
        choices(me).modes.add("Lifelink");
        activate(b, "gains your choice");
        AssertJUnit.assertTrue(in(f, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(b).hasKeyword(Keyword.LIFELINK));
        AssertJUnit.assertFalse(current(b).hasKeyword(Keyword.HASTE));
    }

    @Test
    public void campusRenovation() {
        newGame();
        lands("Mountain", 4, me);
        lands("Plains", 1, me);
        final Card orn = grave("Ornithopter", me);
        library(me, "Ornithopter"); // above a Woolly Thoctar, which needs G
        cast(hand("Campus Renovation", me), orn);
        AssertJUnit.assertTrue(in(orn, ZoneType.Battlefield));
        AssertJUnit.assertEquals("top two exiled", 2, count(me, ZoneType.Exile));
        final Card ex = find(me, ZoneType.Exile, "Ornithopter");
        final Card later = find(me, ZoneType.Exile, "Woolly Thoctar");
        cast(me, alternative(me, ex, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue("cast from exile", in(ex, ZoneType.Battlefield));
        AssertJUnit.assertFalse(current(later).mayPlay(me).isEmpty());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("still playable", current(later).mayPlay(me).isEmpty());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertTrue("until the end of your next turn", current(later).mayPlay(me).isEmpty());
    }

    @Test
    public void casualtiesOfWar() {
        newGame();
        lands("Swamp", 3, me);
        lands("Forest", 3, me);
        final Card art = creature("Ornithopter", opp);
        final Card cre = creature("Woolly Thoctar", opp);
        final Card ench = enter("Collective Blessing", opp);
        final Card land = lands("Plains", 1, opp).get(0);
        choices(me).modes.addAll(List.of("artifact", "creature", "enchantment", "land"));
        cast(hand("Casualties of War", me), art, cre, ench, land);
        for (final Card c : List.of(art, cre, ench, land)) {
            AssertJUnit.assertTrue(c + " destroyed", in(c, ZoneType.Graveyard));
        }
    }

    @Test
    public void chromanticore() {
        newGame();
        final Card ch = creature("Chromanticore", me);
        AssertJUnit.assertTrue(ch.isEnchantment() && ch.isCreature());
        AssertJUnit.assertTrue(hasAll(ch, Keyword.FLYING, Keyword.DOUBLE_STRIKE, Keyword.PROWESS, Keyword.VIGILANCE,
                Keyword.TRAMPLE, Keyword.LIFELINK));
        newGame();
        for (final String l : List.of("Plains", "Island", "Swamp", "Mountain", "Forest")) {
            lands(l, 1, me);
        }
        lands("Swamp", 2, me);
        final Card host = creature("Carrion Feeder", me);
        final Card chr = hand("Chromanticore", me);
        cast(me, alternative(chr, SpellAbility::isBestow), targeting(host));
        AssertJUnit.assertEquals(current(host), current(chr).getAttachedTo());
        AssertJUnit.assertEquals(5, current(host).getNetPower());
        AssertJUnit.assertEquals(5, current(host).getNetToughness());
        AssertJUnit.assertTrue(hasAll(current(host), Keyword.FLYING, Keyword.DOUBLE_STRIKE, Keyword.PROWESS, Keyword.VIGILANCE,
                Keyword.TRAMPLE, Keyword.LIFELINK));
    }

    @Test
    public void collectiveBlessing() {
        newGame();
        final Card mine = creature("Carrion Feeder", me);
        final Card theirs = creature("Carrion Feeder", opp);
        enter("Collective Blessing", me);
        AssertJUnit.assertEquals(4, current(mine).getNetPower());
        AssertJUnit.assertEquals(4, current(mine).getNetToughness());
        AssertJUnit.assertEquals(1, current(theirs).getNetPower());
    }

    @Test
    public void conduitGoblin() {
        newGame();
        creature("Conduit Goblin", me);
        AssertJUnit.assertEquals(2, me.getCounters(CounterEnumType.ENERGY));
        final Card other = enter("Carrion Feeder", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(other);
        playUntil(me, PhaseType.COMBAT_BEGIN);
        AssertJUnit.assertEquals(1, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals(2, current(other).getNetPower());
        AssertJUnit.assertTrue(current(other).hasKeyword(Keyword.HASTE));
    }

    @Test
    public void corpsejackMenace() {
        newGame();
        final Card cm = creature("Corpsejack Menace", me);
        final Card c = creature("Carrion Feeder", me);
        enter("Abzan Ascendancy", me);
        AssertJUnit.assertEquals("doubled", 2, p1p1(current(c)));
        AssertJUnit.assertEquals(2, p1p1(current(cm)));
        final Card theirs = creature("Carrion Feeder", opp);
        enter("Abzan Ascendancy", opp);
        AssertJUnit.assertEquals("only yours", 1, p1p1(current(theirs)));
    }

    @Test
    public void cruelCelebrant() {
        newGame();
        final Card cc = creature("Cruel Celebrant", me);
        kill(creature("Carrion Feeder", me));
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals(21, me.getLife());
        kill(creature("Carrion Feeder", opp));
        AssertJUnit.assertEquals(19, opp.getLife());
        kill(cc);
        AssertJUnit.assertEquals("itself", 18, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
    }

    @Test
    public void cryptbornHorror() {
        newGame();
        lands("Swamp", 1, me);
        lands("Mountain", 1, me);
        damage(creature("Carrion Feeder", me), opp, 3, false);
        final Card h = hand("Cryptborn Horror", me);
        cast(h);
        AssertJUnit.assertEquals(3, p1p1(current(h)));
        AssertJUnit.assertTrue(current(h).hasKeyword(Keyword.TRAMPLE));
    }

    @Test
    public void cyclopsSuperconductor() {
        newGame();
        lands("Forest", 1, me);
        final Card cs = creature("Cyclops Superconductor", me);
        AssertJUnit.assertEquals(3, me.getCounters(CounterEnumType.ENERGY));
        cast(hand("Giant Growth", me), cs);
        AssertJUnit.assertEquals("prowess", 6, current(cs).getNetPower());
        newGame();
        final Card c2 = creature("Cyclops Superconductor", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(opp);
        kill(c2);
        AssertJUnit.assertEquals("paid EEE: damage equal to its power", 18, opp.getLife());
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void despark() {
        newGame();
        lands("Plains", 1, me);
        lands("Swamp", 1, me);
        final Card big = creature("Azorius Skyguard", opp);
        final Card small = creature("Woolly Thoctar", opp);
        final Card d = hand("Despark", me);
        AssertJUnit.assertFalse("mana value 3", canTarget(d, me, small));
        cast(d, big);
        AssertJUnit.assertTrue(in(big, ZoneType.Exile));
    }

    @Test
    public void djinnIlluminatus() {
        newGame();
        lands("Forest", 2, me);
        final Card dj = creature("Djinn Illuminatus", me);
        AssertJUnit.assertTrue(dj.hasKeyword(Keyword.FLYING));
        final Card t = creature("Carrion Feeder", me);
        choices(me).number = 1;
        cast(hand("Giant Growth", me), t);
        AssertJUnit.assertEquals("replicated once: +3/+3 twice", 6,
                current(t).getNetPower() - 1 + current(dj).getNetPower() - 3);
    }

    @Test
    public void doomForetold() {
        newGame();
        final Card df = enter("Doom Foretold", me);
        final Card victim = creature("Carrion Feeder", opp);
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("sacrificed", in(victim, ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(df, ZoneType.Battlefield));
        AssertJUnit.assertEquals(20, opp.getLife());

        newGame();
        final Card df2 = enter("Doom Foretold", me);
        hand("Woolly Thoctar", opp);
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("discarded", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals("drew", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(22, me.getLife());
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card k = tokens(me).get(0);
        AssertJUnit.assertTrue(k.getType().hasCreatureType("Knight") && k.getType().hasCreatureType("Human") && k.isWhite());
        AssertJUnit.assertTrue(k.hasKeyword(Keyword.EXALTED));
        AssertJUnit.assertEquals(2, k.getNetPower());
        AssertJUnit.assertTrue("then sacrificed", in(df2, ZoneType.Graveyard));
    }

    @Test
    public void doubleCleave() {
        newGame();
        lands("Mountain", 2, me);
        final Card t = creature("Carrion Feeder", me);
        final Card dc = hand("Double Cleave", me);
        cast(dc, t);
        AssertJUnit.assertTrue(current(t).hasKeyword(Keyword.DOUBLE_STRIKE));
        AssertJUnit.assertTrue("rebound", in(dc, ZoneType.Exile));
        choices(me).confirm = true;
        choices(me).targets = List.of(current(t));
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("cast again from exile", in(dc, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(t).hasKeyword(Keyword.DOUBLE_STRIKE));
    }

    @Test
    public void dramaticRescue() {
        newGame();
        lands("Plains", 1, me);
        lands("Island", 1, me);
        final Card t = creature("Carrion Feeder", me);
        final Card theirs = creature("Carrion Feeder", opp);
        final Card dr = hand("Dramatic Rescue", me);
        AssertJUnit.assertFalse(canTarget(dr, me, theirs));
        cast(dr, t);
        AssertJUnit.assertTrue(in(t, ZoneType.Hand));
        AssertJUnit.assertEquals(28, me.getLife());
    }

    @Test
    public void dregMangler() {
        newGame();
        final Card dm = enter("Dreg Mangler", me);
        AssertJUnit.assertTrue("haste", forge.game.combat.CombatUtil.canAttack(current(dm)));
        kill(dm);
        lands("Swamp", 3, me);
        lands("Forest", 2, me);
        final Card t = creature("Carrion Feeder", me);
        AssertJUnit.assertTrue(ability(current(dm), "Scavenge").getRestrictions().isSorcerySpeed());
        activate(current(dm), "Scavenge", t);
        AssertJUnit.assertEquals(3, p1p1(current(t)));
        AssertJUnit.assertTrue(in(dm, ZoneType.Exile));
    }

    @Test
    public void drownInFilth() {
        newGame();
        lands("Swamp", 1, me);
        lands("Forest", 1, me);
        library(me, "Swamp", "Swamp", "Woolly Thoctar", "Forest", "Woolly Thoctar");
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Drown in Filth", me), t);
        AssertJUnit.assertEquals("milled five", 6, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals("three lands", 3, current(t).getNetPower());
        AssertJUnit.assertEquals(1, current(t).getNetToughness());
    }

    @Test
    public void ecstaticElectromancer() {
        newGame();
        final Card sw = hand("Swamp", me);
        choices(me).confirm = true;
        final Card ee = creature("Ecstatic Electromancer", me);
        AssertJUnit.assertTrue(ee.hasKeyword(Keyword.PROWESS));
        AssertJUnit.assertTrue("discarded", in(sw, ZoneType.Graveyard));
        AssertJUnit.assertEquals("drew", 1, count(me, ZoneType.Hand));
        attack(me, null, ee);
        AssertJUnit.assertEquals("attacks: again", 2, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void eladamrisCall() {
        newGame();
        lands("Forest", 1, me);
        lands("Plains", 1, me);
        addCardToZone("Ornithopter", me, ZoneType.Library);
        choices(me).prefer = c -> named(c, "Ornithopter");
        cast(hand("Eladamris Call", me));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Ornithopter"));
    }

    @Test
    public void elasIlKor() {
        newGame();
        final Card e = creature("Elas il-Kor Sadistic Pilgrim", me);
        AssertJUnit.assertTrue(e.hasKeyword(Keyword.DEATHTOUCH));
        final Card f = creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(21, me.getLife());
        creature("Carrion Feeder", opp);
        AssertJUnit.assertEquals(21, me.getLife());
        kill(f);
        AssertJUnit.assertEquals(19, opp.getLife());
    }

    @Test
    public void electrosiphon() {
        newGame();
        lands("Island", 2, me);
        lands("Mountain", 1, me);
        final Card bl = oppBlightning();
        cast(me, hand("Electrosiphon", me).getFirstSpellAbility(), targeting(game.getStack().peekAbility()));
        AssertJUnit.assertTrue(in(bl, ZoneType.Graveyard));
        AssertJUnit.assertEquals(20, me.getLife());
        AssertJUnit.assertEquals("its mana value", 3, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void eliteGuardmage() {
        newGame();
        final Card eg = creature("Elite Guardmage", me);
        AssertJUnit.assertTrue(eg.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(23, me.getLife());
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void epicExperiment() {
        newGame();
        lands("Island", 2, me);
        lands("Mountain", 2, me);
        final Card t = creature("Carrion Feeder", me);
        library(me, "Blightning", "Giant Growth");
        choices(me).confirm = true;
        choices(me).targets = List.of(t);
        choices(me).payUnless = false;
        final Card ee = hand("Epic Experiment", me);
        ee.getFirstSpellAbility().setXManaCostPaid(2);
        put(me, ee.getFirstSpellAbility(), null);
        lands("Island", 2, opp);
        put(opp, hand("Mana Leak", opp).getFirstSpellAbility(), targeting(game.getStack().peekAbility()));
        resolve();
        AssertJUnit.assertEquals("can't be countered; Giant Growth cast free", 4, current(t).getNetPower());
        AssertJUnit.assertEquals("Blightning (mana value 3) not cast", 20, opp.getLife());
        AssertJUnit.assertTrue(in(find(me, ZoneType.Graveyard, "Blightning"), ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(find(me, ZoneType.Graveyard, "Giant Growth"), ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Exile));
    }

    @Test
    public void faithfulMending() {
        newGame();
        lands("Plains", 2, me);
        lands("Island", 2, me);
        final Card fm = hand("Faithful Mending", me);
        cast(fm);
        AssertJUnit.assertEquals(22, me.getLife());
        AssertJUnit.assertEquals("drew two, discarded two", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
        cast(me, alternative(fm, SpellAbility::isFlashback), null);
        AssertJUnit.assertEquals(24, me.getLife());
        AssertJUnit.assertTrue(in(fm, ZoneType.Exile));
    }

    @Test
    public void fathomFeeder() {
        newGame();
        final Card ff = creature("Fathom Feeder", me);
        AssertJUnit.assertTrue("devoid", ff.isColorless());
        AssertJUnit.assertTrue(ff.hasKeyword(Keyword.DEATHTOUCH));
        attack(me, null, ff);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals("ingest: two", 2, count(opp, ZoneType.Exile));
        lands("Island", 1, me);
        lands("Swamp", 4, me);
        activate(ff, "Draw two");
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals(4, count(opp, ZoneType.Exile));
    }

    @Test
    public void firemaneAvenger() {
        newGame();
        final Card fa = creature("Firemane Avenger", me);
        AssertJUnit.assertTrue(fa.hasKeyword(Keyword.FLYING));
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        choices(me).targets = List.of(opp);
        attack(me, null, fa, a, b);
        AssertJUnit.assertEquals("3 + 3 + 1 + 1", 12, opp.getLife());
        AssertJUnit.assertEquals(23, me.getLife());
        newGame();
        final Card fa2 = creature("Firemane Avenger", me);
        final Card c = creature("Carrion Feeder", me);
        choices(me).targets = List.of(opp);
        attack(me, null, fa2, c);
        AssertJUnit.assertEquals("one other attacker: no battalion", 16, opp.getLife());
        AssertJUnit.assertEquals(20, me.getLife());
    }

    @Test
    public void firemindsResearch() {
        newGame();
        lands("Island", 2, me);
        lands("Mountain", 2, me);
        lands("Forest", 1, me);
        final Card fr = enter("Fireminds Research", me);
        cast(hand("Giant Growth", me), creature("Carrion Feeder", me));
        AssertJUnit.assertEquals(1, current(fr).getCounters(CounterEnumType.CHARGE));
        current(fr).setCounters(CounterEnumType.CHARGE, 7);
        activate(fr, "Draw a card");
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(5, current(fr).getCounters(CounterEnumType.CHARGE));
        cast(me, ability(current(fr), "5 damage"), sa -> {
            sa.getTargets().add(opp);
            sa.addDividedAllocation(opp, 5);
        });
        AssertJUnit.assertEquals(15, opp.getLife());
        AssertJUnit.assertEquals(0, current(fr).getCounters(CounterEnumType.CHARGE));
    }

    @Test
    public void flayerDrone() {
        newGame();
        final Card fd = creature("Flayer Drone", me);
        AssertJUnit.assertTrue(fd.isColorless() && fd.hasKeyword(Keyword.FIRST_STRIKE));
        choices(me).targets = List.of(opp);
        creature("Ornithopter", me);
        AssertJUnit.assertEquals(19, opp.getLife());
        creature("Woolly Thoctar", me);
        AssertJUnit.assertEquals("colorless only", 19, opp.getLife());
    }

    @Test
    public void forerunnerOfSlaughter() {
        newGame();
        lands("Swamp", 1, me);
        final Card fs = creature("Forerunner of Slaughter", me);
        AssertJUnit.assertTrue(fs.isColorless());
        final Card o = enter("Ornithopter", me);
        final Card t = enter("Woolly Thoctar", me);
        final SpellAbility sa = ability(current(fs), "haste");
        sa.setActivatingPlayer(me);
        AssertJUnit.assertFalse("colorless only", sa.canTarget(current(t)));
        activate(fs, "haste", o);
        AssertJUnit.assertTrue(current(o).hasKeyword(Keyword.HASTE));
    }

    @Test
    public void fusionElemental() {
        newGame();
        library(me, "Ornithopter", "Swamp");
        choices(me).confirm = true;
        enter("Fusion Elemental", me);
        AssertJUnit.assertEquals("discover 5: cast", 1,
                me.getCreaturesInPlay().stream().filter(c -> c.getName().equals("Ornithopter")).count());
        AssertJUnit.assertEquals("the land to the bottom", "Swamp", com.google.common.collect.Iterables.getLast(me.getCardsIn(ZoneType.Library)).getName());
    }

    @Test
    public void gelectrode() {
        newGame();
        lands("Forest", 1, me);
        final Card g = enter("Gelectrode", me);
        activate(g, "1 damage", opp);
        AssertJUnit.assertEquals("haste", 19, opp.getLife());
        AssertJUnit.assertTrue(current(g).isTapped());
        cast(hand("Giant Growth", me), g);
        AssertJUnit.assertFalse("untapped by a sorcery", current(g).isTapped());
    }

    @Test
    public void ghorClanRampager() {
        newGame();
        AssertJUnit.assertTrue(creature("Ghor-Clan Rampager", opp).hasKeyword(Keyword.TRAMPLE));
        final Card a = creature("Carrion Feeder", me);
        choices(me).attackers = List.of(a);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        lands("Mountain", 1, me);
        lands("Forest", 1, me);
        final Card gr = hand("Ghor-Clan Rampager", me);
        activate(gr, "attacking creature", a);
        AssertJUnit.assertTrue(in(gr, ZoneType.Graveyard));
        AssertJUnit.assertEquals(5, current(a).getNetPower());
        AssertJUnit.assertTrue(current(a).hasKeyword(Keyword.TRAMPLE));
        choices(me).attackers = List.of();
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals(15, opp.getLife());
    }

    @Test
    public void giftOfOrzhova() {
        newGame();
        lands("Plains", 1, me);
        lands("Swamp", 2, me);
        final Card t = creature("Carrion Feeder", me);
        cast(hand("Gift of Orzhova", me), t);
        AssertJUnit.assertEquals(2, current(t).getNetPower());
        AssertJUnit.assertEquals(2, current(t).getNetToughness());
        AssertJUnit.assertTrue(hasAll(current(t), Keyword.FLYING, Keyword.LIFELINK));
        kill(t);
        AssertJUnit.assertEquals("afterlife 1", 1, tokens(me).size());
        final Card s = tokens(me).get(0);
        AssertJUnit.assertTrue(s.getType().hasCreatureType("Spirit") && s.isWhite() && s.hasKeyword(Keyword.FLYING));
    }
}
