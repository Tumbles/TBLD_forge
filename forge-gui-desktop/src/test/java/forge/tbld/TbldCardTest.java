package forge.tbld;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import org.testng.AssertJUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Helpers for tests that play TBLD cards in an engine game between two AI players. A test can take
 * over the AI's choices ({@link Choices}): answers to "you may" questions, charm modes, the targets
 * of triggered abilities, and which cards it picks for an effect.
 */
public abstract class TbldCardTest extends AITest {

    protected Game game;
    /** The active player, in their first main phase. */
    protected Player me;
    protected Player opp;

    /** Choices a test makes for a player in place of the AI. Null fields leave the choice to the AI. */
    public static final class Choices {
        /** The answer to every "you may" question, optional trigger and optional cost. */
        public Boolean confirm;
        /** Charm modes, each named by a part of its description, used in order. */
        public final List<String> modes = new ArrayList<>();
        /** Targets for triggered abilities: each targeting part takes those it can target. */
        public List<GameEntity> targets;
        /** Cards (or players) picked first for effects: choosing, sacrificing, discarding, searching. */
        public Predicate<GameEntity> prefer;
        /** The card name chosen for "choose a card name". */
        public String cardName;
        /** The number chosen for "choose a number". */
        public Integer number;
        /** The creatures that attack (the opponent), declared in place of the AI's attack; none unless set, null for the AI's. */
        public List<Card> attackers = List.of();
        /** Blocks, blocker to attacker, declared in place of the AI's blocks; none unless set, null for the AI's. */
        public Map<Card, Card> blocks = Map.of();
    }

    /** An AI player whose choices a test can override. */
    public static final class Controller extends PlayerControllerAi {
        final Choices choices = new Choices();

        Controller(final Game game, final Player p) {
            super(game, p, p.getLobbyPlayer());
        }

        @Override
        public boolean confirmAction(final SpellAbility sa, final PlayerActionConfirmMode mode, final String message,
                final List<String> options, final Card cardToShow, final Map<String, Object> params) {
            return choices.confirm != null ? choices.confirm : super.confirmAction(sa, mode, message, options, cardToShow, params);
        }

        @Override
        public boolean confirmTrigger(final WrappedAbility wrapper) {
            return choices.confirm != null && !wrapper.isMandatory() ? choices.confirm : super.confirmTrigger(wrapper);
        }

        @Override
        public boolean confirmPayment(final forge.game.cost.CostPart costPart, final String prompt, final SpellAbility sa) {
            return choices.confirm != null ? choices.confirm : super.confirmPayment(costPart, prompt, sa);
        }

        @Override
        public List<AbilitySub> chooseModeForAbility(final SpellAbility sa, final List<AbilitySub> possible, final int min,
                final int num, final boolean allowRepeat) {
            if (choices.modes.isEmpty()) {
                return super.chooseModeForAbility(sa, possible, min, num, allowRepeat);
            }
            final List<AbilitySub> out = new ArrayList<>();
            while (out.size() < num && !choices.modes.isEmpty()) {
                final String want = choices.modes.remove(0);
                final AbilitySub m = possible.stream().filter(p -> p.getDescription().contains(want)).findFirst()
                        .orElseThrow(() -> new AssertionError("no mode \"" + want + "\" among " + possible));
                out.add(m);
            }
            sa.setChosenList(out);
            return out;
        }

        /** A choice between abilities ("choose one" of GenericChoice, such as germinate): the next of the modes. */
        @Override
        public SpellAbility chooseSingleSpellForEffect(final List<SpellAbility> spells, final SpellAbility sa, final String title,
                final Map<String, Object> params) {
            if (choices.modes.isEmpty()) {
                return super.chooseSingleSpellForEffect(spells, sa, title, params);
            }
            final String want = choices.modes.remove(0);
            return spells.stream().filter(p -> p.getDescription().contains(want)).findFirst()
                    .orElseThrow(() -> new AssertionError("no choice \"" + want + "\" among " + spells));
        }

        @Override
        public String chooseCardName(final SpellAbility sa, final Predicate<forge.card.ICardFace> cpp, final String valid, final String message) {
            return choices.cardName != null ? choices.cardName : super.chooseCardName(sa, cpp, valid, message);
        }

