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
import java.util.Map;
import java.util.function.Predicate;

/** Multicolour TBLD cards, second batch: each card's abilities played in an engine game and checked against its text. */
public class TbldMultiCards2Test extends TbldCardTest {

    /** The cards whose abilities this test plays. */
    public static final List<String> VERIFIED = List.of(
            "Glare of Subdual", "Gleam of Battle", "Goblin Deathraiders", "Grove Rumbler", "Hardened Tactician", "Herald of Kozilek",
            "Heroic Reinforcements", "High Priest of Penance", "Horned Kavu", "Hussar Patrol", "Hypersonic Dragon", "Ideas Unbound",
            "Infernal Tribute", "Jeskai Ascendancy", "Jeskai Shrinekeeper", "Knowledge is Power", "Kraul Whipcracker",
            "Lazav Wearer of Faces", "Lightning Helix", "Marrow Chomper", "Merfolk Mistbinder", "Moderation", "Mortify",
            "Nivmagus Elemental", "Obeka Brute Chronologist", "Ochran Assassin", "Putrid Leech", "Rakdos Headliner",
            "Razorfin Hunter", "Recoil", "Riddle Gate Gargoyle", "Righteous Authority", "Rubblebelt Rioters", "Ruination Rioter",
            "Scab-Clan Mauler", "Slippery Bogle", "Song of the Dryads", "Spike Jester", "Staggering Insight", "Stitch in Time",
            "Sultai Soothsayer", "Swiftblade Vindicator", "Tajic Blade of the Legion", "Terminal Agony", "Treasury Thrull",
            "Tyrants Scorn", "Unmake", "Venomthrope", "Vona Butcher of Magan", "Zhur-Taa Ancient", "Zhur-Taa Goblin");

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

    private boolean canCast(final Card spell, final Player p) {
        final SpellAbility sa = current(spell).getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        return sa.canPlay();
    }

