package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerTickGuard;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import org.junit.After;
import org.junit.Test;

import java.time.Instant;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class BreakHandlerV2DeferralTest {
    private BreakHandlerV2Script script;

    @After
    public void clearPluginLock() throws Exception {
        BreakHandlerScript.setLockState(false);
        BreakPreparation.finishBreak();
        Microbot.pauseAllScripts.set(false);
        BreakHandlerV2State.setState(BreakHandlerV2State.WAITING_FOR_BREAK);
        if (script != null) {
            Field executor = Script.class.getDeclaredField("scheduledExecutorService");
            executor.setAccessible(true);
            ((ScheduledExecutorService) executor.get(script)).shutdownNow();
        }
    }

    @Test
    public void requestsPreparationEvenWhenLegacyLockIsHeld() {
        try (BreakPreparation.Handle handle = BreakPreparation.register("test")) {
            BreakHandlerScript.setLockState(true);
            assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
            assertTrue(handle.isRequested());
            handle.ready();
            assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
            BreakHandlerScript.setLockState(false);
            assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
        }
    }

    @Test
    public void defersRequestedBreakWhilePluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertTrue(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
    }

    @Test
    public void doesNotDeferNewBreakWhenPluginLockIsReleased() {
        BreakHandlerScript.setLockState(false);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(null));
    }

    @Test
    public void doesNotDeferActiveNoLogoutBreakWhenPluginLockIsHeld() {
        BreakHandlerScript.setLockState(true);

        assertFalse(BreakHandlerV2Script.shouldDeferRequestedBreak(Instant.now()));
    }

    @Test
    public void continuesActiveBreakWhenGlobalPauseBlocksScripts() {
        assertTrue(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                true,
                BreakHandlerV2State.BREAK_REQUESTED,
                false));
    }

    @Test
    public void doesNotContinueNormalWaitingStateWhenGlobalPauseBlocksScripts() {
        assertFalse(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                true,
                BreakHandlerV2State.WAITING_FOR_BREAK,
                false));
    }

    @Test
    public void continuesWaitingStateAfterPreBreakPluginStopPausedScripts() {
        assertTrue(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                true,
                BreakHandlerV2State.WAITING_FOR_BREAK,
                true));
    }

    @Test
    public void doesNotBypassScriptGuardWhenScriptsAreNotPaused() {
        assertFalse(BreakHandlerV2Script.shouldContinueWhenScriptGuardBlocks(
                false,
                BreakHandlerV2State.BREAK_REQUESTED,
                false));
    }

    @Test
    public void activePauseBreakIgnoresLateParticipantTimeoutAndStillEnds() throws Exception {
        script = new BreakHandlerV2Script();
        Instant deadline = Instant.now().plusSeconds(600);
        setField("breakEndTime", deadline);
        BreakHandlerV2State.setState(BreakHandlerV2State.BREAK_REQUESTED);
        Microbot.pauseAllScripts.set(true);
        BreakPreparation.shouldDeferBreak();
        try (BreakPreparation.Handle late = BreakPreparation.register("late-plugin")) {
            Field requestedAt = BreakPreparation.class.getDeclaredField("requestedAt");
            requestedAt.setAccessible(true);
            requestedAt.setLong(null, System.currentTimeMillis() - 120_001);
            assertTrue(BreakPreparation.isAborted());

            invokeBreakRequested();

            assertEquals(deadline, getField("breakEndTime"));
            assertEquals(BreakHandlerV2State.BREAK_REQUESTED, BreakHandlerV2State.getCurrentState());
            assertTrue(Microbot.pauseAllScripts.get());

            setField("breakEndTime", Instant.now().minusSeconds(1));
            invokeBreakRequested();
            assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());
            assertFalse(Microbot.pauseAllScripts.get());
        }
    }

    @Test
    public void failedNewPreparationClearsPauseAndReschedules() throws Exception {
        assertFailedPreparationResumes(false);
    }

    @Test
    public void failedRestoredPausePreparationClearsPauseAndReschedules() throws Exception {
        assertFailedPreparationResumes(true);
    }

    private void assertFailedPreparationResumes(boolean restored) throws Exception {
        script = new BreakHandlerV2Script();
        setField("config", mock(BreakHandlerV2Config.class));
        setField("restoredPauseNeedsPreparation", restored);
        if (restored) {
            setField("breakEndTime", Instant.now().plusSeconds(600));
        }
        BreakHandlerV2State.setState(BreakHandlerV2State.BREAK_REQUESTED);
        Microbot.pauseAllScripts.set(true);
        BreakPreparation.Handle handle = BreakPreparation.register("failed-plugin");
        BreakPreparation.shouldDeferBreak();
        handle.close();
        assertTrue(BreakPreparation.isAborted());

        invokeBreakRequested();

        assertFalse(Microbot.pauseAllScripts.get());
        assertFalse(BreakPreparation.isAborted());
        assertEquals(BreakHandlerV2State.WAITING_FOR_BREAK, BreakHandlerV2State.getCurrentState());
        assertNull(getField("breakEndTime"));
        assertEquals(false, getField("restoredPauseNeedsPreparation"));
        assertNotNull(getField("nextBreakTime"));
    }

    @Test
    public void stopClearsPreparationRequestedByARunningTick() throws Exception {
        script = new BreakHandlerV2Script();
        Field guardField = BreakHandlerV2Script.class.getDeclaredField("tickGuard");
        guardField.setAccessible(true);
        BreakHandlerTickGuard guard = (BreakHandlerTickGuard) guardField.get(script);
        try (BreakPreparation.Handle handle = BreakPreparation.register("running-tick")) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread tick = new Thread(() -> guard.runTick(() -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                BreakPreparation.shouldDeferBreak();
            }));
            tick.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            Thread stopper = new Thread(script::shutdown);
            stopper.start();
            Thread.sleep(200);
            release.countDown();
            stopper.join(5000);
            tick.join(5000);

            assertFalse(stopper.isAlive());
            assertFalse(handle.isRequested());
        }
    }

    private void invokeBreakRequested() throws Exception {
        Method method = BreakHandlerV2Script.class.getDeclaredMethod("handleBreakRequested");
        method.setAccessible(true);
        method.invoke(script);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = BreakHandlerV2Script.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }

    private Object getField(String name) throws Exception {
        Field field = BreakHandlerV2Script.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }
}
