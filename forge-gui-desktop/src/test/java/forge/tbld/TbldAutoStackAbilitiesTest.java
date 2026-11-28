package forge.tbld;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.player.PlayerControllerHuman;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Auto stack abilities": with it on, a person's simultaneous abilities go on the stack in the order they
 * came, and the ordering window is never opened; with it off, they are asked (and their answer is used).
 */
public class TbldAutoStackAbilitiesTest extends AITest {

    /** Counts the ordering questions and answers each by reversing the list it was given. */
    private static IGuiGame reversingGui(final List<String> asked) {
        return (IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(), new Class<?>[] { IGuiGame.class }, (proxy, method, args) -> {
            if (method.getName().equals("order")) {
                asked.add((String) args[0]);
                final List<Object> list = new ArrayList<>((List<?>) args[4]);
                Collections.reverse(list);
                return new IGuiGame.OrderResult<>(list, false);
            }
            return null;
        });
    }

    @Test
    public void theOrderIsAskedOnlyWhenAutoStackIsOff() {
        final Game game = initAndCreateGame();
        final Player me = game.getPlayers().get(1);
        final SpellAbility first = addCardToZone("Searing Touch", me, ZoneType.Hand).getSpellAbilities().get(0);
        final SpellAbility second = addCardToZone("Woolly Thoctar", me, ZoneType.Hand).getSpellAbilities().get(0);
        final List<SpellAbility> abilities = List.of(first, second);

        final List<String> asked = new ArrayList<>();
        final PlayerControllerHuman person = new PlayerControllerHuman(game, me, me.getLobbyPlayer());
        person.setGui(reversingGui(asked));

        person.getYieldController().setPref(FPref.YIELD_AUTO_STACK_ABILITIES, "true");
        AssertJUnit.assertEquals("auto stack changed the order", abilities, person.orderSimultaneousSa(new ArrayList<>(abilities)));
        AssertJUnit.assertTrue("auto stack still opened the ordering window: " + asked, asked.isEmpty());

        person.getYieldController().setPref(FPref.YIELD_AUTO_STACK_ABILITIES, "false");
        final List<SpellAbility> ordered = person.orderSimultaneousSa(new ArrayList<>(abilities));
        AssertJUnit.assertEquals("with auto stack off the person was not asked", 1, asked.size());
        AssertJUnit.assertEquals("the person's order was not used", List.of(second, first), ordered);
    }
}
