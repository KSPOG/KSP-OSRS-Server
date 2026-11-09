package net.runelite.client.plugins.microbot.breakhandler;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import net.runelite.client.plugins.microbot.accountselector.TerminalLogoutHold;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.world.WorldSelectionMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class BreakHandlerTerminalLogoutTest {
    private MockedStatic<Microbot> microbot;
    private MockedStatic<LoginManager> loginManager;
    private BreakHandlerScript script;

    @Before
    public void setUp() throws Exception {
        microbot = Mockito.mockStatic(Microbot.class, Mockito.CALLS_REAL_METHODS);
        microbot.when(Microbot::isLoggedIn).thenReturn(false);
        loginManager = Mockito.mockStatic(LoginManager.class);
        script = new BreakHandlerScript();
        Field config = BreakHandlerScript.class.getDeclaredField("config");
        config.setAccessible(true);
        BreakHandlerConfig breakHandlerConfig = Mockito.mock(BreakHandlerConfig.class);
        Mockito.when(breakHandlerConfig.worldSelectionMode()).thenReturn(WorldSelectionMode.CURRENT_PREFERRED_WORLD);
        config.set(script, breakHandlerConfig);
    }

    @After
    public void tearDown() throws Exception {
        setState(BreakHandlerState.WAITING_FOR_BREAK);
        loginManager.close();
        microbot.close();
    }

    @Test
    public void terminalLogoutKeepsTheBreakHandlerFromLoggingIn() throws Exception {
        TerminalLogoutHold hold = new TerminalLogoutHold();
        hold.onAutoLoginSuppressionRequest(new AutoLoginSuppressionRequest());
        GameStateChanged loginScreen = new GameStateChanged();
        loginScreen.setGameState(GameState.LOGIN_SCREEN);
        hold.onGameStateChanged(loginScreen);
        script.terminalLogoutHold = hold;
        setState(BreakHandlerState.LOGIN_REQUESTED);

        Method handle = BreakHandlerScript.class.getDeclaredMethod("handleLoginRequestedState");
        handle.setAccessible(true);
        handle.invoke(script);
        handle.invoke(script);

        loginManager.verify(LoginManager::login, never());
        loginManager.verify(() -> LoginManager.login(Mockito.anyInt()), never());
        assertEquals(BreakHandlerState.LOGIN_REQUESTED, BreakHandlerScript.getCurrentState());

        hold.release();
        handle.invoke(script);

        loginManager.verify(LoginManager::login);
    }

    @SuppressWarnings("unchecked")
    private static void setState(BreakHandlerState state) throws Exception {
        Field field = BreakHandlerScript.class.getDeclaredField("currentState");
        field.setAccessible(true);
        ((AtomicReference<BreakHandlerState>) field.get(null)).set(state);
    }
}
