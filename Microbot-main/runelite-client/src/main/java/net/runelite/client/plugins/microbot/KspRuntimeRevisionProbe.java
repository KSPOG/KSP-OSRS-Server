package net.runelite.client.plugins.microbot;

import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records a client revision after the injected game has initialized.
 * The pre-initialize value may be zero, which MUST NOT be interpreted as a revision.
 */
public final class KspRuntimeRevisionProbe
{
    private static final Logger log = LoggerFactory.getLogger(KspRuntimeRevisionProbe.class);
    private static final AtomicBoolean loggedNonzero = new AtomicBoolean();
    private static final AtomicBoolean loggedLoginScreen = new AtomicBoolean();

    private KspRuntimeRevisionProbe()
    {
    }

    public static void inspect(Client client, GameState state)
    {
        if (!"true".equalsIgnoreCase(System.getenv("KSP_OSRS_LOG_REVISION")) || client == null)
        {
            return;
        }

        int revision = client.getRevision();
        String buildId = client.getBuildID();

        if (revision > 0)
        {
            if (loggedNonzero.compareAndSet(false, true))
            {
                log.info("KSP_OSRS_REVISION_READY | revision={} buildId={} state={} privateProfile={}",
                    revision, buildId, state, KspPrivateServerConfig.enabled());
            }
            return;
        }

        // Useful independent diagnostic in case a client implementation never populates getRevision().
        if (state == GameState.LOGIN_SCREEN && loggedLoginScreen.compareAndSet(false, true))
        {
            log.warn("KSP_OSRS_REVISION_UNAVAILABLE | revision={} buildId={} state={} note=revision-zero-is-not-protocol-version",
                revision, buildId, state);
        }
    }
}
