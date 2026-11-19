package net.runelite.client.plugins.microbot.questhelper.logic;

import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class RomeoAndJulietWalkTest {

    private static final String BRING_POTION = "Bring the potion to Juliet in the house west of Varrock.";
    private static final String TALK_TO_ROMEO = "Talk to Romeo in Varrock Square to finish the quest.";

    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Dialogue> dialogue;
    private QuestStep step;
    private RomeoAndJuliet logic;

    @Before
    public void setUp() {
        walker = Mockito.mockStatic(Rs2Walker.class);
        player = Mockito.mockStatic(Rs2Player.class);
        inventory = Mockito.mockStatic(Rs2Inventory.class);
        dialogue = Mockito.mockStatic(Rs2Dialogue.class);
        dialogue.when(() -> Rs2Dialogue.hasDialogueOptionTitle(anyString())).thenReturn(false);
        inventory.when(() -> Rs2Inventory.hasItem(any(int[].class))).thenReturn(true);

        step = mock(QuestStep.class);
        when(step.getActiveStep()).thenReturn(step);
        QuestHelper quest = mock(QuestHelper.class);
        when(quest.getQuest()).thenReturn(QuestHelperQuest.ROMEO__JULIET);
        when(quest.getCurrentStep()).thenReturn(step);
        QuestHelperPlugin plugin = mock(QuestHelperPlugin.class);
        when(plugin.getSelectedQuest()).thenReturn(quest);

        logic = spy(new RomeoAndJuliet());
        doReturn(plugin).when(logic).getQuestHelperPlugin();
    }

    @After
    public void tearDown() {
        dialogue.close();
        inventory.close();
        player.close();
        walker.close();
    }

    @Test
    public void walksToJulietStairsWithDoorHandlingWalker() {
        when(step.getText()).thenReturn(Collections.singletonList(BRING_POTION));
        player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3165, 3434, 0));

        assertFalse(logic.executeCustomLogic());

        walker.verify(() -> Rs2Walker.walkTo(new WorldPoint(3159, 3436, 0), 0));
        walker.verify(() -> Rs2Walker.walkFastCanvas(any(WorldPoint.class)), never());
    }

    @Test
    public void walksToJulietTopStairsWithDoorHandlingWalker() {
        when(step.getText()).thenReturn(Collections.singletonList(TALK_TO_ROMEO));
        player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3160, 3426, 1));

        assertFalse(logic.executeCustomLogic());

        walker.verify(() -> Rs2Walker.walkTo(new WorldPoint(3156, 3435, 1), 4));
        walker.verify(() -> Rs2Walker.walkFastCanvas(any(WorldPoint.class)), never());
        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), Mockito.times(1));
    }
}
