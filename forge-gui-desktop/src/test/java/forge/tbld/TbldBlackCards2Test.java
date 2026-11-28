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

/** Black TBLD cards, second batch: each card's abilities played in an engine game and checked against its text. */
public class TbldBlackCards2Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Extirpate", "Eye Collector", "Facevaulter", "False Cure", "Fell Stinger", "Festering Goblin", "Foul-Tongue Shriek",
            "Fungal Infection", "Geth Thane of Contracts", "Ghastly Demise", "Ghoulraiser", "Glaze Fiend", "Glint-Sleeve Siphoner",
            "Gnarled Scarhide", "Gnawing Vermin", "Go Blank", "Gonti Night Minister", "Grasp of Darkness", "Grave Scrabbler",
            "Gray Merchant of Asphodel", "Grip of Desolation", "Haunted Dead", "Havoc Demon", "Illness in the Ranks",
            "Indulgent Aristocrat", "Infernal Tutor", "Infinite Obliteration", "Ink-Eyes Servant of Oni", "Innocent Blood",
            "Knucklebone Witch", "Lilianas Mastery", "Lilianas Steward", "Lord of the Accursed", "Macabre Waltz",
            "Marauding Blight-Priest", "Mardu Shadowspear", "Markov Dreadknight", "Massacre Girl", "Masterminds Acquisition",
            "Megrim", "Memoricide", "Mob", "Moonglove Winnower", "Moriok Scavenger", "Nim Lasher", "Nullpriest of Oblivion",
            "Oathsworn Vampire", "Okiba-Gang Shinobi", "Ordeal of Erebos", "Orzhov Enforcer");

    private List<Card> swamps(final int n) {
        return lands("Swamp", n, me);
    }

    private long named(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().filter(c -> c.getName().equals(name)).count();
    }

    /** The test's "can't be countered": the opponent's Mana Leak (its controller can't pay) leaves the spell to resolve. */
    private void manaLeak(final SpellAbility spell) {
        lands("Island", 2, opp);
        put(opp, hand("Mana Leak", opp).getFirstSpellAbility(), targeting(spell));
        resolve();
    }

    @Test
    public void extirpate() {
        newGame();
        swamps(1);
        final Card target = grave("Execute", opp);
        hand("Execute", opp);
        library(opp, "Execute");
        final Card other = hand("Swamp", opp);
        final Card basic = grave("Swamp", opp);
        final Card ex = hand("Extirpate", me);
        ex.getFirstSpellAbility().setActivatingPlayer(me);
        AssertJUnit.assertFalse("a basic land card", ex.getFirstSpellAbility().canTarget(basic));
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Execute");
        put(me, ex.getFirstSpellAbility(), targeting(target));
        // Split second: no spells while it is on the stack
        lands("Island", 2, opp);
        final SpellAbility leak = hand("Mana Leak", opp).getFirstSpellAbility();
        leak.setActivatingPlayer(opp);
        AssertJUnit.assertFalse("split second", leak.canPlay());
        resolve();
        AssertJUnit.assertEquals(3, named(opp, ZoneType.Exile, "Execute"));
        AssertJUnit.assertTrue(in(other, ZoneType.Hand) && in(basic, ZoneType.Graveyard));
    }

    @Test
    public void eyeCollector() {
        newGame();
        final Card eye = creature("Eye Collector", me);
        AssertJUnit.assertTrue(eye.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertFalse(canBlock(eye));
        damage(eye, opp, 1, true);
        AssertJUnit.assertEquals("each player mills one", 1, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Graveyard));
    }

    @Test
    public void facevaulter() {
        newGame();
        final Card fv = enter("Facevaulter", me);
        AssertJUnit.assertTrue("haste", fv.hasKeyword(Keyword.HASTE));
        final Card goblin = addToken("br_1_1_goblin", me);
        activate(fv, "+3/+3");
        AssertJUnit.assertTrue(in(goblin, ZoneType.Graveyard) || !in(goblin, ZoneType.Battlefield));
        AssertJUnit.assertEquals(4, current(fv).getNetPower());
        AssertJUnit.assertEquals(4, current(fv).getNetToughness());
        AssertJUnit.assertTrue(current(fv).hasKeyword(Keyword.FLYING));
        addToken("br_1_1_goblin", me);
        final SpellAbility again = ability(current(fv), "+3/+3");
        again.setActivatingPlayer(me);
        AssertJUnit.assertFalse("only once each turn", again.canPlay());
    }

    @Test
    public void falseCure() {
        newGame();
        swamps(1);
        cast(hand("False Cure", me));
        me.gainLife(3, null, null);
        resolve();
        AssertJUnit.assertEquals("gain 3, lose 6", 17, me.getLife());
        opp.gainLife(1, null, null);
        resolve();
        AssertJUnit.assertEquals(19, opp.getLife());
    }

    @Test
    public void fellStinger() {
        newGame();
        final Card fodder = addToken("w_0_1_goat", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> c == fodder;
        choices(me).targets = List.of(me);
        final Card stinger = enter("Fell Stinger", me);
        AssertJUnit.assertTrue(stinger.hasKeyword(Keyword.DEATHTOUCH));
        AssertJUnit.assertFalse("exploited", in(fodder, ZoneType.Battlefield));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(18, me.getLife());
    }

    @Test
    public void festeringGoblin() {
        newGame();
        final Card goblin = creature("Festering Goblin", me);
        final Card target = creature("Woolly Thoctar", opp);
        damage(goblin, target, 1, true);
        AssertJUnit.assertEquals("wither", 1, m1m1(current(target)));
        AssertJUnit.assertEquals(0, current(target).getDamage());
        choices(me).confirm = true;
        choices(me).targets = List.of(target);
        kill(goblin);
        AssertJUnit.assertEquals("dies: a -1/-1 counter", 2, m1m1(current(target)));
    }

    @Test
    public void foulTongueShriek() {
        newGame();
        swamps(1);
        final Card a = creature("Woolly Thoctar", me);
        final Card b = creature("Carrion Feeder", me);
        choices(me).attackers = List.of(a, b);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        cast(hand("Foul-Tongue Shriek", me), opp);
        AssertJUnit.assertEquals("two attackers", 18, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
        newGame();
        swamps(2);
        final Card c = hand("Foul-Tongue Shriek", me);
        activate(c, "Cycling");
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void fungalInfection() {
        newGame();
        swamps(2);
        final Card mine = creature("Carrion Feeder", me);
        final Card target = creature("Woolly Thoctar", opp);
        choices(me).modes.add("+1/+1 counter");
        choices(me).prefer = c -> c == mine;
        cast(hand("Fungal Infection", me), target);
        AssertJUnit.assertEquals(5, current(target).getNetPower());
        AssertJUnit.assertEquals(3, current(target).getNetToughness());
        AssertJUnit.assertEquals("germinate: a counter", 1, p1p1(current(mine)));
        choices(me).modes.add("Saproling");
        cast(hand("Fungal Infection", me), target);
        AssertJUnit.assertEquals("germinate: a Saproling", 1, tokens(me).size());
        final Card sap = tokens(me).get(0);
        AssertJUnit.assertTrue(sap.getType().hasCreatureType("Saproling") && sap.isGreen() && sap.getNetPower() == 1);
    }

    @Test
    public void gethThaneOfContracts() {
        newGame();
        swamps(3);
        final Card geth = creature("Geth Thane of Contracts", me);
        final Card other = creature("Woolly Thoctar", me);
        AssertJUnit.assertEquals(5, current(other).getNetPower());
        AssertJUnit.assertEquals(3, current(other).getNetToughness());
        AssertJUnit.assertEquals(3, current(geth).getNetPower());
        final Card back = grave("Arbiter of Woe", me);
        activate(geth, "Return target", back);
        AssertJUnit.assertTrue(in(back, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, current(back).getCounters(CounterEnumType.FINALITY));
        AssertJUnit.assertTrue(ability(current(geth), "Return target").getRestrictions().isSorcerySpeed());
    }

    @Test
    public void ghastlyDemise() {
        newGame();
        swamps(2);
        final Card target = creature("Woolly Thoctar", opp); // toughness 4
        for (int i = 0; i < 3; i++) {
            grave("Swamp", me);
        }
        cast(hand("Ghastly Demise", me), target);
        AssertJUnit.assertTrue("3 cards in the graveyard", in(target, ZoneType.Battlefield));
        // the first Ghastly Demise is the fourth card
        cast(hand("Ghastly Demise", me), target);
        AssertJUnit.assertTrue(in(target, ZoneType.Graveyard));
        final Card black = creature("Carrion Feeder", opp);
        final SpellAbility sa = hand("Ghastly Demise", me).getFirstSpellAbility();
        sa.setActivatingPlayer(me);
        AssertJUnit.assertFalse("a black creature", sa.canTarget(black));
    }

    @Test
    public void ghoulraiser() {
        newGame();
        final Card zombie = grave("Carrion Feeder", me);
        final Card other = grave("Woolly Thoctar", me);
        final Card g = enter("Ghoulraiser", me);
        AssertJUnit.assertTrue(in(zombie, ZoneType.Hand));
        AssertJUnit.assertTrue(in(other, ZoneType.Graveyard));
        g.setSickness(false);
        AssertJUnit.assertFalse(canBlock(g));
    }

    @Test
    public void glazeFiend() {
        newGame();
        final Card fiend = enter("Glaze Fiend", me);
        AssertJUnit.assertTrue(fiend.hasKeyword(Keyword.FLYING) && fiend.hasKeyword(Keyword.HASTE));
        enter("Blood Fountain", me); // and its Blood token
        AssertJUnit.assertEquals("two artifacts entered", 4, current(fiend).getNetPower());
        AssertJUnit.assertEquals(5, current(fiend).getNetToughness());
    }

    @Test
    public void glintSleeveSiphoner() {
        newGame();
        final Card s = enter("Glint-Sleeve Siphoner", me);
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.MENACE));
        AssertJUnit.assertEquals(1, me.getCounters(CounterEnumType.ENERGY));
        s.setSickness(false);
        attack(me, null, s);
        AssertJUnit.assertEquals("attacking", 2, me.getCounters(CounterEnumType.ENERGY));
        me.setCounters(CounterEnumType.ENERGY, 3);
        choices(me).targets = List.of();
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("paid EEE", 0, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(19, me.getLife());
    }

    @Test
    public void gnarledScarhide() {
        newGame();
        swamps(3);
        final Card scarhide = hand("Gnarled Scarhide", me);
        final Card host = creature("Woolly Thoctar", me);
        cast(me, alternative(scarhide, SpellAbility::isBestow), targeting(host));
        AssertJUnit.assertEquals(current(host), current(scarhide).getAttachedTo());
        AssertJUnit.assertEquals(8, current(host).getNetPower());
        AssertJUnit.assertEquals(5, current(host).getNetToughness());
        AssertJUnit.assertFalse(canBlock(current(host)));
        final Card alone = creature("Gnarled Scarhide", me);
        AssertJUnit.assertFalse(canBlock(alone));
    }

    @Test
    public void gnawingVermin() {
        newGame();
        final Card target = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(opp, target);
        final Card v = enter("Gnawing Vermin", me);
        AssertJUnit.assertEquals("mills two", 2, count(opp, ZoneType.Graveyard));
        kill(v);
        AssertJUnit.assertEquals(5, current(target).getNetPower());
        AssertJUnit.assertEquals(3, current(target).getNetToughness());
    }

    @Test
    public void goBlank() {
        newGame();
        swamps(3);
        hand("Execute", opp);
        hand("Woolly Thoctar", opp);
        final Card kept = hand("Swamp", opp);
        grave("Arbiter of Woe", opp);
        choices(opp).prefer = c -> c != kept;
        cast(hand("Go Blank", me), opp);
        AssertJUnit.assertTrue(in(kept, ZoneType.Hand));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertEquals(3, count(opp, ZoneType.Exile));
        newGame();
        swamps(2);
        activate(hand("Go Blank", me), "Cycling");
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void gontiNightMinister() {
        newGame();
        swamps(1);
        final Card gonti = creature("Gonti Night Minister", me);
        library(opp, "False Cure");
        attack(me, null, gonti);
        AssertJUnit.assertEquals(17, opp.getLife());
        final Card exiled = opp.getCardsIn(ZoneType.Exile).getFirst();
        AssertJUnit.assertEquals("False Cure", exiled.getState(forge.card.CardStateName.Original).getName());
        AssertJUnit.assertTrue("face down", exiled.isFaceDown());
        // I may play it; casting a spell I don't own makes me a Treasure
        cast(me, alternative(me, exiled, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue(in(exiled, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).getType().hasSubtype("Treasure"));
    }

    @Test
    public void graspOfDarkness() {
        newGame();
        swamps(2);
        final Card target = creature("Woolly Thoctar", opp);
        final SpellAbility spell = put(me, hand("Grasp of Darkness", me).getFirstSpellAbility(), targeting(target));
        manaLeak(spell);
        AssertJUnit.assertTrue("-5/-5 (it can't be countered)", in(target, ZoneType.Graveyard));
    }

    @Test
    public void graveScrabbler() {
        newGame();
        swamps(2);
        final Card back = grave("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(back);
        final Card scrabbler = hand("Grave Scrabbler", me);
        discard(me, scrabbler);
        AssertJUnit.assertTrue("cast for its madness cost", in(scrabbler, ZoneType.Battlefield));
        AssertJUnit.assertTrue("returned to its owner's hand", in(back, ZoneType.Hand));
        // Cast from the hand, no return
        newGame();
        swamps(2);
        final Card stays = grave("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(stays);
        cast(hand("Grave Scrabbler", me));
        AssertJUnit.assertTrue(in(stays, ZoneType.Graveyard));
    }

    @Test
    public void grayMerchantOfAsphodel() {
        newGame();
        creature("Dread Shade", me); // BBB
        enter("Gray Merchant of Asphodel", me); // BB
        AssertJUnit.assertEquals(15, opp.getLife());
        AssertJUnit.assertEquals(25, me.getLife());
    }

    @Test
    public void gripOfDesolation() {
        newGame();
        swamps(6);
        final Card target = creature("Woolly Thoctar", opp);
        final Card grip = hand("Grip of Desolation", me);
        resolve();
        AssertJUnit.assertTrue("devoid", current(grip).getColor().isColorless());
        final SpellAbility spell = put(me, grip.getFirstSpellAbility(), targeting(target));
        manaLeak(spell);
        AssertJUnit.assertTrue("can't be countered", in(target, ZoneType.Exile));
        AssertJUnit.assertTrue("rebound", in(grip, ZoneType.Exile));
        final Card land = lands("Swamp", 1, opp).get(0);
        choices(me).confirm = true;
        choices(me).targets = List.of(land);
        choices(me).prefer = c -> c == grip || c == land;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("a land, at the next upkeep", in(land, ZoneType.Exile));
    }

    @Test
    public void hauntedDead() {
        newGame();
        swamps(2);
        final Card dead = enter("Haunted Dead", me);
        final Card spirit = tokens(me).get(0);
        AssertJUnit.assertTrue(spirit.getType().hasCreatureType("Spirit") && spirit.isWhite() && spirit.hasKeyword(Keyword.FLYING));
        kill(dead);
        hand("Swamp", me);
        hand("Woolly Thoctar", me);
        activate(dead, "Return");
        AssertJUnit.assertTrue(in(dead, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(dead).isTapped());
        AssertJUnit.assertEquals("discarded two", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(2, tokens(me).size());
    }

    @Test
    public void havocDemon() {
        newGame();
        final Card demon = creature("Havoc Demon", me);
        AssertJUnit.assertTrue(demon.hasKeyword(Keyword.FLYING));
        final Card w = creature("Woolly Thoctar", opp);
        final Card big = creature("Woolly Thoctar", me);
        big.setCounters(CounterEnumType.P1P1, 2);
        kill(demon);
        AssertJUnit.assertTrue(in(w, ZoneType.Graveyard));
        AssertJUnit.assertEquals(3, current(big).getNetPower());
        AssertJUnit.assertEquals(1, current(big).getNetToughness());
    }

    @Test
    public void illnessInTheRanks() {
        newGame();
        enter("Illness in the Ranks", me);
        final Card z = addToken("b_2_2_zombie_decayed", opp);
        final Card w = creature("Woolly Thoctar", opp);
        resolve();
        AssertJUnit.assertEquals(1, current(z).getNetPower());
        AssertJUnit.assertEquals(1, current(z).getNetToughness());
        AssertJUnit.assertEquals(6, current(w).getNetPower());
    }

    @Test
    public void indulgentAristocrat() {
        newGame();
        lands("Wastes", 2, me);
        final Card a = creature("Indulgent Aristocrat", me);
        final Card b = creature("Indulgent Aristocrat", me);
        final Card goat = addToken("w_0_1_goat", me);
        activate(a, "+1/+1 counter");
        AssertJUnit.assertFalse(in(goat, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, p1p1(current(a)));
        AssertJUnit.assertEquals(1, p1p1(current(b)));
        damage(current(a), opp, 2, true);
        AssertJUnit.assertEquals("lifelink", 22, me.getLife());
    }

    @Test
    public void infernalTutor() {
        newGame();
        swamps(2);
        hand("Execute", me);
        library(me, "Execute");
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Execute");
        cast(hand("Infernal Tutor", me));
        AssertJUnit.assertEquals("a card with the revealed card's name", 2, named(me, ZoneType.Hand, "Execute"));
        // Hellbent: any card
        newGame();
        swamps(2);
        library(me, "Arbiter of Woe");
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Arbiter of Woe");
        cast(hand("Infernal Tutor", me));
        AssertJUnit.assertEquals(1, named(me, ZoneType.Hand, "Arbiter of Woe"));
    }

    @Test
    public void infiniteObliteration() {
        newGame();
        swamps(3);
        grave("Carrion Feeder", opp);
        hand("Carrion Feeder", opp);
        library(opp, "Carrion Feeder");
        final Card other = hand("Woolly Thoctar", opp);
        choices(me).cardName = "Carrion Feeder";
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Carrion Feeder");
        cast(hand("Infinite Obliteration", me), opp);
        AssertJUnit.assertEquals(3, named(opp, ZoneType.Exile, "Carrion Feeder"));
        AssertJUnit.assertTrue(in(other, ZoneType.Hand));
    }

    @Test
    public void inkEyesServantOfOni() {
        newGame();
        swamps(7);
        final Card attacker = creature("Carrion Feeder", me);
        final Card stolen = grave("Woolly Thoctar", opp);
        choices(me).attackers = List.of(attacker);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        final Card ink = hand("Ink-Eyes Servant of Oni", me); // not earlier: the AI would cast it
        activate(ink, "Ninjutsu");
        AssertJUnit.assertTrue("ninjutsu returns the attacker", in(attacker, ZoneType.Hand));
        AssertJUnit.assertTrue(current(ink).isAttacking() && current(ink).isTapped());
        choices(me).confirm = true;
        choices(me).targets = List.of(stolen);
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals(15, opp.getLife());
        AssertJUnit.assertTrue(in(stolen, ZoneType.Battlefield));
        AssertJUnit.assertEquals(me, current(stolen).getController());
        activate(current(ink), "Regenerate");
        kill(ink);
        AssertJUnit.assertTrue("regenerated", in(ink, ZoneType.Battlefield));
    }

    @Test
    public void innocentBlood() {
        newGame();
        swamps(1);
        final Card mine = creature("Woolly Thoctar", me);
        final Card theirs = creature("Woolly Thoctar", opp);
        cast(hand("Innocent Blood", me));
        AssertJUnit.assertTrue(in(mine, ZoneType.Graveyard) && in(theirs, ZoneType.Graveyard));
    }

    @Test
    public void knucklebontWitch() {
        newGame();
        swamps(3);
        final Card witch = creature("Knucklebone Witch", me);
        final Card goblin = creature("Festering Goblin", me);
        choices(me).confirm = false;
        kill(goblin);
        AssertJUnit.assertEquals(1, p1p1(current(witch)));
        activate(current(witch), "Regenerate");
        kill(witch);
        AssertJUnit.assertTrue("regenerated", in(witch, ZoneType.Battlefield));
    }

    @Test
    public void lilianasMastery() {
        newGame();
        final Card feeder = creature("Carrion Feeder", me);
        enter("Lilianas Mastery", me);
        AssertJUnit.assertEquals(4, tokens(me).size());
        final Card z = tokens(me).get(0);
        AssertJUnit.assertTrue(z.hasKeyword(Keyword.DECAYED));
        AssertJUnit.assertEquals(3, z.getNetPower());
        AssertJUnit.assertEquals(3, z.getNetToughness());
        AssertJUnit.assertEquals("Zombies you control", 2, current(feeder).getNetPower());
    }

    @Test
    public void lilianasSteward() {
        newGame();
        final Card steward = creature("Lilianas Steward", me);
        hand("Execute", opp);
        activate(steward, "discards", opp);
        AssertJUnit.assertTrue(in(steward, ZoneType.Graveyard));
        AssertJUnit.assertEquals(0, count(opp, ZoneType.Hand));
    }

    @Test
    public void lordOfTheAccursed() {
        newGame();
        swamps(2);
        final Card lord = creature("Lord of the Accursed", me);
        final Card feeder = creature("Carrion Feeder", me);
        final Card theirs = creature("Carrion Feeder", opp);
        AssertJUnit.assertEquals(2, current(feeder).getNetPower());
        AssertJUnit.assertEquals(1, current(theirs).getNetPower());
        AssertJUnit.assertEquals(2, current(lord).getNetPower());
        activate(lord, "menace");
        AssertJUnit.assertTrue(current(lord).hasKeyword(Keyword.MENACE) && current(feeder).hasKeyword(Keyword.MENACE)
                && current(theirs).hasKeyword(Keyword.MENACE));
    }

    @Test
    public void macabreWaltz() {
        newGame();
        swamps(1);
        final Card x = grave("Woolly Thoctar", me);
        final Card y = grave("Carrion Feeder", me);
        cast(hand("Macabre Waltz", me), x, y);
        AssertJUnit.assertEquals("two returned, one discarded", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(in(x, ZoneType.Hand) != in(y, ZoneType.Hand));
    }

    @Test
    public void maraudingBlightPriest() {
        newGame();
        creature("Marauding Blight-Priest", me);
        me.gainLife(1, null, null);
        resolve();
        AssertJUnit.assertEquals(18, opp.getLife());
    }

    @Test
    public void marduShadowspear() {
        newGame();
        swamps(2);
        final Card spear = hand("Mardu Shadowspear", me);
        cast(me, alternative(spear, SpellAbility::isDash), null);
        AssertJUnit.assertTrue("dash: haste", current(spear).hasKeyword(Keyword.HASTE));
        attack(me, null, current(spear));
        AssertJUnit.assertEquals("1 for attacking, 1 combat damage", 18, opp.getLife());
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("returned at the end step", in(spear, ZoneType.Hand));
    }

    @Test
    public void markovDreadknight() {
        newGame();
        swamps(1);
        final Card knight = creature("Markov Dreadknight", me);
        AssertJUnit.assertTrue(knight.hasKeyword(Keyword.FLYING));
        hand("Swamp", me);
        activate(knight, "two +1/+1");
        AssertJUnit.assertEquals(2, p1p1(current(knight)));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
    }

    @Test
    public void massacreGirl() {
        newGame();
        final Card w = creature("Woolly Thoctar", opp); // 6/4
        for (int i = 0; i < 3; i++) {
            addToken("w_0_1_goat", opp);
        }
        final Card girl = enter("Massacre Girl", me);
        AssertJUnit.assertTrue(girl.hasKeyword(Keyword.MENACE));
        AssertJUnit.assertTrue("-1/-1, then -1/-1 for each of three Goats that died", in(w, ZoneType.Graveyard));
        AssertJUnit.assertEquals(4, current(girl).getNetPower());
        AssertJUnit.assertEquals(4, current(girl).getNetToughness());
    }

    @Test
    public void mastermindsAcquisition() {
        newGame();
        swamps(4);
        library(me, "Execute");
        choices(me).modes.add("Search your library");
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Execute");
        cast(hand("Masterminds Acquisition", me));
        AssertJUnit.assertEquals(1, named(me, ZoneType.Hand, "Execute"));
        newGame();
        swamps(4);
        final Card wish = addCardToZone("Arbiter of Woe", me, ZoneType.Sideboard);
        choices(me).modes.add("outside the game");
        cast(hand("Masterminds Acquisition", me));
        AssertJUnit.assertTrue("from the sideboard", in(wish, ZoneType.Hand));
    }

    @Test
    public void megrim() {
        newGame();
        enter("Megrim", me);
        discard(opp, hand("Execute", opp));
        AssertJUnit.assertEquals(17, opp.getLife());
        discard(me, hand("Execute", me));
        AssertJUnit.assertEquals(20, me.getLife());
    }

    @Test
    public void memoricide() {
        newGame();
        swamps(4);
        grave("Execute", opp);
        hand("Execute", opp);
        library(opp, "Execute");
        final Card other = hand("Woolly Thoctar", opp);
        choices(me).cardName = "Execute";
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Execute");
        cast(hand("Memoricide", me), opp);
        AssertJUnit.assertEquals(3, named(opp, ZoneType.Exile, "Execute"));
        AssertJUnit.assertTrue(in(other, ZoneType.Hand));
    }

    @Test
    public void mob() {
        newGame();
        swamps(2);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        final Card target = creature("Woolly Thoctar", opp);
        playUntil(me, PhaseType.MAIN2); // the AI convokes only after combat
        final Card mob = hand("Mob", me);
        mob.getFirstSpellAbility().setXManaCostPaid(1);
        cast(mob, target);
        AssertJUnit.assertTrue(in(target, ZoneType.Graveyard));
        AssertJUnit.assertTrue("convoke tapped both creatures", current(a).isTapped() && current(b).isTapped());
    }

    @Test
    public void moongloveWinnower() {
        newGame();
        final Card equal = creature("Carrion Feeder", opp);
        final Card target = creature("Woolly Thoctar", opp);
        choices(me).targets = List.of(equal, target);
        final Card w = enter("Moonglove Winnower", me);
        AssertJUnit.assertTrue(w.hasKeyword(Keyword.DEATHTOUCH));
        AssertJUnit.assertTrue(in(target, ZoneType.Graveyard) && in(equal, ZoneType.Battlefield));
        // Elfcycling 2
        swamps(2);
        library(me, "Moonglove Winnower");
        final Card cyc = hand("Moonglove Winnower", me);
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Moonglove Winnower");
        activate(cyc, "cycling");
        AssertJUnit.assertTrue(in(cyc, ZoneType.Graveyard));
        AssertJUnit.assertEquals("found an Elf", 1, named(me, ZoneType.Hand, "Moonglove Winnower"));
    }

    @Test
    public void moriokScavenger() {
        newGame();
        library(me, "Glaze Fiend", "Swamp");
        choices(me).confirm = true;
        choices(me).prefer = c -> c instanceof Card && ((Card) c).getName().equals("Glaze Fiend");
        final Card s = enter("Moriok Scavenger", me);
        AssertJUnit.assertEquals("an artifact creature card back", 1, named(me, ZoneType.Hand, "Glaze Fiend"));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
        library(me, "Glaze Fiend", "Swamp");
        kill(s);
        AssertJUnit.assertEquals("dies: again", 2, named(me, ZoneType.Hand, "Glaze Fiend"));
    }

    @Test
    public void nimLasher() {
        newGame();
        final Card lasher = creature("Nim Lasher", me);
        enter("Blood Fountain", me);
        AssertJUnit.assertEquals("two artifacts", 3, current(lasher).getNetPower());
        AssertJUnit.assertEquals(1, current(lasher).getNetToughness());
        // Escape: B, exile three other cards
        newGame();
        swamps(1);
        final Card l = grave("Nim Lasher", me);
        for (int i = 0; i < 3; i++) {
            grave("Swamp", me);
        }
        cast(me, alternative(l, SpellAbility::isEscape), null);
        AssertJUnit.assertTrue(in(l, ZoneType.Battlefield));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Exile));
    }

    @Test
    public void nullpriestOfOblivion() {
        newGame();
        swamps(3);
        final Card stolen = grave("Woolly Thoctar", opp);
        opp.setLife(10, null);
        choices(me).confirm = true;
        choices(me).targets = List.of(stolen);
        final Card priest = enter("Nullpriest of Oblivion", me);
        AssertJUnit.assertTrue(priest.hasKeyword(Keyword.LIFELINK));
        AssertJUnit.assertTrue(in(stolen, ZoneType.Battlefield));
        AssertJUnit.assertEquals(me, current(stolen).getController());
        AssertJUnit.assertTrue("an opponent has 10 or less life: haste", current(stolen).hasKeyword(Keyword.HASTE));
    }

    @Test
    public void oathswornVampire() {
        newGame();
        swamps(2);
        final Card v = grave("Oathsworn Vampire", me);
        AssertJUnit.assertTrue(alternativesFor(v).isEmpty());
        me.gainLife(1, null, null);
        resolve();
        cast(me, alternative(v, SpellAbility::isSpell), null);
        AssertJUnit.assertTrue(in(v, ZoneType.Battlefield));
        AssertJUnit.assertTrue("enters tapped", current(v).isTapped());
    }

    private List<SpellAbility> alternativesFor(final Card c) {
        return current(c).getAllPossibleAbilities(me, true);
    }

    @Test
    public void okibaGangShinobi() {
        newGame();
        swamps(4);
        final Card attacker = creature("Carrion Feeder", me);
        hand("Execute", opp);
        hand("Execute", opp);
        hand("Execute", opp);
        choices(me).attackers = List.of(attacker);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        final Card ninja = hand("Okiba-Gang Shinobi", me);
        activate(ninja, "Ninjutsu");
        AssertJUnit.assertTrue(in(attacker, ZoneType.Hand));
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals(16, opp.getLife());
        AssertJUnit.assertEquals("discards two", 1, count(opp, ZoneType.Hand));
    }

    @Test
    public void ordealOfErebos() {
        newGame();
        swamps(2);
        final Card c = creature("Woolly Thoctar", me);
        c.setCounters(CounterEnumType.P1P1, 1);
        final Card ordeal = hand("Ordeal of Erebos", me);
        cast(ordeal, c);
        final Card g1 = grave("Arbiter of Woe", opp);
        final Card g2 = grave("Carrion Feeder", me);
        choices(me).targets = List.of(g1, g2);
        attack(me, null, current(c));
        AssertJUnit.assertEquals(2, p1p1(current(c)));
        AssertJUnit.assertTrue(in(ordeal, ZoneType.Battlefield));
        untapAll(me);
        playUntil(me, PhaseType.MAIN1);
        attack(me, null, current(c));
        AssertJUnit.assertEquals(3, p1p1(current(c)));
        AssertJUnit.assertTrue("sacrificed at three counters", in(ordeal, ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(g1, ZoneType.Battlefield) && in(g2, ZoneType.Battlefield));
        AssertJUnit.assertEquals(me, current(g1).getController());
    }

    @Test
    public void orzhovEnforcer() {
        newGame();
        swamps(4);
        final Card e = creature("Orzhov Enforcer", me);
        AssertJUnit.assertTrue(e.hasKeyword(Keyword.DEATHTOUCH));
        choices(me).confirm = true;
        cast(hand("Megrim", me));
        AssertJUnit.assertEquals("extort", 19, opp.getLife());
        AssertJUnit.assertEquals(21, me.getLife());
        kill(e);
        final Card spirit = tokens(me).get(0);
        AssertJUnit.assertTrue("afterlife", spirit.getType().hasCreatureType("Spirit") && spirit.isWhite() && spirit.hasKeyword(Keyword.FLYING));
    }
}
