package forge.tbld;

import forge.card.MagicColor;
import forge.game.GameEntity;
import forge.game.GameObject;
import forge.game.ability.AbilityUtils;
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
import java.util.function.Predicate;

/** Green TBLD cards, second batch: each card's abilities played in an engine game and checked against its text. */
public class TbldGreenCards2Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Fertile Ground", "Fierce Empath", "Fists of Ironwood", "Forerunner of the Heralds", "Fortifying Draught",
            "From Beyond", "Frontier Siege", "Gaeas Gift", "Gelatinous Genesis", "Ghoultree", "Giant Growth",
            "Gilded Goose", "Gluttonous Slime", "Gnarlback Rhino", "Grapple with the Past", "Greenbelt Rampager",
            "Groundswell", "Harvest Season", "Herald of the Pantheon", "Hidden Herbalists", "Highspire Infusion",
            "Honored Hydra", "Hooting Mandrills", "Hydras Growth", "Impervious Greatwurm", "Kalonian Tusker",
            "Karametras Acolyte", "Kavu Climber", "Llanowar Stalker", "Longtusk Cub", "Lotus Cobra",
            "Magus of the Library", "Magus of the Vineyard", "Mammoth Growth", "Marwyn the Nurturer", "Moments Peace",
            "Monstrify", "Monstrous Onslaught", "Monstrous Vortex", "Mossborn Hydra", "Mycoloth", "Natural State",
            "Natures Lore", "Natures Spiral", "Noose Constrictor", "Nullmage Shepherd", "Nyleas Colossus",
            "Nyxborn Hydra", "On the Trail", "Oran-Rief Ooze");

    private static final byte COLORLESS = (byte) forge.card.mana.ManaAtom.COLORLESS;
    private static final byte GREEN = MagicColor.GREEN;

    private List<Card> forests(final int n) {
        return lands("Forest", n, me);
    }

    private static boolean named(final Object c, final String name) {
        return c instanceof Card && ((Card) c).getName().equals(name);
    }

    private static Predicate<GameEntity> is(final Card... cards) {
        return e -> e instanceof Card && java.util.Arrays.stream(cards).anyMatch(c -> c.getId() == ((Card) e).getId());
    }

    private static Predicate<GameEntity> called(final String name) {
        return e -> named(e, name);
    }

    private static int pt(final Card c) {
        return c.getNetPower() * 100 + c.getNetToughness();
    }

    private boolean owns(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().anyMatch(c -> named(c, name));
    }

    private long countNamed(final Player p, final ZoneType z, final String name) {
        return p.getCardsIn(z).stream().filter(c -> named(c, name)).count();
    }

    private long tappedLands(final Player p) {
        return p.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isLand() && current(c).isTapped()).count();
    }

    private long treasures(final Player p) {
        return tokens(p).stream().filter(c -> c.getType().hasSubtype("Treasure")).count();
    }

    private Card top(final Player p) {
        return p.getCardsIn(ZoneType.Library).get(0);
    }

    private Card bottom(final Player p) {
        final List<Card> lib = new ArrayList<>(p.getCardsIn(ZoneType.Library));
        return lib.get(lib.size() - 1);
    }

    private static boolean canTarget(final Card spell, final Player p, final GameObject target) {
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canTarget(target);
    }

    /** Whether the ability (an activated ability or a keyword's) can be played now from where the card is. */
    private boolean canActivate(final Card c, final String text) {
        return current(c).getAllPossibleAbilities(me, true).stream().anyMatch(sa -> sa.getDescription().contains(text));
    }

    /** Casts the card from the hand with exactly this many Forests, which must all be tapped: the spell's whole cost. */
    private Card castPaying(final String name, final int forests, final GameObject... targets) {
        forests(forests);
        final Card c = hand(name, me);
        cast(c, targets);
        AssertJUnit.assertEquals(name + " costs " + forests, forests, tappedLands(me));
        return current(c);
    }

    /** The opponent casts the card from their hand, leaving it on the stack. */
    private SpellAbility oppCasts(final String name, final GameObject... targets) {
        return put(opp, hand(name, opp).getFirstSpellAbility(), targeting(targets));
    }

    /** The opponent targets the creature with Giant Growth and pays ward if they can: whether Giant Growth resolved. */
    private boolean wardLetsThrough(final Card target, final int oppForests) {
        final int power = current(target).getNetPower();
        lands("Forest", oppForests, opp);
        choices(opp).payUnless = true;
        oppCasts("Giant Growth", current(target));
        resolve();
        return current(target).getNetPower() == power + 3;
    }

    /** Destroys the permanent the way a destroy effect does, so its leaves-the-battlefield triggers run. */
    private void destroy(final Card c) {
        game.copyLastState();
        final forge.game.card.CardZoneTable table = new forge.game.card.CardZoneTable(game.getLastStateBattlefield(),
                game.getLastStateGraveyard());
        final Map<forge.game.ability.AbilityKey, Object> params = forge.game.ability.AbilityKey.newMap();
        params.put(forge.game.ability.AbilityKey.InternalTriggerTable, table);
        game.getAction().destroy(current(c), null, false, params);
        table.triggerChangesZoneAll(game, null);
        resolve();
    }

    private void addCounters(final Card c, final CounterEnumType type, final int n) {
        current(c).addCounterInternal(type, n, me, false, null, null);
    }

    @Test
    public void fertileGround() {
        newGame();
        final List<Card> fs = forests(3);
        final Card ground = hand("Fertile Ground", me);
        cast(ground, fs.get(0));
        AssertJUnit.assertEquals("enchant land", current(fs.get(0)), current(ground).getAttachedTo());
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        untapAll(me);
        mana(fs.get(0), null);
        AssertJUnit.assertEquals("the enchanted land adds one additional mana", 2, me.getManaPool().totalMana());
        mana(fs.get(1), null);
        AssertJUnit.assertEquals("another land adds just its own", 3, me.getManaPool().totalMana());

        // When it goes to the graveyard from play: a Treasure, to its controller
        newGame();
        final List<Card> fs2 = forests(2);
        final Card ground2 = hand("Fertile Ground", me);
        cast(ground2, fs2.get(0));
        AssertJUnit.assertEquals(0, treasures(me));
        destroy(ground2);
        AssertJUnit.assertTrue(in(ground2, ZoneType.Graveyard));
        AssertJUnit.assertEquals("a Treasure", 1, treasures(me));

        // The land dies first: the Aura goes to the graveyard
        newGame();
        final List<Card> fs3 = forests(2);
        final Card ground3 = hand("Fertile Ground", me);
        cast(ground3, fs3.get(0));
        destroy(fs3.get(0));
        AssertJUnit.assertTrue(in(ground3, ZoneType.Graveyard));
        AssertJUnit.assertEquals("a Treasure when the land dies", 1, treasures(me));

        // On the opponent's land: its controller adds the extra mana; I get the Treasure
        newGame();
        forests(2);
        final Card theirs = lands("Forest", 1, opp).get(0);
        final Card ground4 = hand("Fertile Ground", me);
        cast(ground4, theirs);
        AssertJUnit.assertEquals(current(theirs), current(ground4).getAttachedTo());
        mana(theirs, null);
        AssertJUnit.assertEquals("its controller adds the extra mana", 2, opp.getManaPool().totalMana());
        AssertJUnit.assertEquals(0, me.getManaPool().totalMana());
        destroy(theirs);
        AssertJUnit.assertEquals("I get the Treasure", 1, treasures(me));
        AssertJUnit.assertEquals(0, treasures(opp));
    }

    @Test
    public void fierceEmpath() {
        newGame();
        library(me, "Ghoultree", "Mycoloth");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        final Card fe = castPaying("Fierce Empath", 2);
        AssertJUnit.assertEquals(101, pt(fe));
        AssertJUnit.assertTrue("a creature card with mana value 6 or more", owns(me, ZoneType.Hand, "Ghoultree"));
        AssertJUnit.assertTrue("not the one with mana value 5", owns(me, ZoneType.Library, "Mycoloth"));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));

        // Only mana value 5 in the library
        newGame();
        library(me, "Mycoloth");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        creature("Fierce Empath", me);
        AssertJUnit.assertEquals("nothing to find", 0, count(me, ZoneType.Hand));

        // Declined
        newGame();
        library(me, "Ghoultree");
        choices(me).confirm = false;
        creature("Fierce Empath", me);
        AssertJUnit.assertEquals("a may", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(owns(me, ZoneType.Library, "Ghoultree"));
    }

    @Test
    public void fistsOfIronwood() {
        newGame();
        forests(2);
        final Card feeder = creature("Carrion Feeder", me);
        final Card fists = hand("Fists of Ironwood", me);
        cast(fists, feeder);
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        AssertJUnit.assertEquals(current(feeder), current(fists).getAttachedTo());
        AssertJUnit.assertEquals("two Saprolings", 2, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertTrue(t.getType().hasSubtype("Saproling") && t.isGreen());
            AssertJUnit.assertEquals(101, pt(t));
        }
        AssertJUnit.assertEquals("+1/+0", 201, pt(current(feeder)));
        AssertJUnit.assertTrue("trample", current(feeder).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertEquals("only the enchanted creature", 101, pt(tokens(me).get(0)));
        AssertJUnit.assertFalse(tokens(me).get(0).hasKeyword(Keyword.TRAMPLE));
    }

    @Test
    public void forerunnerOfTheHeralds() {
        newGame();
        library(me, "Razorfin Hunter", "Ornithopter");
        choices(me).confirm = true;
        choices(me).prefer = called("Razorfin Hunter");
        final Card f = castPaying("Forerunner of the Heralds", 4);
        AssertJUnit.assertTrue("flash", f.hasFlashSupertype());
        AssertJUnit.assertEquals(202, pt(f));
        AssertJUnit.assertTrue("the Merfolk card is on top", named(top(me), "Razorfin Hunter"));
        AssertJUnit.assertEquals("not put into the hand", 0, count(me, ZoneType.Hand));

        // Declined
        newGame();
        library(me, "Razorfin Hunter", "Ornithopter");
        choices(me).confirm = false;
        creature("Forerunner of the Heralds", me);
        AssertJUnit.assertTrue(named(top(me), "Ornithopter"));

        // Whenever another Merfolk enters under my control: two counters on target creature
        newGame();
        choices(me).confirm = false;
        final Card herald = creature("Forerunner of the Heralds", me);
        AssertJUnit.assertEquals("itself doesn't count", 0, p1p1(current(herald)));
        final Card feeder = creature("Carrion Feeder", me);
        choices(me).targets = List.of(feeder);
        AssertJUnit.assertEquals("not a Merfolk", 0, p1p1(current(feeder)));
        creature("Razorfin Hunter", me);
        AssertJUnit.assertEquals("two +1/+1 counters", 2, p1p1(current(feeder)));
        creature("Razorfin Hunter", opp);
        AssertJUnit.assertEquals("an opponent's Merfolk", 2, p1p1(current(feeder)));
        creature("Ornithopter", me);
        AssertJUnit.assertEquals("not a Merfolk", 2, p1p1(current(feeder)));
    }

    @Test
    public void fortifyingDraught() {
        newGame();
        forests(1);
        final Card feeder = creature("Carrion Feeder", me);
        final Card fd = hand("Fortifying Draught", me);
        AssertJUnit.assertTrue("flash", current(fd).hasFlashSupertype());
        cast(fd, feeder);
        AssertJUnit.assertEquals("cost G", 1, tappedLands(me));
        AssertJUnit.assertEquals("gain 2 life", 22, me.getLife());
        AssertJUnit.assertEquals("+2/+2", 303, pt(current(feeder)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));

        // X is all the life gained this turn
        newGame();
        forests(1);
        final Card feeder2 = creature("Carrion Feeder", me);
        me.gainLife(3, null, null);
        cast(hand("Fortifying Draught", me), feeder2);
        AssertJUnit.assertEquals(25, me.getLife());
        AssertJUnit.assertEquals("+5/+5", 606, pt(current(feeder2)));
    }

    @Test
    public void fromBeyond() {
        newGame();
        forests(4);
        final Card fb = hand("From Beyond", me);
        cast(fb);
        AssertJUnit.assertEquals("cost 3G", 4, tappedLands(me));
        AssertJUnit.assertTrue("devoid", current(fb).getColor().isColorless());
        AssertJUnit.assertEquals(0, tokens(me).size());
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("not at the opponent's upkeep", 0, tokens(me).size());
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("a Spawn at my upkeep", 1, tokens(me).size());
        final Card spawn = tokens(me).get(0);
        AssertJUnit.assertTrue(spawn.getType().hasSubtype("Eldrazi") && spawn.getType().hasSubtype("Spawn"));
        AssertJUnit.assertTrue("colorless", spawn.getColor().isColorless());
        AssertJUnit.assertEquals(101, pt(spawn));
        mana(spawn, null);
        AssertJUnit.assertEquals("sacrifice: add C", 1, me.getManaPool().getAmountOfColor(COLORLESS));
        AssertJUnit.assertEquals(0, tokens(me).size());

        // 1G, sacrifice: search for an Eldrazi card
        newGame();
        forests(2);
        final Card fb2 = enter("From Beyond", me);
        library(me, "Birthing Hulk", "Kavu Climber");
        choices(me).prefer = c -> true;
        activate(fb2, "Search your library");
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        AssertJUnit.assertTrue(in(fb2, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("an Eldrazi card", owns(me, ZoneType.Hand, "Birthing Hulk"));
    }

    @Test
    public void frontierSiege() {
        // Khans: GG at my main phase, once per turn
        newGame();
        choices(me).modes.add("Khans");
        castPaying("Frontier Siege", 4);
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("GG", 2, me.getManaPool().getAmountOfColor(GREEN));
        AssertJUnit.assertEquals(2, me.getManaPool().totalMana());
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals("once per turn", 0, me.getManaPool().totalMana());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("my main phase only", 0, opp.getManaPool().totalMana() + me.getManaPool().totalMana());

        // Khans: attacking with a flier does nothing
        newGame();
        choices(me).modes.add("Khans");
        castPaying("Frontier Siege", 4);
        final Card raven = creature("Augury Raven", me);
        final Card victim = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(victim, raven);
        attack(me, null, raven);
        AssertJUnit.assertEquals(0, current(victim).getDamage());
        AssertJUnit.assertEquals(17, opp.getLife());

        // Dragons: my attacking flier fights up to one target creature
        newGame();
        choices(me).modes.add("Dragons");
        castPaying("Frontier Siege", 4);
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("no mana in Dragons mode", 0, me.getManaPool().totalMana());
        final Card raven2 = creature("Augury Raven", me);
        final Card victim2 = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(victim2, raven2);
        attack(me, null, raven2);
        AssertJUnit.assertTrue("the Raven fought it", in(victim2, ZoneType.Graveyard));
        AssertJUnit.assertEquals("and took its damage", 1, current(raven2).getDamage());

        // Dragons: up to one, so none is fine; a non-flier can't be the fighter
        newGame();
        choices(me).modes.add("Dragons");
        castPaying("Frontier Siege", 4);
        final Card raven3 = creature("Augury Raven", me);
        final Card victim3 = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(raven3);
        attack(me, null, raven3);
        AssertJUnit.assertEquals("no fight", 0, current(raven3).getDamage());
        AssertJUnit.assertTrue(in(victim3, ZoneType.Battlefield));

        newGame();
        choices(me).modes.add("Dragons");
        castPaying("Frontier Siege", 4);
        final Card ground = creature("Woolly Thoctar", me);
        final Card victim4 = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(victim4, ground);
        attack(me, null, ground);
        AssertJUnit.assertEquals("a creature without flying can't fight", 0, current(victim4).getDamage());
    }

    @Test
    public void gaeasGift() {
        newGame();
        forests(4);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        a.setTapped(true);
        b.setTapped(true);
        final Card gift = hand("Gaeas Gift", me);
        AssertJUnit.assertTrue("flash", current(gift).hasFlashSupertype());
        cast(gift, a, b);
        AssertJUnit.assertEquals("1G for the first target, 1G more for the second", 4, tappedLands(me));
        for (final Card c : List.of(a, b)) {
            final Card cur = current(c);
            AssertJUnit.assertFalse("untapped", cur.isTapped());
            AssertJUnit.assertEquals("a +1/+1 counter", 1, p1p1(cur));
            AssertJUnit.assertTrue(cur.hasKeyword(Keyword.REACH) && cur.hasKeyword(Keyword.TRAMPLE)
                    && cur.hasKeyword(Keyword.HEXPROOF) && cur.hasKeyword(Keyword.INDESTRUCTIBLE));
        }
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("the counter stays", 1, p1p1(current(a)));
        AssertJUnit.assertFalse("this turn", current(a).hasKeyword(Keyword.HEXPROOF) || current(a).hasKeyword(Keyword.INDESTRUCTIBLE)
                || current(a).hasKeyword(Keyword.TRAMPLE) || current(a).hasKeyword(Keyword.REACH));

        // One target: 1G; no targets: 1G
        newGame();
        forests(2);
        final Card c = creature("Carrion Feeder", me);
        c.setTapped(true);
        cast(hand("Gaeas Gift", me), c);
        AssertJUnit.assertEquals(2, tappedLands(me));
        AssertJUnit.assertFalse(current(c).isTapped());

        newGame();
        forests(2);
        cast(hand("Gaeas Gift", me));
        AssertJUnit.assertEquals("any number, including none", 2, tappedLands(me));

        // Three targets, three times the cost
        newGame();
        forests(6);
        final Card x = creature("Carrion Feeder", me);
        final Card y = creature("Carrion Feeder", me);
        final Card z = creature("Carrion Feeder", me);
        final Card gift3 = hand("Gaeas Gift", me);
        cast(gift3, x, y, z);
        AssertJUnit.assertEquals("1G for each target", 6, tappedLands(me));
        AssertJUnit.assertEquals(1, p1p1(current(z)));
    }

    @Test
    public void gelatinousGenesis() {
        newGame();
        forests(5);
        final Card old = creature("Carrion Feeder", me);
        addCounters(old, CounterEnumType.P1P1, 1);
        final Card gg = hand("Gelatinous Genesis", me);
        gg.getFirstSpellAbility().setXManaCostPaid(2);
        cast(gg);
        AssertJUnit.assertEquals("XXG with X = 2", 5, tappedLands(me));
        AssertJUnit.assertEquals("X Oozes", 2, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertTrue(t.getType().hasSubtype("Ooze") && t.isGreen());
            AssertJUnit.assertEquals("X counters, then proliferate", 3, p1p1(current(t)));
            AssertJUnit.assertEquals(303, pt(current(t)));
        }
        AssertJUnit.assertEquals("proliferate adds to a creature already there", 2, p1p1(current(old)));

        // X = 0: no tokens, still proliferate
        newGame();
        forests(1);
        final Card old2 = creature("Carrion Feeder", me);
        addCounters(old2, CounterEnumType.P1P1, 1);
        final Card gg2 = hand("Gelatinous Genesis", me);
        gg2.getFirstSpellAbility().setXManaCostPaid(0);
        cast(gg2);
        AssertJUnit.assertEquals(0, tokens(me).size());
        AssertJUnit.assertEquals(2, p1p1(current(old2)));
    }

    @Test
    public void ghoultree() {
        newGame();
        for (int i = 0; i < 3; i++) {
            grave("Carrion Feeder", me);
        }
        grave("Explore", me);
        grave("Carrion Feeder", opp);
        grave("Carrion Feeder", opp);
        forests(5);
        final Card gt = hand("Ghoultree", me);
        cast(gt);
        AssertJUnit.assertEquals("7G less 3 creature cards in my graveyard", 5, tappedLands(me));
        AssertJUnit.assertEquals(1010, pt(current(gt)));
        AssertJUnit.assertTrue(current(gt).hasKeyword(Keyword.HASTE));
        AssertJUnit.assertTrue(in(gt, ZoneType.Battlefield));

        // The opponent's graveyard doesn't count
        newGame();
        for (int i = 0; i < 3; i++) {
            grave("Carrion Feeder", opp);
        }
        forests(7);
        final Card gt2 = hand("Ghoultree", me);
        final SpellAbility gtSa = current(gt2).getFirstSpellAbility();
        gtSa.setActivatingPlayer(me);
        AssertJUnit.assertFalse("costs 8", forge.ai.ComputerUtil.handlePlayingSpellAbility(me, gtSa, null));
        AssertJUnit.assertTrue(in(gt2, ZoneType.Hand));

        // No creature cards: full price
        newGame();
        forests(8);
        final Card gt3 = hand("Ghoultree", me);
        cast(gt3);
        AssertJUnit.assertEquals(8, tappedLands(me));
    }

    @Test
    public void giantGrowth() {
        newGame();
        forests(1);
        final Card feeder = creature("Carrion Feeder", me);
        final Card gg = hand("Giant Growth", me);
        AssertJUnit.assertTrue("flash", current(gg).hasFlashSupertype());
        cast(gg, feeder);
        AssertJUnit.assertEquals("cost G", 1, tappedLands(me));
        AssertJUnit.assertEquals("+3/+3", 404, pt(current(feeder)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));

        // Cycling 1
        newGame();
        forests(1);
        final Card gg2 = hand("Giant Growth", me);
        activate(gg2, "ycling");
        AssertJUnit.assertEquals("cycling 1", 1, tappedLands(me));
        AssertJUnit.assertTrue(in(gg2, ZoneType.Graveyard));
        AssertJUnit.assertEquals("drew a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, me.getNumDrawnThisTurn());
    }

    @Test
    public void gildedGoose() {
        newGame();
        final Card goose = castPaying("Gilded Goose", 1);
        AssertJUnit.assertEquals(2, goose.getNetToughness());
        AssertJUnit.assertEquals(0, goose.getNetPower());
        AssertJUnit.assertTrue(goose.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals("a Food when it enters", 1, tokens(me).size());
        AssertJUnit.assertTrue(tokens(me).get(0).getType().hasSubtype("Food"));
        goose.setSickness(false);

        // 1G, T: create a Food
        forests(2);
        activate(goose, "Create a Food");
        AssertJUnit.assertEquals("1G", 2, tappedLands(me) - 1);
        AssertJUnit.assertEquals(2, tokens(me).size());
        AssertJUnit.assertTrue(current(goose).isTapped());

        // T, sacrifice a Food: any color
        untapAll(me);
        AssertJUnit.assertTrue(canActivate(goose, "Add one mana"));
        mana(goose, null);
        AssertJUnit.assertEquals(1, me.getManaPool().totalMana());
        AssertJUnit.assertEquals("sacrificed a Food", 1, tokens(me).size());

        // Without a Food, no mana
        untapAll(me);
        for (final Card t : new ArrayList<>(tokens(me))) {
            game.getAction().exile(t, null, null);
        }
        resolve();
        AssertJUnit.assertEquals(0, tokens(me).size());
        AssertJUnit.assertFalse("needs a Food", canActivate(goose, "Add one mana"));

        // Can't make a Food while tapped
        goose.setTapped(true);
        AssertJUnit.assertFalse(canActivate(goose, "Create a Food"));
    }

    @Test
    public void gluttonousSlime() {
        newGame();
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        choices(me).confirm = true;
        choices(me).prefer = is(a, b);
        forests(2);
        final Card slime = hand("Gluttonous Slime", me);
        AssertJUnit.assertTrue("flash", current(slime).hasFlashSupertype());
        cast(slime);
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        AssertJUnit.assertTrue(in(a, ZoneType.Graveyard) && in(b, ZoneType.Graveyard));
        AssertJUnit.assertEquals("devour 1: a counter for each", 2, p1p1(current(slime)));
        AssertJUnit.assertEquals(404, pt(current(slime)));

        // Must be blocked if able
        newGame();
        choices(me).confirm = false;
        final Card s = creature("Gluttonous Slime", me);
        final Card blocker = creature("Ornithopter", opp);
        final Combat none = new Combat(me);
        none.addAttacker(current(s), opp);
        AssertJUnit.assertNotNull("must be blocked if able", CombatUtil.validateBlocks(none, opp));
        final Combat blocked = new Combat(me);
        blocked.addAttacker(current(s), opp);
        blocked.addBlocker(current(s), current(blocker));
        AssertJUnit.assertNull(CombatUtil.validateBlocks(blocked, opp));
        AssertJUnit.assertEquals(202, pt(current(s)));

        // A creature it dealt damage to dies: a counter
        attack(me, Map.of(blocker, s), s);
        AssertJUnit.assertTrue("it dealt lethal damage to the blocker", in(blocker, ZoneType.Graveyard));
        AssertJUnit.assertEquals(1, p1p1(current(s)));
        final Card other = creature("Woolly Thoctar", opp);
        kill(other);
        AssertJUnit.assertEquals("a creature it didn't damage", 1, p1p1(current(s)));
    }

    @Test
    public void gnarlbackRhino() {
        // Riot: a counter
        newGame();
        choices(me).payUnless = true;
        final Card rhino = castPaying("Gnarlback Rhino", 4);
        AssertJUnit.assertEquals(404, pt(rhino));
        AssertJUnit.assertFalse(rhino.hasKeyword(Keyword.HASTE));
        AssertJUnit.assertTrue(rhino.hasKeyword(Keyword.TRAMPLE));

        // Riot: haste
        newGame();
        choices(me).payUnless = false;
        final Card rhino2 = castPaying("Gnarlback Rhino", 4);
        AssertJUnit.assertEquals(303, pt(rhino2));
        AssertJUnit.assertTrue(rhino2.hasKeyword(Keyword.HASTE));

        // Whenever I cast a spell that targets it: draw, then a land from the hand into play
        newGame();
        choices(me).payUnless = false;
        final Card r = creature("Gnarlback Rhino", me);
        forests(1);
        library(me, "Forest");
        choices(me).prefer = c -> true;
        cast(hand("Giant Growth", me), r);
        AssertJUnit.assertEquals("drew a card", 1, me.getNumDrawnThisTurn());
        AssertJUnit.assertEquals("and put the land into play", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(2, countNamed(me, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals("the spell resolved too", 606, pt(current(r)));

        // A spell that doesn't target it: nothing
        newGame();
        choices(me).payUnless = false;
        creature("Gnarlback Rhino", me);
        final Card feeder = creature("Carrion Feeder", me);
        forests(1);
        library(me, "Forest");
        choices(me).prefer = c -> true;
        cast(hand("Giant Growth", me), feeder);
        AssertJUnit.assertEquals(0, me.getNumDrawnThisTurn());

        // The opponent's spell: nothing
        newGame();
        choices(me).payUnless = false;
        final Card r3 = creature("Gnarlback Rhino", me);
        lands("Forest", 1, opp);
        oppCasts("Giant Growth", current(r3));
        resolve();
        AssertJUnit.assertEquals(0, me.getNumDrawnThisTurn());
    }

    @Test
    public void grappleWithThePast() {
        newGame();
        forests(2);
        final Card gwp = hand("Grapple with the Past", me);
        AssertJUnit.assertTrue("flash", current(gwp).hasFlashSupertype());
        library(me, "Forest", "Carrion Feeder", "Explore");
        choices(me).prefer = called("Carrion Feeder");
        final int lib = count(me, ZoneType.Library);
        cast(gwp);
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        AssertJUnit.assertEquals("milled three", lib - 3, count(me, ZoneType.Library));
        AssertJUnit.assertTrue("returned a milled creature card", owns(me, ZoneType.Hand, "Carrion Feeder"));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("the land and the sorcery stay, and the spell", 3, count(me, ZoneType.Graveyard));
        AssertJUnit.assertEquals("I mill, not the opponent", 0, count(opp, ZoneType.Graveyard));

        // A land card, or a card already in the graveyard
        newGame();
        forests(2);
        grave("Ornithopter", me);
        library(me, "Explore", "Explore", "Explore");
        choices(me).prefer = called("Ornithopter");
        cast(hand("Grapple with the Past", me));
        AssertJUnit.assertTrue("a creature card from before", owns(me, ZoneType.Hand, "Ornithopter"));

        newGame();
        forests(2);
        library(me, "Explore", "Explore", "Forest");
        choices(me).prefer = called("Forest");
        cast(hand("Grapple with the Past", me));
        AssertJUnit.assertTrue("a land card", owns(me, ZoneType.Hand, "Forest"));

        // Up to one: may take none; a card that is neither can't be taken
        newGame();
        forests(2);
        library(me, "Explore", "Explore", "Carrion Feeder");
        choices(me).prefer = c -> false;
        cast(hand("Grapple with the Past", me));
        AssertJUnit.assertEquals("a may", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals("three milled and the spell", 4, count(me, ZoneType.Graveyard));

        newGame();
        forests(2);
        library(me, "Explore", "Giant Growth", "Hydras Growth");
        choices(me).prefer = c -> true;
        cast(hand("Grapple with the Past", me));
        AssertJUnit.assertEquals("only a creature or land card", 0, count(me, ZoneType.Hand));
    }

    @Test
    public void greenbeltRampager() {
        // Pays EE
        newGame();
        me.setCounters(CounterEnumType.ENERGY, 2);
        final Card r = castPaying("Greenbelt Rampager", 1);
        AssertJUnit.assertEquals(304, pt(r));
        AssertJUnit.assertTrue(in(r, ZoneType.Battlefield));
        AssertJUnit.assertEquals("paid EE", 0, me.getCounters(CounterEnumType.ENERGY));

        newGame();
        me.setCounters(CounterEnumType.ENERGY, 3);
        final Card r2 = castPaying("Greenbelt Rampager", 1);
        AssertJUnit.assertTrue(in(r2, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, me.getCounters(CounterEnumType.ENERGY));

        // Can't: returns to the hand and I get E
        newGame();
        me.setCounters(CounterEnumType.ENERGY, 1);
        forests(1);
        final Card r3 = hand("Greenbelt Rampager", me);
        cast(r3);
        AssertJUnit.assertTrue("returned to its owner's hand", owns(me, ZoneType.Hand, "Greenbelt Rampager"));
        AssertJUnit.assertEquals(0, countNamed(me, ZoneType.Battlefield, "Greenbelt Rampager"));
        AssertJUnit.assertEquals("one energy and E more", 2, me.getCounters(CounterEnumType.ENERGY));

        newGame();
        forests(1);
        cast(hand("Greenbelt Rampager", me));
        AssertJUnit.assertTrue(owns(me, ZoneType.Hand, "Greenbelt Rampager"));
        AssertJUnit.assertEquals(1, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void groundswell() {
        newGame();
        forests(1);
        final Card feeder = creature("Carrion Feeder", me);
        final Card gs = hand("Groundswell", me);
        AssertJUnit.assertTrue("flash", current(gs).hasFlashSupertype());
        cast(gs, feeder);
        AssertJUnit.assertEquals("cost G", 1, tappedLands(me));
        AssertJUnit.assertEquals("+2/+2 without landfall", 303, pt(current(feeder)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));

        // Landfall
        newGame();
        enter("Forest", me);
        final Card feeder2 = creature("Carrion Feeder", me);
        cast(hand("Groundswell", me), feeder2);
        AssertJUnit.assertEquals("+4/+4 with a land entered this turn", 505, pt(current(feeder2)));

        // An opponent's land doesn't count
        newGame();
        forests(1);
        enter("Forest", opp);
        final Card feeder3 = creature("Carrion Feeder", me);
        cast(hand("Groundswell", me), feeder3);
        AssertJUnit.assertEquals(303, pt(current(feeder3)));
    }

    @Test
    public void harvestSeason() {
        newGame();
        lands("Forest", 1, me);
        lands("Island", 1, me);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        creature("Carrion Feeder", me);
        final Card theirs = creature("Carrion Feeder", opp);
        a.setTapped(true);
        b.setTapped(true);
        theirs.setTapped(true);
        library(me, "Swamp", "Swamp", "Swamp", "Wastes");
        choices(me).prefer = c -> true;
        cast(hand("Harvest Season", me));
        AssertJUnit.assertEquals("cost 1G", 2, countNamed(me, ZoneType.Battlefield, "Forest") + countNamed(me, ZoneType.Battlefield, "Island"));
        AssertJUnit.assertTrue(current(me.getCardsIn(ZoneType.Battlefield).stream().filter(c -> named(c, "Forest")).findFirst().get()).isTapped());
        AssertJUnit.assertTrue(current(me.getCardsIn(ZoneType.Battlefield).stream().filter(c -> named(c, "Island")).findFirst().get()).isTapped());
        AssertJUnit.assertEquals("X is my two tapped creatures", 2, countNamed(me, ZoneType.Battlefield, "Swamp"));
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if (named(c, "Swamp")) {
                AssertJUnit.assertTrue("tapped", current(c).isTapped());
            }
        }
        AssertJUnit.assertTrue("Wastes isn't basic-typed", owns(me, ZoneType.Library, "Wastes"));
        AssertJUnit.assertEquals(1, countNamed(me, ZoneType.Library, "Swamp"));

        // No tapped creatures
        newGame();
        lands("Forest", 1, me);
        lands("Island", 1, me);
        creature("Carrion Feeder", me);
        library(me, "Swamp");
        choices(me).prefer = c -> true;
        cast(hand("Harvest Season", me));
        AssertJUnit.assertEquals(0, countNamed(me, ZoneType.Battlefield, "Swamp"));

        // Up to X
        newGame();
        lands("Forest", 1, me);
        lands("Island", 1, me);
        final Card c1 = creature("Carrion Feeder", me);
        final Card c2 = creature("Carrion Feeder", me);
        final Card c3 = creature("Carrion Feeder", me);
        c1.setTapped(true);
        c2.setTapped(true);
        c3.setTapped(true);
        library(me, "Swamp", "Swamp");
        choices(me).prefer = c -> true;
        cast(hand("Harvest Season", me));
        AssertJUnit.assertEquals("only two in the library", 2, countNamed(me, ZoneType.Battlefield, "Swamp"));
    }

    @Test
    public void heraldOfThePantheon() {
        newGame();
        final Card herald = castPaying("Herald of the Pantheon", 2);
        AssertJUnit.assertEquals(202, pt(herald));
        untapAll(me);

        // Enchantment spells cost 1 less, and gain 1 life
        final Card feeder = creature("Carrion Feeder", me);
        final Card fists = hand("Fists of Ironwood", me);
        cast(fists, feeder);
        AssertJUnit.assertEquals("1G less 1: G", 1, tappedLands(me));
        AssertJUnit.assertEquals("gain 1 life", 21, me.getLife());
        AssertJUnit.assertEquals(2, tokens(me).size());

        // Other spells aren't cheaper
        newGame();
        creature("Herald of the Pantheon", me);
        forests(1);
        final Card feeder2 = creature("Carrion Feeder", me);
        cast(hand("Giant Growth", me), feeder2);
        AssertJUnit.assertEquals(20, me.getLife());
        AssertJUnit.assertEquals(1, tappedLands(me));

        // The opponent's enchantment spells aren't cheaper for them
        newGame();
        creature("Herald of the Pantheon", me);
        lands("Forest", 1, opp);
        final Card theirs = creature("Carrion Feeder", opp);
        final Card fists2 = hand("Fists of Ironwood", opp);
        final SpellAbility sa = fists2.getFirstSpellAbility();
        sa.setActivatingPlayer(opp);
        AssertJUnit.assertFalse("costs 1G for them", forge.ai.ComputerUtil.handlePlayingSpellAbility(opp, sa, targeting(theirs)));
        AssertJUnit.assertEquals(20, me.getLife());
    }

    @Test
    public void hiddenHerbalists() {
        // No revolt: no mana
        newGame();
        final Card h = castPaying("Hidden Herbalists", 2);
        AssertJUnit.assertEquals(202, pt(h));
        AssertJUnit.assertEquals(0, me.getManaPool().totalMana());

        // Revolt: a permanent I controlled left play this turn
        newGame();
        kill(creature("Carrion Feeder", me));
        creature("Hidden Herbalists", me);
        AssertJUnit.assertEquals("revolt: two mana", 2, me.getManaPool().totalMana());

        // The opponent's permanent leaving doesn't count
        newGame();
        kill(creature("Carrion Feeder", opp));
        creature("Hidden Herbalists", me);
        AssertJUnit.assertEquals(0, me.getManaPool().totalMana());

        // Ward 2
        newGame();
        final Card ward = creature("Hidden Herbalists", me);
        AssertJUnit.assertFalse("can't pay 2 more: countered", wardLetsThrough(ward, 2));
        AssertJUnit.assertEquals(202, pt(current(ward)));
        newGame();
        final Card ward2 = creature("Hidden Herbalists", me);
        AssertJUnit.assertTrue("pays 2: resolves", wardLetsThrough(ward2, 3));
    }

    @Test
    public void highspireInfusion() {
        newGame();
        forests(1);
        final Card feeder = creature("Carrion Feeder", me);
        final Card hi = hand("Highspire Infusion", me);
        AssertJUnit.assertTrue("flash", current(hi).hasFlashSupertype());
        cast(hi, feeder);
        AssertJUnit.assertEquals("cost G", 1, tappedLands(me));
        AssertJUnit.assertEquals("+3/+3", 404, pt(current(feeder)));
        AssertJUnit.assertEquals("EE", 2, me.getCounters(CounterEnumType.ENERGY));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));
        AssertJUnit.assertEquals("energy stays", 2, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void honoredHydra() {
        newGame();
        final Card h = castPaying("Honored Hydra", 6);
        AssertJUnit.assertEquals(606, pt(h));
        AssertJUnit.assertTrue(h.hasKeyword(Keyword.TRAMPLE));

        // Embalm 3G
        newGame();
        forests(4);
        final Card e = grave("Honored Hydra", me);
        activate(e, "Embalm");
        AssertJUnit.assertEquals("embalm 3G", 4, tappedLands(me));
        AssertJUnit.assertTrue(in(e, ZoneType.Exile));
        AssertJUnit.assertEquals(1, tokens(me).size());
        final Card t = tokens(me).get(0);
        AssertJUnit.assertTrue("a copy", named(t, "Honored Hydra") && t.hasKeyword(Keyword.TRAMPLE) && pt(t) == 606);
        AssertJUnit.assertTrue("a white Zombie Snake Hydra", t.isWhite() && !t.isGreen() && t.getType().hasCreatureType("Zombie")
                && t.getType().hasCreatureType("Snake") && t.getType().hasCreatureType("Hydra"));

        // Not at flash speed
        newGame();
        final Card e2 = grave("Honored Hydra", me);
        playUntil(opp, PhaseType.MAIN1);
        final SpellAbility embalm = ability(current(e2), "Embalm");
        embalm.setActivatingPlayer(me);
        AssertJUnit.assertFalse("sorcery speed: not on the opponent's turn", embalm.canPlay());
        playUntil(me, PhaseType.MAIN1);
        embalm.setActivatingPlayer(me);
        AssertJUnit.assertTrue("on my own turn", embalm.canPlay());
    }

    @Test
    public void hootingMandrills() {
        newGame();
        for (int i = 0; i < 5; i++) {
            grave("Woolly Thoctar", me);
        }
        forests(1);
        final Card m = hand("Hooting Mandrills", me);
        cast(m);
        AssertJUnit.assertEquals("delve 5, pay G", 1, tappedLands(me));
        AssertJUnit.assertEquals("exiled five cards", 5, countNamed(me, ZoneType.Exile, "Woolly Thoctar"));
        AssertJUnit.assertEquals(0, countNamed(me, ZoneType.Graveyard, "Woolly Thoctar"));
        AssertJUnit.assertTrue(in(m, ZoneType.Battlefield));
        AssertJUnit.assertEquals(404, pt(current(m)));
        AssertJUnit.assertTrue(current(m).hasKeyword(Keyword.TRAMPLE));

        newGame();
        final Card m2 = castPaying("Hooting Mandrills", 6);
        AssertJUnit.assertTrue(in(m2, ZoneType.Battlefield));
    }

    @Test
    public void hydrasGrowth() {
        newGame();
        forests(2);
        final Card feeder = creature("Carrion Feeder", me);
        addCounters(feeder, CounterEnumType.P1P1, 2);
        addCounters(feeder, CounterEnumType.CHARGE, 2);
        final Card hg = hand("Hydras Growth", me);
        cast(hg, feeder);
        AssertJUnit.assertEquals("cost GG", 2, tappedLands(me));
        AssertJUnit.assertEquals(current(feeder), current(hg).getAttachedTo());
        AssertJUnit.assertEquals("a +1/+1 counter when it enters", 3, p1p1(current(feeder)));
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("not at the opponent's upkeep", 3, p1p1(current(feeder)));
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("doubled at my upkeep", 6, p1p1(current(feeder)));
        AssertJUnit.assertEquals("only +1/+1 counters", 2, current(feeder).getCounters(CounterEnumType.CHARGE));
        playUntil(opp, PhaseType.UPKEEP);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(12, p1p1(current(feeder)));
    }

    @Test
    public void imperviousGreatwurm() {
        newGame();
        forests(5);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        final Card c = creature("Carrion Feeder", me);
        playUntil(me, PhaseType.MAIN2); // the AI convokes only after combat
        final Card wurm = hand("Impervious Greatwurm", me);
        cast(wurm);
        AssertJUnit.assertTrue(in(wurm, ZoneType.Battlefield));
        AssertJUnit.assertEquals("5GGG: five Forests", 5, tappedLands(me));
        AssertJUnit.assertTrue("and convoke tapped all three", current(a).isTapped() && current(b).isTapped() && current(c).isTapped());
        AssertJUnit.assertEquals(1616, pt(current(wurm)));

        // Indestructible
        kill(wurm);
        AssertJUnit.assertTrue("indestructible", in(wurm, ZoneType.Battlefield));

        // Indomitable: can't be blocked by power 2 or less
        final Card power0 = creature("Ornithopter", opp);
        final Card power2 = creature("Herald of the Pantheon", opp);
        final Card power3 = creature("Kavu Climber", opp);
        AssertJUnit.assertFalse("power 0", CombatUtil.canBlock(current(wurm), current(power0)));
        AssertJUnit.assertFalse("power 2", CombatUtil.canBlock(current(wurm), current(power2)));
        AssertJUnit.assertTrue("power 3", CombatUtil.canBlock(current(wurm), current(power3)));
    }

    @Test
    public void kalonianTusker() {
        newGame();
        final Card t = castPaying("Kalonian Tusker", 2);
        AssertJUnit.assertEquals(303, pt(t));

        // Beastcycling 2
        newGame();
        forests(2);
        library(me, "Arbor Elf", "Kavu Climber");
        final Card tusker = hand("Kalonian Tusker", me);
        choices(me).prefer = called("Kavu Climber");
        activate(tusker, "cycling");
        AssertJUnit.assertEquals("2", 2, tappedLands(me));
        AssertJUnit.assertTrue(in(tusker, ZoneType.Graveyard));
        AssertJUnit.assertTrue("a Beast card", owns(me, ZoneType.Hand, "Kavu Climber"));
        AssertJUnit.assertEquals("no draw, just the search", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(0, me.getNumDrawnThisTurn());

        // A card that isn't a Beast can't be found
        newGame();
        forests(2);
        library(me, "Arbor Elf");
        final Card tusker2 = hand("Kalonian Tusker", me);
        choices(me).prefer = called("Arbor Elf");
        activate(tusker2, "cycling");
        AssertJUnit.assertFalse("an Elf is no Beast", owns(me, ZoneType.Hand, "Arbor Elf"));
    }

    @Test
    public void karametrasAcolyte() {
        newGame();
        final Card acolyte = castPaying("Karametras Acolyte", 3);
        AssertJUnit.assertEquals(102, pt(acolyte));
        acolyte.setSickness(false);
        untapAll(me);
        mana(acolyte, null);
        AssertJUnit.assertEquals("devotion 1: the Acolyte's own G", 1, me.getManaPool().getAmountOfColor(GREEN));
        me.getManaPool().clearPool(false);

        creature("Hooting Mandrills", me);
        creature("Kalonian Tusker", me);
        creature("Kalonian Tusker", opp);
        creature("Hooting Mandrills", opp);
        creature("Carrion Feeder", me);
        current(acolyte).setTapped(false);
        mana(acolyte, null);
        AssertJUnit.assertEquals("devotion: 1 + 1 + 2, not the opponent's", 4, me.getManaPool().getAmountOfColor(GREEN));
        AssertJUnit.assertEquals(4, me.getManaPool().totalMana());
    }

    @Test
    public void kavuClimber() {
        newGame();
        final Card k = castPaying("Kavu Climber", 4);
        AssertJUnit.assertTrue("flash", k.hasFlashSupertype());
        AssertJUnit.assertEquals(303, pt(k));
        AssertJUnit.assertTrue(k.hasKeyword(Keyword.REACH));
        AssertJUnit.assertEquals("draw a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, me.getNumDrawnThisTurn());
    }

    @Test
    public void llanowarStalker() {
        newGame();
        final Card s = castPaying("Llanowar Stalker", 1);
        AssertJUnit.assertEquals("itself doesn't count", 101, pt(s));
        enter("Carrion Feeder", me);
        AssertJUnit.assertEquals("+1/+0", 201, pt(current(s)));
        enter("Ornithopter", me);
        AssertJUnit.assertEquals(301, pt(current(s)));
        enter("Carrion Feeder", opp);
        AssertJUnit.assertEquals("an opponent's creature", 301, pt(current(s)));
        enter("Forest", me);
        AssertJUnit.assertEquals("a land", 301, pt(current(s)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(s)));
    }

    @Test
    public void longtuskCub() {
        newGame();
        final Card cub = castPaying("Longtusk Cub", 2);
        AssertJUnit.assertEquals(202, pt(cub));
        cub.setSickness(false);
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));
        attack(me, null, cub);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals("EE for damage to a player", 2, me.getCounters(CounterEnumType.ENERGY));

        // Pay EEE
        AssertJUnit.assertFalse("two energy isn't enough", canActivate(cub, "+1/+1 counter"));
        me.setCounters(CounterEnumType.ENERGY, 3);
        activate(cub, "+1/+1 counter");
        AssertJUnit.assertEquals("paid EEE", 0, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals(1, p1p1(current(cub)));
        AssertJUnit.assertEquals(303, pt(current(cub)));
        AssertJUnit.assertTrue("trample this turn", current(cub).hasKeyword(Keyword.TRAMPLE));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse(current(cub).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertEquals(1, p1p1(current(cub)));

        // Damage to a creature gives nothing
        newGame();
        final Card cub2 = creature("Longtusk Cub", me);
        final Card blocker = creature("Ornithopter", opp);
        attack(me, Map.of(blocker, cub2), cub2);
        AssertJUnit.assertEquals(20, opp.getLife());
        AssertJUnit.assertEquals(0, me.getCounters(CounterEnumType.ENERGY));
    }

    @Test
    public void lotusCobra() {
        newGame();
        final Card cobra = castPaying("Lotus Cobra", 2);
        AssertJUnit.assertEquals(201, pt(cobra));
        choices(me).confirm = true;
        enter("Forest", me);
        AssertJUnit.assertEquals("landfall: one mana of any color", 1, me.getManaPool().totalMana());
        enter("Forest", me);
        AssertJUnit.assertEquals(2, me.getManaPool().totalMana());
        choices(me).confirm = false;
        enter("Forest", me);
        AssertJUnit.assertEquals("a may", 2, me.getManaPool().totalMana());
        choices(me).confirm = true;
        enter("Forest", opp);
        AssertJUnit.assertEquals("an opponent's land", 2, me.getManaPool().totalMana());
        enter("Carrion Feeder", me);
        AssertJUnit.assertEquals("not a land", 2, me.getManaPool().totalMana());
    }

    @Test
    public void magusOfTheLibrary() {
        newGame();
        final Card m = castPaying("Magus of the Library", 2);
        AssertJUnit.assertEquals(104, pt(m));
        m.setSickness(false);
        for (int n : new int[] {0, 6, 8}) {
            for (final Card c : new ArrayList<>(me.getCardsIn(ZoneType.Hand))) {
                game.getAction().exile(c, null, null);
            }
            for (int i = 0; i < n; i++) {
                hand("Woolly Thoctar", me);
            }
            AssertJUnit.assertFalse("exactly seven cards in hand, not " + n, canActivate(m, "Draw a card"));
        }
        for (final Card c : new ArrayList<>(me.getCardsIn(ZoneType.Hand))) {
            game.getAction().exile(c, null, null);
        }
        for (int i = 0; i < 7; i++) {
            hand("Woolly Thoctar", me);
        }
        AssertJUnit.assertTrue(canActivate(m, "Draw a card"));
        activate(m, "Draw a card");
        AssertJUnit.assertEquals(8, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(current(m).isTapped());

        // T: add C
        untapAll(me);
        mana(m, "Add");
        AssertJUnit.assertEquals(1, me.getManaPool().getAmountOfColor(COLORLESS));
        AssertJUnit.assertEquals(1, me.getManaPool().totalMana());
    }

    @Test
    public void magusOfTheVineyard() {
        newGame();
        final Card m = castPaying("Magus of the Vineyard", 1);
        AssertJUnit.assertEquals(101, pt(m));
        AssertJUnit.assertEquals("not in this main phase", 0, me.getManaPool().totalMana());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("the opponent adds GG at their first main phase", 2, opp.getManaPool().getAmountOfColor(GREEN));
        AssertJUnit.assertEquals(2, opp.getManaPool().totalMana());
        AssertJUnit.assertEquals(0, me.getManaPool().totalMana());
        playUntil(me, PhaseType.MAIN1);
        AssertJUnit.assertEquals("and I add GG at mine", 2, me.getManaPool().getAmountOfColor(GREEN));
        AssertJUnit.assertEquals(2, me.getManaPool().totalMana());
        playUntil(me, PhaseType.MAIN2);
        AssertJUnit.assertEquals("not the second main phase", 0, me.getManaPool().totalMana());
    }

    @Test
    public void mammothGrowth() {
        newGame();
        forests(4);
        final Card feeder = creature("Carrion Feeder", me);
        final Card mg = hand("Mammoth Growth", me);
        AssertJUnit.assertTrue("flash", current(mg).hasFlashSupertype());
        cast(mg, feeder);
        AssertJUnit.assertEquals("cost 3G", 4, tappedLands(me));
        AssertJUnit.assertEquals("+6/+6", 707, pt(current(feeder)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));

        // Foretell G: pay 2, exile it; cast it later for G
        newGame();
        forests(2);
        final Card c = creature("Carrion Feeder", me);
        final Card mg2 = hand("Mammoth Growth", me);
        cast(me, current(mg2).getAllSpellAbilities().stream().filter(SpellAbility::isForetelling).findFirst().orElseThrow(), null);
        AssertJUnit.assertEquals("foretell costs 2", 2, tappedLands(me));
        AssertJUnit.assertTrue(in(mg2, ZoneType.Exile) && current(mg2).isForetold());
        playUntil(me, PhaseType.MAIN1);
        cast(me, alternative(mg2, SpellAbility::isForetold), targeting(c));
        AssertJUnit.assertEquals("foretold cost G", 1, tappedLands(me));
        AssertJUnit.assertEquals("+6/+6", 707, pt(current(c)));
    }

    @Test
    public void marwynTheNurturer() {
        newGame();
        final Card marwyn = castPaying("Marwyn the Nurturer", 3);
        AssertJUnit.assertEquals(101, pt(marwyn));
        marwyn.setSickness(false);
        AssertJUnit.assertEquals("itself doesn't count", 0, p1p1(current(marwyn)));
        enter("Arbor Elf", me);
        AssertJUnit.assertEquals("another Elf", 1, p1p1(current(marwyn)));
        enter("Carrion Feeder", me);
        AssertJUnit.assertEquals("not an Elf", 1, p1p1(current(marwyn)));
        enter("Arbor Elf", opp);
        AssertJUnit.assertEquals("an opponent's Elf", 1, p1p1(current(marwyn)));
        enter("Arbor Elf", me);
        AssertJUnit.assertEquals(2, p1p1(current(marwyn)));
        mana(marwyn, null);
        AssertJUnit.assertEquals("an amount of G equal to its power", 3, me.getManaPool().getAmountOfColor(GREEN));
        AssertJUnit.assertEquals(3, me.getManaPool().totalMana());
    }

    @Test
    public void momentsPeace() {
        newGame();
        forests(2);
        final Card atk = creature("Woolly Thoctar", me);
        final Card mp = hand("Moments Peace", me);
        AssertJUnit.assertTrue("flash", current(mp).hasFlashSupertype());
        cast(mp);
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        attack(me, null, atk);
        AssertJUnit.assertEquals("no combat damage", 20, opp.getLife());

        newGame();
        final Card atk2 = creature("Woolly Thoctar", me);
        attack(me, null, atk2);
        AssertJUnit.assertEquals("without it, damage", 14, opp.getLife());

        // To creatures too
        newGame();
        forests(2);
        final Card atk3 = creature("Woolly Thoctar", me);
        final Card blocker = creature("Ornithopter", opp);
        cast(hand("Moments Peace", me));
        attack(me, Map.of(blocker, atk3), atk3);
        AssertJUnit.assertTrue("the blocker takes no damage", in(blocker, ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, current(blocker).getDamage());

        // Flashback 2G, then exiled
        newGame();
        forests(3);
        final Card atk4 = creature("Woolly Thoctar", me);
        final Card mp2 = grave("Moments Peace", me);
        cast(me, alternative(mp2, SpellAbility::isFlashback), null);
        AssertJUnit.assertEquals("flashback 2G", 3, tappedLands(me));
        AssertJUnit.assertTrue("exiled", in(mp2, ZoneType.Exile));
        attack(me, null, atk4);
        AssertJUnit.assertEquals(20, opp.getLife());
    }

    @Test
    public void monstrify() {
        newGame();
        forests(8);
        final Card feeder = creature("Carrion Feeder", me);
        final Card m = hand("Monstrify", me);
        cast(m, feeder);
        AssertJUnit.assertEquals("cost 3G", 4, tappedLands(me));
        AssertJUnit.assertEquals("four +1/+1 counters", 4, p1p1(current(feeder)));
        AssertJUnit.assertTrue("trample this turn", current(feeder).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertTrue(in(m, ZoneType.Graveyard));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse(current(feeder).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertEquals(4, p1p1(current(feeder)));

        // Retrace: discard a land card as an additional cost
        playUntil(me, PhaseType.MAIN1);
        final Card forest = hand("Forest", me);
        cast(me, withOptionalCosts(current(m)), targeting(feeder));
        AssertJUnit.assertEquals("eight counters", 8, p1p1(current(feeder)));
        AssertJUnit.assertTrue("discarded the land", in(forest, ZoneType.Graveyard));
        AssertJUnit.assertTrue("Monstrify is in the graveyard again", in(m, ZoneType.Graveyard));
    }

    @Test
    public void monstrousOnslaught() {
        newGame();
        forests(5);
        final Card big = creature("Woolly Thoctar", me);
        creature("Carrion Feeder", me);
        final Card t1 = creature("Woolly Thoctar", opp);
        final Card t2 = creature("Arbor Elf", opp);
        final Card t3 = creature("Ornithopter", opp);
        final Card mo = hand("Monstrous Onslaught", me);
        cast(me, current(mo).getFirstSpellAbility(), sa -> {
            final int x = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("DividedAsYouChoose"), sa);
            AssertJUnit.assertEquals("the greatest power among my creatures", 6, x);
            sa.getTargets().add(t1);
            sa.getTargets().add(t2);
            sa.getTargets().add(t3);
            sa.addDividedAllocation(t1, 4);
            sa.addDividedAllocation(t2, 1);
            sa.addDividedAllocation(t3, x - 5);
        });
        AssertJUnit.assertEquals("cost 3GG", 5, tappedLands(me));
        AssertJUnit.assertTrue("4 damage: dead", in(t1, ZoneType.Graveyard));
        AssertJUnit.assertTrue("1 damage: dead", in(t2, ZoneType.Graveyard));
        AssertJUnit.assertEquals("1 damage", 1, current(t3).getDamage());
        AssertJUnit.assertTrue(in(big, ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, current(big).getDamage());
        AssertJUnit.assertEquals("it targets creatures only", 20, opp.getLife());
        AssertJUnit.assertFalse(canTarget(current(mo), me, opp));
    }

    @Test
    public void monstrousVortex() {
        newGame();
        forests(6);
        enter("Monstrous Vortex", me);
        library(me, "Giant Growth", "Hooting Mandrills", "Ghoultree");
        choices(me).confirm = false;
        cast(hand("Honored Hydra", me));
        AssertJUnit.assertEquals("a creature spell with power 6: discover 6", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue("mana value 6 or less: Hooting Mandrills", owns(me, ZoneType.Hand, "Hooting Mandrills"));
        AssertJUnit.assertTrue("passed over Ghoultree, which went to the bottom", owns(me, ZoneType.Library, "Ghoultree")
                && !named(top(me), "Ghoultree"));
        AssertJUnit.assertTrue("and stopped at Mandrills", owns(me, ZoneType.Library, "Giant Growth"));

        // Power 4: no trigger
        newGame();
        forests(6);
        enter("Monstrous Vortex", me);
        library(me, "Giant Growth", "Hooting Mandrills", "Ghoultree");
        choices(me).confirm = false;
        cast(hand("Hooting Mandrills", me));
        AssertJUnit.assertEquals("power 4", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(named(top(me), "Ghoultree"));

        // The opponent's creature spell: no trigger
        newGame();
        enter("Monstrous Vortex", me);
        lands("Forest", 6, opp);
        library(me, "Giant Growth", "Hooting Mandrills", "Ghoultree");
        choices(me).confirm = false;
        oppCasts("Honored Hydra");
        resolve();
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(named(top(me), "Ghoultree"));

        // A noncreature spell with power 5 or more can't exist; a spell that isn't a creature: no trigger
        newGame();
        forests(1);
        enter("Monstrous Vortex", me);
        library(me, "Giant Growth", "Hooting Mandrills", "Ghoultree");
        cast(hand("Giant Growth", me), creature("Woolly Thoctar", me));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(named(top(me), "Ghoultree"));
    }

    @Test
    public void mossbornHydra() {
        newGame();
        final Card h = castPaying("Mossborn Hydra", 3);
        AssertJUnit.assertEquals("enters with a +1/+1 counter", 1, p1p1(h));
        AssertJUnit.assertEquals(101, pt(current(h)));
        AssertJUnit.assertTrue(h.hasKeyword(Keyword.TRAMPLE));
        enter("Forest", me);
        AssertJUnit.assertEquals("landfall: double", 2, p1p1(current(h)));
        enter("Forest", me);
        AssertJUnit.assertEquals(4, p1p1(current(h)));
        AssertJUnit.assertEquals(404, pt(current(h)));
        enter("Forest", opp);
        AssertJUnit.assertEquals("an opponent's land", 4, p1p1(current(h)));
        enter("Carrion Feeder", me);
        AssertJUnit.assertEquals("not a land", 4, p1p1(current(h)));
    }

    @Test
    public void mycoloth() {
        newGame();
        forests(5);
        final Card feeder = creature("Carrion Feeder", me);
        choices(me).confirm = true;
        choices(me).prefer = is(feeder);
        final Card m = hand("Mycoloth", me);
        cast(m);
        AssertJUnit.assertEquals("cost 3GG", 5, tappedLands(me));
        AssertJUnit.assertTrue(in(feeder, ZoneType.Graveyard));
        AssertJUnit.assertEquals("devour 2: two counters per creature", 2, p1p1(current(m)));
        AssertJUnit.assertEquals(606, pt(current(m)));
        choices(me).confirm = null;
        playUntil(opp, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("my upkeep only", 0, tokens(me).size());
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals("a Saproling for each counter", 2, tokens(me).size());
        for (final Card t : tokens(me)) {
            AssertJUnit.assertTrue(t.getType().hasSubtype("Saproling") && t.isGreen());
            AssertJUnit.assertEquals(101, pt(t));
        }

        // No counters, no tokens
        newGame();
        choices(me).confirm = false;
        creature("Mycoloth", me);
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertEquals(0, tokens(me).size());

        // Ward 2
        newGame();
        choices(me).confirm = false;
        final Card w = creature("Mycoloth", me);
        AssertJUnit.assertFalse(wardLetsThrough(w, 2));
        newGame();
        choices(me).confirm = false;
        final Card w2 = creature("Mycoloth", me);
        AssertJUnit.assertTrue(wardLetsThrough(w2, 3));
    }

    @Test
    public void naturalState() {
        newGame();
        forests(1);
        final Card thopter = creature("Ornithopter", opp);
        final Card ns = hand("Natural State", me);
        AssertJUnit.assertTrue("flash", current(ns).hasFlashSupertype());
        cast(ns, thopter);
        AssertJUnit.assertEquals("cost G", 1, tappedLands(me));
        AssertJUnit.assertTrue("an artifact with mana value 0", in(thopter, ZoneType.Graveyard));

        newGame();
        forests(1);
        final Card trail = enter("On the Trail", opp);
        cast(hand("Natural State", me), trail);
        AssertJUnit.assertTrue("an enchantment with mana value 1", in(trail, ZoneType.Graveyard));

        newGame();
        forests(1);
        final Card ns2 = hand("Natural State", me);
        AssertJUnit.assertFalse("mana value 4", canTarget(ns2, me, enter("From Beyond", opp)));
        AssertJUnit.assertFalse("a creature that isn't an artifact", canTarget(ns2, me, creature("Carrion Feeder", opp)));
        final Card orni = creature("Ornithopter", me);
        AssertJUnit.assertTrue("my own too", canTarget(ns2, me, orni));
    }

    @Test
    public void naturesLore() {
        newGame();
        library(me, "Swamp", "Forest");
        choices(me).prefer = c -> true;
        forests(2);
        cast(hand("Natures Lore", me));
        AssertJUnit.assertEquals("cost 1G", 2, tappedLands(me));
        AssertJUnit.assertEquals("a Forest into play", 3, countNamed(me, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals("untapped", 1, me.getCardsIn(ZoneType.Battlefield).stream().filter(c -> !current(c).isTapped()).count());
        AssertJUnit.assertTrue("not the Swamp", owns(me, ZoneType.Library, "Swamp"));

        // Only a Swamp in the library
        newGame();
        library(me, "Swamp");
        choices(me).prefer = c -> true;
        forests(2);
        cast(hand("Natures Lore", me));
        AssertJUnit.assertEquals(0, countNamed(me, ZoneType.Battlefield, "Swamp"));
        AssertJUnit.assertEquals(2, countNamed(me, ZoneType.Battlefield, "Forest"));
    }

    @Test
    public void naturesSpiral() {
        newGame();
        forests(1);
        final Card dead = grave("Carrion Feeder", me);
        final Card spiral = hand("Natures Spiral", me);
        cast(spiral, dead);
        AssertJUnit.assertEquals("cost G", 1, tappedLands(me));
        AssertJUnit.assertTrue("returned to my hand", in(dead, ZoneType.Hand));
        AssertJUnit.assertEquals("that player gains 2 life", 22, me.getLife());
        AssertJUnit.assertEquals(20, opp.getLife());

        // An opponent's graveyard: it returns to their hand and they gain the life
        newGame();
        forests(1);
        final Card theirs = grave("Carrion Feeder", opp);
        cast(hand("Natures Spiral", me), theirs);
        AssertJUnit.assertTrue(in(theirs, ZoneType.Hand) && theirs.getOwner() == opp);
        AssertJUnit.assertEquals("its owner gains the life", 22, opp.getLife());
        AssertJUnit.assertEquals(20, me.getLife());

        // Nonsorcery: a sorcery (Flash is a supertype) can't be targeted
        newGame();
        final Card spiral2 = hand("Natures Spiral", me);
        AssertJUnit.assertFalse("a sorcery", canTarget(spiral2, me, grave("Explore", me)));
        AssertJUnit.assertFalse("a flash sorcery", canTarget(spiral2, me, grave("Giant Growth", me)));
        AssertJUnit.assertTrue("an enchantment", canTarget(spiral2, me, grave("On the Trail", me)));
        AssertJUnit.assertTrue("a land", canTarget(spiral2, me, grave("Forest", opp)));
        AssertJUnit.assertFalse("a card in the hand", canTarget(spiral2, me, hand("Carrion Feeder", me)));
    }

    @Test
    public void nooseConstrictor() {
        newGame();
        final Card n = castPaying("Noose Constrictor", 2);
        AssertJUnit.assertTrue("flash", n.hasFlashSupertype());
        AssertJUnit.assertTrue(n.hasKeyword(Keyword.REACH));
        AssertJUnit.assertEquals(202, pt(n));
        AssertJUnit.assertFalse("nothing to discard", canActivate(n, "+1/+1"));
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        activate(n, "+1/+1");
        AssertJUnit.assertEquals(303, pt(current(n)));
        AssertJUnit.assertEquals("discarded a card", 1, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Graveyard));
        activate(n, "+1/+1");
        AssertJUnit.assertEquals(404, pt(current(n)));
        AssertJUnit.assertEquals(0, count(me, ZoneType.Hand));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 202, pt(current(n)));
    }

    @Test
    public void nullmageShepherd() {
        newGame();
        final Card shepherd = castPaying("Nullmage Shepherd", 4);
        AssertJUnit.assertEquals(204, pt(shepherd));
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        final Card c = creature("Carrion Feeder", me);
        final Card thopter = creature("Ornithopter", opp);
        final Card trail = enter("On the Trail", opp);
        AssertJUnit.assertFalse("can't target a creature that isn't an artifact", ability(current(shepherd), "Destroy").canTarget(a));
        a.setTapped(true);
        AssertJUnit.assertFalse("only three untapped creatures", canActivate(shepherd, "Destroy"));
        a.setTapped(false);
        AssertJUnit.assertTrue(canActivate(shepherd, "Destroy"));
        activate(shepherd, "Destroy", thopter);
        AssertJUnit.assertTrue("an artifact", in(thopter, ZoneType.Graveyard));
        AssertJUnit.assertTrue("tapped four creatures", current(shepherd).isTapped() && current(a).isTapped()
                && current(b).isTapped() && current(c).isTapped());
        AssertJUnit.assertFalse("none untapped", canActivate(shepherd, "Destroy"));
        untapAll(me);
        activate(shepherd, "Destroy", trail);
        AssertJUnit.assertTrue("an enchantment", in(trail, ZoneType.Graveyard));
    }

    @Test
    public void nyleasColossus() {
        newGame();
        final Card feeder = creature("Carrion Feeder", me);
        choices(me).targets = List.of(feeder);
        final Card col = castPaying("Nyleas Colossus", 4);
        AssertJUnit.assertEquals(404, pt(col));
        AssertJUnit.assertTrue(col.isEnchantment() && col.isCreature());
        AssertJUnit.assertEquals("it entered: double target creature", 202, pt(current(feeder)));
        enter("On the Trail", me);
        AssertJUnit.assertEquals("another enchantment: double again", 404, pt(current(feeder)));
        enter("Carrion Feeder", me);
        AssertJUnit.assertEquals("a creature isn't an enchantment", 404, pt(current(feeder)));
        enter("On the Trail", opp);
        AssertJUnit.assertEquals("an opponent's enchantment", 404, pt(current(feeder)));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", 101, pt(current(feeder)));
    }

    @Test
    public void nyxbornHydra() {
        newGame();
        forests(3);
        final Card h = hand("Nyxborn Hydra", me);
        h.getFirstSpellAbility().setXManaCostPaid(2);
        cast(h);
        AssertJUnit.assertEquals("XG with X = 2", 3, tappedLands(me));
        AssertJUnit.assertEquals("enters with X counters", 2, p1p1(current(h)));
        AssertJUnit.assertEquals(202, pt(current(h)));
        AssertJUnit.assertTrue(current(h).hasKeyword(Keyword.REACH) && current(h).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertTrue(current(h).isEnchantment() && current(h).isCreature());

        // Bestow XGG
        newGame();
        forests(4);
        final Card feeder = creature("Carrion Feeder", me);
        final Card h2 = hand("Nyxborn Hydra", me);
        final SpellAbility bestow = alternative(h2, SpellAbility::isBestow);
        bestow.setXManaCostPaid(2);
        cast(me, bestow, targeting(feeder));
        AssertJUnit.assertEquals("bestow XGG with X = 2", 4, tappedLands(me));
        AssertJUnit.assertEquals(current(feeder), current(h2).getAttachedTo());
        AssertJUnit.assertEquals("+1/+1 for each counter on it", 303, pt(current(feeder)));
        AssertJUnit.assertTrue("and reach and trample", current(feeder).hasKeyword(Keyword.REACH) && current(feeder).hasKeyword(Keyword.TRAMPLE));
        AssertJUnit.assertFalse("an Aura, not a creature", current(h2).isCreature());
        kill(feeder);
        AssertJUnit.assertTrue("a creature again", in(h2, ZoneType.Battlefield) && current(h2).isCreature());
        AssertJUnit.assertEquals(202, pt(current(h2)));
    }

    @Test
    public void onTheTrail() {
        newGame();
        castPaying("On the Trail", 1);
        library(me, "Forest", "Forest", "Forest");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        me.drawCards(1);
        resolve();
        AssertJUnit.assertEquals("the first card: nothing", 1, countNamed(me, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        me.drawCards(1);
        resolve();
        AssertJUnit.assertEquals("the second card: a land into play", 2, countNamed(me, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        me.drawCards(1);
        resolve();
        AssertJUnit.assertEquals("the third: nothing", 2, count(me, ZoneType.Hand));

        // Both cards at once
        newGame();
        enter("On the Trail", me);
        library(me, "Forest", "Forest");
        choices(me).confirm = true;
        choices(me).prefer = c -> true;
        me.drawCards(2);
        resolve();
        AssertJUnit.assertEquals(1, countNamed(me, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));

        // Declined; no land in hand; the opponent's draws
        newGame();
        enter("On the Trail", me);
        library(me, "Forest", "Forest");
        choices(me).confirm = false;
        me.drawCards(2);
        resolve();
        AssertJUnit.assertEquals("a may", 0, countNamed(me, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Hand));

        newGame();
        enter("On the Trail", me);
        library(opp, "Forest", "Forest");
        choices(opp).confirm = true;
        choices(opp).prefer = c -> true;
        opp.drawCards(2);
        resolve();
        AssertJUnit.assertEquals(0, countNamed(opp, ZoneType.Battlefield, "Forest"));
        AssertJUnit.assertEquals(0, countNamed(me, ZoneType.Battlefield, "Forest"));

        newGame();
        enter("On the Trail", me);
        me.drawCards(2);
        resolve();
        AssertJUnit.assertEquals("no land in hand", 2, count(me, ZoneType.Hand));
    }

    @Test
    public void oranRiefOoze() {
        newGame();
        final Card feeder = creature("Arbor Elf", me);
        choices(me).targets = List.of(feeder);
        final Card ooze = castPaying("Oran-Rief Ooze", 3);
        AssertJUnit.assertEquals(202, pt(ooze));
        AssertJUnit.assertEquals("a counter on target creature I control", 1, p1p1(current(feeder)));
        ooze.setSickness(false);
        attack(me, null, ooze);
        AssertJUnit.assertEquals("attacks: proliferate", 2, p1p1(current(feeder)));

        // Another creature attacking doesn't
        newGame();
        final Card ooze2 = creature("Oran-Rief Ooze", me);
        final Card feeder2 = creature("Arbor Elf", me);
        addCounters(feeder2, CounterEnumType.P1P1, 1);
        attack(me, null, feeder2);
        AssertJUnit.assertEquals(1, p1p1(current(feeder2)));
        AssertJUnit.assertTrue(in(ooze2, ZoneType.Battlefield));
    }
}
