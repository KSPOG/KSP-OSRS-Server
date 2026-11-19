package net.runelite.client.plugins.microbot.accountselector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptStarted;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Script;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class TerminalLogoutHoldTest {
    private static GameStateChanged gameState(GameState state) {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
    }

    private static TerminalLogoutHold loggedOutOnPurpose() {
        TerminalLogoutHold hold = new TerminalLogoutHold();
        hold.onAutoLoginSuppressionRequest(new AutoLoginSuppressionRequest());
        hold.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        return hold;
    }

    @Test public void ordinaryLogoutsDoNotHold() {
        TerminalLogoutHold hold = new TerminalLogoutHold();
        hold.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        assertFalse(hold.isActive());
    }

    @Test public void terminalLogoutHoldsUntilAManualLogin() {
        TerminalLogoutHold hold = loggedOutOnPurpose();
        assertTrue(hold.isActive());
        hold.onGameStateChanged(gameState(GameState.LOGGED_IN));
        assertFalse(hold.isActive());
    }

    @Test public void breakHandlersAndAutoLoginStartingDoNotReleaseTheHold() {
        TerminalLogoutHold hold = loggedOutOnPurpose();
        hold.onScriptStarted(new ScriptStarted(mock(BreakHandlerScript.class)));
        hold.onScriptStarted(new ScriptStarted(mock(BreakHandlerV2Script.class)));
        hold.onScriptStarted(new ScriptStarted(mock(AutoLoginScript.class)));
        assertTrue(hold.isActive());
    }

    @Test public void anotherScriptStartingReleasesTheHold() {
        TerminalLogoutHold hold = loggedOutOnPurpose();
        hold.onScriptStarted(new ScriptStarted(mock(Script.class)));
        assertFalse(hold.isActive());
    }

    @Test public void loggedOutRequestHoldsWhenAlreadyAtTheLoginScreen() {
        try (MockedStatic<LoginManager> loginManager = Mockito.mockStatic(LoginManager.class)) {
            loginManager.when(LoginManager::getGameState).thenReturn(GameState.LOGIN_SCREEN);
            TerminalLogoutHold hold = new TerminalLogoutHold();
            hold.onAutoLoginSuppressionRequest(new AutoLoginSuppressionRequest());
            hold.onAutoLoginSuppressionRequest(AutoLoginSuppressionRequest.loggedOut());
            assertTrue(hold.isActive());
        }
    }
}
