/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.gamemodes.match.input;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.event.GameEventCombatChanged;
import forge.game.event.GameEventCombatUpdate;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.Selection;
import forge.gui.events.UiEventBlockerAssigned;
import forge.player.PlayerControllerHuman;
import forge.util.ITriggerEvent;
import forge.util.Localizer;
import forge.util.ThreadUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Declaring blockers (TBLD): select one of your creatures, then the attacker it blocks. After that
 * nothing is selected, and selecting an attacker does nothing. Selecting a creature that is
 * blocking takes its block back, without selecting it. Selecting the chosen creature again
 * cancels the choice. A right-click on one of your creatures removes it from combat.
 */
public class InputBlock extends InputSyncronizedBase {

    private static final long serialVersionUID = 6120743598368928128L;

    /** The creatures chosen to block with, waiting for an attacker; several when a group was selected at once. */
    private final List<Card> chosenBlockers = new ArrayList<>();
    private final Combat combat;
    private final Player defender;

    public InputBlock(final PlayerControllerHuman controller, final Player defender0, final Combat combat0) {
        super(controller);
        defender = defender0;
        combat = combat0;
    }

    /** {@inheritDoc} */
    @Override
    protected final void showMessage() {
        Localizer localizer = Localizer.getInstance();
        getController().getGui().updateButtons(getOwner(), true, false, true);

        if (chosenBlockers.isEmpty()) {
            showMessage(localizer.getMessage("lblSelectCreatureToBlockWith"));
        } else {
            showMessage(localizer.getMessage("lblSelectAttackerToBlock", blockerNames()));
        }

        if (combat != null)
            getController().getGame().fireEvent(GameEventCombatUpdate.fromCards(combat.getAttackers(), combat.getAllBlockers()));

        getController().getGui().showCombat();
    }

    private String blockerNames() {
        final StringBuilder sb = new StringBuilder();
        for (final Card c : chosenBlockers) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.isFaceDown() ? Localizer.getInstance().getMessage("lblMorph") : c.getDisplayName() + " (" + c.getId() + ")");
        }
        return sb.toString();
    }

    @Override
    protected boolean allowAwaitNextInput() {
        return true;
    }

    /** Before a creature is chosen, the creatures that may block; after, the attackers it may block. */
    @Override
    protected List<CardView> getActionable() {
        if (chosenBlockers.isEmpty()) {
            final List<CardView> result = new ArrayList<>();
            for (final Card c : defender.getCreaturesInPlay()) {
                if (canBeChosen(c)) {
                    result.add(c.getView());
                }
            }
            return result;
        }
        final List<CardView> result = new ArrayList<>();
        for (final Card attacker : combat.getAttackers()) {
            for (final Card blocker : chosenBlockers) {
                if (CombatUtil.canBlock(attacker, blocker, combat)) {
                    result.add(attacker.getView());
                    break;
                }
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public final void onOk() {
        final String blockErrors = CombatUtil.validateBlocks(combat, defender);
        if (blockErrors == null) {
            // Done blocking
            stop();
        } else {
            //must run in game thread to prevent problems for mobile game
            ThreadUtil.invokeInGameThread(() -> getController().getGui().message(blockErrors));
        }
    }

    private boolean isPotentialBlocker(final Card card) {
        return card.getController() == defender && card.isCreature() && defender.getZone(ZoneType.Battlefield).contains(card);
    }

    /** A creature may be chosen if it isn't blocking yet and could block some attacker. */
    private boolean canBeChosen(final Card blocker) {
        if (!isPotentialBlocker(blocker) || combat.isBlocking(blocker)) {
            return false;
        }
        for (final Card attacker : combat.getAttackers()) {
            if (CombatUtil.canBlock(attacker, blocker, combat)) {
                return true;
            }
        }
        return false;
    }

    /** Takes back every block of {@code blocker}. */
    private void unassign(final Card blocker) {
        combat.undoBlockingAssignment(blocker);
        blocker.getGame().getMatch().fireEvent(new UiEventBlockerAssigned(CardView.get(blocker), null));
    }

    /** {@inheritDoc} */
    @Override
    public final boolean onCardSelected(final Card card, final List<Card> otherCardsToSelect, final ITriggerEvent triggerEvent) {
        boolean isCorrectAction = false;
        final List<Card> clicked = new ArrayList<>();
        clicked.add(card);
        if (otherCardsToSelect != null) {
            clicked.addAll(otherCardsToSelect);
        }
        if (triggerEvent != null && triggerEvent.getButton() == 3 && card.getController() == defender) {
            for (final Card c : clicked) {
                if (c.getController() == defender) {
                    combat.removeFromCombat(c);
                    chosenBlockers.remove(c);
                    c.getGame().getMatch().fireEvent(new UiEventBlockerAssigned(CardView.get(c), null));
                }
            }
            isCorrectAction = true;
        } else if (combat.isAttacking(card)) {
            // Only a chosen creature can be assigned; with none chosen, selecting an attacker does nothing
            for (final Card blocker : chosenBlockers) {
                if (CombatUtil.canBlock(card, blocker, combat)) {
                    combat.addBlocker(card, blocker);
                    blocker.getGame().getMatch().fireEvent(new UiEventBlockerAssigned(CardView.get(blocker), CardView.get(card)));
                    isCorrectAction = true;
                }
            }
            if (isCorrectAction) {
                chosenBlockers.clear();
            }
        } else if (isPotentialBlocker(card)) {
            if (combat.isBlocking(card)) {
                // Selecting a blocking creature takes its block back, and leaves it unselected
                for (final Card c : clicked) {
                    if (isPotentialBlocker(c) && combat.isBlocking(c)) {
                        unassign(c);
                    }
                }
                chosenBlockers.clear();
                isCorrectAction = true;
            } else if (chosenBlockers.contains(card)) {
                // Selecting the chosen creature again cancels the choice
                chosenBlockers.clear();
                isCorrectAction = true;
            } else {
                final List<Card> chosen = new ArrayList<>();
                for (final Card c : clicked) {
                    if (canBeChosen(c)) {
                        chosen.add(c);
                    }
                }
                if (!chosen.isEmpty()) {
                    chosenBlockers.clear();
                    chosenBlockers.addAll(chosen);
                    isCorrectAction = true;
                }
            }
        }

        if (isCorrectAction) {
            card.getGame().fireEvent(new GameEventCombatChanged());
        }

        showSelection();
        showMessage();
        return isCorrectAction;
    }

    @Override
    public String getActivateAction(Card card) {
        if (combat.isAttacking(card)) {
            for (final Card blocker : chosenBlockers) {
                if (CombatUtil.canBlock(card, blocker, combat)) {
                    return Localizer.getInstance().getMessage("lblDeclareBlockersForCard");
                }
            }
            return null;
        }
        if (isPotentialBlocker(card) && combat.isBlocking(card)) {
            return Localizer.getInstance().getMessage("lblRemoveFromCombat");
        }
        if (canBeChosen(card)) {
            return Localizer.getInstance().getMessage("lblBlockWithCard");
        }
        return null;
    }

    @Override
    protected Selection getSelection() {
        if (chosenBlockers.isEmpty()) {
            return Selection.NONE;
        }
        final List<CardView> views = new ArrayList<>();
        for (final Card c : chosenBlockers) {
            views.add(CardView.get(c));
        }
        return Selection.ofChosen(views);
    }
}