        @Override
        public int chooseNumber(final SpellAbility sa, final String title, final int min, final int max) {
            return choices.number != null ? choices.number : super.chooseNumber(sa, title, min, max);
        }

        @Override
        public int chooseNumber(final SpellAbility sa, final String title, final int min, final int max, final Map<String, Object> params) {
            return choices.number != null ? choices.number : super.chooseNumber(sa, title, min, max, params);
        }

        private <T extends GameEntity> List<T> pick(final Iterable<T> options, final int min, final int max) {
            final List<T> out = new ArrayList<>();
            for (final T o : options) {
                if (out.size() < max && choices.prefer.test(o)) {
                    out.add(o);
                }
            }
            for (final T o : options) {
                if (out.size() < min && !out.contains(o)) {
                    out.add(o);
                }
            }
            return out;
        }

        @Override
        public <T extends GameEntity> T chooseSingleEntityForEffect(final FCollectionView<T> optionList, final DelayedReveal delayedReveal,
                final SpellAbility sa, final String title, final boolean isOptional, final Player targetedPlayer, final Map<String, Object> params) {
            if (choices.prefer == null) {
                return super.chooseSingleEntityForEffect(optionList, delayedReveal, sa, title, isOptional, targetedPlayer, params);
            }
            final List<T> p = pick(optionList, isOptional ? 0 : 1, 1);
            return p.isEmpty() ? null : p.get(0);
        }

        @Override
        public <T extends GameEntity> List<T> chooseEntitiesForEffect(final FCollectionView<T> optionList, final int min, final int max,
                final DelayedReveal delayedReveal, final SpellAbility sa, final String title, final Player targetedPlayer, final Map<String, Object> params) {
            if (choices.prefer == null) {
                return super.chooseEntitiesForEffect(optionList, min, max, delayedReveal, sa, title, targetedPlayer, params);
            }
            return pick(optionList, min, max);
        }

        @Override
        public CardCollectionView chooseCardsForEffect(final CardCollectionView sourceList, final SpellAbility sa, final String title,
                final int min, final int max, final boolean isOptional, final Map<String, Object> params) {
            if (choices.prefer == null) {
                return super.chooseCardsForEffect(sourceList, sa, title, min, max, isOptional, params);
            }
            return new CardCollection(pick(sourceList, isOptional ? 0 : min, max));
        }

        @Override
        public CardCollectionView choosePermanentsToSacrifice(final SpellAbility sa, final int min, final int max,
                final CardCollectionView validTargets, final String message) {
            if (choices.prefer == null) {
                return super.choosePermanentsToSacrifice(sa, min, max, validTargets, message);
            }
            return new CardCollection(pick(validTargets, min, max));
        }

        @Override
        public CardCollection chooseCardsToDiscardFrom(final Player p, final SpellAbility sa, final CardCollection validCards,
                final int min, final int max, final DelayedReveal delayedReveal) {
            if (choices.prefer == null) {
                return super.chooseCardsToDiscardFrom(p, sa, validCards, min, max, delayedReveal);
            }
            return new CardCollection(pick(validCards, min, max));
        }

        @Override
        public Card chooseSingleCardForZoneChange(final ZoneType destination, final List<ZoneType> origin, final SpellAbility sa,
                final CardCollection fetchList, final DelayedReveal delayedReveal, final String selectPrompt, final boolean isOptional,
                final Player decider) {
            if (choices.prefer == null) {
                return super.chooseSingleCardForZoneChange(destination, origin, sa, fetchList, delayedReveal, selectPrompt, isOptional, decider);
            }
            final List<Card> p = pick(fetchList, isOptional ? 0 : 1, 1);
            return p.isEmpty() ? null : p.get(0);
        }

        @Override
        public List<Card> chooseCardsForZoneChange(final ZoneType destination, final List<ZoneType> origin, final SpellAbility sa,
                final CardCollection fetchList, final int min, final int max, final DelayedReveal delayedReveal, final String selectPrompt,
                final Player decider) {
            if (choices.prefer == null) {
                return super.chooseCardsForZoneChange(destination, origin, sa, fetchList, min, max, delayedReveal, selectPrompt, decider);
            }
            return pick(fetchList, min, max);
        }

