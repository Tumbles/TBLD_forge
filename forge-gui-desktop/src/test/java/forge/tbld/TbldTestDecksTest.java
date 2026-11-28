package forge.tbld;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilMana;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameEntity;
import forge.game.GameEntityCounterTable;
import forge.game.card.Card;
import forge.game.card.CardDamageTable;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.tbld.server.TbldPictures;
import forge.tbld.server.TbldTestDecks;
import forge.tbld.server.TbldWebServer;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every card of the two web test decks does what its text says, abilities included, in a game run
 * by the engine (AI players). {@link #VERIFIED} lists them; {@code TbldPool verify} counts these
 * cards as implemented and checks that list against {@code tbld-pool/verified.txt}.
 * The web path (a person's triggered abilities reaching the stack) is {@code TbldWebTriggersTest}.
 */
public class TbldTestDecksTest extends AITest {

    /** The cards whose abilities this test plays. Keep in step with tbld-pool/verified.txt. */
    public static final List<String> VERIFIED = List.of(
            // red test deck
            "Rigging Runner", "Goblin Wardriver", "Voldaren Epicure", "Harsh Mentor", "Crackling Cyclops",
            "Hound of Griselbrand", "Volley Veteran", "Searing Touch", "Galvanic Blast", "Firebolt", "Searing Blood",
            // green test deck
            "Young Wolf", "Deeproot Champion", "Servant of the Conduit", "Sacred Wolf", "Jungleborn Pioneer",
            "Greenweaver Druid", "Ironscale Hydra", "Stingerfling Spider", "Blanchwood Armor", "Hunger of the Howlpack",
            "Stand Together", "Feed the Clan");

    private Game game;
    private Player me;
    private Player opp;

    private void newGame() {
        game = initAndCreateGame();
        me = game.getPlayers().get(1); // the active player, in their first main phase
        opp = game.getPlayers().get(0);
    }

    private List<Card> lands(final String name, final int n, final Player p) {
        return addCards(name, n, p);
    }

    private Card creature(final String name, final Player p) {
        // Entering the battlefield (not just being added to it) registers its triggers, which dies triggers need
        final Card c = enter(name, p);
        c.setSickness(false);
        return c;
    }

    /** Puts a card from the hand into play as if it resolved, then resolves its triggers. */
    private Card enter(final String name, final Player p) {
        final Card c = addCardToZone(name, p, ZoneType.Hand);
        final Card inPlay = game.getAction().moveToPlay(c, null, null);
        resolve();
        return inPlay;
    }

    /**
     * Resolves everything on the stack, triggered abilities included, without leaving the step;
     * state-based actions (creatures with lethal damage dying) and static abilities are checked first.
     */
    private void resolve() {
        for (int i = 0; i < 100 && !game.isGameOver(); i++) {
            // Waiting triggers first, as the engine runs them before checking state-based actions
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) {
                return;
            }
            game.getPhaseHandler().mainLoopStep();
        }
    }

    /** Casts the spell ability, paying with the caster's mana sources, then resolves the stack. */
    private void cast(final Player p, final SpellAbility sa, final Consumer<SpellAbility> targets) {
        sa.setActivatingPlayer(p);
        AssertJUnit.assertTrue("could not cast " + sa, ComputerUtil.handlePlayingSpellAbility(p, sa, targets));
        resolve();
    }

    private void cast(final Card c, final GameEntity... targets) {
        cast(c.getController(), c.getFirstSpellAbility(), sa -> {
            for (final GameEntity t : targets) {
                sa.getTargets().add(t);
            }
        });
    }

    private Card hand(final String name, final Player p) {
        return addCardToZone(name, p, ZoneType.Hand);
    }

    private static int p1p1(final Card c) {
        return c.getCounters(CounterEnumType.P1P1);
    }

    private Card current(final Card c) {
        return game.getCardState(c);
    }

    @Test
    public void testDecksHoldOnlyVerifiedCardsWithPictures() {
        // The red and green decks' cards are verified here; the other test decks' cards by the card tests
        final List<Deck> own = List.of(TbldWebServer.testDeck(), TbldWebServer.greenTestDeck());
        final Set<String> verified = TbldVerified.all();
        final Set<String> ids = new HashSet<>();
        for (final TbldTestDecks.Entry entry : TbldTestDecks.ALL) {
            AssertJUnit.assertTrue("two test decks with id " + entry.id(), ids.add(entry.id()));
            final Deck d = entry.deck().get();
            final boolean isOwn = own.stream().anyMatch(o -> o.getName().equals(d.getName()));
            AssertJUnit.assertEquals(d.getName() + " size", 40, d.getMain().countAll());
            for (final Map.Entry<PaperCard, Integer> e : d.getMain()) {
                final PaperCard pc = e.getKey();
                AssertJUnit.assertEquals(pc.getName(), "TBLD", pc.getEdition());
                AssertJUnit.assertNotNull(pc.getName() + " (version " + pc.getArtIndex() + ") has no picture",
                        TbldPictures.urlFor(pc.getName(), pc.getArtIndex()));
                if (!pc.getRules().getType().isBasicLand()) {
                    AssertJUnit.assertTrue(pc.getName() + " is in " + d.getName() + " but not verified",
                            isOwn ? VERIFIED.contains(pc.getName()) : verified.contains(pc.getName()));
                }
            }
        }
        // The tokens they make have Brett's token pictures
        AssertJUnit.assertEquals("/pics/TBLD%20Merfolk.jpg", TbldPictures.tokenUrlFor("Merfolk Token", "1/1", "U"));
        AssertJUnit.assertEquals("a pumped token keeps its picture", "/pics/TBLD%20Merfolk.jpg", TbldPictures.tokenUrlFor("Merfolk Token", "3/3", "U"));
        AssertJUnit.assertEquals("/pics/TBLD%20Blood.jpg", TbldPictures.tokenUrlFor("Blood Token", null, ""));
        AssertJUnit.assertNull("a token of other colors", TbldPictures.tokenUrlFor("Merfolk Token", "1/1", "G"));
    }

    @Test
    public void redCreaturesInCombat() {
        newGame();
        // Battle cry gives the other attackers +1/+0; Hound of Griselbrand's double strike deals its damage twice
        creature("Goblin Wardriver", me);
        final Card runner = creature("Rigging Runner", me);
        final Card hound = creature("Hound of Griselbrand", me);
        AssertJUnit.assertTrue(runner.hasFirstStrike());
        AssertJUnit.assertTrue(hound.hasDoubleStrike());
        final Card early = enter("Rigging Runner", me);
        AssertJUnit.assertEquals("Raid without an attack", 0, p1p1(early));
        playUntilPhase(game, PhaseType.MAIN2);
        // Wardriver 2, Runner 1+1, Hound (2+1) twice; the Rigging Runner that just entered can't attack
        AssertJUnit.assertEquals("combat damage with battle cry and double strike", 20 - 2 - 2 - 6, opp.getLife());
        final Card raided = enter("Rigging Runner", me);
        AssertJUnit.assertEquals("Raid after an attack", 1, p1p1(raided));
    }

    @Test
    public void undying() {
        // Killed by the opponent's Galvanic Blast (2 damage), each returns with a +1/+1 counter; Young Wolf,
        // then 2/2, dies to a second one and stays dead
        newGame();
        lands("Mountain", 3, opp);
        for (final String name : List.of("Hound of Griselbrand", "Young Wolf")) {
            final Card c = creature(name, me);
            cast(hand("Galvanic Blast", opp), c);
            final Card back = findCardWithName(game, name);
            AssertJUnit.assertNotNull(name + " did not return", back);
            AssertJUnit.assertEquals(name + " returned without its counter", 1, p1p1(back));
        }
        cast(hand("Galvanic Blast", opp), findCardWithName(game, "Young Wolf"));
        AssertJUnit.assertNull("Young Wolf returned twice", findCardWithName(game, "Young Wolf"));
    }

    @Test
    public void voldarenEpicure() {
        newGame();
        enter("Voldaren Epicure", me);
        AssertJUnit.assertEquals(19, opp.getLife());
        AssertJUnit.assertEquals("Blood token", 1, CardLists.filter(me.getCardsIn(ZoneType.Battlefield), c -> c.isToken() && c.isArtifact()
                && c.getType().hasSubtype("Blood")).size());
    }

    @Test
    public void harshMentor() {
        newGame();
        creature("Harsh Mentor", me);
        final Card druid = creature("Greenweaver Druid", opp);
        hand("Forest", opp);
        // A mana ability doesn't trigger it
        final SpellAbility mana = druid.getManaAbilities().getFirst();
        mana.setActivatingPlayer(opp);
        ComputerUtil.playNoStack(opp, mana, game, true);
        resolve();
        AssertJUnit.assertEquals("Greenweaver Druid's mana", 2, opp.getManaPool().totalMana());
        AssertJUnit.assertTrue(druid.isTapped());
        AssertJUnit.assertEquals("a mana ability triggered Harsh Mentor", 20, opp.getLife());
        // Greenweaver Druid's "Discard a land card: Untap this" does
        final SpellAbility untap = druid.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility() && !sa.isManaAbility()).findFirst().orElseThrow();
        cast(opp, untap, null);
        AssertJUnit.assertFalse("Greenweaver Druid did not untap", current(druid).isTapped());
        AssertJUnit.assertEquals("the land was not discarded", 1, opp.getCardsIn(ZoneType.Graveyard).size());
        AssertJUnit.assertEquals("Harsh Mentor did not deal 2 damage", 18, opp.getLife());
    }

    @Test
    public void volleyVeteranAndCracklingCyclops() {
        newGame();
        creature("Goblin Wardriver", me);
        final Card target = creature("Crackling Cyclops", opp); // 0/4
        enter("Volley Veteran", me);
        AssertJUnit.assertTrue("Volley Veteran has the Flash supertype", findCardWithName(game, "Volley Veteran").hasFlashSupertype());
        AssertJUnit.assertEquals("damage equal to my two Goblins", 2, current(target).getDamage());

        // Crackling Cyclops: a noncreature spell gives target Giant +3/+0
        final Card cyclops = creature("Crackling Cyclops", me);
        lands("Mountain", 1, me);
        cast(hand("Galvanic Blast", me), opp);
        AssertJUnit.assertEquals(3, current(cyclops).getNetPower());
    }

    @Test
    public void burnSpells() {
        newGame();
        lands("Mountain", 7, me);
        // Searing Touch: 2 damage, and that player can't gain life this turn
        cast(hand("Searing Touch", me), opp);
        AssertJUnit.assertEquals(18, opp.getLife());
        AssertJUnit.assertFalse("a player dealt damage by Searing Touch can gain life", opp.canGainLife());
        // with buyback it returns to the hand
        final Card touch = hand("Searing Touch", me);
        final SpellAbility withBuyback = GameActionUtil.addOptionalCosts(touch.getFirstSpellAbility(),
                GameActionUtil.getOptionalCostValues(touch.getFirstSpellAbility()));
        cast(me, withBuyback, sa -> sa.getTargets().add(opp));
        AssertJUnit.assertEquals(16, opp.getLife());
        AssertJUnit.assertTrue("buyback did not return Searing Touch to the hand", current(touch).isInZone(ZoneType.Hand));

        newGame();
        lands("Mountain", 6, me);
        // Galvanic Blast: 2 damage, 4 with metalcraft
        cast(hand("Galvanic Blast", me), opp);
        AssertJUnit.assertEquals(18, opp.getLife());
        addTokens("c_a_blood_draw", 3, me);
        cast(hand("Galvanic Blast", me), opp);
        AssertJUnit.assertEquals("metalcraft", 14, opp.getLife());
        // Firebolt: 2 damage, then flashback 4R from the graveyard, which exiles it
        final Card firebolt = hand("Firebolt", me);
        cast(firebolt, opp);
        AssertJUnit.assertEquals(12, opp.getLife());
        final Card inGraveyard = current(firebolt);
        AssertJUnit.assertTrue(inGraveyard.isInZone(ZoneType.Graveyard));
        me.getZone(ZoneType.Battlefield).getCards().forEach(c -> c.setTapped(false));
        final SpellAbility flashback = GameActionUtil.getAlternativeCosts(inGraveyard.getFirstSpellAbility(), me, false)
                .stream().filter(SpellAbility::isFlashback).findFirst().orElseThrow();
        cast(me, flashback, sa -> sa.getTargets().add(opp));
        AssertJUnit.assertEquals("flashback", 10, opp.getLife());
        AssertJUnit.assertTrue("flashback did not exile Firebolt", current(firebolt).isInZone(ZoneType.Exile));
    }

    @Test
    public void searingBlood() {
        newGame();
        lands("Mountain", 2, me);
        final Card wolf = creature("Deeproot Champion", opp); // 1/1
        cast(hand("Searing Blood", me), wolf);
        AssertJUnit.assertTrue(current(wolf).isInZone(ZoneType.Graveyard));
        AssertJUnit.assertEquals("3 damage to the controller of the creature that died", 17, opp.getLife());
        // A creature that survives: no 3 damage
        lands("Mountain", 2, me);
        final Card big = creature("Crackling Cyclops", opp);
        cast(hand("Searing Blood", me), big);
        AssertJUnit.assertEquals(2, current(big).getDamage());
        AssertJUnit.assertEquals(17, opp.getLife());
    }

    @Test
    public void deeprootChampion() {
        newGame();
        final Card champion = creature("Deeproot Champion", me);
        lands("Forest", 4, me);
        cast(hand("Feed the Clan", me));
        AssertJUnit.assertEquals("a noncreature spell", 1, p1p1(current(champion)));
        cast(hand("Young Wolf", me));
        AssertJUnit.assertEquals("a creature spell gives no counter", 1, p1p1(current(champion)));
    }

    @Test
    public void servantOfTheConduit() {
        newGame();
        final Card servant = enter("Servant of the Conduit", me);
        AssertJUnit.assertEquals("energy", 2, me.getCounters(CounterEnumType.ENERGY));
        servant.setSickness(false);
        // T, Pay E: add one mana of any color (here it pays for Hunger of the Howlpack, G)
        final Card hunger = hand("Hunger of the Howlpack", me);
        final SpellAbility sa = hunger.getFirstSpellAbility();
        sa.setActivatingPlayer(me);
        AssertJUnit.assertTrue(ComputerUtilMana.canPayManaCost(sa, me, 0, false));
        cast(hunger, current(servant));
        AssertJUnit.assertTrue(current(servant).isTapped());
        AssertJUnit.assertEquals("energy paid", 1, me.getCounters(CounterEnumType.ENERGY));
        AssertJUnit.assertEquals(1, p1p1(current(servant)));
    }

    @Test
    public void sacredWolfHasHexproof() {
        newGame();
        final Card wolf = creature("Sacred Wolf", me);
        final SpellAbility theirs = hand("Searing Blood", opp).getFirstSpellAbility();
        theirs.setActivatingPlayer(opp);
        final SpellAbility mine = hand("Hunger of the Howlpack", me).getFirstSpellAbility();
        mine.setActivatingPlayer(me);
        AssertJUnit.assertFalse("an opponent's spell can target Sacred Wolf", wolf.canBeTargetedBy(theirs));
        AssertJUnit.assertTrue(wolf.canBeTargetedBy(mine));
    }

    @Test
    public void junglebornPioneer() {
        newGame();
        enter("Jungleborn Pioneer", me);
        final List<Card> tokens = CardLists.filter(me.getCardsIn(ZoneType.Battlefield), Card::isToken);
        AssertJUnit.assertEquals(1, tokens.size());
        final Card merfolk = tokens.get(0);
        AssertJUnit.assertTrue(merfolk.getType().hasCreatureType("Merfolk"));
        AssertJUnit.assertEquals(1, merfolk.getNetPower());
        AssertJUnit.assertEquals(1, merfolk.getNetToughness());
        AssertJUnit.assertTrue(merfolk.getColor().hasBlue());
        AssertJUnit.assertTrue(merfolk.hasKeyword(Keyword.HEXPROOF));
    }

    @Test
    public void greenweaverDruid() {
        newGame();
        final Card druid = creature("Greenweaver Druid", me);
        // T: add GG, enough for Blanchwood Armor (1G)
        final Card armor = hand("Blanchwood Armor", me);
        final SpellAbility sa = armor.getFirstSpellAbility();
        sa.setActivatingPlayer(me);
        AssertJUnit.assertTrue("Greenweaver Druid's GG doesn't pay 1G", ComputerUtilMana.canPayManaCost(sa, me, 0, false));
        cast(armor, druid);
        AssertJUnit.assertTrue(current(druid).isTapped());
        AssertJUnit.assertTrue(current(armor).isAttachedToEntity(current(druid)));
    }

    @Test
    public void ironscaleHydra() {
        newGame();
        final Card hydra = creature("Ironscale Hydra", me);
        final Card attacker = creature("Hound of Griselbrand", opp);
        final CardDamageTable damage = new CardDamageTable();
        damage.put(attacker, hydra, 3);
        game.getAction().dealDamage(true, damage, new CardDamageTable(), new GameEntityCounterTable(), null);
        resolve();
        AssertJUnit.assertEquals("combat damage was not prevented", 0, current(hydra).getDamage());
        AssertJUnit.assertEquals(1, p1p1(current(hydra)));
        // Other damage is dealt as usual
        lands("Mountain", 1, opp);
        cast(hand("Galvanic Blast", opp), current(hydra));
        AssertJUnit.assertEquals(2, current(hydra).getDamage());
        AssertJUnit.assertEquals(1, p1p1(current(hydra)));
    }

    @Test
    public void stingerflingSpider() {
        newGame();
        final Card flyer = creature("Ornithopter", opp);
        final Card spider = enter("Stingerfling Spider", me);
        AssertJUnit.assertTrue(spider.hasKeyword(Keyword.REACH));
        AssertJUnit.assertTrue(spider.hasFlashSupertype());
        AssertJUnit.assertTrue("the creature with flying was not destroyed", current(flyer).isInZone(ZoneType.Graveyard));
    }

    @Test
    public void blanchwoodArmor() {
        newGame();
        lands("Forest", 3, me);
        final Card wolf = creature("Young Wolf", me);
        cast(hand("Blanchwood Armor", me), wolf);
        AssertJUnit.assertEquals("+1/+1 for each of three Forests", 4, current(wolf).getNetPower());
        AssertJUnit.assertEquals(4, current(wolf).getNetToughness());
        lands("Forest", 1, me);
        resolve();
        AssertJUnit.assertEquals(5, current(wolf).getNetPower());
    }

    @Test
    public void hungerOfTheHowlpack() {
        newGame();
        lands("Forest", 2, me);
        final Card wolf = creature("Sacred Wolf", me);
        cast(hand("Hunger of the Howlpack", me), wolf);
        AssertJUnit.assertEquals(1, p1p1(current(wolf)));
        // Morbid: three counters once a creature died this turn
        final Card victim = creature("Deeproot Champion", opp);
        game.getAction().destroy(victim, null, false, null);
        resolve();
        cast(hand("Hunger of the Howlpack", me), current(wolf));
        AssertJUnit.assertEquals(4, p1p1(current(wolf)));
    }

    @Test
    public void standTogether() {
        newGame();
        lands("Forest", 3, me);
        final Card a = creature("Young Wolf", me);
        final Card b = creature("Sacred Wolf", me);
        cast(me, hand("Stand Together", me).getFirstSpellAbility(), sa -> {
            sa.getTargets().add(a);
            sa.getSubAbility().getTargets().add(b);
        });
        AssertJUnit.assertEquals(2, p1p1(current(a)));
        AssertJUnit.assertEquals(2, p1p1(current(b)));
    }

    @Test
    public void feedTheClan() {
        newGame();
        lands("Forest", 4, me);
        cast(hand("Feed the Clan", me));
        AssertJUnit.assertEquals(25, me.getLife());
        creature("Ironscale Hydra", me);
        cast(hand("Feed the Clan", me));
        AssertJUnit.assertEquals("Ferocious", 35, me.getLife());
    }
}
