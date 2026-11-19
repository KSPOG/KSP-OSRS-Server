package net.runelite.client.plugins.microbot.util.skills.slayer;

import java.util.Collections;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Rs2SlayerGetSlayerTaskTest {
    private static final int BOSS_TASK_ID = 98;
    private static final int REGULAR_TASK_ID = 7;
    private static final int SUBLIST_ROW = 4000;
    private static final int TASK_ROW = 5000;

    private MockedStatic<Microbot> microbot;

    @Before
    public void setUp() {
        microbot = Mockito.mockStatic(Microbot.class);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private void taskId(int id) {
        microbot.when(() -> Microbot.getVarbitPlayerValue(VarPlayerID.SLAYER_TARGET)).thenReturn(id);
    }

    @Test
    public void regularTaskWithEmptyNameFieldReturnsNull() {
        taskId(REGULAR_TASK_ID);
        microbot.when(() -> Microbot.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, REGULAR_TASK_ID))
                .thenReturn(Collections.singletonList(TASK_ROW));
        microbot.when(() -> Microbot.getDBTableField(TASK_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
                .thenReturn(new Object[]{});

        assertNull(Rs2Slayer.getSlayerTask());
    }

    @Test
    public void bossTaskWithEmptySublistFieldReturnsNull() {
        taskId(BOSS_TASK_ID);
        microbot.when(() -> Microbot.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_SUBTABLE_ID, 0, BOSS_TASK_ID))
                .thenReturn(Collections.singletonList(SUBLIST_ROW));
        microbot.when(() -> Microbot.getDBTableField(SUBLIST_ROW, DBTableID.SlayerTaskSublist.COL_TASK, 0))
                .thenReturn(new Object[]{});

        assertNull(Rs2Slayer.getSlayerTask());
    }

    @Test
    public void bossTaskWithEmptyNameFieldReturnsNull() {
        taskId(BOSS_TASK_ID);
        microbot.when(() -> Microbot.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_SUBTABLE_ID, 0, BOSS_TASK_ID))
                .thenReturn(Collections.singletonList(SUBLIST_ROW));
        microbot.when(() -> Microbot.getDBTableField(SUBLIST_ROW, DBTableID.SlayerTaskSublist.COL_TASK, 0))
                .thenReturn(new Object[]{TASK_ROW});
        microbot.when(() -> Microbot.getDBTableField(TASK_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
                .thenReturn(new Object[]{});

        assertNull(Rs2Slayer.getSlayerTask());
    }

    @Test
    public void regularTaskReturnsName() {
        taskId(REGULAR_TASK_ID);
        microbot.when(() -> Microbot.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, REGULAR_TASK_ID))
                .thenReturn(Collections.singletonList(TASK_ROW));
        microbot.when(() -> Microbot.getDBTableField(TASK_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
                .thenReturn(new Object[]{"Goblins"});

        assertEquals("Goblins", Rs2Slayer.getSlayerTask());
    }

    @Test
    public void bossTaskReturnsName() {
        taskId(BOSS_TASK_ID);
        microbot.when(() -> Microbot.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_SUBTABLE_ID, 0, BOSS_TASK_ID))
                .thenReturn(Collections.singletonList(SUBLIST_ROW));
        microbot.when(() -> Microbot.getDBTableField(SUBLIST_ROW, DBTableID.SlayerTaskSublist.COL_TASK, 0))
                .thenReturn(new Object[]{TASK_ROW});
        microbot.when(() -> Microbot.getDBTableField(TASK_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
                .thenReturn(new Object[]{"Cerberus"});

        assertEquals("Cerberus", Rs2Slayer.getSlayerTask());
    }
}
