package net.runelite.client.plugins.microbot;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.Permission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
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

    private static final boolean HUBS_ENABLED = "true".equalsIgnoreCase(System.getenv("KSP_LOCAL_PLUGIN_HUBS"));
    private static final Set<String> HUB_HOSTS = Set.of(
        "repo.runelite.net", "chsami.github.io", "github.com", "api.github.com",
        "release-assets.githubusercontent.com", "objects.githubusercontent.com");
    private static final Set<String> HUB_ADDRESSES = ConcurrentHashMap.newKeySet();

    private KspJs5LoopbackGuard() {}

    public static void configureHubNetworking(OkHttpClient.Builder builder)
    {
        if (!KspJs5GamepackProbeConfig.enabled() || !HUBS_ENABLED) return;
        builder.proxy(Proxy.NO_PROXY).followSslRedirects(false).dns(host ->
        {
            if (isLoopbackHost(host)) return Dns.SYSTEM.lookup(host);
            if (!HUB_HOSTS.contains(host)) throw new UnknownHostException("Host is outside the local plugin hubs");
            List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
            for (InetAddress address : addresses) HUB_ADDRESSES.add(address.getHostAddress());
            return addresses;
        }).addInterceptor(chain ->
        {
            validateHubRequest(chain.request().url(), chain.request().method());
            return chain.proceed(chain.request());
        }).addNetworkInterceptor(chain ->
        {
            validateHubRequest(chain.request().url(), chain.request().method());
            return chain.proceed(chain.request());
        });
    }

    private static void validateHubRequest(HttpUrl url, String method) throws IOException
    {
        if (isLoopbackHost(url.host())) return;
        String path = url.encodedPath();
        boolean hubPath;
        switch (url.host())
        {
            case "repo.runelite.net":
                hubPath = path.startsWith("/plugins/");
                break;
            case "chsami.github.io":
                hubPath = path.startsWith("/Microbot-Hub/");
                break;
            case "github.com":
                hubPath = path.startsWith("/chsami/Microbot-Hub/releases/");
                break;
            case "api.github.com":
                hubPath = path.equals("/repos/chsami/Microbot-Hub/releases")
                    || path.startsWith("/repos/chsami/Microbot-Hub/releases/");
                break;
            case "release-assets.githubusercontent.com":
            case "objects.githubusercontent.com":
                hubPath = true;
                break;
            default:
                hubPath = false;
        }
        if (!url.isHttps() || url.port() != 443 || !hubPath || !("GET".equals(method) || "HEAD".equals(method)))
        {
            throw new IOException("URL is outside the local plugin hubs");
        }
    }

    private boolean allowedHubConnect(String host, int port)
    {
        if (!HUBS_ENABLED || host == null) return false;
        if (port == -1) return HUB_HOSTS.contains(host);
        if (port != 443 || !HUB_ADDRESSES.contains(host)) return false;
        for (Class<?> frame : getClassContext())
        {
            if (frame.getName().startsWith("okhttp3.internal.connection.")) return true;
        }
        return false;
    }

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
        System.out.println("KSP_JS5_LOOPBACK_GUARD_ACTIVE pluginHubs=" + HUBS_ENABLED);
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
        if (!isLoopbackHost(host) && !allowedHubConnect(host, port))
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
        if (!isLoopbackHost(host)) throw new SecurityException("Local client refuses non-loopback peers");
    }

    @Override
    public void checkPermission(Permission permission)
    {
        // Deliberately restrict socket destinations only. The rest of the
        // RuneLite process remains subject to the ordinary JVM permissions.
    }
}
