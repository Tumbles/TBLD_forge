package forge.tbld;

import com.google.common.collect.Lists;
import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.tbld.TbldEmblems;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPayManaOfCostPayment;
import forge.gui.interfaces.IGuiGame;
import forge.player.LobbyPlayerHuman;
import forge.player.PlayerControllerHuman;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * While a person pays for a spell, the Auto button starts disabled when the spell is affordable only with
 * their Player Two emblem (Auto never spends it), and turns enabled once they have used the emblem and
 * what is left of the cost is payable.
 */
public class TbldAutoPayAfterEmblemTest extends AITest {

    /** A recording stand-in for the client: every {@code updateButtons} call as {label1, enable1}. */
    private final List<Object[]> buttons = Collections.synchronizedList(new ArrayList<>());

    private IGuiGame recordingGui() {
        return (IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(), new Class<?>[] { IGuiGame.class }, (proxy, method, args) -> {
            if ("updateButtons".equals(method.getName()) && args != null && args.length >= 6) {
                buttons.add(new Object[] { args[1], args[3] });
            }
            if ("getAbilityToPlay".equals(method.getName()) && args != null && args[1] instanceof List<?> offered && !offered.isEmpty()) {
                return offered.get(0); // the client uses a single ability as it is
            }
            final Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            return null;
        });
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private boolean lastAutoEnabled() {
        synchronized (buttons) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                if ("Auto".equals(buttons.get(i)[0])) {
                    return (Boolean) buttons.get(i)[1];
                }
            }
        }
        return false;
    }

    @Test(timeOut = 60000)
    public void autoPayEnablesOnceThePlayerTwoEmblemHasPaid() throws Exception {
        initAndCreateGame();
        final List<RegisteredPlayer> players = Lists.newArrayList(
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerHuman("person")),
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("ai", null)));
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setTbldStartingEmblems(true);
        final Game game = new Game(players, rules, new Match(rules, players, "Test"));
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        game.getPhaseHandler().onStackResolved();

        final Player person = game.getPlayers().get(0);
        final PlayerControllerHuman controller = (PlayerControllerHuman) person.getController();
        controller.setGui(recordingGui());
        final Card emblem = TbldEmblems.give(game, person, 1);
        addCards("Mountain", 2, person);
        final Card ogre = addCardToZone("Rodeo Pyromancers", person, ZoneType.Hand); // {2}{R}
        final SpellAbility cast = ogre.getFirstSpellAbility();
        cast.setActivatingPlayer(person);

        final InputPayManaOfCostPayment pay = new InputPayManaOfCostPayment(controller, new ManaCostBeingPaid(ogre.getManaCost()), cast, person, null, false);
        pay.showMessageInitial();
        flushEdt();
        AssertJUnit.assertFalse("Auto is enabled although it would need the emblem", lastAutoEnabled());

        pay.selectCard(emblem, null, null);
        final long deadline = System.currentTimeMillis() + 8000;
        while (!lastAutoEnabled() && System.currentTimeMillis() < deadline) {
            flushEdt();
            Thread.sleep(50);
        }
        AssertJUnit.assertTrue("the emblem was not used", person.getCardsIn(ZoneType.Command).isEmpty());
        AssertJUnit.assertTrue("Auto stayed disabled after the emblem paid its part (" + pay + "); buttons: " + describe(), lastAutoEnabled());
    }

    private String describe() {
        final StringBuilder sb = new StringBuilder();
        synchronized (buttons) {
            for (final Object[] b : buttons) {
                sb.append(b[0]).append('=').append(b[1]).append(' ');
            }
        }
        return sb.toString();
    }
}
