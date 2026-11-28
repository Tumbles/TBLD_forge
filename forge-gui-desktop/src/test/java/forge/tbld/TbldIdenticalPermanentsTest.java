package forge.tbld;

import forge.game.card.Card;
import forge.tbld.server.BrowserSeat;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

/**
 * The web client stacks identical permanents and splits a stack when its copies differ; it relies on the
 * snapshot's `fx` for what a card has been given. The fingerprint is the same for identical copies and
 * differs once one of them has gained a keyword.
 */
public class TbldIdenticalPermanentsTest extends TbldCardTest {

    @Test
    public void fingerprintIsEqualForIdenticalCopiesAndDiffersOnceOneGainsAKeyword() {
        newGame();
        final Card a = creature("Woolly Thoctar", me);
        final Card b = creature("Woolly Thoctar", me);
        final String before = BrowserSeat.effectsKey(a.getView());
        AssertJUnit.assertEquals("identical copies have different fingerprints", before, BrowserSeat.effectsKey(b.getView()));

        b.addChangedCardKeywords(List.of("Flying"), List.of(), false, game.getNextTimestamp(), null);
        AssertJUnit.assertEquals("an unchanged copy's fingerprint changed", before, BrowserSeat.effectsKey(a.getView()));
        AssertJUnit.assertTrue("gaining flying did not change the fingerprint",
                !before.equals(BrowserSeat.effectsKey(b.getView())));
    }
}
