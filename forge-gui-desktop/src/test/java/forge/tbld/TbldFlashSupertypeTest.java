package forge.tbld;

import forge.ai.AITest;
import forge.card.CardType;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class TbldFlashSupertypeTest extends AITest {

    @Test
    public void flashSupertypeParses() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card touch = addCardToZone("Searing Touch", p, ZoneType.Hand);
        AssertJUnit.assertTrue(touch.getType().hasSupertype(CardType.Supertype.Flash));
        AssertJUnit.assertTrue(touch.isSorcery());
        AssertJUnit.assertFalse(touch.isInstant());
    }

    @Test
    public void flashSupertypeAllowsInstantSpeedCasting() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card touch = addCardToZone("Searing Touch", p, ZoneType.Hand);
        Card lavaAxe = addCardToZone("Lava Axe", p, ZoneType.Hand);

        // The opponent's upkeep: sorcery timing is not available to p.
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, opp);
        AssertJUnit.assertFalse(p.canCastSorcery());

        AssertJUnit.assertTrue(touch.getFirstSpellAbility().canCastTiming(p));
        AssertJUnit.assertFalse(lavaAxe.getFirstSpellAbility().canCastTiming(p));
    }

    @Test
    public void damagedPlayerCantGainLife() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        Card touch = addCardToZone("Searing Touch", p, ZoneType.Hand);

        SpellAbility sa = touch.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        sa.getTargets().add(opp);
        int lifeBefore = opp.getLife();
        AbilityUtils.resolve(sa);
        game.getAction().checkStaticAbilities();

        AssertJUnit.assertEquals(lifeBefore - 2, opp.getLife());
        AssertJUnit.assertFalse(opp.canGainLife());
        AssertJUnit.assertTrue(p.canGainLife());
    }
}
