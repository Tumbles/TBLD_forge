package forge.tbld;

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

/** Black TBLD cards, fourth batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlackCards4Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Smallpox", "Smothering Abomination", "Souldrinker", "Spoils of the Vault", "Stroke of Midnight", "Stromkirk Condemned",
            "Sudden Edict", "Sultai Scavenger", "Tangled Colony", "The Creation of Avacyn", "The Last Ride", "Thriving Rats",
            "Tinybones Bauble Burglar", "Tomb Tyrant", "Torment of Hailfire", "Torment of Scarabs", "Tormented Hero", "Tragic Slip",
            "Treacherous Pit-Dweller", "Twist of Fate", "Typhoid Rats", "Undead Butler", "Underworld Connections", "Vampire Cutthroat",
            "Vampire Nighthawk", "Vampire of the Dire Moon", "Vengeful Reaper", "Vicious Conquistador", "Vicious Rumors",
            "Victim of Night", "Vile Manifestation", "Vito Thorn of the Dusk Rose", "Voracious Null", "Voracious Vermin", "Wail of War",
            "Warren Pilferers", "Warren Soultrader", "Whisper Blood Liturgist", "Wishclaw Talisman", "Wits End");

    private List<Card> swamps(final int n) {
        return lands("Swamp", n, me);
    }

    private static boolean named(final Object c, final String name) {
        return c instanceof Card && ((Card) c).getName().equals(name);
    }

    private SpellAbility suspend(final Card c) {
        return alternative(c, sa -> sa.getDescription().startsWith("Suspend"));
    }

    private SpellAbility foretell(final Card c) {
        return current(c).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow();
    }

    private boolean canActivate(final Card c, final String text) {
        final SpellAbility sa = ability(current(c), text);
        sa.setActivatingPlayer(c.getController());
        return sa.canPlay();
    }

    @Test
    public void smallpox() {
        newGame();
        swamps(3);
        creature("Carrion Feeder", me);
        hand("Woolly Thoctar", me);
        creature("Woolly Thoctar", opp);
        lands("Swamp", 1, opp);
        hand("Woolly Thoctar", opp);
        final Card pox = hand("Smallpox", me);
        cast(pox);
        for (final Player p : game.getPlayers()) {
            AssertJUnit.assertEquals(19, p.getLife());
            AssertJUnit.assertEquals("discarded", 0, count(p, ZoneType.Hand));
            AssertJUnit.assertEquals("sacrificed a creature", 0, p.getCreaturesInPlay().size());
        }
        AssertJUnit.assertEquals("sacrificed a land", 2, me.getLandsInPlay().size());
        AssertJUnit.assertEquals(0, opp.getLandsInPlay().size());
    }

    @Test
    public void smotheringAbomination() {
        newGame();
        final Card abom = creature("Smothering Abomination", me);
        AssertJUnit.assertTrue(abom.isColorless() && abom.hasKeyword(Keyword.FLYING));
        final Card fodder = addToken("w_0_1_goat", me);
        choices(me).prefer = c -> c == fodder;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertFalse("sacrificed at upkeep", in(fodder, ZoneType.Battlefield));
        AssertJUnit.assertEquals("drew for the sacrifice", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void souldrinker() {
        newGame();
        final Card sd = creature("Souldrinker", me);
        final Card a = creature("Carrion Feeder", opp);
        final Card b = creature("Carrion Feeder", me);
        kill(a);
        AssertJUnit.assertEquals(2, p1p1(current(sd)));
        AssertJUnit.assertTrue(current(sd).hasKeyword(Keyword.FLYING));
        kill(b);
        AssertJUnit.assertEquals("first time each turn only", 2, p1p1(current(sd)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("flying this turn", current(sd).hasKeyword(Keyword.FLYING));
        kill(creature("Carrion Feeder", opp));
        AssertJUnit.assertEquals("again next turn", 4, p1p1(current(sd)));
        // Madness 2B
        newGame();
        swamps(3);
        final Card mad = hand("Souldrinker", me);
        choices(me).confirm = true;
        discard(me, mad);
        AssertJUnit.assertTrue("cast for its madness cost", in(mad, ZoneType.Battlefield));
    }

    @Test
    public void spoilsOfTheVault() {
        newGame();
        swamps(1);
        library(me, "Swamp", "Ornithopter", "Carrion Feeder");
        choices(me).cardName = "Swamp";
        cast(hand("Spoils of the Vault", me));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Swamp"));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Exile));
        AssertJUnit.assertEquals("1 life for each exiled card", 18, me.getLife());
    }

    @Test
    public void strokeOfMidnight() {
        newGame();
        swamps(3);
        for (int i = 0; i < 5; i++) {
            addCardToZone("Woolly Thoctar", me, ZoneType.Library);
        }
        cast(hand("Stroke of Midnight", me));
        AssertJUnit.assertEquals("mill twelve", 12, count(me, ZoneType.Graveyard) - 1);
        AssertJUnit.assertEquals(3, count(me, ZoneType.Library));

        newGame();
        swamps(1);
        final Card s = hand("Stroke of Midnight", me);
        cast(me, suspend(s), null);
        AssertJUnit.assertTrue("suspended for B", in(s, ZoneType.Exile));
        AssertJUnit.assertEquals(3, current(s).getCounters(CounterEnumType.TIME));
        choices(me).confirm = true;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(2, current(s).getCounters(CounterEnumType.TIME));
        playUntil(me, PhaseType.UPKEEP);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("cast when the last counter is removed", in(s, ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Library));
    }

    @Test
    public void stromkirkCondemned() {
        newGame();
        final Card sc = creature("Stromkirk Condemned", me);
        final Card vamp = creature("Pulse Tracker", me);
        final Card other = creature("Carrion Feeder", me);
        hand("Swamp", me);
        hand("Swamp", me);
        activate(sc, "Vampires");
        AssertJUnit.assertEquals(3, current(sc).getNetPower());
        AssertJUnit.assertEquals(2, current(vamp).getNetToughness());
        AssertJUnit.assertEquals(1, current(other).getNetPower());
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertFalse("once each turn", canActivate(sc, "Vampires"));
    }

    @Test
    public void suddenEdict() {
        newGame();
        swamps(2);
        final Card t = creature("Woolly Thoctar", opp);
        put(me, hand("Sudden Edict", me).getFirstSpellAbility(), targeting(opp));
        lands("Island", 2, opp);
        final SpellAbility leak = hand("Mana Leak", opp).getFirstSpellAbility();
        leak.setActivatingPlayer(opp);
        AssertJUnit.assertFalse("split second", leak.canPlay());
        resolve();
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard));
    }

    @Test
    public void sultaiScavenger() {
        newGame();
        swamps(1);
        for (int i = 0; i < 8; i++) {
            grave("Swamp", me);
        }
        final Card s = hand("Sultai Scavenger", me);
        cast(s);
        AssertJUnit.assertTrue("delve", in(s, ZoneType.Battlefield) && current(s).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
        untapAll(me);
        activate(s, "indestructible");
        AssertJUnit.assertTrue(current(s).hasKeyword(Keyword.INDESTRUCTIBLE));
        AssertJUnit.assertEquals("three exiled", 0, count(me, ZoneType.Graveyard));
    }

    @Test
    public void tangledColony() {
        newGame();
        final Card tc = creature("Tangled Colony", me);
        AssertJUnit.assertFalse(canBlock(tc));
        final Card blocker = creature("Woolly Thoctar", opp);
        attack(me, java.util.Map.of(blocker, tc), tc);
        AssertJUnit.assertTrue(in(tc, ZoneType.Graveyard));
        AssertJUnit.assertEquals("X = damage dealt", 6, tokens(me).size());
        final Card rat = tokens(me).get(0);
        rat.setSickness(false);
        AssertJUnit.assertTrue(rat.getType().hasCreatureType("Rat") && !canBlock(rat));
    }

    @Test
    public void theCreationOfAvacyn() {
        newGame();
        swamps(3);
        library(me, "Woolly Thoctar");
        addCardToZone("Vampire Nighthawk", me, ZoneType.Library);
        choices(me).prefer = c -> named(c, "Vampire Nighthawk");
        final Card saga = hand("The Creation of Avacyn", me);
        cast(saga);
        final Card exiled = me.getCardsIn(ZoneType.Exile).get(0);
        AssertJUnit.assertTrue("I: exiled face down", exiled.isFaceDown());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertFalse("II: face up", current(exiled).isFaceDown());
        AssertJUnit.assertEquals("a creature: lose its mana value", 17, me.getLife());
        choices(me).confirm = true;
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertTrue("III: into play", in(exiled, ZoneType.Battlefield));
        AssertJUnit.assertTrue("sacrificed after III", in(saga, ZoneType.Graveyard));

        newGame();
        swamps(3);
        addCardToZone("Swamp", me, ZoneType.Library);
        choices(me).prefer = c -> named(c, "Swamp");
        cast(hand("The Creation of Avacyn", me));
        final Card land = me.getCardsIn(ZoneType.Exile).get(0);
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("not a creature", 20, me.getLife());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertTrue("into its owner's hand", in(land, ZoneType.Hand));
    }

    @Test
    public void theLastRide() {
        newGame();
        swamps(3);
        final Card ride = enter("The Last Ride", me);
        creature("Woolly Thoctar", me);
        me.setLife(5, null);
        activate(ride, "Crew");
        AssertJUnit.assertTrue("crewed", current(ride).isCreature());
        AssertJUnit.assertEquals("-X/-X, X = life", 8, current(ride).getNetPower());
        AssertJUnit.assertEquals(8, current(ride).getNetToughness());
        activate(ride, "Draw a card");
        AssertJUnit.assertEquals(3, me.getLife());
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(10, current(ride).getNetPower());
    }

    @Test
    public void thrivingRats() {
        newGame();
        final Card rats = creature("Thriving Rats", me);
        AssertJUnit.assertEquals(1, me.getCounters(CounterEnumType.ENERGY));
        discard(opp, hand("Woolly Thoctar", opp));
        AssertJUnit.assertEquals("an opponent discarded", 2, me.getCounters(CounterEnumType.ENERGY));
        choices(me).confirm = true;
        attack(me, null, rats);
        AssertJUnit.assertEquals(1, p1p1(current(rats)));
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals(18, opp.getLife());
    }

    @Test
    public void tinybonesBaubleBurglar() {
        newGame();
        swamps(4);
        final Card tb = creature("Tinybones Bauble Burglar", me);
        final Card elf = hand("Arbor Elf", opp);
        activate(tb, "discards");
        AssertJUnit.assertTrue("exiled with a stash counter", in(elf, ZoneType.Exile));
        AssertJUnit.assertEquals(1, current(elf).getCounters(CounterEnumType.STASH));
        AssertJUnit.assertTrue(ability(current(tb), "discards").getRestrictions().isSorcerySpeed());
        swamps(1);
        cast(me, alternative(me, elf, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue("cast with black mana", in(elf, ZoneType.Battlefield));
        AssertJUnit.assertEquals(me, current(elf).getController());
    }

    @Test
    public void tombTyrant() {
        newGame();
        swamps(3);
        final Card tyrant = creature("Tomb Tyrant", me);
        final Card zombie = creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(2, current(zombie).getNetPower());
        AssertJUnit.assertEquals(3, current(tyrant).getNetPower());
        addToken("w_0_1_goat", me);
        grave("Carrion Feeder", me);
        grave("Putrid Goblin", me);
        AssertJUnit.assertFalse("three Zombie cards needed", canActivate(tyrant, "Return a Zombie"));
        grave("Sightless Ghoul", me);
        activate(tyrant, "Return a Zombie");
        AssertJUnit.assertTrue("sacrificed the Goat", tokens(me).isEmpty());
        AssertJUnit.assertEquals(2, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(3, me.getCreaturesInPlay().size());
    }

    @Test
    public void tormentOfHailfire() {
        newGame();
        swamps(4);
        hand("Woolly Thoctar", opp);
        choices(opp).modes.add("discard");
        choices(opp).payUnless = true;
        final Card th = hand("Torment of Hailfire", me);
        th.getFirstSpellAbility().setXManaCostPaid(2);
        cast(th);
        AssertJUnit.assertEquals("X = 2: discarded once, then lost 3", 17, opp.getLife());
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Hand));
    }

    @Test
    public void tormentOfScarabs() {
        newGame();
        swamps(3);
        final Card curse = hand("Torment of Scarabs", me);
        cast(curse, opp);
        AssertJUnit.assertTrue(current(curse).isAttachedToEntity(opp));
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("nothing to sacrifice or discard", 17, opp.getLife());
        hand("Woolly Thoctar", opp);
        choices(opp).modes.add("discard");
        choices(opp).payUnless = true;
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("discarded instead", 17, opp.getLife());
    }

    @Test
    public void tormentedHero() {
        newGame();
        swamps(1);
        final Card hero = enter("Tormented Hero", me);
        AssertJUnit.assertTrue("enters tapped", current(hero).isTapped());
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Skulduggery", me), hero, t);
        AssertJUnit.assertEquals("heroic", 17, opp.getLife());
        AssertJUnit.assertEquals(23, me.getLife());
    }

    @Test
    public void tragicSlip() {
        newGame();
        swamps(2);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Tragic Slip", me), t);
        AssertJUnit.assertEquals(5, current(t).getNetPower());
        AssertJUnit.assertEquals(3, current(t).getNetToughness());
        kill(addToken("w_0_1_goat", me));
        cast(hand("Tragic Slip", me), t);
        AssertJUnit.assertTrue("morbid: -13/-13", in(t, ZoneType.Graveyard));
    }

    @Test
    public void treacherousPitDweller() {
        newGame();
        final Card d = creature("Treacherous Pit-Dweller", me);
        AssertJUnit.assertEquals(me, current(d).getController());
        choices(me).targets = List.of(opp);
        kill(d);
        AssertJUnit.assertTrue("undying", in(d, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, p1p1(current(d)));
        AssertJUnit.assertEquals("entered from a graveyard: the opponent gains control", opp, current(d).getController());
    }

    @Test
    public void twistOfFate() {
        newGame();
        swamps(4);
        final Card small = creature("Carrion Feeder", opp);
        final Card big = creature("Woolly Thoctar", opp);
        final Card mine = creature("Carrion Feeder", me);
        cast(hand("Twist of Fate", me), opp);
        AssertJUnit.assertTrue(in(small, ZoneType.Graveyard) && in(mine, ZoneType.Battlefield));
        AssertJUnit.assertEquals(4, current(big).getNetPower());
        AssertJUnit.assertEquals(2, current(big).getNetToughness());
        AssertJUnit.assertEquals("scry 2", 1, me.getScryThisTurn());
    }

    @Test
    public void typhoidRats() {
        newGame();
        final Card rats = creature("Typhoid Rats", me);
        final Card big = creature("Woolly Thoctar", opp);
        damage(rats, big, 1, true);
        AssertJUnit.assertTrue("deathtouch", in(big, ZoneType.Graveyard));
        final Card a = grave("Rancid Rats", me);
        final Card b = grave("Ravenous Rats", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(a, b);
        damage(rats, opp, 1, true);
        AssertJUnit.assertTrue("sacrificed", in(rats, ZoneType.Graveyard));
        AssertJUnit.assertTrue("two Rats back", in(a, ZoneType.Hand) && in(b, ZoneType.Hand));
    }

    @Test
    public void undeadButler() {
        newGame();
        final Card butler = creature("Undead Butler", me);
        AssertJUnit.assertEquals("mill three", 3, count(me, ZoneType.Graveyard));
        choices(me).confirm = true;
        final Card target = me.getCardsIn(ZoneType.Graveyard).get(0);
        choices(me).targets = List.of(target);
        kill(butler);
        AssertJUnit.assertTrue("exiled", in(butler, ZoneType.Exile));
        AssertJUnit.assertTrue(in(target, ZoneType.Hand));
    }

    @Test
    public void underworldConnections() {
        newGame();
        swamps(3);
        final Card land = lands("Swamp", 1, me).get(0);
        final Card uc = hand("Underworld Connections", me);
        cast(uc, land);
        AssertJUnit.assertTrue(current(uc).isAttachedToEntity(current(land)));
        untapAll(me);
        activate(land, "Draw a card");
        AssertJUnit.assertEquals(19, me.getLife());
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(current(land).isTapped());
    }

    @Test
    public void vampireCutthroat() {
        newGame();
        final Card vc = creature("Vampire Cutthroat", me);
        AssertJUnit.assertTrue(vc.hasKeyword(Keyword.SKULK) && vc.hasKeyword(Keyword.LIFELINK));
        AssertJUnit.assertFalse("skulk", forge.game.combat.CombatUtil.canBlock(vc, creature("Woolly Thoctar", opp)));
        damage(vc, opp, 1, true);
        AssertJUnit.assertEquals(21, me.getLife());
        kill(vc);
        hand("Swamp", me);
        hand("Swamp", me);
        activate(current(vc), "Return");
        AssertJUnit.assertTrue(in(vc, ZoneType.Battlefield));
        AssertJUnit.assertEquals("discarded two", 0, count(me, ZoneType.Hand));
    }

    @Test
    public void vampireNighthawk() {
        newGame();
        final Card v = creature("Vampire Nighthawk", me);
        AssertJUnit.assertTrue(v.hasKeyword(Keyword.FLYING) && v.hasKeyword(Keyword.DEATHTOUCH) && v.hasKeyword(Keyword.LIFELINK));
        final Card big = creature("Woolly Thoctar", opp);
        damage(v, big, 2, true);
        AssertJUnit.assertTrue(in(big, ZoneType.Graveyard));
        AssertJUnit.assertEquals(22, me.getLife());
    }

    @Test
    public void vampireOfTheDireMoon() {
        newGame();
        swamps(1);
        final Card v = creature("Vampire of the Dire Moon", me);
        AssertJUnit.assertTrue(v.hasKeyword(Keyword.DEATHTOUCH) && v.hasKeyword(Keyword.LIFELINK));
        final Card t = creature("Woolly Thoctar", opp);
        activate(v, "becomes a Vampire", t);
        AssertJUnit.assertTrue(current(t).getType().hasCreatureType("Vampire") && current(t).getType().hasCreatureType("Beast"));
        kill(v);
        AssertJUnit.assertFalse("as long as you control it", current(t).getType().hasCreatureType("Vampire"));
    }

    @Test
    public void vengefulReaper() {
        newGame();
        swamps(2);
        final Card r = hand("Vengeful Reaper", me);
        cast(me, foretell(r), null);
        AssertJUnit.assertTrue(in(r, ZoneType.Exile) && current(r).isForetold());
        playUntil(me, PhaseType.MAIN1);
        cast(me, alternative(r, SpellAbility::isForetold), null);
        AssertJUnit.assertTrue("foretold for 1B", in(r, ZoneType.Battlefield));
        final Card c = current(r);
        AssertJUnit.assertTrue(c.hasKeyword(Keyword.FLYING) && c.hasKeyword(Keyword.DEATHTOUCH) && c.hasKeyword(Keyword.HASTE));
    }

    @Test
    public void viciousConquistador() {
        newGame();
        final Card v = creature("Vicious Conquistador", me);
        attack(me, null, v);
        AssertJUnit.assertEquals("1 drained, 1 combat damage", 18, opp.getLife());
        AssertJUnit.assertEquals(21, me.getLife());
    }

    @Test
    public void viciousRumors() {
        newGame();
        swamps(1);
        hand("Woolly Thoctar", opp);
        cast(hand("Vicious Rumors", me));
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals("discarded and milled", 2, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals(21, me.getLife());
    }

    @Test
    public void victimOfNight() {
        newGame();
        swamps(2);
        final Card zombie = creature("Carrion Feeder", opp);
        final Card vampire = creature("Pulse Tracker", opp);
        final Card t = creature("Woolly Thoctar", opp);
        final Card v = hand("Victim of Night", me);
        v.getFirstSpellAbility().setActivatingPlayer(me);
        AssertJUnit.assertFalse(v.getFirstSpellAbility().canTarget(zombie));
        AssertJUnit.assertFalse(v.getFirstSpellAbility().canTarget(vampire));
        cast(v, t);
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard));
    }

    @Test
    public void vileManifestation() {
        newGame();
        swamps(2);
        final Card vm = creature("Vile Manifestation", me);
        AssertJUnit.assertEquals(0, current(vm).getNetPower());
        grave("Silent Skimmer", me);
        grave("Foul-Tongue Shriek", me);
        grave("Woolly Thoctar", me);
        resolve();
        AssertJUnit.assertEquals("cycling and Eldrazicycling", 2, current(vm).getNetPower());
        final Card other = hand("Vile Manifestation", me);
        activate(other, "Cycling");
        AssertJUnit.assertTrue("cycling 2", in(other, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(3, current(vm).getNetPower());
    }

    @Test
    public void vitoThornOfTheDuskRose() {
        newGame();
        swamps(5);
        final Card vito = creature("Vito Thorn of the Dusk Rose", me);
        choices(me).targets = List.of(opp);
        me.gainLife(3, null, null);
        resolve();
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertFalse("only if an opponent has 10 or less life", canActivate(vito, "lifelink"));
        opp.setLife(10, null);
        final Card other = creature("Carrion Feeder", me);
        activate(vito, "lifelink");
        AssertJUnit.assertTrue(current(other).hasKeyword(Keyword.LIFELINK) && current(vito).hasKeyword(Keyword.LIFELINK));
    }

    @Test
    public void voraciousNull() {
        newGame();
        swamps(2);
        final Card vn = creature("Voracious Null", me);
        addToken("w_0_1_goat", me);
        activate(vn, "two +1/+1");
        AssertJUnit.assertEquals(2, p1p1(current(vn)));
        AssertJUnit.assertTrue(tokens(me).isEmpty());
    }

    @Test
    public void voraciousVermin() {
        newGame();
        final Card vv = creature("Voracious Vermin", me);
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card rat = tokens(me).get(0);
        rat.setSickness(false);
        AssertJUnit.assertTrue(rat.getType().hasCreatureType("Rat") && !canBlock(rat));
        kill(rat);
        AssertJUnit.assertEquals(1, p1p1(current(vv)));
        kill(creature("Carrion Feeder", opp));
        AssertJUnit.assertEquals("only your creatures", 1, p1p1(current(vv)));
    }

    @Test
    public void wailOfWar() {
        newGame();
        swamps(6);
        final Card small = creature("Carrion Feeder", opp);
        final Card big = creature("Woolly Thoctar", opp);
        choices(me).modes.add("-1/-1");
        cast(hand("Wail of War", me), opp);
        AssertJUnit.assertTrue(in(small, ZoneType.Graveyard));
        AssertJUnit.assertEquals(5, current(big).getNetPower());
        final Card a = grave("Woolly Thoctar", me);
        final Card b = grave("Carrion Feeder", me);
        final Card c = grave("Rancid Rats", me);
        choices(me).modes.add("Return up to two");
        cast(hand("Wail of War", me), a, b, c);
        AssertJUnit.assertTrue(in(a, ZoneType.Hand) && in(b, ZoneType.Hand) && in(c, ZoneType.Graveyard));
    }

    @Test
    public void warrenPilferers() {
        newGame();
        final Card goblin = grave("Putrid Goblin", me);
        choices(me).targets = List.of(goblin);
        final Card wp = enter("Warren Pilferers", me);
        AssertJUnit.assertTrue(in(goblin, ZoneType.Hand));
        AssertJUnit.assertTrue("a Goblin: haste", current(wp).hasKeyword(Keyword.HASTE));
        final Card other = grave("Woolly Thoctar", me);
        choices(me).targets = List.of(other);
        final Card wp2 = enter("Warren Pilferers", me);
        AssertJUnit.assertTrue(in(other, ZoneType.Hand));
        AssertJUnit.assertFalse(current(wp2).hasKeyword(Keyword.HASTE));
    }

    @Test
    public void warrenSoultrader() {
        newGame();
        final Card ws = creature("Warren Soultrader", me);
        final Card goat = addToken("w_0_1_goat", me);
        activate(ws, "Treasure");
        AssertJUnit.assertFalse(in(goat, ZoneType.Battlefield));
        AssertJUnit.assertEquals(19, me.getLife());
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).getType().hasSubtype("Treasure"));
    }

    @Test
    public void whisperBloodLiturgist() {
        newGame();
        final Card w = creature("Whisper Blood Liturgist", me);
        addToken("w_0_1_goat", me);
        addToken("w_0_1_goat", me);
        final Card back = grave("Woolly Thoctar", me);
        activate(w, "Return target", back);
        AssertJUnit.assertTrue(in(back, ZoneType.Battlefield));
        AssertJUnit.assertTrue("sacrificed two", tokens(me).isEmpty());
        // Ward: the opponent's spell is countered unless they discard a card
        lands("Swamp", 3, opp);
        hand("Woolly Thoctar", opp);
        choices(opp).payUnless = false;
        put(opp, hand("Poison the Cup", opp).getFirstSpellAbility(), targeting(current(w)));
        resolve();
        AssertJUnit.assertTrue("countered", in(w, ZoneType.Battlefield));
        untapAll(opp);
        choices(opp).payUnless = true;
        put(opp, hand("Poison the Cup", opp).getFirstSpellAbility(), targeting(current(w)));
        resolve();
        AssertJUnit.assertEquals("discarded for the ward", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue(in(w, ZoneType.Graveyard));
    }

    @Test
    public void wishclawTalisman() {
        newGame();
        swamps(1);
        final Card wt = enter("Wishclaw Talisman", me);
        AssertJUnit.assertEquals(3, current(wt).getCounters(CounterEnumType.WISH));
        addCardToZone("Swamp", me, ZoneType.Library);
        choices(me).prefer = c -> named(c, "Swamp");
        activate(wt, "Search");
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Swamp"));
        AssertJUnit.assertEquals(2, current(wt).getCounters(CounterEnumType.WISH));
        AssertJUnit.assertEquals("an opponent gains control", opp, current(wt).getController());
        lands("Swamp", 1, opp);
        current(wt).setTapped(false);
        AssertJUnit.assertFalse("only during your turn", canActivate(wt, "Search"));
    }

    @Test
    public void witsEnd() {
        newGame();
        swamps(7);
        for (int i = 0; i < 3; i++) {
            hand("Woolly Thoctar", opp);
        }
        put(me, hand("Wits End", me).getFirstSpellAbility(), targeting(opp));
        lands("Island", 2, opp);
        put(opp, hand("Mana Leak", opp).getFirstSpellAbility(), targeting(game.getStack().peekAbility()));
        resolve();
        AssertJUnit.assertEquals("can't be countered: the whole hand discarded", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(4, count(opp, ZoneType.Graveyard));

        newGame();
        swamps(4);
        final Card w = hand("Wits End", me);
        cast(me, suspend(w), null);
        AssertJUnit.assertEquals(2, current(w).getCounters(CounterEnumType.TIME));
        choices(me).confirm = true;
        choices(me).targets = List.of(opp);
        playUntil(me, PhaseType.UPKEEP);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("cast from suspend", in(w, ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue(count(opp, ZoneType.Graveyard) > 0);
    }
}