    /** The card's spell with one of its alternative additional costs ("discard a card or pay 3 life"). */
    private SpellAbility withAdditionalCost(final Card c, final Class<? extends forge.game.cost.CostPart> type) {
        final SpellAbility base = current(c).getFirstSpellAbility();
        base.setActivatingPlayer(c.getController());
        return forge.game.GameActionUtil.getAdditionalCostSpell(base).stream()
                .filter(sa -> sa.getPayCosts().hasSpecificCostType(type)).findFirst().orElseThrow();
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
    public void glareOfSubdual() {
        newGame();
        final Card glare = enter("Glare of Subdual", me);
        final Card mine = creature("Carrion Feeder", me);
        final Card t = creature("Woolly Thoctar", opp);
        activate(glare, "Tap target", t);
        AssertJUnit.assertTrue(current(t).isTapped());
        AssertJUnit.assertTrue("tapped a creature to pay", current(mine).isTapped());
    }

    @Test
    public void gleamOfBattle() {
        newGame();
        enter("Gleam of Battle", me);
        final Card a = creature("Carrion Feeder", me);
        attack(me, null, a);
        AssertJUnit.assertEquals("attacks", 1, p1p1(current(a)));
        final Card blocker = creature("Woolly Thoctar", me);
        final Card attacker = creature("Carrion Feeder", opp);
        playUntil(opp, PhaseType.MAIN1);
        attack(opp, Map.of(blocker, attacker), attacker);
        AssertJUnit.assertEquals("blocks", 1, p1p1(current(blocker)));
    }

    @Test
    public void goblinDeathraiders() {
        newGame();
        final Card g = creature("Goblin Deathraiders", me);
        AssertJUnit.assertTrue(g.hasKeyword(Keyword.TRAMPLE));
        final Card b = creature("Ornithopter", opp);
        attack(me, Map.of(b, g), g);
        AssertJUnit.assertEquals("trample", 19, opp.getLife());
    }

    @Test
    public void groveRumbler() {
        newGame();
        final Card g = creature("Grove Rumbler", me);
        AssertJUnit.assertTrue(g.hasKeyword(Keyword.TRAMPLE));
        enter("Forest", me);
        AssertJUnit.assertEquals(5, current(g).getNetPower());
        AssertJUnit.assertEquals(5, current(g).getNetToughness());
        enter("Forest", opp);
        AssertJUnit.assertEquals("only your lands", 5, current(g).getNetPower());
    }

    @Test
    public void hardenedTactician() {
        newGame();
        lands("Swamp", 1, me);
        final Card ht = creature("Hardened Tactician", me);
        final Card goat = addToken("w_0_1_goat", me);
        activate(ht, "Draw a card");
        AssertJUnit.assertFalse(in(goat, ZoneType.Battlefield));
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertFalse("a token is needed", canActivate(ht, "Draw a card"));
    }

    @Test
    public void heraldOfKozilek() {
        newGame();
        final Card h = creature("Herald of Kozilek", me);
        AssertJUnit.assertTrue(h.isColorless());
        lands("Swamp", 1, me);
        lands("Mountain", 1, me);
        final Card fd = hand("Flayer Drone", me);
        cast(fd);
        AssertJUnit.assertTrue("1BR for BR", in(fd, ZoneType.Battlefield));
    }

    @Test
    public void heroicReinforcements() {
        newGame();
        lands("Mountain", 3, me);
        lands("Plains", 1, me);
        final Card t = creature("Carrion Feeder", me);
        cast(hand("Heroic Reinforcements", me));
        AssertJUnit.assertEquals(2, tokens(me).size());
        for (final Card s : tokens(me)) {
            AssertJUnit.assertTrue(s.getType().hasCreatureType("Soldier") && s.isWhite());
            AssertJUnit.assertEquals(2, s.getNetPower());
            AssertJUnit.assertTrue(s.hasKeyword(Keyword.HASTE));
        }
        AssertJUnit.assertEquals(2, current(t).getNetPower());
        AssertJUnit.assertTrue(current(t).hasKeyword(Keyword.HASTE));
    }

    @Test
    public void highPriestOfPenance() {
        newGame();
        final Card hp = creature("High Priest of Penance", me);
        final Card t = creature("Woolly Thoctar", opp);
        choices(me).confirm = true;
        choices(me).targets = List.of(t);
        damage(creature("Carrion Feeder", opp), hp, 1, false);
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard));
    }

    @Test
    public void hornedKavu() {
        newGame();
        final Card t = creature("Woolly Thoctar", me);
        choices(me).prefer = is(t);
        final Card hk = enter("Horned Kavu", me);
        AssertJUnit.assertTrue(hk.hasKeyword(Keyword.HASTE));
        AssertJUnit.assertTrue("a red or green creature returned", in(t, ZoneType.Hand));
        AssertJUnit.assertTrue(in(hk, ZoneType.Battlefield));
    }

    @Test
    public void hussarPatrol() {
        newGame();
        lands("Plains", 1, me);
        lands("Island", 1, me);
        playUntil(opp, PhaseType.MAIN1);
        final Card hp = hand("Hussar Patrol", me);
        AssertJUnit.assertTrue("flash", canCast(hp, me));
        cast(hp);
        AssertJUnit.assertTrue(current(hp).hasKeyword(Keyword.VIGILANCE));
        playUntil(me, PhaseType.MAIN1);
        final Card bl = oppBlightning();
        choices(opp).payUnless = false;
        activate(hp, "Counter target", game.getStack().peekAbility());
        AssertJUnit.assertTrue(in(hp, ZoneType.Graveyard) && in(bl, ZoneType.Graveyard));
        AssertJUnit.assertEquals(20, me.getLife());
    }

    @Test
    public void hypersonicDragon() {
        newGame();
        final Card hd = creature("Hypersonic Dragon", me);
        AssertJUnit.assertTrue(hasAll(hd, Keyword.FLYING, Keyword.HASTE));
        lands("Swamp", 2, me);
        lands("Mountain", 1, me);
        playUntil(opp, PhaseType.MAIN1);
        final Card bl = hand("Blightning", me);
        AssertJUnit.assertTrue("a sorcery at flash speed", canCast(bl, me));
        cast(bl, opp);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertEquals("added U or R", 1, me.getManaPool().totalMana());
    }

    @Test
    public void ideasUnbound() {
        newGame();
        lands("Island", 1, me);
        lands("Mountain", 1, me);
        cast(hand("Ideas Unbound", me));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Hand));
        playUntil(me, PhaseType.END_OF_TURN);
        AssertJUnit.assertEquals("discarded three at the end step", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(4, count(me, ZoneType.Graveyard));
    }

    @Test
    public void infernalTribute() {
        newGame();
        lands("Swamp", 1, me);
        lands("Mountain", 1, me);
        final Card it = hand("Infernal Tribute", me);
        final Card fodder = hand("Swamp", me);
        cast(me, withAdditionalCost(it, forge.game.cost.CostDiscard.class), null);
        AssertJUnit.assertTrue("discarded", in(fodder, ZoneType.Graveyard));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(20, me.getLife());
        newGame();
        lands("Swamp", 1, me);
        lands("Mountain", 1, me);
        final Card it2 = hand("Infernal Tribute", me);
        cast(me, withAdditionalCost(it2, forge.game.cost.CostPayLife.class), null);
        AssertJUnit.assertEquals("or paid 3 life", 17, me.getLife());
        AssertJUnit.assertEquals(2, count(me, ZoneType.Hand));
    }

    @Test
    public void jeskaiAscendancy() {
        newGame();
        lands("Forest", 1, me);
        enter("Jeskai Ascendancy", me);
        final Card t = creature("Carrion Feeder", me);
        current(t).setTapped(true);
        choices(me).confirm = true;
        cast(hand("Giant Growth", me), t);
        AssertJUnit.assertEquals("3 + 1", 5, current(t).getNetPower());
        AssertJUnit.assertFalse("untapped", current(t).isTapped());
        AssertJUnit.assertEquals("drew one, discarded one", 0, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(2, count(me, ZoneType.Graveyard));
    }

    @Test
    public void jeskaiShrinekeeper() {
        newGame();
        final Card js = enter("Jeskai Shrinekeeper", me);
        AssertJUnit.assertTrue(hasAll(js, Keyword.FLYING, Keyword.HASTE));
        attack(me, null, js);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertEquals(21, me.getLife());
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
    }

    @Test
    public void knowledgeIsPower() {
        newGame();
        lands("Swamp", 1, me);
        enter("Knowledge is Power", me);
        final Card t = creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(1, current(t).getNetPower());
        final Card cyc = hand("Knowledge is Power", me);
        activate(cyc, "Cycling");
        AssertJUnit.assertTrue("cycling 1", in(cyc, ZoneType.Graveyard));
        AssertJUnit.assertEquals(2, current(t).getNetPower());
        me.drawCards(1);
        resolve();
        AssertJUnit.assertEquals("two drawn this turn", 3, current(t).getNetToughness());
    }

    @Test
    public void kraulWhipcracker() {
        newGame();
        final Card goat = addToken("w_0_1_goat", opp);
        final Card nontoken = creature("Carrion Feeder", opp);
        choices(me).targets = List.of(nontoken, goat);
        final Card kw = enter("Kraul Whipcracker", me);
        AssertJUnit.assertTrue(kw.hasKeyword(Keyword.REACH));
        AssertJUnit.assertFalse(in(goat, ZoneType.Battlefield));
        AssertJUnit.assertTrue("tokens only", in(nontoken, ZoneType.Battlefield));
    }

    @Test
    public void lazav() {
        newGame();
        final Card lazav = creature("Lazav Wearer of Faces", me);
        final Card t = grave("Woolly Thoctar", opp);
        choices(me).targets = List.of(t);
        attack(me, null, lazav);
        AssertJUnit.assertTrue(in(t, ZoneType.Exile));
        AssertJUnit.assertEquals("investigate", 1, tokens(me).size());
        final Card clue = tokens(me).get(0);
        lands("Swamp", 2, me);
        choices(me).confirm = true;
        choices(me).prefer = c -> named(c, "Woolly Thoctar");
        activate(clue, "Scan");
        AssertJUnit.assertEquals("a copy", "Woolly Thoctar", current(lazav).getName());
        AssertJUnit.assertEquals(6, current(lazav).getNetPower());
        AssertJUnit.assertTrue(current(lazav).getType().hasCreatureType("Shapeshifter"));
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertEquals("this turn", "Lazav Wearer of Faces", current(lazav).getName());
    }

    @Test
    public void lightningHelix() {
        newGame();
        lands("Mountain", 1, me);
        lands("Plains", 1, me);
        cast(hand("Lightning Helix", me), opp);
        AssertJUnit.assertEquals(17, opp.getLife());
        AssertJUnit.assertEquals("lifelink", 23, me.getLife());
    }

    @Test
    public void marrowChomper() {
        newGame();
        lands("Swamp", 2, me);
        lands("Forest", 1, me);
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        choices(me).confirm = true;
        choices(me).prefer = is(a, b);
        final Card mc = hand("Marrow Chomper", me);
        cast(mc);
        AssertJUnit.assertTrue(in(a, ZoneType.Graveyard) && in(b, ZoneType.Graveyard));
        AssertJUnit.assertEquals("devour 2", 4, p1p1(current(mc)));
        AssertJUnit.assertEquals("2 life each", 24, me.getLife());
    }

    @Test
    public void merfolkMistbinder() {
        newGame();
        final Card mm = creature("Merfolk Mistbinder", me);
        final Card merfolk = creature("Razorfin Hunter", me);
        final Card other = creature("Carrion Feeder", me);
        AssertJUnit.assertEquals(3, current(merfolk).getNetPower());
        AssertJUnit.assertEquals(2, current(merfolk).getNetToughness());
        AssertJUnit.assertEquals(1, current(other).getNetPower());
        AssertJUnit.assertEquals("other Merfolk", 2, current(mm).getNetPower());
    }

    @Test
    public void moderation() {
        newGame();
        lands("Forest", 2, me);
        enter("Moderation", me);
        final Card t = creature("Carrion Feeder", me);
        final Card first = hand("Giant Growth", me);
        AssertJUnit.assertTrue(first.getFirstSpellAbility().checkRestrictions(first, me));
        cast(first, t);
        AssertJUnit.assertEquals("drew", 1, count(me, ZoneType.Hand));
        final Card second = hand("Giant Growth", me);
        AssertJUnit.assertFalse("one spell each turn", second.getFirstSpellAbility().checkRestrictions(second, me));
    }

    @Test
    public void mortify() {
        newGame();
        lands("Plains", 3, me);
        lands("Swamp", 3, me);
        final Card t = creature("Woolly Thoctar", opp);
        final Card e = enter("Collective Blessing", opp);
        cast(hand("Mortify", me), t);
        cast(hand("Mortify", me), e);
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard) && in(e, ZoneType.Graveyard));
    }

    @Test
    public void nivmagusElemental() {
        newGame();
        lands("Forest", 1, me);
        final Card ne = creature("Nivmagus Elemental", me);
        final Card t = creature("Carrion Feeder", me);
        final Card gg = hand("Giant Growth", me);
        put(me, gg.getFirstSpellAbility(), targeting(t));
        activate(ne, "two +1/+1");
        AssertJUnit.assertEquals(2, p1p1(current(ne)));
        AssertJUnit.assertTrue("exiled from the stack", in(gg, ZoneType.Exile));
        AssertJUnit.assertEquals("it didn't resolve", 1, current(t).getNetPower());
    }

    @Test
    public void obekaBruteChronologist() {
        newGame();
        lands("Forest", 1, me);
        final Card ob = creature("Obeka Brute Chronologist", me);
        final Card t = creature("Carrion Feeder", me);
        final Card gg = hand("Giant Growth", me);
        put(me, gg.getFirstSpellAbility(), targeting(t));
        choices(me).confirm = true;
        activate(ob, "may end the turn");
        AssertJUnit.assertTrue("the stack exiled", in(gg, ZoneType.Exile));
        AssertJUnit.assertEquals(1, current(t).getNetPower());
        AssertJUnit.assertTrue("the turn ended", game.getPhaseHandler().is(PhaseType.CLEANUP) || game.getPhaseHandler().isPlayerTurn(opp));
    }

    @Test
    public void ochranAssassin() {
        newGame();
        final Card oa = creature("Ochran Assassin", me);
        AssertJUnit.assertTrue(oa.hasKeyword(Keyword.DEATHTOUCH));
        creature("Carrion Feeder", opp);
        final Card b = creature("Woolly Thoctar", opp);
        final Card c = creature("Woolly Thoctar", opp);
        choices(me).attackers = List.of(oa);
        choices(opp).blocks = null;
        playUntil(me, PhaseType.COMBAT_DECLARE_BLOCKERS);
        AssertJUnit.assertEquals("Carrion Feeder can't block; the others must",
                List.of(b.getId(), c.getId()).stream().sorted().toList(),
                game.getCombat().getBlockers(current(oa)).stream().map(Card::getId).sorted().toList());
    }

    @Test
    public void putridLeech() {
        newGame();
        final Card pl = creature("Putrid Leech", me);
        activate(pl, "+2/+2");
        AssertJUnit.assertEquals(4, current(pl).getNetPower());
        AssertJUnit.assertEquals(18, me.getLife());
        AssertJUnit.assertFalse("once each turn", canActivate(pl, "+2/+2"));
    }

    @Test
    public void rakdosHeadliner() {
        newGame();
        final Card rh = enter("Rakdos Headliner", me);
        AssertJUnit.assertTrue("haste", forge.game.combat.CombatUtil.canAttack(current(rh)));
        hand("Woolly Thoctar", me);
        choices(me).payUnless = true;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("echo paid", in(rh, ZoneType.Battlefield));
        AssertJUnit.assertEquals("by discarding", 0, count(me, ZoneType.Hand));
        newGame();
        final Card rh2 = enter("Rakdos Headliner", me);
        hand("Woolly Thoctar", me);
        choices(me).payUnless = false;
        playUntil(me, PhaseType.UPKEEP);
        AssertJUnit.assertTrue("not paid: sacrificed", in(rh2, ZoneType.Graveyard));
    }

    @Test
    public void razorfinHunter() {
        newGame();
        final Card rh = creature("Razorfin Hunter", me);
        activate(rh, "1 damage", opp);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertTrue(current(rh).isTapped());
    }

    @Test
    public void recoil() {
        newGame();
        lands("Island", 2, me);
        lands("Swamp", 1, me);
        final Card t = creature("Woolly Thoctar", opp);
        hand("Ornithopter", opp);
        cast(hand("Recoil", me), t);
        AssertJUnit.assertEquals("returned, then discarded one", 1, count(opp, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(opp, ZoneType.Graveyard));
        AssertJUnit.assertFalse(in(t, ZoneType.Battlefield));
    }

    @Test
    public void riddleGateGargoyle() {
        newGame();
        final Card g = creature("Riddle Gate Gargoyle", me);
        AssertJUnit.assertTrue(g.isArtifact() && g.hasKeyword(Keyword.FLYING));
        AssertJUnit.assertEquals(3, me.getCounters(CounterEnumType.ENERGY));
        choices(me).confirm = true;
        choices(me).targets = List.of(g);
        attack(me, null, g);
        AssertJUnit.assertEquals("paid EE", 1, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals("lifelink", 22, me.getLife());
    }

    @Test
    public void righteousAuthority() {
        newGame();
        lands("Plains", 1, me);
        lands("Island", 2, me);
        final Card t = creature("Carrion Feeder", me);
        cast(hand("Righteous Authority", me), t);
        hand("Woolly Thoctar", me);
        hand("Woolly Thoctar", me);
        resolve();
        AssertJUnit.assertEquals("two cards in hand", 3, current(t).getNetPower());
        playUntil(me, PhaseType.DRAW);
        AssertJUnit.assertEquals("an additional card", 4, count(me, ZoneType.Hand));
        AssertJUnit.assertEquals(5, current(t).getNetToughness());
    }

    @Test
    public void rubblebeltRioters() {
        newGame();
        final Card rr = enter("Rubblebelt Rioters", me);
        creature("Woolly Thoctar", me);
        attack(me, null, rr);
        AssertJUnit.assertEquals("haste; greatest power 6", 14, opp.getLife());
    }

    @Test
    public void ruinationRioter() {
        newGame();
        final Card rr = creature("Ruination Rioter", me);
        grave("Swamp", me);
        grave("Forest", me);
        grave("Woolly Thoctar", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(opp);
        kill(rr);
        AssertJUnit.assertEquals("two lands", 18, opp.getLife());
    }

    @Test
    public void scabClanMauler() {
        newGame();
        lands("Mountain", 1, me);
        lands("Forest", 1, me);
        damage(creature("Carrion Feeder", me), opp, 1, false);
        final Card m = hand("Scab-Clan Mauler", me);
        cast(m);
        AssertJUnit.assertEquals("bloodthirst 2", 2, p1p1(current(m)));
        AssertJUnit.assertTrue(current(m).hasKeyword(Keyword.TRAMPLE));
        newGame();
        lands("Mountain", 1, me);
        lands("Forest", 1, me);
        final Card m2 = hand("Scab-Clan Mauler", me);
        cast(m2);
        AssertJUnit.assertEquals(0, p1p1(current(m2)));
    }

    @Test
    public void slipperyBogle() {
        newGame();
        final Card sb = creature("Slippery Bogle", me);
        AssertJUnit.assertTrue(sb.hasKeyword(Keyword.HEXPROOF));
        AssertJUnit.assertFalse(canTarget(hand("Auger Spree", opp), opp, sb));
    }

    @Test
    public void songOfTheDryads() {
        newGame();
        lands("Forest", 2, me);
        lands("Plains", 1, me);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Song of the Dryads", me), t);
        final Card c = current(t);
        AssertJUnit.assertTrue(c.isLand() && !c.isCreature() && c.getType().hasSubtype("Forest"));
        AssertJUnit.assertTrue(c.isColorless());
        AssertJUnit.assertFalse("taps for G", c.getManaAbilities().isEmpty());
    }

    @Test
    public void spikeJester() {
        newGame();
        final Card sj = enter("Spike Jester", me);
        attack(me, null, sj);
        AssertJUnit.assertEquals("haste", 17, opp.getLife());
    }

    @Test
    public void staggeringInsight() {
        newGame();
        lands("Plains", 1, me);
        lands("Island", 1, me);
        final Card t = creature("Carrion Feeder", me);
        cast(hand("Staggering Insight", me), t);
        AssertJUnit.assertEquals(2, current(t).getNetPower());
        AssertJUnit.assertTrue(current(t).hasKeyword(Keyword.LIFELINK));
        attack(me, null, t);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertEquals(22, me.getLife());
        AssertJUnit.assertEquals("draws on combat damage", 1, count(me, ZoneType.Hand));
    }

    @Test
    public void stitchInTime() {
        boolean won = false;
        boolean lost = false;
        for (int i = 0; i < 40 && !(won && lost); i++) {
            newGame();
            lands("Island", 1, me);
            lands("Mountain", 1, me);
            cast(hand("Stitch in Time", me));
            final int extra = game.getPhaseHandler().getExtraTurnForPlayer(me);
            AssertJUnit.assertTrue(extra <= 1);
            if (extra == 1) {
                won = true;
                playUntil(me, PhaseType.UPKEEP);
                AssertJUnit.assertEquals("the extra turn follows this one", 2, game.getPhaseHandler().getTurn());
            } else {
                lost = true;
            }
        }
        AssertJUnit.assertTrue("a coin flip, won and lost", won && lost);
    }

    @Test
    public void sultaiSoothsayer() {
        newGame();
        library(me, "Swamp", "Forest", "Ornithopter", "Island");
        choices(me).prefer = c -> named(c, "Ornithopter");
        enter("Sultai Soothsayer", me);
        AssertJUnit.assertEquals(1, count(me, ZoneType.Hand));
        AssertJUnit.assertTrue(named(me.getCardsIn(ZoneType.Hand).get(0), "Ornithopter"));
        AssertJUnit.assertEquals(3, count(me, ZoneType.Graveyard));
    }

    @Test
    public void swiftbladeVindicator() {
        newGame();
        final Card sv = creature("Swiftblade Vindicator", me);
        AssertJUnit.assertTrue(hasAll(sv, Keyword.DOUBLE_STRIKE, Keyword.VIGILANCE, Keyword.TRAMPLE));
        attack(me, null, sv);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertFalse(current(sv).isTapped());
    }

    @Test
    public void tajic() {
        newGame();
        final Card tj = creature("Tajic Blade of the Legion", me);
        AssertJUnit.assertTrue(tj.hasKeyword(Keyword.INDESTRUCTIBLE));
        final Card a = creature("Carrion Feeder", me);
        final Card b = creature("Carrion Feeder", me);
        attack(me, null, tj, a, b);
        AssertJUnit.assertEquals("battalion: 7 + 1 + 1", 11, opp.getLife());
        newGame();
        final Card tj2 = creature("Tajic Blade of the Legion", me);
        attack(me, null, tj2, creature("Carrion Feeder", me));
        AssertJUnit.assertEquals(17, opp.getLife());
    }

    @Test
    public void terminalAgony() {
        newGame();
        lands("Swamp", 1, me);
        lands("Mountain", 1, me);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Terminal Agony", me), t);
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard));
        untapAll(me);
        final Card t2 = creature("Woolly Thoctar", opp);
        final Card mad = hand("Terminal Agony", me);
        choices(me).confirm = true;
        choices(me).targets = List.of(t2);
        discard(me, mad);
        AssertJUnit.assertTrue("madness BR", in(t2, ZoneType.Graveyard));
        AssertJUnit.assertTrue(in(mad, ZoneType.Graveyard));
    }

    @Test
    public void treasuryThrull() {
        newGame();
        lands("Forest", 1, me);
        lands("Plains", 1, me);
        final Card tt = creature("Treasury Thrull", me);
        choices(me).confirm = true;
        cast(hand("Giant Growth", me), tt);
        AssertJUnit.assertEquals("extort", 19, opp.getLife());
        AssertJUnit.assertEquals(21, me.getLife());
        final Card back = grave("Ornithopter", me);
        choices(me).targets = List.of(back);
        attack(me, null, tt);
        AssertJUnit.assertTrue(in(back, ZoneType.Hand));
    }

    @Test
    public void tyrantsScorn() {
        newGame();
        lands("Island", 2, me);
        lands("Swamp", 2, me);
        final Card small = creature("Woolly Thoctar", opp);
        final Card big = creature("Azorius Skyguard", opp);
        choices(me).modes.add("Destroy");
        final Card ts = hand("Tyrants Scorn", me);
        cast(ts, small);
        AssertJUnit.assertTrue(in(small, ZoneType.Graveyard));
        choices(me).modes.add("Return");
        cast(hand("Tyrants Scorn", me), big);
        AssertJUnit.assertTrue(in(big, ZoneType.Hand));
    }

    @Test
    public void unmake() {
        newGame();
        lands("Plains", 1, me);
        lands("Swamp", 2, me);
        final Card t = creature("Woolly Thoctar", opp);
        cast(hand("Unmake", me), t);
        AssertJUnit.assertTrue(in(t, ZoneType.Exile));
    }

    @Test
    public void venomthrope() {
        newGame();
        lands("Forest", 2, me);
        lands("Island", 1, me);
        playUntil(opp, PhaseType.MAIN1);
        final Card v = hand("Venomthrope", me);
        AssertJUnit.assertTrue("flash", canCast(v, me));
        cast(v);
        AssertJUnit.assertTrue(hasAll(current(v), Keyword.FLYING, Keyword.DEATHTOUCH, Keyword.HEXPROOF));
    }

    @Test
    public void vona() {
        newGame();
        final Card v = creature("Vona Butcher of Magan", me);
        AssertJUnit.assertTrue(hasAll(v, Keyword.VIGILANCE, Keyword.LIFELINK));
        final Card t = enter("Collective Blessing", opp);
        activate(v, "Destroy target", t);
        AssertJUnit.assertTrue(in(t, ZoneType.Graveyard));
        AssertJUnit.assertEquals(13, me.getLife());
        playUntil(opp, PhaseType.MAIN1);
        AssertJUnit.assertFalse("only during your turn", canActivate(v, "Destroy target"));
    }

    @Test
    public void zhurTaaAncient() {
        newGame();
        creature("Zhur-Taa Ancient", me);
        final Card f = lands("Forest", 1, opp).get(0);
        mana(f, null);
        AssertJUnit.assertEquals("any player's lands", 2, opp.getManaPool().totalMana());
    }

    @Test
    public void zhurTaaGoblin() {
        newGame();
        lands("Mountain", 1, me);
        lands("Forest", 1, me);
        choices(me).payUnless = true;
        final Card g = hand("Zhur-Taa Goblin", me);
        cast(g);
        AssertJUnit.assertEquals("riot: a counter", 1, p1p1(current(g)));
        AssertJUnit.assertFalse(current(g).hasKeyword(Keyword.HASTE));
        newGame();
        lands("Mountain", 1, me);
        lands("Forest", 1, me);
        choices(me).payUnless = false;
        final Card g2 = hand("Zhur-Taa Goblin", me);
        cast(g2);
        AssertJUnit.assertEquals(0, p1p1(current(g2)));
        AssertJUnit.assertTrue("riot: haste", current(g2).hasKeyword(Keyword.HASTE));
    }
}