        /** A spell cast by an effect (rebound, madness, cascade): cast when confirm is true, with the given targets. */
        @Override
        public boolean playSaFromPlayEffect(final SpellAbility tgtSA) {
            if (choices.confirm == null) {
                return super.playSaFromPlayEffect(tgtSA);
            }
            if (!choices.confirm) {
                return false;
            }
            if (choices.targets != null) {
                setTargets(tgtSA, choices.targets);
            } else if (!getAi().doTrigger(tgtSA, true)) {
                return false; // the AI found no targets
            }
            return ComputerUtil.playStack(tgtSA, player, getGame());
        }

        @Override
        public void declareAttackers(final Player attacker, final forge.game.combat.Combat combat) {
            if (choices.attackers == null) {
                super.declareAttackers(attacker, combat);
                return;
            }
            for (final Card c : choices.attackers) {
                combat.addAttacker(getGame().getCardState(c), attacker.getWeakestOpponent());
            }
        }

        @Override
        public void declareBlockers(final Player defender, final forge.game.combat.Combat combat) {
            if (choices.blocks == null) {
                super.declareBlockers(defender, combat);
                return;
            }
            choices.blocks.forEach((b, a) -> combat.addBlocker(getGame().getCardState(a), getGame().getCardState(b)));
        }

        @Override
        public void orderAndPlaySimultaneousSa(final List<SpellAbility> activePlayerSAs) {
            if (choices.targets == null && choices.modes.isEmpty()) {
                super.orderAndPlaySimultaneousSa(activePlayerSAs);
                return;
            }
            final List<SpellAbility> rest = new ArrayList<>();
            for (final SpellAbility sa : activePlayerSAs) {
                if (!sa.isTrigger() || sa.isCopied()) {
                    rest.add(sa);
                    continue;
                }
                if (sa.getApi() == ApiType.Charm && !CharmEffect.makeChoices(sa)) {
                    continue;
                }
                if (choices.targets != null) {
                    setTargets(sa, choices.targets);
                }
                ComputerUtil.playStack(sa, player, getGame());
            }
            if (!rest.isEmpty()) {
                super.orderAndPlaySimultaneousSa(rest);
            }
        }
    }

    /** Adds the card, and updates the game's last known state so effects counting the zone see it. */
    @Override
    protected Card addCardToZone(final String name, final Player p, final ZoneType zone) {
        final Card c = super.addCardToZone(name, p, zone);
        p.getGame().copyLastState();
        return c;
    }

    @Override
    protected Card addToken(final String name, final Player p) {
        final Card c = super.addToken(name, p);
        p.getGame().copyLastState();
        return c;
    }

    /** Prints the game log of a failed test. */
    @org.testng.annotations.AfterMethod
    public void printLogOnFailure(final org.testng.ITestResult result) {
        if (!result.isSuccess() && game != null) {
            System.out.println("---- game log of " + result.getName());
            game.getGameLog().getAllEntries().forEach(e -> System.out.println("  " + e.type() + ": " + e.message()));
        }
    }

    /** Each targeting part of the spell ability takes those of the targets it can target. */
    static void setTargets(final SpellAbility sa, final List<GameEntity> targets) {
        for (SpellAbility s = sa; s != null; s = s.getSubAbility()) {
            if (s.usesTargeting()) {
                s.resetTargets();
                for (final GameEntity e : targets) {
                    if (s.canTarget(e) && s.getTargets().size() < s.getMaxTargets()) {
                        s.getTargets().add(e);
                    }
                }
            }
        }
    }

    protected void newGame() {
        game = initAndCreateGame();
        for (final Player p : game.getPlayers()) {
            p.dangerouslySetController(new Controller(game, p));
        }
        me = game.getPlayers().get(1);
        opp = game.getPlayers().get(0);
        // Enough to draw from while turns pass
        for (final Player p : game.getPlayers()) {
            for (int i = 0; i < 10; i++) {
                addCardToZone("Woolly Thoctar", p, ZoneType.Library);
            }
        }
    }

