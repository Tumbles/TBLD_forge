package forge.tbld;

import forge.game.GameObject;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Green TBLD cards, first batch: each card's abilities played in an engine game and checked against its text. */
public class TbldGreenCards1Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Aid from the Cowl", "Alpha Authority", "Ancestral Mask", "Apex Altisaur", "Arachnoform", "Arbor Elf",
            "Arborback Stomper", "Archweaver", "Aspect of Hydra", "Avenger of Zendikar", "Basking Rootwalla",
            "Become Immense", "Birthing Hulk", "Blisterpod", "Blossoming Defense", "Boar Umbra", "Boneyard Wurm",
            "Brood Monitor", "Brutalizer Exarch", "Centaur Battlemaster", "Charging Badger", "Coiling Stalker",
            "Colossification", "Commune with Spirits", "Composer of Spring", "Copperhorn Scout", "Creeping Renaissance",
            "Crush of Wurms", "Cryptolith Rite", "Cultivate", "Deadbridge Goliath", "Deadly Recluse", "Death-Hood Cobra",
            "Defend the Hearth", "Dragonsguard Elite", "Drudge Beetle", "Dubious Challenge", "Eidolon of Blossoms",
            "Elemental Resonance", "Elves of Deep Shadow", "Elvish Branchbender", "Elvish Vanguard", "Elvish Visionary",
            "Emergent Sequence", "Enter the Unknown", "Epic Confrontation", "Eureka", "Explore", "Fallow Earth",
            "Feed the Pack");

    private static final byte COLORLESS = (byte) forge.card.mana.ManaAtom.COLORLESS;

    /** Whether the player's lands can pay the ability's mana cost. */
    private boolean canPay(final SpellAbility sa) {
        sa.setActivatingPlayer(me);
        return forge.ai.ComputerUtilMana.canPayManaCost(sa.getPayCosts(), sa, me, 0, false);
    }

    private List<Card> forests(final int n) {
        return lands("Forest", n, me);
    }

    private static boolean named(final Object c, final String name) {
        return c instanceof Card && ((Card) c).getName().equals(name);
    }

    private static int pt(final Card c) {
        return c.getNetPower() * 100 + c.getNetToughness();
    }

    private boolean canCast(final Card spell, final Player p) {
        final SpellAbility sa = current(spell).getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canPlay();
    }

    private static boolean canTarget(final Card spell, final Player p, final GameObject target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTarget(target);
    }

    private boolean owns(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().anyMatch(c -> named(c, name));
    }

    private long countNamed(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().filter(c -> named(c, name)).count();
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

    /** Whether the ability (an activated ability or a keyword's, such as scavenge) can be played now from where the card is. */
    private boolean canActivate(final Card c, final String text) {
        return current(c).getAllPossibleAbilities(me, true).stream().anyMatch(sa -> sa.getDescription().contains(text));
    }

    private Card top(final Player p) {
        return p.getCardsIn(ZoneType.Library).get(0);
    }

    private Card bottom(final Player p) {
        final List<Card> lib = new ArrayList<>(p.getCardsIn(ZoneType.Library));
        return lib.get(lib.size() - 1);
    }

    @Test
    public void aidFromTheCowl() {
        // Revolt, a permanent card: put onto the battlefield
        newGame();
        enter("Aid from the Cowl", me);
        final Card feeder = creature("Carrion Feeder", me);
        library(me, "Ornithopter");
        kill(feeder);
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("permanent card put onto the battlefield", owns(me, ZoneType.Battlefield, "Ornithopter"));

        // No permanent left the battlefield: nothing happens
        newGame();
        enter("Aid from the Cowl", me);
        creature("Carrion Feeder", me);
        library(me, "Ornithopter");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertFalse("no revolt", owns(me, ZoneType.Battlefield, "Ornithopter"));
        AssertJUnit.assertTrue("not revealed or moved", named(top(me), "Ornithopter"));

        // Revolt, not a permanent card: may go on the bottom
        newGame();
        enter("Aid from the Cowl", me);
        kill(creature("Carrion Feeder", me));
        library(me, "Explore");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("a sorcery card goes on the bottom", named(bottom(me), "Explore"));
        AssertJUnit.assertFalse(named(top(me), "Explore"));

        // Declined: stays on top
        newGame();
        enter("Aid from the Cowl", me);
        kill(creature("Carrion Feeder", me));
        library(me, "Ornithopter");
        choices(me).confirm = false;
        choices(me).prefer = c -> false;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertFalse("may", owns(me, ZoneType.Battlefield, "Ornithopter"));
        AssertJUnit.assertTrue(named(top(me), "Ornithopter"));
    }

    @Test
    public void alphaAuthority() {
        newGame();
        forests(2);
        final Card feeder = creature("Carrion Feeder", me);
        final Card plain = creature("Carrion Feeder", me);
        cast(hand("Alpha Authority", me), feeder);
        AssertJUnit.assertTrue("enchanted creature has hexproof", current(feeder).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertFalse(current(plain).hasKeyword(Keyword.HEXPROOF));
        final Card unsummon = hand("Unsummon", opp);
        AssertJUnit.assertFalse("hexproof: the opponent can't target it", canTarget(unsummon, opp, current(feeder)));
        AssertJUnit.assertTrue(canTarget(unsummon, opp, current(plain)));

        // Can't be blocked by more than one creature
        final Card b1 = creature("Ornithopter", opp);
        final Card b2 = creature("Ornithopter", opp);
        final Combat two = new Combat(me);
        two.addAttacker(current(feeder), opp);
        two.addBlocker(current(feeder), current(b1));
        two.addBlocker(current(feeder), current(b2));
        AssertJUnit.assertNotNull("two blockers", CombatUtil.validateBlocks(two, opp));
        final Combat one = new Combat(me);
        one.addAttacker(current(feeder), opp);
        one.addBlocker(current(feeder), current(b1));
        AssertJUnit.assertNull("one blocker", CombatUtil.validateBlocks(one, opp));
        final Combat free = new Combat(me);
        free.addAttacker(current(plain), opp);
        free.addBlocker(current(plain), current(b1));
        free.addBlocker(current(plain), current(b2));
        AssertJUnit.assertNull("an unenchanted creature can be double-blocked", CombatUtil.validateBlocks(free, opp));
    }

    @Test
    public void ancestralMask() {
        newGame();
        forests(3);
        final Card feeder = creature("Carrion Feeder", me);
        cast(hand("Ancestral Mask", me), feeder);
        AssertJUnit.assertEquals("no other enchantment", 101, pt(current(feeder)));
        enter("Cryptolith Rite", me);
        AssertJUnit.assertEquals("+2/+2 for one other enchantment", 303, pt(current(feeder)));
        enter("Cryptolith Rite", opp);
        AssertJUnit.assertEquals("an opponent's enchantment counts too", 505, pt(current(feeder)));
        enter("Eidolon of Blossoms", opp);
        AssertJUnit.assertEquals("an enchantment creature counts", 707, pt(current(feeder)));
    }

    @Test
    public void apexAltisaur() {
        newGame();
        final Card f1 = creature("Carrion Feeder", opp);
        final Card f2 = creature("Carrion Feeder", opp);
        final Card f3 = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(f1, f2, f3);
        final Card alt = enter("Apex Altisaur", me);
        AssertJUnit.assertEquals(1010, pt(current(alt)));
        AssertJUnit.assertTrue("fights when it enters", in(f1, ZoneType.Graveyard));
        AssertJUnit.assertTrue("enrage: dealt damage by that fight, it fights again", in(f2, ZoneType.Graveyard));
        AssertJUnit.assertTrue("and again", in(f3, ZoneType.Graveyard));
        AssertJUnit.assertEquals("3 damage from three fights", 3, current(alt).getDamage());

        // Up to one, and only creatures you don't control
        newGame();
        final Card mine = creature("Carrion Feeder", me);
        choices(me).targets = List.of(mine);
        final Card alt2 = enter("Apex Altisaur", me);
        AssertJUnit.assertTrue("no fight with its own side", in(mine, ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, current(alt2).getDamage());

        // Enrage on damage outside of a fight
        newGame();
        final Card alt3 = enter("Apex Altisaur", me);
        final Card target = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(target);
        damage(creature("Ornithopter", opp), current(alt3), 2, false);
        AssertJUnit.assertTrue("fights its target when dealt damage", in(target, ZoneType.Graveyard));
    }

    @Test
    public void arachnoform() {
        newGame();
        forests(2);
        final Card feeder = creature("Carrion Feeder", me);
        cast(hand("Arachnoform", me), feeder);
        AssertJUnit.assertEquals("+3/+2", 403, pt(current(feeder)));
        AssertJUnit.assertTrue(current(feeder).hasKeyword(Keyword.REACH));
        AssertJUnit.assertTrue("every creature type", current(feeder).getType().hasSubtype("Goblin"));
        AssertJUnit.assertTrue(current(feeder).getType().hasSubtype("Merfolk"));
        AssertJUnit.assertTrue(current(feeder).getType().hasSubtype("Zombie"));
    }

    @Test
    public void arborElf() {
        newGame();
        final Card forest = forests(1).get(0);
        final Card mountain = lands("Mountain", 1, me).get(0);
        final Card elf = creature("Arbor Elf", me);
        forest.setTapped(true);
        mountain.setTapped(true);
        AssertJUnit.assertFalse("only a Forest", ability(current(elf), "Untap target Forest").canTarget(mountain));
        activate(elf, "Untap target Forest", forest);
        AssertJUnit.assertFalse("untapped the Forest", current(forest).isTapped());
        AssertJUnit.assertTrue("tapped to do it", current(elf).isTapped());
        AssertJUnit.assertTrue("the Mountain stays tapped", current(mountain).isTapped());
    }

    @Test
    public void arborbackStomper() {
        newGame();
        choices(me).modes.add("Gain 5 life");
        final Card s = enter("Arborback Stomper", me);
        AssertJUnit.assertEquals(504, pt(s));
        AssertJUnit.assertTrue(s.hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertEquals(25, me.getLife());
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));

        newGame();
        choices(me).modes.add("EEEEE");
        enter("Arborback Stomper", me);
        AssertJUnit.assertEquals(20, me.getLife());
        AssertJUnit.assertEquals("five energy counters", 5, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void archweaver() {
        newGame();
        final Card a = creature("Archweaver", me);
        AssertJUnit.assertEquals(606, pt(a));
        AssertJUnit.assertTrue(a.hasKeyword(Keyword.REACH));
        AssertJUnit.assertTrue(a.hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertTrue("hexproof while it entered this turn", current(a).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertFalse(canTarget(hand("Unsummon", opp), opp, current(a)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("no longer", current(a).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertTrue(canTarget(hand("Unsummon", opp), opp, current(a)));

        // Flash
        newGame();
        playUntil(opp, PhaseType.MAIN1);
        forests(5);
        AssertJUnit.assertTrue("flash", canCast(hand("Archweaver", me), me));
    }

    @Test
    public void aspectOfHydra() {
        newGame();
        forests(1);
        final Card feeder = creature("Carrion Feeder", me);
        cast(hand("Aspect of Hydra", me), feeder);
        AssertJUnit.assertEquals("no devotion: +1/+1", 202, pt(current(feeder)));

        newGame();
        forests(1);
        creature("Archweaver", me);
        creature("Arbor Elf", me);
        creature("Archweaver", opp);
        final Card feeder2 = creature("Carrion Feeder", me);
        cast(hand("Aspect of Hydra", me), feeder2);
        AssertJUnit.assertEquals("devotion 3 (not the opponent's): +4/+4", 505, pt(current(feeder2)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder2)));

        newGame();
        playUntil(opp, PhaseType.MAIN1);
        forests(1);
        AssertJUnit.assertTrue("flash", canCast(hand("Aspect of Hydra", me), me));
    }

    @Test
    public void avengerOfZendikar() {
        newGame();
        forests(3);
        lands("Forest", 2, opp);
        final Card a = enter("Avenger of Zendikar", me);
        AssertJUnit.assertEquals(505, pt(a));
        AssertJUnit.assertEquals("a Plant for each land I control", 3, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertEquals("Plant", t.getName().replace(" Token", ""));
            AssertJUnit.assertEquals("0/1 green", 1, pt(t));
            AssertJUnit.assertTrue(t.getType().hasSubtype("Plant") && t.getColor().hasGreen());
        }
        choices(me).confirm = true;
        enter("Forest", me);
        for (final Card t : tokens(me)) {
            AssertJUnit.assertEquals("landfall: a +1/+1 counter on each Plant", 1, p1p1(t));
            AssertJUnit.assertEquals(102, pt(t));
        }
        AssertJUnit.assertEquals("not on Avenger", 0, p1p1(current(a)));
        enter("Forest", opp);
        for (final Card t : tokens(me)) {
            AssertJUnit.assertEquals("an opponent's land does not", 1, p1p1(t));
        }
        choices(me).confirm = false;
        enter("Forest", me);
        for (final Card t : tokens(me)) {
            AssertJUnit.assertEquals("may", 1, p1p1(t));
        }
    }

    @Test
    public void baskingRootwalla() {
        newGame();
        forests(4);
        final Card rw = creature("Basking Rootwalla", me);
        AssertJUnit.assertEquals(101, pt(rw));
        activate(rw, "+2/+2");
        AssertJUnit.assertEquals("+2/+2", 303, pt(current(rw)));
        final SpellAbility again = ability(current(rw), "+2/+2");
        again.setActivatingPlayer(me);
        AssertJUnit.assertFalse("only once each turn", again.canPlay());
        AssertJUnit.assertEquals("paid 1G", 2, tapped(me.getCardsIn(ZoneType.Battlefield)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(rw)));

        // Madness 0
        newGame();
        choices(me).confirm = true;
        final Card m = hand("Basking Rootwalla", me);
        discard(me, m);
        AssertJUnit.assertTrue("cast for its madness cost, 0", in(m, ZoneType.Battlefield));

        newGame();
        choices(me).confirm = false;
        final Card m2 = hand("Basking Rootwalla", me);
        discard(me, m2);
        AssertJUnit.assertTrue("or into the graveyard", in(m2, ZoneType.Graveyard));
    }

    @Test
    public void becomeImmense() {
        newGame();
        forests(6);
        final Card feeder = creature("Carrion Feeder", me);
        final Card bi = hand("Become Immense", me);
        AssertJUnit.assertTrue("flash", current(bi).hasFlashSupertype());
        cast(bi, feeder);
        AssertJUnit.assertEquals("+6/+6", 707, pt(current(feeder)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));

        // Delve: five cards in the graveyard pay for 5 of the 6
        newGame();
        forests(1);
        for (int i = 0; i < 5; i++) {
            grave("Woolly Thoctar", me);
        }
        final Card feeder2 = creature("Carrion Feeder", me);
        cast(hand("Become Immense", me), feeder2);
        AssertJUnit.assertEquals("delve", 707, pt(current(feeder2)));
        AssertJUnit.assertEquals("exiled five cards, not the spell", 0, countNamed(me, ZoneType.Graveyard, "Woolly Thoctar"));
        AssertJUnit.assertEquals(5, countNamed(me, ZoneType.Exile, "Woolly Thoctar"));
        AssertJUnit.assertEquals("paid the sixth with the Forest", 1, tapped(me.getCardsIn(ZoneType.Battlefield)));
    }

    @Test
    public void birthingHulk() {
        newGame();
        final Card hulk = creature("Birthing Hulk", me);
        AssertJUnit.assertEquals(504, pt(hulk));
        AssertJUnit.assertTrue("devoid", current(hulk).getColor().isColorless());
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertEquals("two Spawn at my end step", 2, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertTrue(t.getType().hasSubtype("Eldrazi") && t.getType().hasSubtype("Spawn"));
            AssertJUnit.assertEquals(101, pt(t));
            AssertJUnit.assertTrue("colorless", t.getColor().isColorless());
        }
        mana(tokens(me).get(0), null);
        AssertJUnit.assertEquals("sacrifice: add C", 1, me.getManaPool().getAmountOfColor(COLORLESS));
        AssertJUnit.assertEquals(1, tokens(me).size());
        playUntil(opp, PhaseType.END_OF_TURN);
        AssertJUnit.assertEquals("not at the opponent's end step", 1, tokens(me).size());

        // Regenerate
        newGame();
        lands("Wastes", 2, me);
        final Card h2 = creature("Birthing Hulk", me);
        activate(h2, "Regenerate");
        kill(h2);
        AssertJUnit.assertTrue("regenerated", in(h2, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(h2).isTapped());
        AssertJUnit.assertEquals("damage removed", 0, current(h2).getDamage());

        newGame();
        final Card h3 = creature("Birthing Hulk", me);
        kill(h3);
        AssertJUnit.assertTrue("no shield, it dies", in(h3, ZoneType.Graveyard));
    }

    @Test
    public void blisterpod() {
        newGame();
        final Card b = creature("Blisterpod", me);
        AssertJUnit.assertEquals(101, pt(b));
        AssertJUnit.assertTrue("devoid", current(b).getColor().isColorless());
        AssertJUnit.assertTrue(current(b).getType().hasSubtype("Eldrazi"));
        AssertJUnit.assertEquals(0, tokens(me).size());
        kill(b);
        AssertJUnit.assertEquals("a Spawn when it dies", 1, tokens(me).size());
        final Card spawn = tokens(me).get(0);
        AssertJUnit.assertTrue(spawn.getType().hasSubtype("Eldrazi") && spawn.getType().hasSubtype("Spawn"));
        AssertJUnit.assertEquals(101, pt(spawn));
        AssertJUnit.assertTrue(spawn.getColor().isColorless());
        mana(spawn, null);
        AssertJUnit.assertEquals("sacrifice: add C", 1, me.getManaPool().getAmountOfColor(COLORLESS));
    }

    @Test
    public void blossomingDefense() {
        newGame();
        forests(1);
        final Card feeder = creature("Carrion Feeder", me);
        final Card land = hand("Forest", me);
        choices(me).prefer = c -> true;
        final Card bd = hand("Blossoming Defense", me);
        AssertJUnit.assertTrue("flash", current(bd).hasFlashSupertype());
        cast(bd, feeder);
        AssertJUnit.assertEquals("+2/+2", 303, pt(current(feeder)));
        AssertJUnit.assertTrue("hexproof", current(feeder).hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertTrue("a land from the hand", in(land, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(land).isTapped());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));
        AssertJUnit.assertFalse(current(feeder).hasKeyword(Keyword.HEXPROOF));

        // May: no land; only a creature you control
        newGame();
        forests(1);
        final Card feeder2 = creature("Carrion Feeder", me);
        final Card land2 = hand("Forest", me);
        choices(me).prefer = c -> false;
        cast(hand("Blossoming Defense", me), feeder2);
        AssertJUnit.assertTrue("may", in(land2, ZoneType.Hand));
        newGame();
        final Card oppFeeder = creature("Carrion Feeder", opp);
        AssertJUnit.assertFalse(canTarget(hand("Blossoming Defense", me), me, current(oppFeeder)));
    }

    @Test
    public void boarUmbra() {
        newGame();
        forests(3);
        final Card feeder = creature("Carrion Feeder", me);
        final Card umbra = hand("Boar Umbra", me);
        cast(umbra, feeder);
        AssertJUnit.assertEquals("+3/+3", 404, pt(current(feeder)));
        AssertJUnit.assertTrue(current(feeder).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertTrue(current(feeder).hasKeyword(Keyword.HASTE));
        kill(feeder);
        AssertJUnit.assertTrue("umbra armor: it isn't destroyed", in(feeder, ZoneType.Battlefield));
        AssertJUnit.assertTrue("the Aura is", in(umbra, ZoneType.Graveyard));
        AssertJUnit.assertEquals("damage removed", 0, current(feeder).getDamage());
        AssertJUnit.assertEquals(101, pt(current(feeder)));
        kill(feeder);
        AssertJUnit.assertTrue("only once", in(feeder, ZoneType.Graveyard));
    }

    @Test
    public void boneyardWurm() {
        newGame();
        grave("Woolly Thoctar", me);
        grave("Woolly Thoctar", me);
        grave("Explore", me);
        grave("Woolly Thoctar", opp);
        final Card w = creature("Boneyard Wurm", me);
        AssertJUnit.assertEquals("my creature cards in my graveyard", 202, pt(current(w)));
        grave("Ornithopter", me);
        resolve();
        AssertJUnit.assertEquals("an artifact creature card counts", 303, pt(current(w)));
        AssertJUnit.assertTrue("no evasion: even power 0 blocks it", CombatUtil.canBlock(current(w), current(creature("Ornithopter", opp))));
        AssertJUnit.assertFalse(current(w).hasKeyword("Indomitable"));
    }

    @Test
    public void broodMonitor() {
        newGame();
        forests(5);
        final Card bm = hand("Brood Monitor", me);
        cast(bm);
        AssertJUnit.assertTrue(in(bm, ZoneType.Battlefield));
        AssertJUnit.assertEquals(303, pt(current(bm)));
        AssertJUnit.assertTrue("devoid", current(bm).getColor().isColorless());
        AssertJUnit.assertEquals("three Spawn when I cast it", 3, tokens(me).size());
        // Now on the battlefield: one more Spawn each time
        kill(creature("Blisterpod", me));
        AssertJUnit.assertEquals("a Blisterpod makes two Spawn instead of one", 5, tokens(me).size());
        final Card oppPod = creature("Blisterpod", opp);
        kill(oppPod);
        AssertJUnit.assertEquals("only mine", 1, tokens(opp).size());

        newGame();
        final Card pod = creature("Blisterpod", me);
        kill(pod);
        AssertJUnit.assertEquals("without it, one", 1, tokens(me).size());
    }

    @Test
    public void brutalizerExarch() {
        newGame();
        library(me, "Carrion Feeder");
        library(me, "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        choices(me).modes.add("Search your library");
        choices(me).prefer = c -> named(c, "Carrion Feeder");
        final int lib = count(me, ZoneType.Library);
        final Card b = enter("Brutalizer Exarch", me);
        AssertJUnit.assertEquals(303, pt(b));
        AssertJUnit.assertTrue(b.getType().hasSubtype("Cleric"));
        AssertJUnit.assertTrue("on top of the library", named(top(me), "Carrion Feeder"));
        AssertJUnit.assertEquals("not into the hand", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(lib, count(me, ZoneType.Library));

        newGame();
        final Card rite = enter("Cryptolith Rite", opp);
        final Card orn = creature("Ornithopter", opp);
        choices(me).modes.add("Put target noncreature permanent");
        choices(me).targets = List.of(orn, rite);
        enter("Brutalizer Exarch", me);
        AssertJUnit.assertTrue("on the bottom of its owner's library", in(rite, ZoneType.Library));
        AssertJUnit.assertEquals(rite, bottom(opp));
        AssertJUnit.assertTrue("a creature can't be chosen", in(orn, ZoneType.Battlefield));

        newGame();
        final Card land = lands("Forest", 1, opp).get(0);
        choices(me).modes.add("Put target noncreature permanent");
        choices(me).targets = List.of(land);
        enter("Brutalizer Exarch", me);
        AssertJUnit.assertEquals("a land is a noncreature permanent", land, bottom(opp));

        newGame();
        playUntil(opp, PhaseType.MAIN1);
        forests(6);
        AssertJUnit.assertTrue("flash", canCast(hand("Brutalizer Exarch", me), me));
    }

    @Test
    public void centaurBattlemaster() {
        newGame();
        forests(3);
        final Card c = creature("Centaur Battlemaster", me);
        AssertJUnit.assertEquals(303, pt(c));
        AssertJUnit.assertTrue(c.hasKeyword(Keyword.TRAMPLE));
        cast(hand("Blossoming Defense", me), c);
        AssertJUnit.assertEquals("heroic: three +1/+1 counters", 3, p1p1(current(c)));
        cast(hand("Explore", me));
        AssertJUnit.assertEquals("not for a spell that doesn't target it", 3, p1p1(current(c)));
        // The opponent's spell targeting it
        newGame();
        lands("Forest", 1, opp);
        final Card c2 = creature("Centaur Battlemaster", me);
        cast(hand("Aspect of Hydra", opp), c2);
        AssertJUnit.assertEquals("only my spells", 0, p1p1(current(c2)));
    }

    @Test
    public void chargingBadger() {
        newGame();
        final Card b = creature("Charging Badger", me);
        AssertJUnit.assertTrue(b.hasKeyword(Keyword.TRAMPLE));
        attack(me, null, current(b));
        AssertJUnit.assertEquals("frenzy 3: 1 + 3 damage", 16, opp.getLife());
        AssertJUnit.assertEquals(401, pt(current(b)));

        newGame();
        final Card b2 = creature("Charging Badger", me);
        final Card blocker = creature("Ornithopter", opp);
        attack(me, Map.of(blocker, b2), current(b2));
        AssertJUnit.assertEquals("blocked: no bonus (and 1 power doesn't trample over 2 toughness)", 20, opp.getLife());
        AssertJUnit.assertEquals(101, pt(current(b2)));
    }

    @Test
    public void coilingStalker() {
        newGame();
        forests(1);
        final Card attacker = creature("Carrion Feeder", me);
        final Card withCounter = creature("Ornithopter", me);
        withCounter.addCounterInternal(CounterEnumType.P1P1, 1, me, false, null, null);
        final Card other = creature("Arbor Elf", me);
        choices(me).targets = List.of(withCounter, other);
        choices(me).attackers = List.of(attacker);
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        choices(me).attackers = List.of();
        final Card stalker = hand("Coiling Stalker", me); // not earlier: the AI would cast it
        activate(stalker, "Ninjutsu");
        AssertJUnit.assertTrue("ninjutsu returns the attacker", in(attacker, ZoneType.Hand));
        AssertJUnit.assertTrue("tapped and attacking", current(stalker).isAttacking() && current(stalker).isTapped());
        AssertJUnit.assertEquals(201, pt(current(stalker)));
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals("combat damage to a player", 18, opp.getLife());
        AssertJUnit.assertEquals("a creature that has a +1/+1 counter can't be chosen", 1, p1p1(current(withCounter)));
        AssertJUnit.assertEquals("a +1/+1 counter on the other one", 1, p1p1(current(other)));
        AssertJUnit.assertEquals("one counter only", 0, p1p1(current(stalker)));
    }

    @Test
    public void colossification() {
        newGame();
        forests(7);
        final Card feeder = creature("Carrion Feeder", me);
        cast(hand("Colossification", me), feeder);
        AssertJUnit.assertEquals("+20/+20", 2121, pt(current(feeder)));
        AssertJUnit.assertTrue("tapped when it entered", current(feeder).isTapped());

        newGame();
        forests(7);
        final Card oppCreature = creature("Carrion Feeder", opp);
        cast(hand("Colossification", me), oppCreature);
        AssertJUnit.assertEquals("an opponent's creature too", 2121, pt(current(oppCreature)));
        AssertJUnit.assertTrue(current(oppCreature).isTapped());
    }

    @Test
    public void communeWithSpirits() {
        newGame();
        forests(1);
        library(me, "Woolly Thoctar", "Forest", "Cryptolith Rite", "Woolly Thoctar");
        final int lib = count(me, ZoneType.Library);
        choices(me).prefer = c -> named(c, "Cryptolith Rite");
        cast(hand("Commune with Spirits", me));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("the enchantment card", named(me.getCardsIn(ZoneType.Hand).get(0), "Cryptolith Rite"));
        AssertJUnit.assertEquals("the other three went to the bottom", lib - 1, count(me, ZoneType.Library));
        final List<Card> libList = new ArrayList<>(me.getCardsIn(ZoneType.Library));
        AssertJUnit.assertEquals(1, libList.subList(libList.size() - 3, libList.size()).stream().filter(c -> named(c, "Forest")).count());
        AssertJUnit.assertEquals("top four cards only", 0, libList.subList(0, libList.size() - 3).stream().filter(c -> named(c, "Forest")).count());

        // A land card
        newGame();
        forests(1);
        library(me, "Forest", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        choices(me).prefer = c -> true;
        cast(hand("Commune with Spirits", me));
        AssertJUnit.assertTrue("a land card", named(me.getCardsIn(ZoneType.Hand).get(0), "Forest"));

        // Nothing to take: may, and only an enchantment or a land
        newGame();
        forests(1);
        library(me, "Forest", "Cryptolith Rite", "Woolly Thoctar", "Woolly Thoctar");
        choices(me).prefer = c -> false;
        cast(hand("Commune with Spirits", me));
        AssertJUnit.assertEquals("may", 0, count(me, ZoneType.Hand));
        newGame();
        forests(1);
        library(me, "Ornithopter", "Explore", "Woolly Thoctar", "Carrion Feeder");
        choices(me).prefer = c -> true;
        cast(hand("Commune with Spirits", me));
        AssertJUnit.assertEquals("neither an enchantment nor a land among them", 0, count(me, ZoneType.Hand));
    }

    @Test
    public void composerOfSpring() {
        newGame();
        creature("Composer of Spring", me);
        final Card land = hand("Forest", me);
        final Card thoctar = hand("Woolly Thoctar", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        enter("Cryptolith Rite", me);
        AssertJUnit.assertTrue("a land from the hand", in(land, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(land).isTapped());
        AssertJUnit.assertTrue("not a creature, under six enchantments", in(thoctar, ZoneType.Hand));

        newGame();
        creature("Composer of Spring", me);
        final Card land2 = hand("Forest", me);
        choices(me).confirm = false;
        choices(me).prefer = c -> true;
        enter("Cryptolith Rite", me);
        AssertJUnit.assertTrue("may", in(land2, ZoneType.Hand));
        newGame();
        creature("Composer of Spring", me);
        final Card land3 = hand("Forest", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        enter("Cryptolith Rite", opp);
        AssertJUnit.assertTrue("only under my control", in(land3, ZoneType.Hand));

        // Six or more enchantments: a creature or a land
        newGame();
        creature("Composer of Spring", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        for (int i = 0; i < 5; i++) {
            enter("Cryptolith Rite", me);
        }
        final Card thoctar2 = hand("Woolly Thoctar", me);
        enter("Cryptolith Rite", me);
        AssertJUnit.assertTrue("six enchantments: a creature card", in(thoctar2, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tapped", current(thoctar2).isTapped());
    }

    @Test
    public void copperhornScout() {
        newGame();
        final Card scout = creature("Copperhorn Scout", me);
        final Card mine = creature("Woolly Thoctar", me);
        final Card theirs = creature("Woolly Thoctar", opp);
        mine.setTapped(true);
        theirs.setTapped(true);
        attack(me, null, current(scout));
        AssertJUnit.assertFalse("untaps each other creature I control", current(mine).isTapped());
        AssertJUnit.assertTrue("not the opponent's", current(theirs).isTapped());
        AssertJUnit.assertTrue("not itself", current(scout).isTapped());

        // Dash G
        newGame();
        forests(1);
        final Card s2 = hand("Copperhorn Scout", me);
        cast(me, alternative(s2, SpellAbility::isDash), null);
        AssertJUnit.assertTrue("dash: haste", current(s2).hasKeyword(Keyword.HASTE));
        AssertJUnit.assertEquals("paid G", 1, tapped(me.getCardsIn(ZoneType.Battlefield)));
        attack(me, null, current(s2));
        AssertJUnit.assertEquals(19, opp.getLife());
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("returned to my hand at the end step", in(s2, ZoneType.Hand));
    }

    @Test
    public void creepingRenaissance() {
        newGame();
        forests(5);
        final Card thoctar = grave("Woolly Thoctar", me);
        final Card orn = grave("Ornithopter", me);
        final Card rite = grave("Cryptolith Rite", me);
        final Card forest = grave("Forest", me);
        final Card explore = grave("Explore", me);
        final Card oppThoctar = grave("Woolly Thoctar", opp);
        choices(me).type = "Creature";
        final Card cr = hand("Creeping Renaissance", me);
        cast(cr);
        AssertJUnit.assertTrue("creature cards return", in(thoctar, ZoneType.Hand) && in(orn, ZoneType.Hand));
        AssertJUnit.assertTrue("not other types", in(rite, ZoneType.Graveyard) && in(forest, ZoneType.Graveyard));
        AssertJUnit.assertTrue("not a sorcery", in(explore, ZoneType.Graveyard));
        AssertJUnit.assertTrue("not the opponent's", in(oppThoctar, ZoneType.Graveyard));
        AssertJUnit.assertFalse("a permanent type", choices(me).offeredTypes.contains("Sorcery"));
        AssertJUnit.assertTrue(choices(me).offeredTypes.contains("Land") && choices(me).offeredTypes.contains("Enchantment"));
        AssertJUnit.assertTrue("it is in the graveyard now", in(cr, ZoneType.Graveyard));

        newGame();
        forests(5);
        final Card rite2 = grave("Cryptolith Rite", me);
        final Card forest2 = grave("Forest", me);
        choices(me).type = "Enchantment";
        cast(hand("Creeping Renaissance", me));
        AssertJUnit.assertTrue(in(rite2, ZoneType.Hand));
        AssertJUnit.assertTrue(in(forest2, ZoneType.Graveyard));

        // Flashback 7GG
        newGame();
        forests(8);
        final Card fb = grave("Creeping Renaissance", me);
        final Card orn2 = grave("Ornithopter", me);
        choices(me).type = "Creature";
        AssertJUnit.assertFalse("8 lands are not enough", canPay(alternative(fb, SpellAbility::isFlashback)));
        forests(1);
        AssertJUnit.assertTrue("9 are", canPay(alternative(fb, SpellAbility::isFlashback)));
        cast(me, alternative(fb, SpellAbility::isFlashback), null);
        AssertJUnit.assertEquals("paid 7GG", 9, tapped(me.getCardsIn(ZoneType.Battlefield)));
        AssertJUnit.assertTrue(in(orn2, ZoneType.Hand));
        AssertJUnit.assertTrue("exiled", in(fb, ZoneType.Exile));
    }

    @Test
    public void crushOfWurms() {
        newGame();
        forests(9);
        final Card cw = hand("Crush of Wurms", me);
        cast(cw);
        AssertJUnit.assertEquals("two Wurms", 2, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertEquals(505, pt(t));
            AssertJUnit.assertTrue(t.getColor().hasGreen() && t.getType().hasSubtype("Wurm"));
            AssertJUnit.assertFalse("indomitable: a 0-power blocker can't block it", CombatUtil.canBlock(t, creature("Ornithopter", opp)));
            AssertJUnit.assertTrue("a creature with power 6 can", CombatUtil.canBlock(t, creature("Woolly Thoctar", opp)));
        }
        AssertJUnit.assertTrue("rebound: exiled", in(cw, ZoneType.Exile));
        choices(me).confirm = true;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("cast again at my next upkeep", 4, tokens(me).size());
        AssertJUnit.assertTrue("then to the graveyard", in(cw, ZoneType.Graveyard));
    }

    @Test
    public void cryptolithRite() {
        newGame();
        enter("Cryptolith Rite", me);
        final Card feeder = creature("Carrion Feeder", me);
        final Card theirs = creature("Carrion Feeder", opp);
        AssertJUnit.assertTrue("creatures I control have a mana ability", !current(feeder).getManaAbilities().isEmpty());
        AssertJUnit.assertTrue("not the opponent's", current(theirs).getManaAbilities().isEmpty());
        mana(feeder, "any color");
        AssertJUnit.assertEquals("one mana", 1, me.getManaPool().totalMana());
        AssertJUnit.assertTrue("tapped", current(feeder).isTapped());

        // Pays for a spell: two creatures pay 1G
        newGame();
        enter("Cryptolith Rite", me);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Arbor Elf", me);
        final int hand = count(me, ZoneType.Hand);
        cast(hand("Explore", me));
        AssertJUnit.assertEquals("Explore resolved: drew a card", hand + 1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(current(a).isTapped() && current(b).isTapped());
    }

    @Test
    public void cultivate() {
        newGame();
        forests(3);
        library(me, "Woolly Thoctar", "Wastes", "Mountain", "Forest");
        final int battlefield = count(me, ZoneType.Battlefield);
        choices(me).prefer = c -> true;
        cast(hand("Cultivate", me));
        AssertJUnit.assertEquals("one land into play", battlefield + 1, count(me, ZoneType.Battlefield));
        AssertJUnit.assertEquals("and one into the hand", 1, count(me, ZoneType.Hand));
        final Card played = me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isLand).filter(c -> !named(c, "Forest")
                || me.getCardsIn(ZoneType.Battlefield).stream().filter(x -> named(x, "Forest")).count() > 3).findFirst().orElseThrow();
        AssertJUnit.assertTrue("tapped", current(played).isTapped());
        AssertJUnit.assertTrue("a land card in hand", me.getCardsIn(ZoneType.Hand).get(0).isLand());
        AssertJUnit.assertTrue("Wastes has no basic land type", owns(me, ZoneType.Library, "Wastes"));

        // Up to two: one land only
        newGame();
        forests(3);
        library(me, "Mountain");
        choices(me).prefer = c -> true;
        cast(hand("Cultivate", me));
        AssertJUnit.assertEquals(1, countNamed(me, ZoneType.Battlefield, "Mountain"));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(current(me.getCardsIn(ZoneType.Battlefield).stream().filter(c -> named(c, "Mountain")).findFirst().orElseThrow()).isTapped());

        // Up to two: none
        newGame();
        forests(3);
        library(me, "Mountain", "Forest");
        choices(me).prefer = c -> false;
        cast(hand("Cultivate", me));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(0, countNamed(me, ZoneType.Battlefield, "Mountain"));
    }

    @Test
    public void deadbridgeGoliath() {
        newGame();
        forests(5);
        final Card g = grave("Deadbridge Goliath", me);
        final Card feeder = creature("Carrion Feeder", me);
        AssertJUnit.assertTrue("2GGG is payable", canActivate(g, "Scavenge"));
        activate(g, "Scavenge", feeder);
        AssertJUnit.assertEquals("a +1/+1 counter for each point of its power", 5, p1p1(current(feeder)));
        AssertJUnit.assertTrue("exiled from the graveyard", in(g, ZoneType.Exile));
        AssertJUnit.assertEquals("paid 2GGG", 5, tapped(me.getCardsIn(ZoneType.Battlefield)));

        newGame();
        forests(4);
        final Card g2 = grave("Deadbridge Goliath", me);
        AssertJUnit.assertFalse("four lands are not enough", canPay(ability(current(g2), "Scavenge")));

        // Sorcery speed
        newGame();
        final Card g3 = grave("Deadbridge Goliath", me);
        creature("Carrion Feeder", me);
        playUntil(opp, PhaseType.MAIN1);
        forests(5);
        AssertJUnit.assertFalse("can't be activated at flash speed", canActivate(g3, "Scavenge"));
        playUntil(me, PhaseType.MAIN1);
        untapAll(me);
        AssertJUnit.assertTrue("in my own main phase it can", canActivate(g3, "Scavenge"));
        AssertJUnit.assertEquals(506, pt(g3));
    }

    @Test
    public void deadlyRecluse() {
        newGame();
        forests(1);
        library(me, "Archweaver");
        library(me, "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        final Card r = hand("Deadly Recluse", me);
        AssertJUnit.assertEquals(102, pt(current(r)));
        AssertJUnit.assertTrue(current(r).hasKeyword(Keyword.REACH) && current(r).hasKeyword(Keyword.DEATHTOUCH));
        choices(me).prefer = c -> true;
        activate(r, "cycling");
        AssertJUnit.assertTrue("discarded", in(r, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("a Spider card", named(me.getCardsIn(ZoneType.Hand).get(0), "Archweaver"));
        AssertJUnit.assertEquals("paid 1", 1, tapped(me.getCardsIn(ZoneType.Battlefield)));

        // Flash
        newGame();
        playUntil(opp, PhaseType.MAIN1);
        forests(2);
        AssertJUnit.assertTrue("flash", canCast(hand("Deadly Recluse", me), me));
    }

    @Test
    public void deathHoodCobra() {
        newGame();
        final Card c = creature("Death-Hood Cobra", me);
        AssertJUnit.assertEquals(101, pt(c));
        AssertJUnit.assertTrue(c.getType().isArtifact() && c.getType().hasSubtype("Snake") && c.getType().hasSubtype("Phyrexian"));
        AssertJUnit.assertTrue(current(c).getColor().hasGreen());
        attack(me, null, current(c));
        AssertJUnit.assertEquals("frenzy 1: 1 + 1 damage", 18, opp.getLife());

        // 1H/G: reach and deathtouch, paying 2 life instead of G
        newGame();
        lands("Wastes", 1, me);
        final Card c2 = creature("Death-Hood Cobra", me);
        AssertJUnit.assertFalse(c2.hasKeyword(Keyword.REACH) || c2.hasKeyword(Keyword.DEATHTOUCH));
        activate(c2, "reach");
        AssertJUnit.assertTrue(current(c2).hasKeyword(Keyword.REACH));
        AssertJUnit.assertTrue(current(c2).hasKeyword(Keyword.DEATHTOUCH));
        AssertJUnit.assertEquals("a colorless land pays the 1, so 2 life for the H/G", 18, me.getLife());

        newGame();
        forests(2);
        final Card c3 = creature("Death-Hood Cobra", me);
        activate(c3, "reach");
        AssertJUnit.assertTrue(current(c3).hasKeyword(Keyword.DEATHTOUCH));
        AssertJUnit.assertEquals("paid with mana", 2, tapped(me.getCardsIn(ZoneType.Battlefield)));
        AssertJUnit.assertEquals(20, me.getLife());

        // Cast for 1 and 2 life
        newGame();
        lands("Wastes", 1, me);
        final Card c4 = hand("Death-Hood Cobra", me);
        cast(c4);
        AssertJUnit.assertTrue(in(c4, ZoneType.Battlefield));
        AssertJUnit.assertEquals(18, me.getLife());
    }

    @Test
    public void defendTheHearth() {
        newGame();
        forests(1);
        final Card thoctar = creature("Woolly Thoctar", me);
        final Card feeder = creature("Carrion Feeder", me);
        final Card blocker = creature("Ornithopter", opp);
        final Card dth = hand("Defend the Hearth", me);
        AssertJUnit.assertTrue("flash", current(dth).hasFlashSupertype());
        cast(dth);
        attack(me, Map.of(blocker, thoctar), current(thoctar), current(feeder));
        AssertJUnit.assertEquals("combat damage to players is prevented", 20, opp.getLife());
        AssertJUnit.assertTrue("combat damage to creatures is not", in(blocker, ZoneType.Graveyard));
        damage(feeder, opp, 3, false);
        AssertJUnit.assertEquals("other damage is not", 17, opp.getLife());
        playUntil(opp, PhaseType.MAIN1);
        attack(me, null, current(feeder));
        AssertJUnit.assertEquals("only this turn", 16, opp.getLife());

        // Combat damage to me too
        newGame();
        forests(1);
        final Card dth2 = hand("Defend the Hearth", opp);
        lands("Forest", 1, opp);
        final Card big = creature("Woolly Thoctar", me);
        cast(dth2);
        attack(me, null, current(big));
        AssertJUnit.assertEquals("prevented for the opponent who cast it too", 20, opp.getLife());
    }

    @Test
    public void dragonsguardElite() {
        newGame();
        forests(6);
        final Card e = creature("Dragonsguard Elite", me);
        AssertJUnit.assertEquals(202, pt(current(e)));
        cast(hand("Explore", me));
        AssertJUnit.assertEquals("magecraft: a sorcery spell", 1, p1p1(current(e)));
        untapAll(me);
        cast(hand("Aspect of Hydra", me), e);
        AssertJUnit.assertEquals("a Flash Sorcery is a sorcery", 2, p1p1(current(e)));
        untapAll(me);
        cast(hand("Arbor Elf", me));
        AssertJUnit.assertEquals("not a creature spell", 2, p1p1(current(e)));
        untapAll(me);
        cast(hand("Alpha Authority", me), e);
        AssertJUnit.assertEquals("not an enchantment spell", 2, p1p1(current(e)));
        untapAll(me);
        activate(e, "Double");
        AssertJUnit.assertEquals("4GG: double the +1/+1 counters", 4, p1p1(current(e)));
        AssertJUnit.assertEquals("paid 4GG", 6, tapped(me.getCardsIn(ZoneType.Battlefield)));

        newGame();
        lands("Forest", 2, opp);
        final Card e2 = creature("Dragonsguard Elite", me);
        cast(hand("Explore", opp));
        AssertJUnit.assertEquals("only my spells", 0, p1p1(current(e2)));
    }

    @Test
    public void drudgeBeetle() {
        newGame();
        final int lib = count(me, ZoneType.Library);
        final Card b = enter("Drudge Beetle", me);
        AssertJUnit.assertEquals(201, pt(b));
        AssertJUnit.assertEquals("mill two", lib - 2, count(me, ZoneType.Library));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Graveyard));

        // Scavenge G
        newGame();
        forests(1);
        final Card beetle = grave("Drudge Beetle", me);
        final Card feeder = creature("Carrion Feeder", me);
        activate(beetle, "Scavenge", feeder);
        AssertJUnit.assertEquals("counters equal to its power", 2, p1p1(current(feeder)));
        AssertJUnit.assertTrue(in(beetle, ZoneType.Exile));
        AssertJUnit.assertEquals("paid G", 1, tapped(me.getCardsIn(ZoneType.Battlefield)));

        newGame();
        final Card beetle2 = grave("Drudge Beetle", me);
        creature("Carrion Feeder", me);
        playUntil(opp, PhaseType.MAIN1);
        forests(1);
        AssertJUnit.assertFalse("can't be activated at flash speed", canActivate(beetle2, "Scavenge"));
    }

    @Test
    public void dubiousChallenge() {
        newGame();
        forests(4);
        library(me, "Woolly Thoctar", "Forest", "Ornithopter", "Carrion Feeder");
        choices(me).prefer = c -> named(c, "Carrion Feeder") || named(c, "Ornithopter");
        choices(opp).prefer = c -> named(c, "Ornithopter");
        cast(hand("Dubious Challenge", me), opp);
        AssertJUnit.assertTrue("the opponent chose one", owns(opp, ZoneType.Battlefield, "Ornithopter"));
        AssertJUnit.assertTrue("I get the rest", owns(me, ZoneType.Battlefield, "Carrion Feeder"));
        AssertJUnit.assertEquals(1, me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).count());
        AssertJUnit.assertEquals("only creature cards", 0, countNamed(me, ZoneType.Exile, "Forest"));
        AssertJUnit.assertTrue("shuffled in: the land is in the library", owns(me, ZoneType.Library, "Forest"));

        // Up to two
        newGame();
        forests(4);
        library(me, "Ornithopter", "Carrion Feeder");
        choices(me).prefer = c -> named(c, "Carrion Feeder");
        choices(opp).prefer = c -> true;
        cast(hand("Dubious Challenge", me), opp);
        AssertJUnit.assertTrue("the one exiled creature goes to the opponent", owns(opp, ZoneType.Battlefield, "Carrion Feeder"));
        AssertJUnit.assertEquals(0, me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).count());
        AssertJUnit.assertTrue("the other stays in the library", owns(me, ZoneType.Library, "Ornithopter"));

        // Only the top ten cards
        newGame();
        forests(4);
        library(me, "Ornithopter");
        library(me, "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar",
                "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar", "Woolly Thoctar");
        choices(me).prefer = c -> named(c, "Ornithopter");
        choices(opp).prefer = c -> true;
        cast(hand("Dubious Challenge", me), opp);
        AssertJUnit.assertFalse("the eleventh card is not looked at", owns(opp, ZoneType.Battlefield, "Ornithopter"));
        AssertJUnit.assertTrue(owns(me, ZoneType.Library, "Ornithopter"));
    }

    @Test
    public void eidolonOfBlossoms() {
        newGame();
        final int hand = count(me, ZoneType.Hand);
        final Card e = enter("Eidolon of Blossoms", me);
        AssertJUnit.assertEquals(202, pt(e));
        AssertJUnit.assertEquals("draws when it enters", hand + 1, count(me, ZoneType.Hand));
        enter("Cryptolith Rite", me);
        AssertJUnit.assertEquals("or another enchantment enters", hand + 2, count(me, ZoneType.Hand));
        enter("Cryptolith Rite", opp);
        AssertJUnit.assertEquals("not under the opponent's control", hand + 2, count(me, ZoneType.Hand));
        creature("Carrion Feeder", me);
        enter("Forest", me);
        AssertJUnit.assertEquals("not a creature or land", hand + 2, count(me, ZoneType.Hand));
        enter("Eidolon of Blossoms", me);
        AssertJUnit.assertEquals("a second Eidolon, and the first sees it enter too", hand + 4, count(me, ZoneType.Hand));
    }

    @Test
    public void elementalResonance() {
        newGame();
        forests(4);
        final Card weaver = creature("Archweaver", me);
        cast(hand("Elemental Resonance", me), weaver);
        AssertJUnit.assertTrue("ward 2", current(weaver).hasKeyword(Keyword.WARD));
        AssertJUnit.assertEquals("nothing until the first main phase", 0, me.getManaPool().totalMana());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("mana equal to its mana cost, 3GG", 5, me.getManaPool().totalMana());
        AssertJUnit.assertEquals("two green", 2, me.getManaPool().getAmountOfColor(forge.card.MagicColor.GREEN));
        AssertJUnit.assertEquals("three generic as colorless", 3, me.getManaPool().getAmountOfColor(COLORLESS));

        // An opponent's permanent can be enchanted: its mana cost
        newGame();
        forests(4);
        choices(opp).modes.add("Search your library"); // not the mode that removes my lands
        final Card oppExarch = creature("Brutalizer Exarch", opp);
        cast(hand("Elemental Resonance", me), oppExarch);
        AssertJUnit.assertTrue("ward 2 for an opponent's permanent", current(oppExarch).hasKeyword(Keyword.WARD));
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("I get the mana of its cost, 5G", 6, me.getManaPool().totalMana());
        AssertJUnit.assertEquals(1, me.getManaPool().getAmountOfColor(forge.card.MagicColor.GREEN));
        AssertJUnit.assertEquals(5, me.getManaPool().getAmountOfColor(COLORLESS));

        // Only in my first main phase, and only mine
        newGame();
        forests(4);
        final Card w3 = creature("Archweaver", me);
        cast(hand("Elemental Resonance", me), w3);
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("the opponent's main phase: nothing", 0, me.getManaPool().totalMana());
        AssertJUnit.assertEquals(0, opp.getManaPool().totalMana());
    }

    @Test
    public void elvesOfDeepShadow() {
        newGame();
        final Card e = creature("Elves of Deep Shadow", me);
        AssertJUnit.assertEquals(101, pt(e));
        mana(e, "Add");
        AssertJUnit.assertEquals("one B", 1, me.getManaPool().getAmountOfColor(forge.card.MagicColor.BLACK));
        AssertJUnit.assertEquals(1, me.getManaPool().totalMana());
        AssertJUnit.assertEquals("pay 1 life", 19, me.getLife());
        AssertJUnit.assertTrue("tapped", current(e).isTapped());
    }

    @Test
    public void elvishBranchbender() {
        newGame();
        final Card forest = forests(1).get(0);
        final Card bender = creature("Elvish Branchbender", me);
        activate(bender, "target land", forest);
        AssertJUnit.assertTrue("a creature", current(forest).isCreature());
        AssertJUnit.assertTrue("still a land", current(forest).isLand());
        AssertJUnit.assertTrue("a Treefolk", current(forest).getType().hasSubtype("Treefolk"));
        AssertJUnit.assertEquals("X is the number of Elves I control: one", 101, pt(current(forest)));
        AssertJUnit.assertTrue(current(forest).hasKeyword(Keyword.TRAMPLE) && current(forest).hasKeyword(Keyword.HASTE));

        newGame();
        final Card forest2 = forests(1).get(0);
        creature("Elvish Vanguard", me);
        creature("Arbor Elf", me);
        creature("Elvish Vanguard", opp);
        final Card bender2 = creature("Elvish Branchbender", me);
        activate(bender2, "target land", forest2);
        AssertJUnit.assertEquals("Branchbender and two other Elves", 303, pt(current(forest2)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("this turn only", current(forest2).isCreature());
        AssertJUnit.assertTrue(current(forest2).isLand());

        newGame();
        final Card oppLand = lands("Forest", 1, opp).get(0);
        final Card bender3 = creature("Elvish Branchbender", me);
        activate(bender3, "target land", oppLand);
        AssertJUnit.assertTrue("any land", current(oppLand).isCreature());
    }

    @Test
    public void elvishVanguard() {
        newGame();
        final Card v = creature("Elvish Vanguard", me);
        AssertJUnit.assertEquals(101, pt(v));
        creature("Arbor Elf", me);
        AssertJUnit.assertEquals("another Elf enters", 1, p1p1(current(v)));
        creature("Elvish Branchbender", me);
        AssertJUnit.assertEquals(2, p1p1(current(v)));
        creature("Carrion Feeder", me);
        AssertJUnit.assertEquals("not a Zombie", 2, p1p1(current(v)));
        creature("Arbor Elf", opp);
        AssertJUnit.assertEquals("not under the opponent's control", 2, p1p1(current(v)));
        creature("Elvish Vanguard", me);
        AssertJUnit.assertEquals("another Elvish Vanguard is another Elf", 3, p1p1(current(v)));
        AssertJUnit.assertEquals("counters make it bigger", 404, pt(current(v)));

        newGame();
        final Card v2 = enter("Elvish Vanguard", me);
        AssertJUnit.assertEquals("not itself", 0, p1p1(v2));
    }

    @Test
    public void elvishVisionary() {
        newGame();
        final int hand = count(me, ZoneType.Hand);
        final Card e = enter("Elvish Visionary", me);
        AssertJUnit.assertEquals(101, pt(e));
        AssertJUnit.assertEquals("draw a card", hand + 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("and get E", 1, me.getCounters(CounterEnumType.ENERGY));
        enter("Elvish Visionary", me);
        AssertJUnit.assertEquals(2, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void emergentSequence() {
        // Two lands entered earlier this turn: 2 x 3 - 2 = 4 counters
        newGame();
        forests(2);
        enter("Forest", me);
        enter("Forest", me);
        library(me, "Mountain");
        final Card seq = hand("Emergent Sequence", me);
        cast(seq);
        final Card fractal = me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).findFirst().orElseThrow();
        AssertJUnit.assertEquals("Mountain", fractal.getName());
        AssertJUnit.assertTrue("still a land", current(fractal).isLand());
        AssertJUnit.assertTrue("a Fractal", current(fractal).getType().hasSubtype("Fractal"));
        AssertJUnit.assertTrue("green and blue", current(fractal).getColor().hasGreen() && current(fractal).getColor().hasBlue());
        AssertJUnit.assertEquals("twice the three lands that entered, minus two", 4, p1p1(current(fractal)));
        AssertJUnit.assertEquals(404, pt(current(fractal)));
        AssertJUnit.assertTrue("summoning sick", current(fractal).isSick());

        // Only the land it fetches: 2 x 1 - 2 = 0 counters, a 0/0 dies
        newGame();
        forests(2);
        library(me, "Mountain");
        cast(hand("Emergent Sequence", me));
        AssertJUnit.assertTrue("0/0: it dies", owns(me, ZoneType.Graveyard, "Mountain"));

        // One land entered earlier: 2 x 2 - 2 = 2
        newGame();
        forests(2);
        enter("Forest", me);
        library(me, "Mountain");
        cast(hand("Emergent Sequence", me));
        final Card f3 = me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).findFirst().orElseThrow();
        AssertJUnit.assertEquals(202, pt(current(f3)));

        // Only a basic-typed land
        newGame();
        forests(2);
        library(me, "Wastes");
        cast(hand("Emergent Sequence", me));
        AssertJUnit.assertTrue("Wastes has no basic land type", owns(me, ZoneType.Library, "Wastes"));
        AssertJUnit.assertEquals(0, me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).count());
    }

    @Test
    public void enterTheUnknown() {
        newGame();
        forests(1);
        final Card c = creature("Carrion Feeder", me);
        library(me, "Forest");
        final Card eu = hand("Enter the Unknown", me);
        cast(eu, c);
        AssertJUnit.assertEquals("explores: a land goes to the hand", 1, countNamed(me, ZoneType.Hand, "Forest"));
        AssertJUnit.assertEquals("no counter", 0, p1p1(current(c)));
        AssertJUnit.assertEquals("one more land this turn", 2, me.getMaxLandPlays());
        AssertJUnit.assertEquals("cost G", 1, tapped(me.getCardsIn(ZoneType.Battlefield)));

        // A nonland card: a +1/+1 counter
        newGame();
        forests(1);
        final Card c2 = creature("Carrion Feeder", me);
        library(me, "Ornithopter");
        choices(me).prefer = c3 -> false;
        cast(hand("Enter the Unknown", me), c2);
        AssertJUnit.assertEquals("a nonland card: a counter", 1, p1p1(current(c2)));

        // Strive: G more for each target beyond the first
        newGame();
        forests(3);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Arbor Elf", me);
        library(me, "Ornithopter", "Forest");
        choices(me).prefer = c3 -> false;
        cast(hand("Enter the Unknown", me), a, b);
        AssertJUnit.assertEquals("both explored: one land, one counter", 1, p1p1(current(a)) + p1p1(current(b)));
        AssertJUnit.assertEquals("a land to the hand", 1, countNamed(me, ZoneType.Hand, "Forest"));
        AssertJUnit.assertEquals("GG for two targets", 2, tapped(me.getCardsIn(ZoneType.Battlefield)));
        AssertJUnit.assertEquals("an additional land for each creature that explored", 3, me.getMaxLandPlays());

        newGame();
        forests(1);
        creature("Carrion Feeder", me);
        creature("Arbor Elf", me);
        final Card eu2 = hand("Enter the Unknown", me);
        final SpellAbility sa = current(eu2).getFirstSpellAbility();
        sa.setActivatingPlayer(me);
        AssertJUnit.assertTrue("castable with one target", sa.canPlay());
        // Not an opponent's creature
        final Card theirs = creature("Carrion Feeder", opp);
        AssertJUnit.assertFalse(canTarget(eu2, me, current(theirs)));
    }

    @Test
    public void epicConfrontation() {
        newGame();
        forests(2);
        final Card mine = creature("Carrion Feeder", me);
        final Card theirs = creature("Woolly Thoctar", opp);
        cast(hand("Epic Confrontation", me), mine, theirs);
        AssertJUnit.assertTrue("it fought: my creature dealt 2, took 6 and would die: exiled instead", in(mine, ZoneType.Exile));
        AssertJUnit.assertEquals("damage dealt by the fight", 2, current(theirs).getDamage());
        damage(creature("Ornithopter", me), current(theirs), 2, false);
        AssertJUnit.assertTrue("exiled instead of dying: it was dealt damage this way", in(theirs, ZoneType.Exile));

        newGame();
        forests(2);
        final Card mine2 = creature("Woolly Thoctar", me);
        final Card theirs2 = creature("Woolly Thoctar", opp);
        final Card other = creature("Carrion Feeder", opp);
        cast(hand("Epic Confrontation", me), mine2, theirs2);
        AssertJUnit.assertEquals(707, pt(current(mine2)));
        AssertJUnit.assertTrue("7 damage kills it, and it is exiled", in(theirs2, ZoneType.Exile));
        AssertJUnit.assertTrue("mine survived: 7/7 took 6", in(mine2, ZoneType.Battlefield));
        kill(other);
        AssertJUnit.assertTrue("a creature not dealt damage this way dies normally", in(other, ZoneType.Graveyard));
        kill(mine2);
        AssertJUnit.assertTrue("mine was dealt damage this way too, so it is exiled", in(mine2, ZoneType.Exile));

        newGame();
        final Card oppC = creature("Carrion Feeder", opp);
        final Card mine3 = creature("Carrion Feeder", me);
        AssertJUnit.assertFalse("the first target is a creature I control", canTarget(hand("Epic Confrontation", me), me, current(oppC)));
        AssertJUnit.assertTrue(canTarget(hand("Epic Confrontation", me), me, current(mine3)));
    }

    @Test
    public void eureka() {
        newGame();
        forests(4);
        final Card a = hand("Woolly Thoctar", me);
        final Card b = hand("Woolly Thoctar", me);
        final Card explore = hand("Explore", me);
        final Card feeder = hand("Carrion Feeder", opp);
        final Card forest = hand("Forest", opp);
        choices(me).prefer = c -> named(c, "Woolly Thoctar");
        choices(opp).prefer = c -> named(c, "Carrion Feeder") || named(c, "Forest");
        cast(hand("Eureka", me));
        AssertJUnit.assertTrue("I put one", in(a, ZoneType.Battlefield));
        AssertJUnit.assertTrue("and again, until no one puts a card into play", in(b, ZoneType.Battlefield));
        AssertJUnit.assertTrue("the opponent too", in(feeder, ZoneType.Battlefield));
        AssertJUnit.assertTrue("and a land", in(forest, ZoneType.Battlefield));
        AssertJUnit.assertTrue("a sorcery can't be put into play", in(explore, ZoneType.Hand));

        newGame();
        forests(4);
        final Card c = hand("Woolly Thoctar", me);
        choices(me).prefer = x -> false;
        cast(hand("Eureka", me));
        AssertJUnit.assertTrue("may", in(c, ZoneType.Hand));

        // Not a Flash Sorcery
        newGame();
        playUntil(opp, PhaseType.MAIN1);
        forests(4);
        AssertJUnit.assertFalse("a sorcery", canCast(hand("Eureka", me), me));

        // Cycling 2
        newGame();
        forests(2);
        final Card e = hand("Eureka", me);
        final int hand = count(me, ZoneType.Hand);
        activate(e, "ycling");
        AssertJUnit.assertTrue("discarded", in(e, ZoneType.Graveyard));
        AssertJUnit.assertEquals("draw a card", hand, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("paid 2", 2, tapped(me.getCardsIn(ZoneType.Battlefield)));
    }

    @Test
    public void explore() {
        newGame();
        forests(2);
        library(me, "Forest", "Carrion Feeder");
        AssertJUnit.assertEquals(1, me.getMaxLandPlays());
        cast(hand("Explore", me));
        AssertJUnit.assertEquals("an additional land", 2, me.getMaxLandPlays());
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("the top card", named(me.getCardsIn(ZoneType.Hand).get(0), "Carrion Feeder"));
        AssertJUnit.assertTrue("nothing looked at or moved: the next card is still on top", named(top(me), "Forest"));
        AssertJUnit.assertEquals(1, me.getNumDrawnThisTurn());
        AssertJUnit.assertEquals("paid 1G", 2, tapped(me.getCardsIn(ZoneType.Battlefield)));
    }

    @Test
    public void fallowEarth() {
        newGame();
        forests(3);
        final Card mine = lands("Mountain", 1, me).get(0);
        final Card theirs = lands("Forest", 1, opp).get(0);
        final Card fe = hand("Fallow Earth", me);
        AssertJUnit.assertFalse("a sorcery, not flash", current(fe).hasFlashSupertype());
        cast(fe, theirs);
        AssertJUnit.assertTrue("an opponent's land goes on top of its owner's library", in(theirs, ZoneType.Library));
        AssertJUnit.assertEquals(current(theirs), top(opp));
        AssertJUnit.assertEquals("paid 2G", 3, tapped(me.getCardsIn(ZoneType.Battlefield)));
        untapAll(me);
        cast(hand("Fallow Earth", me), mine);
        AssertJUnit.assertEquals("my land on top of my library", current(mine), top(me));

        // Cycling: sacrifice a land
        newGame();
        final List<Card> fs = forests(2);
        final Card cyc = hand("Fallow Earth", me);
        final int hand = count(me, ZoneType.Hand);
        activate(cyc, "ycling");
        AssertJUnit.assertTrue("discarded", in(cyc, ZoneType.Graveyard));
        AssertJUnit.assertEquals("sacrificed a land", 1, me.getCardsIn(ZoneType.Battlefield).size());
        AssertJUnit.assertEquals("one land in the graveyard", 2, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals("draw a card", hand, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("no mana", 0, tapped(me.getCardsIn(ZoneType.Battlefield)));

        // Not castable at flash speed
        newGame();
        playUntil(opp, PhaseType.MAIN1);
        forests(3);
        AssertJUnit.assertFalse(canCast(hand("Fallow Earth", me), me));
    }

    @Test
    public void feedThePack() {
        newGame();
        enter("Feed the Pack", me);
        final Card thoctar = creature("Woolly Thoctar", me);
        choices(me).confirm = true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("sacrificed", in(thoctar, ZoneType.Graveyard));
        AssertJUnit.assertEquals("X = its toughness: four Wolves", 4, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertEquals(201, pt(t));
            AssertJUnit.assertTrue(t.getType().hasSubtype("Wolf") && t.getColor().hasGreen());
        }

        // May
        newGame();
        enter("Feed the Pack", me);
        final Card t2 = creature("Woolly Thoctar", me);
        choices(me).confirm = false;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("may", in(t2, ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, tokens(me).size());

        // A nontoken creature only
        newGame();
        enter("Feed the Pack", me);
        final Card orn = creature("Ornithopter", me);
        choices(me).confirm = true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue(in(orn, ZoneType.Graveyard));
        AssertJUnit.assertEquals("toughness 2", 2, tokens(me).size());

        newGame();
        enter("Feed the Pack", me);
        final Card tokenWolf = addToken("tbld_g_2_1_wolf", me);
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("a token can't be sacrificed", in(tokenWolf, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, tokens(me).size());

        // Only at its controller's end step
        newGame();
        enter("Feed the Pack", opp);
        final Card t4 = creature("Woolly Thoctar", opp);
        choices(opp).confirm = true;
        choices(opp).prefer = c -> true;
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertTrue("not at my end step", in(t4, ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, tokens(opp).size());
    }
}
