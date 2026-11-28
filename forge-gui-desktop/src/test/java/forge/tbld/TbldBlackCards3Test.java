package forge.tbld;

import forge.StaticData;
import forge.deck.DeckFormat;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

/** Black TBLD cards, third batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlackCards3Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Palace Siege", "Perilous Predicament", "Pestilence", "Phyrexian Negator", "Piper of the Swarm", "Pitiless Carnage",
            "Pitiless Horde", "Plunge into Darkness", "Poison the Cup", "Priest of the Blood Rite", "Pulse Tracker", "Putrid Goblin",
            "Qarsi Sadist", "Queens Agent", "Raiders Spoils", "Rancid Rats", "Ransack the Lab", "Ravenous Rats", "Ravens Crime",
            "Reapers Talisman", "Reaping the Graves", "Reaver Drone", "Return Upon the Tide", "Revenge of the Rats", "Rot Hulk",
            "Salvage Slasher", "Sanctum Seeker", "Scheming Symmetry", "Scion of the Swarm", "Scrounge", "Serrated Scorpion",
            "Shrieking Affliction", "Shrivel", "Sibsig Muckdraggers", "Sifter of Skulls", "Sightless Ghoul", "Sign in Blood",
            "Silent Skimmer", "Skinrender", "Skirsdag High Priest", "Skulduggery");

    private List<Card> swamps(final int n) {
        return lands("Swamp", n, me);
    }

    private static boolean named(final Object c, final String name) {
        return c instanceof Card && ((Card) c).getName().equals(name);
    }

    private int creatures(final Player p) {
        return p.getCreaturesInPlay().size();
    }

    @Test
    public void palaceSiege() {
        newGame();
        swamps(5);
        choices(me).modes.add("Dragons");
        cast(hand("Palace Siege", me));
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("Dragons: each opponent loses 2", 18, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());

        newGame();
        swamps(5);
        choices(me).modes.add("Khans");
        cast(hand("Palace Siege", me));
        final int library = count(me, ZoneType.Library);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("Khans: mill two", library - 2, count(me, ZoneType.Library));
        AssertJUnit.assertEquals("then a creature card back", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(20, opp.getLife());
    }

    @Test
    public void perilousPredicament() {
        newGame();
        swamps(3);
        final Card thopter = creature("Ornithopter", opp);
        creature("Woolly Thoctar", opp);
        creature("Carrion Feeder", opp);
        cast(hand("Perilous Predicament", me));
        AssertJUnit.assertTrue("the artifact creature", in(thopter, ZoneType.Graveyard));
        AssertJUnit.assertEquals("and one nonartifact creature", 1, creatures(opp));
    }

    @Test
    public void pestilence() {
        newGame();
        swamps(1);
        final Card pest = enter("Pestilence", me);
        final Card thopter = creature("Ornithopter", opp);
        activate(pest, "1 damage");
        AssertJUnit.assertEquals(19, me.getLife());
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals(1, current(thopter).getDamage());
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("a creature is in play", in(pest, ZoneType.Battlefield));
        kill(thopter);
        playUntil(opp, PhaseType.CLEANUP);
        AssertJUnit.assertTrue("no creatures at the end step", in(pest, ZoneType.Graveyard));
    }

    @Test
    public void phyrexianNegator() {
        newGame();
        final List<Card> lands = swamps(3);
        final Card negator = creature("Phyrexian Negator", me);
        AssertJUnit.assertTrue(negator.hasKeyword(Keyword.TRAMPLE));
        choices(me).prefer = c -> lands.contains(c);
        damage(creature("Carrion Feeder", opp), negator, 2, false);
        AssertJUnit.assertEquals("sacrificed two permanents", 2, me.getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertTrue(in(negator, ZoneType.Battlefield));
        // Ward: the opponent's spell is countered unless they sacrifice a permanent
        lands("Swamp", 3, opp);
        choices(opp).payUnless = false;
        put(opp, hand("Poison the Cup", opp).getFirstSpellAbility(), targeting(current(negator)));
        resolve();
        AssertJUnit.assertTrue("countered", in(negator, ZoneType.Battlefield));
        untapAll(opp);
        choices(opp).payUnless = true;
        put(opp, hand("Poison the Cup", opp).getFirstSpellAbility(), targeting(current(negator)));
        resolve();
        AssertJUnit.assertEquals("paid the ward", 3, opp.getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertTrue(in(negator, ZoneType.Graveyard));
    }

    @Test
    public void piperOfTheSwarm() {
        newGame();
        swamps(2);
        final Card piper = creature("Piper of the Swarm", me);
        activate(piper, "Rat token");
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card rat = tokens(me).get(0);
        AssertJUnit.assertTrue(rat.getType().hasCreatureType("Rat") && rat.isBlack() && rat.getNetPower() == 1);
        AssertJUnit.assertFalse("can't block", canBlock(rat));
        AssertJUnit.assertTrue("Rats you control have menace", rat.hasKeyword(Keyword.MENACE));
        AssertJUnit.assertFalse(current(piper).hasKeyword(Keyword.MENACE));
        addToken("b_1_1_rat_noblock", me);
        addToken("b_1_1_rat_noblock", me);
        swamps(4);
        untapAll(me);
        final Card target = creature("Woolly Thoctar", opp);
        activate(piper, "Gain control", target);
        AssertJUnit.assertEquals(me, current(target).getController());
        AssertJUnit.assertEquals("three Rats sacrificed", 0, tokens(me).size());
    }

    @Test
    public void pitilessCarnage() {
        newGame();
        swamps(4);
        final Card a = addToken("w_0_1_goat", me);
        final Card b = addToken("w_0_1_goat", me);
        choices(me).prefer = c -> c == a || c == b;
        cast(hand("Pitiless Carnage", me));
        AssertJUnit.assertTrue(tokens(me).isEmpty());
        AssertJUnit.assertEquals("drew two", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(4, me.getCardsIn(ZoneType.Battlefield).size());

        newGame();
        swamps(3);
        final Card plotted = hand("Pitiless Carnage", me);
        cast(me, alternative(plotted, SpellAbility::isPlotting), null);
        AssertJUnit.assertTrue("plotted for 1BB", in(plotted, ZoneType.Exile) && current(plotted).isPlotted());
        playUntil(me, PhaseType.MAIN1);
        me.getCardsIn(ZoneType.Battlefield).forEach(c -> c.setTapped(true));
        choices(me).prefer = c -> false;
        cast(me, alternative(me, plotted, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue("cast without paying its mana cost", in(plotted, ZoneType.Graveyard));
    }

    @Test
    public void pitilessHorde() {
        newGame();
        swamps(4);
        final Card horde = hand("Pitiless Horde", me);
        cast(me, alternative(horde, SpellAbility::isDash), null);
        AssertJUnit.assertTrue("dash: haste", current(horde).hasKeyword(Keyword.HASTE));
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("returned at the end step", in(horde, ZoneType.Hand));

        newGame();
        creature("Pitiless Horde", me);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(18, me.getLife());
    }

    @Test
    public void plungeIntoDarkness() {
        newGame();
        swamps(2);
        final Card a = addToken("w_0_1_goat", me);
        final Card b = addToken("w_0_1_goat", me);
        final Card keep = creature("Carrion Feeder", me);
        choices(me).prefer = c -> c == a || c == b;
        choices(me).number = 3;
        choices(me).payUnless = true;
        final int library = count(me, ZoneType.Library);
        cast(hand("Plunge into Darkness", me));
        AssertJUnit.assertTrue(tokens(me).isEmpty() && in(keep, ZoneType.Battlefield));
        AssertJUnit.assertEquals("gain 8, pay 3", 25, me.getLife());
        AssertJUnit.assertEquals(library - 3, count(me, ZoneType.Library));
        AssertJUnit.assertEquals("one of them into the hand", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("the rest exiled", 2, count(me, ZoneType.Exile));
    }

    @Test
    public void poisonTheCup() {
        newGame();
        swamps(3);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Poison the Cup", me), t);
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard));
        AssertJUnit.assertEquals("no scry from the hand", 0, me.getScryThisTurn());

        newGame();
        swamps(2);
        final Card cup = hand("Poison the Cup", me);
        cast(me, current(cup).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow(), null);
        AssertJUnit.assertTrue(in(cup, ZoneType.Exile) && current(cup).isForetold());
        playUntil(me, PhaseType.MAIN1);
        final Card t2 = creature("Woolly Thoctar", opp);
        cast(me, alternative(cup, SpellAbility::isForetold), targeting(t2));
        AssertJUnit.assertTrue(in(t2, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cast from exile: scry 2", 1, me.getScryThisTurn());
    }

    @Test
    public void priestOfTheBloodRite() {
        newGame();
        creature("Priest of the Blood Rite", me);
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card demon = tokens(me).get(0);
        AssertJUnit.assertTrue(demon.getType().hasCreatureType("Demon") && demon.isBlack() && demon.getNetPower() == 5
                && demon.getNetToughness() == 5 && demon.hasKeyword(Keyword.FLYING));
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(17, me.getLife());
    }

    @Test
    public void pulseTracker() {
        newGame();
        final Card tracker = creature("Pulse Tracker", me);
        AssertJUnit.assertFalse(tracker.hasKeyword(Keyword.MENACE));
        opp.setLife(10, null);
        resolve();
        AssertJUnit.assertTrue("menace at 10 or less life", current(tracker).hasKeyword(Keyword.MENACE));
        attack(me, null, current(tracker));
        AssertJUnit.assertEquals("1 for attacking, 1 combat damage", 8, opp.getLife());
    }

    @Test
    public void putridGoblin() {
        newGame();
        final Card goblin = creature("Putrid Goblin", me);
        kill(goblin);
        AssertJUnit.assertTrue("persist", in(goblin, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, m1m1(current(goblin)));
        AssertJUnit.assertEquals(1, current(goblin).getNetPower());
        kill(goblin);
        AssertJUnit.assertTrue(in(goblin, ZoneType.Graveyard));
    }

    @Test
    public void qarsiSadist() {
        newGame();
        final Card fodder = addToken("w_0_1_goat", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> c == fodder;
        choices(me).targets = List.of(opp);
        enter("Qarsi Sadist", me);
        AssertJUnit.assertTrue(tokens(me).isEmpty());
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
    }

    @Test
    public void queensAgent() {
        newGame();
        library(me, "Swamp");
        final Card agent = creature("Queens Agent", me);
        AssertJUnit.assertTrue("explore: a land to the hand", named(me.getCardsIn(ZoneType.Hand).get(0), "Swamp"));
        AssertJUnit.assertEquals(0, p1p1(current(agent)));
        library(me, "Woolly Thoctar");
        final Card agent2 = creature("Queens Agent", me);
        AssertJUnit.assertEquals("explore: a nonland gives a counter", 1, p1p1(current(agent2)));
        AssertJUnit.assertTrue(agent.hasKeyword(Keyword.LIFELINK));
        damage(current(agent), opp, 3, false);
        AssertJUnit.assertEquals(23, me.getLife());
    }

    @Test
    public void raidersSpoils() {
        newGame();
        enter("Raiders Spoils", me);
        final Card feeder = creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(2, current(feeder).getNetPower());
        AssertJUnit.assertEquals(1, current(feeder).getNetToughness());
        choices(me).confirm = true;
        damage(current(feeder), opp, 2, true);
        AssertJUnit.assertEquals("paid 1 life", 19, me.getLife());
        AssertJUnit.assertEquals("drew a card", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void rancidRats() {
        newGame();
        final Card rats = creature("Rancid Rats", me);
        final Card big = creature("Woolly Thoctar", opp);
        final Card small = creature("Pulse Tracker", opp);
        AssertJUnit.assertFalse("skulk", forge.game.combat.CombatUtil.canBlock(rats, big));
        AssertJUnit.assertTrue(forge.game.combat.CombatUtil.canBlock(rats, small));
        damage(rats, big, 1, true);
        AssertJUnit.assertTrue("deathtouch", in(big, ZoneType.Graveyard));
    }

    @Test
    public void ransackTheLab() {
        newGame();
        swamps(2);
        final int library = count(me, ZoneType.Library);
        cast(hand("Ransack the Lab", me));
        AssertJUnit.assertEquals(library - 3, count(me, ZoneType.Library));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("the rest into the graveyard", 3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void ravenousRats() {
        newGame();
        AssertJUnit.assertEquals("up to ten in a deck", Integer.valueOf(10),
                DeckFormat.canHaveSpecificNumberInDeck(StaticData.instance().getCommonCards().getCard("Ravenous Rats", "TBLD")));
        final Card c = hand("Woolly Thoctar", opp);
        choices(me).targets = List.of(opp);
        enter("Ravenous Rats", me);
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertTrue("discarded, so no Rat", tokens(me).isEmpty());
        enter("Ravenous Rats", me);
        AssertJUnit.assertEquals("couldn't discard: a Rat", 1, tokens(me).size());
        final Card rat = tokens(me).get(0);
        AssertJUnit.assertTrue(rat.getType().hasCreatureType("Rat") && rat.isBlack());
        rat.setSickness(false);
        AssertJUnit.assertFalse(canBlock(rat));
    }

    @Test
    public void ravensCrime() {
        newGame();
        swamps(1);
        hand("Woolly Thoctar", opp);
        hand("Woolly Thoctar", opp);
        final Card crime = hand("Ravens Crime", me);
        cast(crime, opp);
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Hand));
        untapAll(me);
        final Card land = hand("Swamp", me);
        cast(me, withOptionalCosts(current(crime)), targeting(opp));
        AssertJUnit.assertEquals("retrace from the graveyard", 0, count(opp, ZoneType.Hand));
        AssertJUnit.assertTrue("discarding a land", in(land, ZoneType.Graveyard));
    }

    @Test
    public void reapersTalisman() {
        newGame();
        swamps(2);
        final Card talisman = enter("Reapers Talisman", me);
        final Card rats = creature("Carrion Feeder", me);
        activate(talisman, "Equip", rats);
        AssertJUnit.assertTrue(current(talisman).isAttachedToEntity(current(rats)));
        AssertJUnit.assertFalse(current(rats).hasKeyword(Keyword.DEATHTOUCH));
        attack(me, null, current(rats));
        AssertJUnit.assertTrue("deathtouch this turn", current(rats).hasKeyword(Keyword.DEATHTOUCH));
        AssertJUnit.assertEquals("alone: lose 2, and 1 combat damage", 17, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
        // Not alone: no drain
        newGame();
        swamps(2);
        final Card t2 = enter("Reapers Talisman", me);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        activate(t2, "Equip", a);
        attack(me, null, current(a), current(b));
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals(20, me.getLife());
    }

    @Test
    public void reapingTheGraves() {
        newGame();
        swamps(3);
        grave("Woolly Thoctar", me);
        grave("Carrion Feeder", me);
        hand("Woolly Thoctar", opp);
        cast(hand("Ravens Crime", me), opp);
        cast(hand("Reaping the Graves", me));
        AssertJUnit.assertEquals("storm: the copy returns the other", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(me.getCardsIn(ZoneType.Hand).stream().allMatch(Card::isCreature));
    }

    @Test
    public void reaverDrone() {
        newGame();
        final Card drone = creature("Reaver Drone", me);
        AssertJUnit.assertTrue("devoid", drone.isColorless());
        damage(drone, opp, 2, true);
        AssertJUnit.assertEquals("ingest: the top two", 2, count(opp, ZoneType.Exile));
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(19, me.getLife());
        creature("Ornithopter", me);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("another colorless creature", 19, me.getLife());
        kill(drone);
        AssertJUnit.assertTrue(in(drone, ZoneType.Graveyard));
        activate(current(drone), "Return");
        AssertJUnit.assertTrue(in(drone, ZoneType.Hand));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Exile));
        AssertJUnit.assertEquals(2, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void returnUponTheTide() {
        newGame();
        swamps(6);
        final Card elf = grave("Arbor Elf", me);
        cast(hand("Return Upon the Tide", me), elf);
        AssertJUnit.assertTrue(in(elf, ZoneType.Battlefield));
        AssertJUnit.assertEquals("an Elf: two Elf Warriors", 2, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).stream().allMatch(t -> t.getType().hasCreatureType("Warrior") && t.isGreen()));

        newGame();
        swamps(2);
        final Card tide = hand("Return Upon the Tide", me);
        cast(me, current(tide).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow(), null);
        playUntil(me, PhaseType.MAIN1);
        me.getCardsIn(ZoneType.Battlefield).stream().skip(1).forEach(c -> c.setTapped(true));
        final Card back = grave("Woolly Thoctar", me);
        cast(me, alternative(tide, SpellAbility::isForetold), targeting(back));
        AssertJUnit.assertTrue("foretold for B", in(back, ZoneType.Battlefield));
        AssertJUnit.assertTrue("not an Elf", tokens(me).isEmpty());
    }

    @Test
    public void revengeOfTheRats() {
        newGame();
        swamps(4);
        for (int i = 0; i < 3; i++) {
            grave("Woolly Thoctar", me);
        }
        grave("Swamp", me);
        final Card rev = hand("Revenge of the Rats", me);
        cast(rev);
        AssertJUnit.assertEquals(3, tokens(me).size());
        final Card rat = tokens(me).get(0);
        rat.setSickness(false);
        AssertJUnit.assertTrue(rat.getType().hasCreatureType("Rat") && !canBlock(rat));
        untapAll(me);
        cast(me, alternative(rev, SpellAbility::isFlashback), null);
        AssertJUnit.assertEquals("flashback", 6, tokens(me).size());
        AssertJUnit.assertTrue(in(rev, ZoneType.Exile));
    }

    @Test
    public void rotHulk() {
        newGame();
        final Card a = grave("Putrid Goblin", me);
        final Card b = grave("Sightless Ghoul", me);
        final Card c = grave("Carrion Feeder", me);
        final Card notZombie = grave("Woolly Thoctar", me);
        choices(me).targets = List.of(a, b, c, notZombie);
        final Card hulk = creature("Rot Hulk", me);
        AssertJUnit.assertTrue(hulk.hasKeyword(Keyword.MENACE));
        AssertJUnit.assertTrue("three Zombies", in(a, ZoneType.Battlefield) && in(b, ZoneType.Battlefield) && in(c, ZoneType.Battlefield));
        AssertJUnit.assertTrue(in(notZombie, ZoneType.Graveyard));
        kill(hulk);
        AssertJUnit.assertTrue("exiled instead", in(hulk, ZoneType.Exile));
    }

    @Test
    public void salvageSlasher() {
        newGame();
        swamps(2);
        grave("Ornithopter", me);
        grave("Ornithopter", me);
        grave("Woolly Thoctar", me);
        final Card slasher = hand("Salvage Slasher", me);
        cast(me, alternative(slasher, SpellAbility::isDash), null);
        AssertJUnit.assertEquals("+1/+0 for each artifact card", 3, current(slasher).getNetPower());
        AssertJUnit.assertEquals(2, current(slasher).getNetToughness());
        AssertJUnit.assertTrue("dash: haste", current(slasher).hasKeyword(Keyword.HASTE));
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("returned at the end step", in(slasher, ZoneType.Hand));
    }

    @Test
    public void sanctumSeeker() {
        newGame();
        final Card seeker = creature("Sanctum Seeker", me);
        final Card vampire = creature("Pulse Tracker", me);
        final Card other = creature("Rancid Rats", me);
        attack(me, null, seeker, vampire, other);
        // Two Vampires attacking drain 2, Pulse Tracker 1, combat damage 3 + 1 + 1
        AssertJUnit.assertEquals(12, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
    }

    @Test
    public void schemingSymmetry() {
        newGame();
        swamps(1);
        addCardToZone("Swamp", me, ZoneType.Library);
        addCardToZone("Island", opp, ZoneType.Library);
        choices(me).prefer = c -> named(c, "Swamp");
        choices(opp).prefer = c -> named(c, "Island");
        cast(hand("Scheming Symmetry", me), me, opp);
        AssertJUnit.assertEquals("Swamp", me.getCardsIn(ZoneType.Library).get(0).getName());
        AssertJUnit.assertEquals("Island", opp.getCardsIn(ZoneType.Library).get(0).getName());
    }

    @Test
    public void scionOfTheSwarm() {
        newGame();
        final Card scion = creature("Scion of the Swarm", me);
        AssertJUnit.assertTrue(scion.hasKeyword(Keyword.FLYING));
        choices(me).targets = List.of(scion);
        me.gainLife(2, null, null);
        resolve();
        AssertJUnit.assertEquals(1, p1p1(current(scion)));
    }

    @Test
    public void scrounge() {
        newGame();
        swamps(5);
        final Card thopter = grave("Ornithopter", opp);
        final Card s = hand("Scrounge", me);
        cast(me, withOptionalCosts(s), targeting(opp));
        AssertJUnit.assertTrue(in(thopter, ZoneType.Battlefield));
        AssertJUnit.assertEquals(me, current(thopter).getController());
        AssertJUnit.assertTrue("buyback", in(s, ZoneType.Hand));
    }

    @Test
    public void serratedScorpion() {
        newGame();
        final Card s = creature("Serrated Scorpion", me);
        kill(s);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
    }

    @Test
    public void shriekingAffliction() {
        newGame();
        enter("Shrieking Affliction", me);
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("no cards in hand", 17, opp.getLife());
        hand("Woolly Thoctar", opp);
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("two cards in hand", 17, opp.getLife());
    }

    @Test
    public void shrivel() {
        newGame();
        swamps(2);
        final Card feeder = creature("Carrion Feeder", opp);
        final Card thopter = creature("Ornithopter", opp);
        final Card big = creature("Woolly Thoctar", opp);
        choices(me).number = 0;
        cast(hand("Shrivel", me));
        AssertJUnit.assertTrue("exiled instead of dying", in(feeder, ZoneType.Exile));
        AssertJUnit.assertEquals(1, current(thopter).getNetToughness());
        AssertJUnit.assertEquals(5, current(big).getNetPower());
        kill(big);
        AssertJUnit.assertTrue("this turn", in(big, ZoneType.Exile));

        newGame();
        swamps(4);
        final Card t2 = creature("Ornithopter", opp);
        choices(me).number = 1;
        cast(hand("Shrivel", me));
        AssertJUnit.assertTrue("replicated once: -2/-2", in(t2, ZoneType.Exile));
    }

    @Test
    public void sibsigMuckdraggers() {
        newGame();
        swamps(1);
        for (int i = 0; i < 8; i++) {
            grave("Swamp", me);
        }
        final Card m = hand("Sibsig Muckdraggers", me);
        cast(m);
        AssertJUnit.assertTrue("delve: 8 exiled", in(m, ZoneType.Battlefield) && count(me, ZoneType.Exile) == 8);

        newGame();
        final Card a = grave("Woolly Thoctar", me);
        final Card b = grave("Carrion Feeder", me);
        final Card c = grave("Rancid Rats", me);
        choices(me).targets = List.of(a, b, c);
        enter("Sibsig Muckdraggers", me);
        AssertJUnit.assertTrue("up to two", in(a, ZoneType.Hand) && in(b, ZoneType.Hand) && in(c, ZoneType.Graveyard));
    }

    @Test
    public void sifterOfSkulls() {
        newGame();
        creature("Sifter of Skulls", me);
        final Card rats = creature("Rancid Rats", me);
        final Card goat = addToken("w_0_1_goat", me);
        kill(goat);
        AssertJUnit.assertTrue("a token dying makes nothing", tokens(me).isEmpty());
        kill(rats);
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card spawn = tokens(me).get(0);
        AssertJUnit.assertTrue(spawn.getType().hasCreatureType("Eldrazi") && spawn.isColorless() && spawn.getNetPower() == 1);
        mana(spawn, null);
        AssertJUnit.assertEquals(1, me.getManaPool().totalMana());
        AssertJUnit.assertTrue("sacrificed", tokens(me).isEmpty());
    }

    @Test
    public void sightlessGhoul() {
        // Its picture is {2}{B}, 3/1; the XML said {1}{B}, 2/1 until 2026-11-28 and a cast with two lands went through
        newGame();
        swamps(2);
        final Card cast = hand("Sightless Ghoul", me);
        AssertJUnit.assertEquals("costs three", 3, cast.getManaCost().getCMC());
        final SpellAbility spell = cast.getFirstSpellAbility();
        spell.setActivatingPlayer(me);
        AssertJUnit.assertFalse("two lands can't pay for it", forge.ai.ComputerUtilMana.canPayManaCost(spell, me, 0, false));
        AssertJUnit.assertFalse("and it isn't cast", forge.ai.ComputerUtil.handlePlayingSpellAbility(me, spell, null));
        AssertJUnit.assertTrue("still in hand", in(cast, ZoneType.Hand));
        swamps(1);
        cast(cast);
        AssertJUnit.assertTrue("cast with three lands", in(cast, ZoneType.Battlefield));
        AssertJUnit.assertEquals("all three tapped", 3, me.getLandsInPlay().stream().filter(Card::isTapped).count());
        AssertJUnit.assertEquals(3, current(cast).getNetPower());
        AssertJUnit.assertEquals(1, current(cast).getNetToughness());

        newGame();
        final Card ghoul = creature("Sightless Ghoul", me);
        AssertJUnit.assertFalse(canBlock(ghoul));
        kill(ghoul);
        AssertJUnit.assertTrue("undying", in(ghoul, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, p1p1(current(ghoul)));
        AssertJUnit.assertEquals(4, current(ghoul).getNetPower());
    }

    @Test
    public void signInBlood() {
        newGame();
        swamps(2);
        cast(hand("Sign in Blood", me), opp);
        AssertJUnit.assertEquals(2, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(18, opp.getLife());
    }

    @Test
    public void silentSkimmer() {
        newGame();
        final Card skimmer = creature("Silent Skimmer", me);
        AssertJUnit.assertTrue(skimmer.isColorless() && skimmer.hasKeyword(Keyword.FLYING));
        attack(me, null, skimmer);
        AssertJUnit.assertEquals(3, count(opp, ZoneType.Exile));
        AssertJUnit.assertEquals(17, opp.getLife());
        addCardToZone("Reaver Drone", me, ZoneType.Library);
        final Card cycler = hand("Silent Skimmer", me);
        choices(me).prefer = c -> named(c, "Reaver Drone");
        activate(cycler, "cycling");
        AssertJUnit.assertTrue("Eldrazicycling", in(cycler, ZoneType.Graveyard));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Reaver Drone"));
        AssertJUnit.assertEquals("two exiled cards to their graveyard", 2, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void skinrender() {
        newGame();
        final Card target = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(target);
        final Card s = creature("Skinrender", me);
        AssertJUnit.assertEquals(3, m1m1(current(target)));
        damage(s, opp, 3, true);
        AssertJUnit.assertEquals("toxic 3", 3, opp.getPoisonCounters());
    }

    @Test
    public void skirsdagHighPriest() {
        newGame();
        final Card priest = creature("Skirsdag High Priest", me);
        creature("Carrion Feeder", me);
        creature("Rancid Rats", me);
        final SpellAbility ab = ability(current(priest), "Demon");
        ab.setActivatingPlayer(me);
        AssertJUnit.assertFalse("morbid", ab.canPlay());
        final Card goat = addToken("w_0_1_goat", opp);
        kill(goat);
        activate(priest, "Demon");
        final Card demon = tokens(me).get(0);
        AssertJUnit.assertTrue(demon.getType().hasCreatureType("Demon") && demon.getNetPower() == 5 && demon.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("tapped three", 3, me.getCreaturesInPlay().stream().filter(Card::isTapped).count());
    }

    @Test
    public void skulduggery() {
        newGame();
        swamps(1);
        final Card mine = creature("Carrion Feeder", me);
        final Card theirs = creature("Woolly Thoctar", opp);
        cast(hand("Skulduggery", me), mine, theirs);
        AssertJUnit.assertEquals(4, current(mine).getNetPower());
        AssertJUnit.assertEquals(2, current(mine).getNetToughness());
        AssertJUnit.assertEquals(3, current(theirs).getNetPower());
        AssertJUnit.assertEquals(3, current(theirs).getNetToughness());
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("this turn", 1, current(mine).getNetPower());
    }
}