    /** The choices the test makes for this player (in place of the AI). */
    protected static Choices choices(final Player p) {
        return ((Controller) p.getController()).choices;
    }

    protected List<Card> lands(final String name, final int n, final Player p) {
        return addCards(name, n, p);
    }

    /** A creature that entered the battlefield (its triggers resolved) and can attack or tap. */
    protected Card creature(final String name, final Player p) {
        final Card c = enter(name, p);
        c.setSickness(false);
        return c;
    }

    /** Puts a card from the hand into play as if it resolved, then resolves its triggers. */
    protected Card enter(final String name, final Player p) {
        final Card c = addCardToZone(name, p, ZoneType.Hand);
        final Card inPlay = game.getAction().moveToPlay(c, null, null);
        resolve();
        return inPlay;
    }

    protected Card hand(final String name, final Player p) {
        return addCardToZone(name, p, ZoneType.Hand);
    }

    protected Card grave(final String name, final Player p) {
        return addCardToZone(name, p, ZoneType.Graveyard);
    }

    /** Puts cards on top of the library, so the last one named is drawn first. */
    protected void library(final Player p, final String... names) {
        for (final String n : names) {
            final Card c = createCard(n, p);
            c.setGameTimestamp(game.getNextTimestamp());
            p.getZone(ZoneType.Library).add(c, 0);
        }
        game.copyLastState();
    }

