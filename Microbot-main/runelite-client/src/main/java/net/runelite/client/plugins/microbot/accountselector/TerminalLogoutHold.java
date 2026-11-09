package net.runelite.client.plugins.microbot.accountselector;

import javax.inject.Singleton;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.ScriptStarted;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Script;
import net.runelite.client.plugins.microbot.util.security.LoginManager;

@Singleton
public class TerminalLogoutHold {
    private final AutoLoginSuppression suppression;

    public TerminalLogoutHold() {
        this(new AutoLoginSuppression());
    }

    TerminalLogoutHold(AutoLoginSuppression suppression) {
        this.suppression = suppression;
    }

    public boolean isActive() {
        return suppression.isSuppressed();
    }

    public void release() {
        suppression.clear();
    }

    @Subscribe
    public void onAutoLoginSuppressionRequest(AutoLoginSuppressionRequest event) {
        if (event.isRenewal()) {
            suppression.renew();
        } else if (event.isLoggedOut()) {
            suppression.holdLoggedOut(LoginManager.getGameState());
        } else {
            suppression.request();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        suppression.observe(event.getGameState());
    }

    @Subscribe
    public void onScriptStarted(ScriptStarted event) {
        if (event.getScript() instanceof AutoLoginScript
                || event.getScript() instanceof BreakHandlerScript
                || event.getScript() instanceof BreakHandlerV2Script) return;
        suppression.clear();
    }
}
