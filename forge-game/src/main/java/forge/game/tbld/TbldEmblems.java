package forge.game.tbld;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.GamePieceType;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TBLD rule: starting emblems. Once the turn order is known, the player who goes first gets the
 * Player One emblem, the next player in turn order Player Two, and so on up to Player Five. A
 * two-player game uses Player One and Player Two. Emblems stay in their owner's command zone.
 *
 * <p>The emblems are written as card scripts. Their abilities use three engine additions:
 * {@code ExileFromCommand<1/CARDNAME>} (the "Exile this" cost), {@code CantBeCountered$ True} on
 * an ability, and {@code ExtraTurn$ False} on a BeginTurn replacement. "Exile this" removes the
 * emblem from the game, as for any emblem or effect that leaves the command zone.
 *
 * <p>Player One's "You skip your first draw step" replaces the usual rule that the first player
 * in a two-player game skips their first draw: {@code PhaseHandler} turns that rule off when
 * starting emblems are on.
 */
public final class TbldEmblems {

    /** The emblems' names, in turn order. Their pictures are "TBLD Player One.png" and so on. */
    public static final List<String> NAMES = List.of("Player One", "Player Two", "Player Three", "Player Four", "Player Five");
    /** The printed type line's subtypes, in the same order. Emblems have no types in the rules. */
    private static final List<String> SUBTYPES = List.of("Book of Lore", "Shard of Mana", "Glass of Time", "Crown of Influence",
            "Scepter of Supremacy");

    private static final String[] SCRIPTS = {
        // Emblem — Book of Lore
        """
        Name:Player One
        ManaCost:no cost
        Types:
        R:Event$ BeginPhase | ActiveZones$ Command | ValidPlayer$ You | Phase$ Draw | CheckSVar$ FirstDrawSkipped | SVarCompare$ EQ0 | ReplaceWith$ DBFirstDrawSkipped | Description$ You skip your first draw step.
        SVar:DBFirstDrawSkipped:DB$ StoreSVar | SVar$ FirstDrawSkipped | Type$ Number | Expression$ 1
        SVar:FirstDrawSkipped:Number$0
        A:AB$ Draw | ActivationZone$ Command | Cost$ 3 ExileFromCommand<1/CARDNAME> | CostDesc$ {3}, Exile this: | PlayerTurn$ True | CantBeCountered$ True | Defined$ You | NumCards$ 2 | SubAbility$ DBExileFromHand | SpellDescription$ Draw two cards, then exile a card from your hand. Activate only during your turn. This ability can't be countered.
        SVar:DBExileFromHand:DB$ ChangeZone | Origin$ Hand | Destination$ Exile | ChangeType$ Card | ChangeNum$ 1 | Mandatory$ True
        Oracle:You skip your first draw step.\\n{3}, Exile this: Draw two cards, then exile a card from your hand. Activate only during your turn. This ability can't be countered.
        """,
        // Emblem — Shard of Mana
        """
        Name:Player Two
        ManaCost:no cost
        Types:
        A:AB$ Mana | ActivationZone$ Command | Cost$ ExileFromCommand<1/CARDNAME> | CostDesc$ Exile this: | Produced$ C | SpellDescription$ Add {C}.
        Oracle:Exile this: Add {C}.
        """,
        // Emblem — Glass of Time
        """
        Name:Player Three
        ManaCost:no cost
        Types:
        A:AB$ AddTurn | ActivationZone$ Command | Cost$ 2 ExileFromCommand<1/CARDNAME> | CostDesc$ {2}, Exile this: | PlayerTurn$ True | CantBeCountered$ True | Defined$ You | NumTurns$ 1 | SubAbility$ DBSkipNextTurn | SpellDescription$ Take an extra turn after this one. The next time you would begin a turn that isn't an extra turn, skip that turn. Activate only during your turn. This ability can't be countered.
        SVar:DBSkipNextTurn:DB$ Effect | Name$ Player Three's Effect | ReplacementEffects$ RSkipNextTurn | Duration$ Permanent
        SVar:RSkipNextTurn:Event$ BeginTurn | ActiveZones$ Command | ValidPlayer$ You | ExtraTurn$ False | Layer$ Control | ReplaceWith$ ExileEffect | Description$ The next time you would begin a turn that isn't an extra turn, skip that turn.
        SVar:ExileEffect:DB$ ChangeZone | Defined$ Self | Origin$ Command | Destination$ Exile
        Oracle:{2}, Exile this: Take an extra turn after this one. The next time you would begin a turn that isn't an extra turn, skip that turn. Activate only during your turn. This ability can't be countered.
        """,
        // Emblem — Crown of Influence
        """
        Name:Player Four
        ManaCost:no cost
        Types:
        A:AB$ Counter | ActivationZone$ Command | Cost$ ExileFromCommand<1/CARDNAME> | CostDesc$ Exile this: | TargetType$ Spell | ValidTgts$ Card | TgtPrompt$ Select target spell | CantBeCountered$ True | SpellDescription$ Counter target spell. This ability can't be countered.
        Oracle:Exile this: Counter target spell. This ability can't be countered.
        """,
        // Emblem — Scepter of Supremacy
        """
        Name:Player Five
        ManaCost:no cost
        Types:
        S:Mode$ Continuous | EffectZone$ Command | Affected$ You | SetMaxHandSize$ Unlimited | Description$ You have no maximum hand size.
        T:Mode$ Phase | Phase$ Draw | ValidPlayer$ You | TriggerZones$ Command | Execute$ TrigDraw | TriggerDescription$ Draw an additional card during each of your draw steps.
        SVar:TrigDraw:DB$ Draw | Defined$ You | NumCards$ 1
        Oracle:You have no maximum hand size.\\nDraw an additional card during each of your draw steps.
        """,
    };