    /**
     * Resolves everything on the stack, triggered abilities included, without leaving the step;
     * state-based actions (creatures with lethal damage dying) and static abilities are checked first.
     */
    protected void resolve() {
        for (int i = 0; i < 100 && !game.isGameOver(); i++) {
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

    /** Casts or activates the spell ability, paying with the player's mana sources, then resolves the stack. */
    protected void cast(final Player p, final SpellAbility sa, final Consumer<SpellAbility> targets) {
        sa.setActivatingPlayer(p);
        AssertJUnit.assertTrue("could not cast " + sa, ComputerUtil.handlePlayingSpellAbility(p, sa, targets));
        resolve();
    }

    /** Casts the card's first spell ability with these targets (on the first part of it that targets). */
    protected void cast(final Card c, final forge.game.GameObject... targets) {
        cast(c.getController(), current(c).getFirstSpellAbility(), targeting(targets));
    }

    /** Activates the card's ability whose description contains the text, with these targets. */
    protected void activate(final Card c, final String text, final forge.game.GameObject... targets) {
        cast(c.getController(), ability(current(c), text), targeting(targets));
    }

    /** The card's activated (or other non-spell) ability whose description contains the text. */
    protected static SpellAbility ability(final Card c, final String text) {
        return c.getAllSpellAbilities().stream().filter(sa -> !sa.isSpell() && sa.getDescription().contains(text)).findFirst()
                .orElseThrow(() -> new AssertionError(c + " has no ability \"" + text + "\": " + c.getAllSpellAbilities()));
    }

    /** Puts each target on the first part of the spell ability that can target it. */
    protected static Consumer<SpellAbility> targeting(final forge.game.GameObject... targets) {
        return sa -> {
            for (final forge.game.GameObject t : targets) {
                for (SpellAbility s = sa; s != null; s = s.getSubAbility()) {
                    if (s.usesTargeting() && s.canTarget(t) && s.getTargets().size() < s.getMaxTargets()) {
                        s.getTargets().add(t);
                        break;
                    }
                }
            }
        };
    }

    /** Puts the spell ability on the stack (paid, with these targets) without resolving it. */
    protected SpellAbility put(final Player p, final SpellAbility sa, final Consumer<SpellAbility> targets) {
        sa.setActivatingPlayer(p);
        AssertJUnit.assertTrue("could not cast " + sa, ComputerUtil.handlePlayingSpellAbility(p, sa, targets));
        return sa;
    }

    /** Plays a mana ability (no stack). */
    protected void mana(final Card c, final String text) {
        final SpellAbility sa = c.getManaAbilities().stream().filter(m -> text == null || m.getDescription().contains(text)).findFirst()
                .orElseThrow(() -> new AssertionError(c + " has no mana ability " + text));
        sa.setActivatingPlayer(c.getController());
        AssertJUnit.assertTrue("could not play " + sa, ComputerUtil.playNoStack(c.getController(), sa, game, true));
        resolve();
    }

    /** The card's spell with this alternative cost (flashback, escape, spectacle, ...) from where it is now. */
    protected SpellAbility alternative(final Card c, final Predicate<SpellAbility> which) {
        return alternative(c.getOwner(), c, which);
    }

    /** The spell or ability of the card, for this player, that matches (casting another player's card, say). */
    protected SpellAbility alternative(final Player p, final Card c, final Predicate<SpellAbility> which) {
        return current(c).getAllPossibleAbilities(p, false).stream().filter(which).findFirst()
                .orElseThrow(() -> new AssertionError(c + " has no such spell or ability for " + p));
    }

    /** The card's first spell with all its optional costs (kicker, buyback, ...) paid. */
    protected static SpellAbility withOptionalCosts(final Card c) {
        return forge.game.GameActionUtil.addOptionalCosts(c.getFirstSpellAbility(),
                forge.game.GameActionUtil.getOptionalCostValues(c.getFirstSpellAbility()));
    }

    protected static void untapAll(final Player p) {
        p.getCardsIn(ZoneType.Battlefield).forEach(c -> c.setTapped(false));
    }

    /** Deals damage from the source to the target, as combat damage or not, then resolves triggers. */
    protected void damage(final Card source, final GameEntity target, final int n, final boolean combat) {
        final forge.game.card.CardDamageTable table = new forge.game.card.CardDamageTable();
        table.put(source, target, n);
        game.getAction().dealDamage(combat, table, new forge.game.card.CardDamageTable(), new forge.game.GameEntityCounterTable(), null);
        resolve();
    }

    /** These creatures of the active player attack the opponent, unblocked unless blocks are given; play stops in main 2. */
    protected void attack(final Player p, final Map<Card, Card> blocks, final Card... attackers) {
        choices(p).attackers = List.of(attackers);
        for (final Player o : p.getOpponents()) {
            choices(o).blocks = blocks == null ? Map.of() : blocks;
        }
        playUntil(p, PhaseType.MAIN2);
        choices(p).attackers = List.of();
        for (final Player o : p.getOpponents()) {
            choices(o).blocks = Map.of();
        }
    }

    protected static boolean canBlock(final Card c) {
        return forge.game.combat.CombatUtil.canBlock(c);
    }

    protected void discard(final Player p, final Card c) {
        p.discard(c, null, true, forge.game.ability.AbilityKey.newMap());
        resolve();
    }

    protected static int p1p1(final Card c) {
        return c.getCounters(CounterEnumType.P1P1);
    }

    protected static int m1m1(final Card c) {
        return c.getCounters(CounterEnumType.M1M1);
    }

    protected Card current(final Card c) {
        return game.getCardState(c);
    }

    protected boolean in(final Card c, final ZoneType z) {
        return current(c).isInZone(z);
    }

    protected List<Card> tokens(final Player p) {
        return CardLists.filter(p.getCardsIn(ZoneType.Battlefield), Card::isToken);
    }

    protected int count(final Player p, final ZoneType z) {
        return p.getCardsIn(z).size();
    }

    /** Plays (both players' AI acting) until the phase of this player's turn, then resolves its triggers. */
    protected void playUntil(final Player p, final PhaseType phase) {
        final forge.game.phase.PhaseHandler ph = game.getPhaseHandler();
        final PhaseType from = ph.getPhase();
        final int turn = ph.getTurn();
        int i = 0;
        // Leave the current step first
        while (!game.isGameOver() && ++i < 2000 && ph.getPhase() == from && ph.getTurn() == turn) {
            ph.mainLoopStep();
        }
        while (!game.isGameOver() && ++i < 4000 && !(ph.is(phase) && ph.isPlayerTurn(p))) {
            ph.mainLoopStep();
        }
        resolve();
    }

    /** Destroys the creature the way state-based actions do (lethal damage), so its dies triggers run. */
    protected void kill(final Card c) {
        current(c).setDamage(current(c).getNetToughness());
        resolve();
    }
}
