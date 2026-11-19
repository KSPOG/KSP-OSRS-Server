package net.runelite.client.plugins.microbot;

import java.security.Permission;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic-only Java socket egress guard. Requires Java 21 VM option
 * -Djava.security.manager=allow. Not an OS-level network sandbox:
 * native libraries and spawned processes are outside this guard.
 */
@SuppressWarnings("removal")
public final class KspJs5LoopbackGuard extends SecurityManager
{
    private static final AtomicInteger BLOCKED = new AtomicInteger();

    private KspJs5LoopbackGuard() {}

    public static void install()
    {
        if (!KspJs5GamepackProbeConfig.enabled())
        {
            return;
        }
        KspJs5GamepackProbeConfig.localJavConfigUrl(); // validate before anything else
        System.setSecurityManager(new KspJs5LoopbackGuard());
        if (!(System.getSecurityManager() instanceof KspJs5LoopbackGuard))
        {
            throw new IllegalStateException("JS5 local network guard was not installed");
        }
        System.out.println("KSP_JS5_LOOPBACK_GUARD_ACTIVE javaSockets=loopbackOnly nativeSockets=NOT_COVERED");
    }

    static boolean isLoopbackHost(String host)
    {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || "127.0.0.1".equals(h)
            || "::1".equals(h) || "[::1]".equals(h)
            || "0:0:0:0:0:0:0:1".equals(h);
    }

    @Override
    public void checkConnect(String host, int port)
    {
        if (!isLoopbackHost(host))
        {
            int count = BLOCKED.incrementAndGet();
            if (count <= 12)
            {
                System.err.println("KSP_JS5_NONLOCAL_SOCKET_BLOCKED ordinal=" + count
                    + " destination=" + host + " port=" + port);
            }
            throw new SecurityException("JS5 probe blocked non-loopback Java socket connection");
        }
    }

    @Override
    public void checkAccept(String host, int port)
    {
        checkConnect(host, port);
    }

    @Override
    public void checkPermission(Permission permission)
    {
        // Deliberately restrict socket destinations only. The rest of the
        // RuneLite process remains subject to the ordinary JVM permissions.
    }
}
