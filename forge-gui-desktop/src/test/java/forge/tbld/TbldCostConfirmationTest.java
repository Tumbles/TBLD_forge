package forge.tbld;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.CostPart;
import forge.game.cost.CostRemoveCounter;
import forge.game.cost.CostSacrifice;
import forge.game.cost.PaymentDecision;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.player.HumanCostDecision;
import forge.player.PlayerControllerHuman;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A person activating a Blood token is not asked "Do you want to sacrifice Blood?", and one activating
 * Fireminds Research is not asked "Do you want to remove two charge counters?": activating the ability is the
 * decision. The same costs paid for an effect ("unless ...") are still confirmed.
 */
public class TbldCostConfirmationTest extends AITest {

    @Test
    public void activatingBloodDoesNotConfirmTheSacrifice() {
        final Game game = initAndCreateGame();
        final Player me = game.getPlayers().get(1);
        final Card blood = addToken("c_a_blood_draw", me);
        final SpellAbility draw = blood.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        draw.setActivatingPlayer(me);
        CostSacrifice sacrifice = null;
        for (final CostPart part : draw.getPayCosts().getCostParts()) {
            if (part instanceof CostSacrifice s) {
                sacrifice = s;
            }
        }
        AssertJUnit.assertNotNull("Blood's cost has no sacrifice", sacrifice);

        final List<String> asked = new ArrayList<>();
        final PlayerControllerHuman person = new PlayerControllerHuman(game, me, me.getLobbyPlayer()) {
            @Override
            public boolean confirmPayment(final CostPart costPart, final String question, final SpellAbility sa) {
                asked.add(question);
                return true;
            }
        };

        final PaymentDecision paid = new HumanCostDecision(person, me, draw, false, null).visit(sacrifice);
        AssertJUnit.assertNotNull("the sacrifice was not paid", paid);
        AssertJUnit.assertTrue("the sacrifice is not of the Blood token", paid.cards.contains(blood));
        AssertJUnit.assertTrue("activating Blood asked " + asked, asked.isEmpty());

        new HumanCostDecision(person, me, draw, true, null).visit(sacrifice);
        AssertJUnit.assertEquals("a sacrifice paid for an effect was not confirmed", 1, asked.size());
    }

    @Test
    public void activatingFiremindsResearchDoesNotConfirmRemovingItsCounters() {
        final Game game = initAndCreateGame();
        final Player me = game.getPlayers().get(1);
        final Card research = addCardToZone("Fireminds Research", me, ZoneType.Battlefield);
        research.addCounterInternal(CounterEnumType.CHARGE, 5, me, false, null, null);
        final SpellAbility draw = research.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        draw.setActivatingPlayer(me);
        CostRemoveCounter remove = null;
        for (final CostPart part : draw.getPayCosts().getCostParts()) {
            if (part instanceof CostRemoveCounter r) {
                remove = r;
            }
        }
        AssertJUnit.assertNotNull("the draw ability's cost has no counter removal", remove);

        final List<String> asked = new ArrayList<>();
        final PlayerControllerHuman person = new PlayerControllerHuman(game, me, me.getLobbyPlayer()) {
            @Override
            public boolean confirmPayment(final CostPart costPart, final String question, final SpellAbility sa) {
                asked.add(question);
                return true;
            }
        };

        final PaymentDecision paid = new HumanCostDecision(person, me, draw, false, null).visit(remove);
        AssertJUnit.assertNotNull("the counters were not removed", paid);
        AssertJUnit.assertTrue("activating Fireminds Research asked " + asked, asked.isEmpty());

        new HumanCostDecision(person, me, draw, true, null).visit(remove);
        AssertJUnit.assertEquals("removing counters for an effect was not confirmed", 1, asked.size());
    }
}