    private TbldEmblems() {
    }

    /**
     * Gives each player the emblem for their place in turn order, starting with {@code first}.
     * Starting emblems given earlier in this game are taken away first, so this can be called
     * again if the starting player changes.
     */
    public static void giveStartingEmblems(final Game game, final Player first) {
        removeStartingEmblems(game);
        Player p = first;
        int place = 0;
        do {
            if (place < NAMES.size()) {
                give(game, p, place);
            }
            place++;
            p = game.getNextPlayerAfter(p);
        } while (p != null && p != first && place <= game.getPlayers().size());
    }

    /** Puts the emblem for turn-order place {@code place} (0 for Player One) into {@code p}'s command zone. */
    public static Card give(final Game game, final Player p, final int place) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(SCRIPTS[place].split("\n")));
        final Card emblem = CardFactory.getCard(new PaperCard(rules, "TBLD", CardRarity.Special), p, game);
        emblem.setGamePieceType(GamePieceType.EFFECT);
        emblem.setEmblem(true);
        game.getAction().moveToCommand(emblem, null);
        return emblem;
    }

    /** The type line printed on an emblem: "Emblem — Book of Lore" for Player One; "Emblem" for any other emblem. */
    public static String typeLine(final String name) {
        final int i = NAMES.indexOf(name);
        return i < 0 ? "Emblem" : "Emblem — " + SUBTYPES.get(i);
    }

    /** Whether this card is one of the starting emblems. */
    public static boolean isStartingEmblem(final Card c) {
        return c.isEmblem() && NAMES.contains(c.getName());
    }

    private static void removeStartingEmblems(final Game game) {
        for (final Player p : game.getPlayers()) {
            final PlayerZone command = p.getZone(ZoneType.Command);
            final List<Card> old = new ArrayList<>();
            for (final Card c : command.getCards()) {
                if (isStartingEmblem(c)) {
                    old.add(c);
                }
            }
            for (final Card c : old) {
                command.remove(c);
            }
        }
    }
}
