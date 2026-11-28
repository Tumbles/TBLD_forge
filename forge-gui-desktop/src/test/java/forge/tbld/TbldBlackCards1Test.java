package forge.tbld;

import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

/** Black TBLD cards, first batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlackCards1Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Aether Snap", "Alms of the Vein", "Ambitions Cost", "Arbiter of Woe", "Army of the Damned", "Bankrupt in Blood",
            "Bearer of Silence", "Blood Fountain", "Blood Pet", "Blood Scrivener", "Bloodcrazed Paladin", "Bloodgift Demon",
            "Bloodthrone Vampire", "Bone Miser", "Cabal Therapist", "Callous Bloodmage", "Carrion Feeder", "Caustic Crawler",
            "Caustic Tar", "Cleaving Reaper", "Cling to Dust", "Cruel Reality", "Culling Drone", "Daggerdrome Imp", "Dead Drop",
            "Dead Revels", "Debt to the Kami", "Defeat", "Defiant Bloodlord", "Despoiler of Souls", "Diabolic Tutor",
            "Discordant Piper", "Disfigure", "Dispossess", "Divest", "Drakestown Forgotten", "Dread Defiler", "Dread Shade",
            "Dream Devourer", "Dregscape Zombie", "Drossclaw", "Dutiful Return", "Easy Prey", "Endless Ranks of the Dead",
            "Entomber Exarch", "Ereboss Intervention", "Eternal Taskmaster", "Ever After", "Evernight Shade", "Execute");

    private List<Card> swamps(final int n) {
        return lands("Swamp", n, me);
    }

    @Test
    public void aetherSnap() {
        newGame();
        swamps(2);
        final Card wolf = creature("Woolly Thoctar", opp);
        wolf.setCounters(CounterEnumType.P1P1, 2);
        me.setCounters(CounterEnumType.ENERGY, 3);
        opp.setCounters(CounterEnumType.POISON, 1);
        final Card token = addToken("b_2_2_zombie_decayed", opp);
        cast(hand("Aether Snap", me));
        AssertJUnit.assertEquals(0, current(wolf).getNumAllCounters());
        AssertJUnit.assertEquals("a player's counters", 0, me.getNumAllCounters() + opp.getNumAllCounters());
        AssertJUnit.assertFalse("the token was not exiled", in(token, ZoneType.Battlefield));
        AssertJUnit.assertTrue(in(wolf, ZoneType.Battlefield));
    }

    @Test
    public void almsOfTheVein() {
        newGame();
        swamps(3);
        cast(hand("Alms of the Vein", me), opp);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertEquals(23, me.getLife());
        // Madness B: discarded, it is cast for B
        newGame();
        swamps(1);
        choices(me).confirm = true;
        discard(me, hand("Alms of the Vein", me));
        AssertJUnit.assertEquals("madness", 17, opp.getLife());
    }

    @Test
    public void ambitionsCost() {
        newGame();
        swamps(4);
        cast(hand("Ambitions Cost", me));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(17, me.getLife());
        newGame();
        swamps(2);
        final Card c = hand("Ambitions Cost", me);
        activate(c, "Cycling");
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertEquals("cycling draws a card", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void arbiterOfWoe() {
        newGame();
        swamps(6);
        final Card fodder = creature("Woolly Thoctar", me);
        hand("Woolly Thoctar", opp);
        hand("Woolly Thoctar", opp);
        final Card arbiter = hand("Arbiter of Woe", me);
        cast(arbiter);
        AssertJUnit.assertTrue("the additional cost sacrifices a creature", in(fodder, ZoneType.Graveyard));
        AssertJUnit.assertTrue(current(arbiter).hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(22, me.getLife());
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(18, opp.getLife());
    }

    @Test
    public void armyOfTheDamned() {
        newGame();
        swamps(7);
        final Card army = hand("Army of the Damned", me);
        cast(army);
        AssertJUnit.assertEquals(13, tokens(me).size());
        final Card z = tokens(me).get(0);
        AssertJUnit.assertTrue(z.getType().hasCreatureType("Zombie") && z.isBlack() && z.getNetPower() == 2 && z.getNetToughness() == 2);
        AssertJUnit.assertTrue(z.hasKeyword(Keyword.DECAYED));
        z.setSickness(false);
        AssertJUnit.assertFalse("decayed: can't block", canBlock(z));
        untapAll(me);
        cast(me, alternative(army, SpellAbility::isFlashback), null);
        AssertJUnit.assertEquals("flashback", 26, tokens(me).size());
        AssertJUnit.assertTrue(in(army, ZoneType.Exile));
    }

    @Test
    public void bankruptInBlood() {
        newGame();
        swamps(1);
        final Card a = creature("Woolly Thoctar", me);
        final Card b = creature("Woolly Thoctar", me);
        final Card bankrupt = hand("Bankrupt in Blood", me);
        final SpellAbility spell = put(me, bankrupt.getFirstSpellAbility(), null);
        AssertJUnit.assertTrue(in(a, ZoneType.Graveyard) && in(b, ZoneType.Graveyard));
        // Mana Leak can't counter it (its controller has no mana to pay)
        lands("Island", 2, opp);
        put(opp, hand("Mana Leak", opp).getFirstSpellAbility(), targeting(spell));
        resolve();
        AssertJUnit.assertEquals("drew three cards", 3, count(me, ZoneType.Hand));
    }

    @Test
    public void bearerOfSilence() {
        newGame();
        swamps(2);
        final Card victim = creature("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(opp);
        final Card bearer = hand("Bearer of Silence", me);
        put(me, bearer.getFirstSpellAbility(), null);
        lands("Wastes", 1, me); // for the cast trigger's C
        resolve();
        AssertJUnit.assertTrue("paying C makes the opponent sacrifice a creature", in(victim, ZoneType.Graveyard));
        final Card b = current(bearer);
        AssertJUnit.assertTrue("devoid", b.getColor().isColorless());
        AssertJUnit.assertTrue(b.hasKeyword(Keyword.FLYING));
        b.setSickness(false);
        AssertJUnit.assertFalse(canBlock(b));
        // Ingest: combat damage to a player exiles the top two cards of their library
        final int library = count(opp, ZoneType.Library);
        damage(b, opp, 2, true);
        AssertJUnit.assertEquals(library - 2, count(opp, ZoneType.Library));
        AssertJUnit.assertEquals(2, count(opp, ZoneType.Exile));
    }

    @Test
    public void bloodFountain() {
        newGame();
        swamps(3);
        final Card fountain = enter("Blood Fountain", me);
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).getType().hasSubtype("Blood"));
        final Card x = grave("Woolly Thoctar", me);
        final Card y = grave("Arbiter of Woe", me);
        activate(fountain, "Return", x, y);
        AssertJUnit.assertTrue(in(x, ZoneType.Hand) && in(y, ZoneType.Hand));
        AssertJUnit.assertTrue(in(fountain, ZoneType.Graveyard));
    }

    @Test
    public void bloodPet() {
        newGame();
        final Card pet = creature("Blood Pet", me);
        mana(pet, null);
        AssertJUnit.assertEquals(1, me.getManaPool().totalMana());
        AssertJUnit.assertEquals(2, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals(22, me.getLife());
        AssertJUnit.assertTrue(in(pet, ZoneType.Graveyard));
    }

    @Test
    public void bloodScrivener() {
        newGame();
        creature("Blood Scrivener", me);
        me.drawCard();
        resolve();
        AssertJUnit.assertEquals("draws two with an empty hand", 2, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(19, me.getLife());
        me.drawCard();
        resolve();
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(19, me.getLife());
    }

    @Test
    public void bloodcrazedPaladin() {
        newGame();
        for (int i = 0; i < 2; i++) {
            game.getAction().destroy(creature("Woolly Thoctar", opp), null, false, null);
        }
        resolve();
        final Card paladin = enter("Bloodcrazed Paladin", me);
        AssertJUnit.assertEquals("a counter for each creature that died", 2, p1p1(paladin));
        AssertJUnit.assertTrue(paladin.hasFlashSupertype());
        damage(paladin, opp, 3, true);
        AssertJUnit.assertEquals("lifelink", 23, me.getLife());
    }

    @Test
    public void bloodgiftDemon() {
        newGame();
        final Card demon = creature("Bloodgift Demon", me);
        AssertJUnit.assertTrue(demon.hasKeyword(Keyword.FLYING));
        choices(me).targets = List.of(opp);
        final int hand = count(opp, ZoneType.Hand);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("draws two (and one in their own draw step)", hand + 3, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(18, opp.getLife());
    }

    @Test
    public void bloodthroneVampire() {
        newGame();
        swamps(2);
        final Card vampire = creature("Bloodthrone Vampire", me);
        final Card fodder = creature("Woolly Thoctar", me);
        activate(vampire, "+2/+2");
        AssertJUnit.assertTrue(in(fodder, ZoneType.Graveyard));
        AssertJUnit.assertEquals(4, current(vampire).getNetPower());
        AssertJUnit.assertEquals(3, current(vampire).getNetToughness());
        final SpellAbility exile = ability(current(vampire), "Exile target");
        exile.setActivatingPlayer(me);
        AssertJUnit.assertFalse("only if an opponent has 10 or less life", exile.canPlay());
        opp.setLife(10, null);
        activate(current(vampire), "Exile target", current(fodder));
        AssertJUnit.assertTrue(in(fodder, ZoneType.Exile));
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).hasKeyword(Keyword.DECAYED));
    }

    @Test
    public void boneMiser() {
        newGame();
        creature("Bone Miser", me);
        discard(me, hand("Woolly Thoctar", me));
        AssertJUnit.assertEquals("a creature card: two Zombies", 2, tokens(me).size());
        discard(me, hand("Swamp", me));
        AssertJUnit.assertEquals("a land card: BB", 2, me.getManaPool().totalMana());
        discard(me, hand("Execute", me));
        AssertJUnit.assertEquals("a noncreature, nonland card: draw", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void cabalTherapist() {
        newGame();
        final Card therapist = creature("Cabal Therapist", me);
        AssertJUnit.assertTrue(therapist.hasKeyword(Keyword.MENACE));
        final Card fodder = creature("Woolly Thoctar", me);
        hand("Execute", opp);
        hand("Execute", opp);
        final Card keep = hand("Swamp", opp);
        choices(me).confirm = true;
        choices(me).cardName = "Execute";
        choices(me).targets = List.of(opp);
        choices(me).prefer = c -> c == fodder;
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertTrue("a creature was sacrificed (the AI chose)", in(fodder, ZoneType.Graveyard) || in(therapist, ZoneType.Graveyard));
        AssertJUnit.assertEquals("both cards with the name were discarded", 2,
                opp.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getName().equals("Execute")).count());
        AssertJUnit.assertTrue(in(keep, ZoneType.Hand) || in(keep, ZoneType.Battlefield));
    }

    @Test
    public void callousBloodmage() {
        newGame();
        choices(me).modes.add("Pest");
        enter("Callous Bloodmage", me);
        final Card pest = tokens(me).get(0);
        AssertJUnit.assertTrue(pest.getType().hasCreatureType("Pest") && pest.isBlack() && pest.isGreen() && pest.getNetPower() == 1);
        kill(pest);
        AssertJUnit.assertEquals("the Pest's death gains 1 life", 21, me.getLife());
        newGame();
        choices(me).modes.add("Draw a card");
        enter("Callous Bloodmage", me);
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(18, me.getLife());
        newGame();
        grave("Woolly Thoctar", opp);
        grave("Execute", opp);
        choices(me).modes.add("Exile target player");
        choices(me).targets = List.of(opp);
        enter("Callous Bloodmage", me);
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals(2, count(opp, ZoneType.Exile));
    }

    @Test
    public void carrionFeeder() {
        newGame();
        final Card feeder = creature("Carrion Feeder", me);
        AssertJUnit.assertFalse(canBlock(feeder));
        final Card fodder = creature("Woolly Thoctar", me);
        choices(me).prefer = c -> c == fodder;
        activate(feeder, "+1/+1 counter");
        AssertJUnit.assertTrue(in(fodder, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, p1p1(current(feeder)));
    }

    @Test
    public void causticCrawler() {
        newGame();
        creature("Caustic Crawler", me);
        final Card target = creature("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(target);
        enter("Swamp", me);
        AssertJUnit.assertEquals(2, m1m1(current(target)));
    }

    @Test
    public void causticTar() {
        newGame();
        swamps(4);
        final Card land = lands("Swamp", 1, me).get(0);
        cast(hand("Caustic Tar", me), land);
        untapAll(me);
        activate(current(land), "loses 3 life", opp);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertTrue(current(land).isTapped());
    }

    @Test
    public void cleavingReaper() {
        newGame();
        final Card reaper = grave("Cleaving Reaper", me);
        final SpellAbility back = ability(reaper, "Return");
        back.setActivatingPlayer(me);
        AssertJUnit.assertFalse("no Angel or Berserker entered this turn", back.canPlay());
        final Card other = creature("Cleaving Reaper", me);
        AssertJUnit.assertTrue(other.hasKeyword(Keyword.FLYING) && other.hasKeyword(Keyword.TRAMPLE));
        activate(reaper, "Return");
        AssertJUnit.assertTrue(in(reaper, ZoneType.Hand));
        AssertJUnit.assertEquals(17, me.getLife());
    }

    @Test
    public void clingToDust() {
        newGame();
        swamps(2);
        final Card creatureCard = grave("Woolly Thoctar", opp);
        cast(hand("Cling to Dust", me), creatureCard);
        AssertJUnit.assertTrue(in(creatureCard, ZoneType.Exile));
        AssertJUnit.assertEquals("a creature card: gain 3", 23, me.getLife());
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        final Card land = grave("Swamp", opp);
        cast(hand("Cling to Dust", me), land);
        AssertJUnit.assertEquals("otherwise: draw", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(23, me.getLife());
        // Escape 3B, exiling five other cards from the graveyard
        newGame();
        swamps(4);
        final Card cling = grave("Cling to Dust", me);
        for (int i = 0; i < 5; i++) {
            grave("Swamp", me);
        }
        final Card target = grave("Woolly Thoctar", opp);
        cast(me, alternative(cling, SpellAbility::isEscape), targeting(target));
        AssertJUnit.assertTrue(in(target, ZoneType.Exile));
        AssertJUnit.assertEquals("five cards exiled for escape", 5, count(me, ZoneType.Exile));
    }

    @Test
    public void cruelReality() {
        newGame();
        swamps(7);
        final Card victim = creature("Woolly Thoctar", opp);
        cast(hand("Cruel Reality", me), opp);
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("enchanted player sacrifices a creature", in(victim, ZoneType.Graveyard));
        AssertJUnit.assertEquals(20, opp.getLife());
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("no creature: they lose 5 life", 15, opp.getLife());
    }

    @Test
    public void cullingDrone() {
        newGame();
        swamps(2);
        final Card drone = creature("Culling Drone", me);
        AssertJUnit.assertTrue(drone.getColor().isColorless() && drone.hasKeyword(Keyword.INGEST));
        final Card fodder = addToken("w_0_1_goat", me); // the AI sacrifices its worst creature
        activate(drone, "Spawn");
        AssertJUnit.assertTrue(in(fodder, ZoneType.Graveyard));
        final List<Card> spawns = tokens(me).stream().filter(c -> c.getType().hasCreatureType("Spawn")).toList();
        AssertJUnit.assertEquals(2, spawns.size());
        final Card spawn = spawns.get(0);
        AssertJUnit.assertTrue(spawn.getType().hasCreatureType("Spawn") && spawn.getNetPower() == 1);
        mana(spawn, null);
        AssertJUnit.assertEquals("the Spawn's C", 1, me.getManaPool().totalMana());
    }

    @Test
    public void daggerdromeImp() {
        newGame();
        swamps(2);
        final Card imp = grave("Daggerdrome Imp", me);
        activate(imp, "Encore");
        AssertJUnit.assertTrue(in(imp, ZoneType.Exile));
        final Card copy = tokens(me).get(0);
        AssertJUnit.assertEquals("Daggerdrome Imp", copy.getName());
        AssertJUnit.assertTrue(copy.hasKeyword(Keyword.HASTE) && copy.hasKeyword(Keyword.FLYING) && copy.hasKeyword(Keyword.LIFELINK));
        AssertJUnit.assertFalse(canBlock(copy));
        choices(me).attackers = List.of(copy);
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("the copy attacked (lifelink)", me.getLife() > 20);
        AssertJUnit.assertTrue("the copy is sacrificed at the end step", tokens(me).isEmpty());
    }

    @Test
    public void deadDrop() {
        newGame();
        swamps(2);
        for (int i = 0; i < 5; i++) {
            grave("Swamp", me);
        }
        final Card a = creature("Woolly Thoctar", opp);
        final Card b = creature("Woolly Thoctar", opp);
        cast(hand("Dead Drop", me), opp);
        AssertJUnit.assertTrue(in(a, ZoneType.Graveyard) && in(b, ZoneType.Graveyard));
        AssertJUnit.assertEquals("delve exiled five cards", 5, count(me, ZoneType.Exile));
    }

    @Test
    public void deadRevels() {
        newGame();
        swamps(5);
        final Card x = grave("Woolly Thoctar", me);
        final Card y = grave("Arbiter of Woe", me);
        final Card z = grave("Cleaving Reaper", me);
        cast(hand("Dead Revels", me), x, y, z);
        AssertJUnit.assertTrue(in(x, ZoneType.Hand) && in(y, ZoneType.Hand) && in(z, ZoneType.Hand));
        // Spectacle 1B once an opponent lost life this turn
        newGame();
        swamps(2);
        final Card w = grave("Woolly Thoctar", me);
        final Card revels = hand("Dead Revels", me);
        opp.loseLife(1, false, false, null);
        cast(me, alternative(revels, SpellAbility::isSpectacle), targeting(w));
        AssertJUnit.assertTrue(in(w, ZoneType.Hand));
    }

    @Test
    public void debtToTheKami() {
        newGame();
        swamps(3);
        final Card c = creature("Woolly Thoctar", opp);
        choices(me).modes.add("creature");
        cast(hand("Debt to the Kami", me), opp);
        AssertJUnit.assertTrue(in(c, ZoneType.Exile));
        untapAll(me);
        final Card e = enter("Endless Ranks of the Dead", opp);
        choices(me).modes.add("enchantment");
        cast(hand("Debt to the Kami", me), opp);
        AssertJUnit.assertTrue(in(e, ZoneType.Exile));
    }

    @Test
    public void defeat() {
        newGame();
        swamps(2);
        final Card small = creature("Carrion Feeder", opp);
        final Card big = creature("Woolly Thoctar", opp);
        final Card defeat = hand("Defeat", me);
        final SpellAbility sa = defeat.getFirstSpellAbility();
        sa.setActivatingPlayer(me);
        AssertJUnit.assertFalse("power 5", sa.canTarget(big));
        AssertJUnit.assertFalse("my own creature", sa.canTarget(creature("Discordant Piper", me)));
        hand("Execute", opp);
        cast(defeat, small);
        AssertJUnit.assertTrue(in(small, ZoneType.Graveyard));
        AssertJUnit.assertEquals("no raid without an attack", 1, count(opp, ZoneType.Hand));
        // Raid
        newGame();
        swamps(2);
        final Card attacker = creature("Carrion Feeder", me);
        hand("Execute", opp);
        attack(me, null, attacker);
        final Card target = creature("Carrion Feeder", opp);
        cast(hand("Defeat", me), target);
        AssertJUnit.assertTrue(in(target, ZoneType.Graveyard));
        AssertJUnit.assertEquals("raid: the opponent discards", 0, count(opp, ZoneType.Hand));
    }

    @Test
    public void defiantBloodlord() {
        newGame();
        final Card lord = creature("Defiant Bloodlord", me);
        AssertJUnit.assertTrue(lord.hasKeyword(Keyword.FLYING) && lord.hasFlashSupertype());
        me.gainLife(3, lord, null);
        resolve();
        AssertJUnit.assertEquals(17, opp.getLife());
    }

    @Test
    public void despoilerOfSouls() {
        newGame();
        swamps(2);
        final Card despoiler = grave("Despoiler of Souls", me);
        grave("Woolly Thoctar", me);
        grave("Arbiter of Woe", me);
        activate(despoiler, "Return");
        AssertJUnit.assertTrue(in(despoiler, ZoneType.Battlefield));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Exile));
        AssertJUnit.assertFalse(canBlock(current(despoiler)));
    }

    @Test
    public void diabolicTutor() {
        newGame();
        swamps(3);
        library(me, "Execute");
        final Card wanted = me.getCardsIn(ZoneType.Library).stream().filter(c -> c.getName().equals("Execute")).findFirst().orElseThrow();
        choices(me).prefer = c -> c == wanted;
        cast(hand("Diabolic Tutor", me));
        AssertJUnit.assertTrue(in(wanted, ZoneType.Hand));
    }

    @Test
    public void discordantPiper() {
        newGame();
        discard(me, hand("Discordant Piper", me));
        AssertJUnit.assertEquals("put into the graveyard from the hand", 1, tokens(me).size());
        final Card goat = tokens(me).get(0);
        AssertJUnit.assertTrue(goat.getType().hasCreatureType("Goat") && goat.isWhite() && goat.getNetPower() == 0 && goat.getNetToughness() == 1);
        kill(creature("Discordant Piper", me));
        AssertJUnit.assertEquals("dies", 2, tokens(me).size());
    }

    @Test
    public void disfigure() {
        newGame();
        swamps(8);
        final Card a = creature("Woolly Thoctar", opp);
        cast(hand("Disfigure", me), a); // 6/4
        AssertJUnit.assertEquals(4, current(a).getNetPower());
        AssertJUnit.assertEquals(2, current(a).getNetToughness());
        final Card b = creature("Woolly Thoctar", opp);
        final Card kicked = hand("Disfigure", me); // -4/-4 kills a 6/4
        cast(me, withOptionalCosts(kicked), targeting(b));
        AssertJUnit.assertTrue("kicked: -4/-4 kills a 5/4", in(b, ZoneType.Graveyard));
    }

    @Test
    public void dispossess() {
        newGame();
        swamps(3);
        grave("Blood Fountain", opp);
        hand("Blood Fountain", opp);
        library(opp, "Blood Fountain");
        final Card other = hand("Execute", opp);
        choices(me).cardName = "Blood Fountain";
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Blood Fountain");
        cast(hand("Dispossess", me), opp);
        AssertJUnit.assertEquals(3, opp.getCardsIn(ZoneType.Exile).stream().filter(c -> c.getName().equals("Blood Fountain")).count());
        AssertJUnit.assertTrue(in(other, ZoneType.Hand));
    }

    @Test
    public void divest() {
        newGame();
        swamps(1);
        final Card c = hand("Woolly Thoctar", opp);
        final Card land = hand("Swamp", opp);
        cast(hand("Divest", me), opp);
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(land, ZoneType.Hand));
    }

    @Test
    public void drakestownForgotten() {
        newGame();
        swamps(1);
        grave("Woolly Thoctar", me);
        grave("Woolly Thoctar", opp);
        grave("Arbiter of Woe", opp);
        grave("Execute", opp);
        final Card d = creature("Drakestown Forgotten", me);
        AssertJUnit.assertEquals(3, p1p1(d));
        final Card target = creature("Woolly Thoctar", opp);
        activate(d, "-1/-1 counter", target);
        AssertJUnit.assertEquals(2, p1p1(current(d)));
        AssertJUnit.assertEquals(1, m1m1(current(target)));
    }

    @Test
    public void dreadDefiler() {
        newGame();
        swamps(3);
        lands("Wastes", 1, me);
        final Card defiler = creature("Dread Defiler", me);
        AssertJUnit.assertTrue(defiler.getColor().isColorless());
        final Card exiled = grave("Arbiter of Woe", me); // power 5
        activate(defiler, "loses life", opp);
        AssertJUnit.assertTrue(in(exiled, ZoneType.Exile));
        AssertJUnit.assertEquals(15, opp.getLife());
    }

    @Test
    public void dreadShade() {
        newGame();
        swamps(2);
        final Card shade = creature("Dread Shade", me);
        activate(shade, "+1/+1");
        activate(current(shade), "+1/+1");
        AssertJUnit.assertEquals(5, current(shade).getNetPower());
        AssertJUnit.assertEquals(5, current(shade).getNetToughness());
    }

    @Test
    public void dreamDevourer() {
        newGame();
        swamps(4);
        final Card devourer = creature("Dream Devourer", me);
        final Card ambitions = hand("Ambitions Cost", me);
        final Card land = hand("Swamp", me);
        resolve();
        AssertJUnit.assertTrue("nonland cards in hand have foretell", current(ambitions).hasKeyword(Keyword.FORETELL));
        AssertJUnit.assertFalse(current(land).hasKeyword(Keyword.FORETELL));
        final SpellAbility foretell = current(ambitions).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow();
        cast(me, foretell, null);
        AssertJUnit.assertTrue(in(ambitions, ZoneType.Exile) && current(ambitions).isForetold());
        AssertJUnit.assertEquals("+2/+0 when you foretell", 3, current(devourer).getNetPower());
        AssertJUnit.assertEquals(2, me.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isLand() && c.isTapped()).count());
        // A later turn: cast it for its mana cost reduced by 2 (3B becomes 1B)
        me.getCardsIn(ZoneType.Battlefield).forEach(c -> c.setTapped(true));
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals(1, current(devourer).getNetPower());
        me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isLand).skip(2).forEach(c -> c.setTapped(true));
        final int hand = count(me, ZoneType.Hand);
        cast(me, alternative(ambitions, SpellAbility::isForetold), null);
        AssertJUnit.assertEquals("cast for 1B", hand + 3, count(me, ZoneType.Hand));
    }

    @Test
    public void dregscapeZombie() {
        newGame();
        final Card zombie = grave("Dregscape Zombie", me);
        final Card cost = grave("Woolly Thoctar", me);
        activate(zombie, "Encore");
        AssertJUnit.assertTrue(in(zombie, ZoneType.Exile) && in(cost, ZoneType.Exile));
        final Card copy = tokens(me).get(0);
        AssertJUnit.assertEquals("Dregscape Zombie", copy.getName());
        AssertJUnit.assertTrue(copy.hasKeyword(Keyword.HASTE));
        AssertJUnit.assertFalse(canBlock(copy));
    }

    @Test
    public void drossclaw() {
        newGame();
        lands("Swamp", 1, me);
        final Card claw = enter("Drossclaw", me);
        final Card germ = tokens(me).get(0);
        AssertJUnit.assertTrue(germ.getType().hasCreatureType("Germ") && germ.isBlack());
        AssertJUnit.assertEquals(current(claw).getAttachedTo(), germ);
        AssertJUnit.assertEquals("+1/+1", 1, germ.getNetPower());
        germ.setSickness(false);
        attack(me, null, germ);
        AssertJUnit.assertEquals("1 for the attack, 1 combat damage", 18, opp.getLife());
        final Card other = creature("Woolly Thoctar", me);
        untapAll(me);
        activate(current(claw), "Equip", other);
        AssertJUnit.assertEquals(current(claw).getAttachedTo(), current(other));
        AssertJUnit.assertEquals("6/4 equipped", 7, current(other).getNetPower());
    }

    @Test
    public void dutifulReturn() {
        newGame();
        swamps(4);
        final Card x = grave("Woolly Thoctar", me);
        final Card y = grave("Arbiter of Woe", me);
        final Card dutiful = hand("Dutiful Return", me);
        cast(dutiful, x, y);
        AssertJUnit.assertTrue(in(x, ZoneType.Hand) && in(y, ZoneType.Hand));
        AssertJUnit.assertTrue("rebound exiles it", in(dutiful, ZoneType.Exile));
        final Card z = grave("Cleaving Reaper", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(z);
        choices(me).prefer = c -> c == dutiful || c == z;
        playUntil(me, PhaseType.UPKEEP);
        resolve();
        AssertJUnit.assertTrue("cast again from exile at the next upkeep", in(z, ZoneType.Hand));
        AssertJUnit.assertTrue(in(dutiful, ZoneType.Graveyard));
    }

    @Test
    public void easyPrey() {
        newGame();
        swamps(2);
        final Card cheap = creature("Carrion Feeder", opp);
        final Card big = creature("Woolly Thoctar", opp);
        final Card prey = hand("Easy Prey", me);
        prey.getFirstSpellAbility().setActivatingPlayer(me);
        AssertJUnit.assertFalse("mana value 3", prey.getFirstSpellAbility().canTarget(big));
        cast(prey, cheap);
        AssertJUnit.assertTrue(in(cheap, ZoneType.Graveyard));
        untapAll(me);
        final Card cycle = hand("Easy Prey", me);
        activate(cycle, "Cycling");
        AssertJUnit.assertTrue(in(cycle, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void endlessRanksOfTheDead() {
        newGame();
        creature("Endless Ranks of the Dead", me);
        creature("Carrion Feeder", me);
        creature("Dregscape Zombie", me);
        creature("Blood Scrivener", me);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("half of three Zombies, rounded up", 2, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).hasKeyword(Keyword.DECAYED));
    }

    @Test
    public void entomberExarch() {
        newGame();
        final Card back = grave("Woolly Thoctar", me);
        choices(me).modes.add("Return target");
        choices(me).targets = List.of(back);
        final Card exarch = enter("Entomber Exarch", me);
        AssertJUnit.assertTrue(exarch.hasFlashSupertype());
        AssertJUnit.assertTrue(in(back, ZoneType.Hand));
        newGame();
        final Card c = hand("Woolly Thoctar", opp);
        final Card land = hand("Swamp", opp);
        choices(me).modes.add("reveals their hand");
        choices(me).targets = List.of(opp);
        choices(me).prefer = x -> x == c;
        enter("Entomber Exarch", me);
        AssertJUnit.assertTrue(in(c, ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(land, ZoneType.Hand));
    }

    @Test
    public void erebossIntervention() {
        newGame();
        swamps(3);
        final Card target = creature("Woolly Thoctar", opp);
        final Card first = hand("Ereboss Intervention", me);
        first.getFirstSpellAbility().setXManaCostPaid(2);
        choices(me).modes.add("-X/-X");
        cast(first, target); // 6/4
        AssertJUnit.assertEquals(4, current(target).getNetPower());
        AssertJUnit.assertEquals(2, current(target).getNetToughness());
        AssertJUnit.assertEquals(22, me.getLife());
        newGame();
        swamps(2);
        final Card a = grave("Woolly Thoctar", opp);
        final Card b = grave("Execute", opp);
        final Card second = hand("Ereboss Intervention", me);
        second.getFirstSpellAbility().setXManaCostPaid(1);
        choices(me).modes.add("Exile up to");
        cast(second, a, b);
        AssertJUnit.assertTrue("twice X = 2 cards", in(a, ZoneType.Exile) && in(b, ZoneType.Exile));
    }

    @Test
    public void eternalTaskmaster() {
        newGame();
        swamps(3);
        final Card taskmaster = enter("Eternal Taskmaster", me);
        AssertJUnit.assertTrue("enters tapped", taskmaster.isTapped());
        taskmaster.setTapped(false);
        taskmaster.setSickness(false);
        final Card back = grave("Woolly Thoctar", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(back);
        attack(me, null, taskmaster);
        AssertJUnit.assertTrue("paid 2B: back to the hand", in(back, ZoneType.Hand));
    }

    @Test
    public void everAfter() {
        newGame();
        swamps(6);
        final Card x = grave("Woolly Thoctar", me);
        final Card y = grave("Carrion Feeder", me);
        final Card ever = hand("Ever After", me);
        cast(ever, x, y);
        for (final Card c : List.of(current(x), current(y))) {
            AssertJUnit.assertTrue(c.isInZone(ZoneType.Battlefield));
            AssertJUnit.assertTrue(c.getName() + " is a black Zombie", c.isBlack() && c.getType().hasCreatureType("Zombie"));
        }
        AssertJUnit.assertTrue("Woolly Thoctar keeps its colors", current(x).isRed());
        AssertJUnit.assertTrue("on the bottom of the library", in(ever, ZoneType.Library));
        AssertJUnit.assertEquals(current(ever), me.getCardsIn(ZoneType.Library).getLast());
    }

    @Test
    public void evernightShade() {
        newGame();
        swamps(1);
        final Card shade = creature("Evernight Shade", me);
        activate(shade, "+1/+1");
        AssertJUnit.assertEquals(2, current(shade).getNetPower());
        kill(shade);
        final Card back = findCardWithName(game, "Evernight Shade");
        AssertJUnit.assertTrue("undying", back.isInZone(ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, p1p1(back));
    }

    @Test
    public void execute() {
        newGame();
        swamps(3);
        final Card white = creature("Adorned Pouncer", opp);
        final Card black = creature("Discordant Piper", opp);
        final Card ex = hand("Execute", me);
        ex.getFirstSpellAbility().setActivatingPlayer(me);
        AssertJUnit.assertTrue(ex.hasFlashSupertype());
        AssertJUnit.assertFalse("not white", ex.getFirstSpellAbility().canTarget(black));
        cast(ex, white);
        AssertJUnit.assertTrue(in(white, ZoneType.Exile));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }
}
