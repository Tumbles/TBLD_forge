package forge.tbld;

import forge.ai.AvailableActions;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * The cards the web client highlights as playable ({@link AvailableActions}) leave out spells and abilities
 * that have no legal target.
 */
public class TbldCastableHighlightTest extends TbldCardTest {

    private boolean highlighted(final Player p, final Card c) {
        return AvailableActions.collectActionable(p, 5000).contains(current(c).getView());
    }

    /** The opponent casts the card from their hand, leaving it on the stack. */
    private SpellAbility oppCasts(final String name, final forge.game.GameObject... targets) {
        return put(opp, hand(name, opp).getFirstSpellAbility(), targeting(targets));
    }

    @Test
    public void counterspellNeedsAFittingSpellOnTheStack() {
        newGame();
        lands("Island", 2, me);
        lands("Mountain", 4, opp);
        final Card confound = hand("Confound", me); // counter target spell that targets a permanent
        AssertJUnit.assertFalse("empty stack", highlighted(me, confound));

        oppCasts("Ornithopter");
        AssertJUnit.assertFalse("a spell that targets nothing", highlighted(me, confound));

        resolve();
        final Card feeder = creature("Carrion Feeder", me);
        oppCasts("Searing Touch", me);
        AssertJUnit.assertFalse("a spell that targets a player", highlighted(me, confound));

        resolve();
        oppCasts("Searing Touch", feeder);
        AssertJUnit.assertTrue("a spell that targets a permanent", highlighted(me, confound));
    }

    @Test
    public void spellNeedsALegalTargetOnTheBattlefield() {
        newGame();
        lands("Island", 1, me);
        final Card inquiry = hand("Curious Inquiry", me); // target creature
        AssertJUnit.assertFalse("no creature to target", highlighted(me, inquiry));

        creature("Carrion Feeder", opp);
        AssertJUnit.assertTrue("a creature to target", highlighted(me, inquiry));
    }
}
