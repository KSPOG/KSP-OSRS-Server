package net.runelite.client.plugins.microbot;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Experimental, explicit, loopback-only native gamepack connection probe.
 *
 * Modifies applet-visible endpoint hints only. Whether revision-242 gamepack
 * actually uses these hints for JS5 must be verified through network evidence.
 * Does not bypass private-login readiness, modify gamepack classes, or connect
 * to public services when the probe is enabled.
 */
public final class KspJs5GamepackProbeConfig
{
    private static final String ENABLE_FLAG = "KSP_OSRS_JS5_GAMEPACK_PROBE";
    private static final String PORT_FLAG = "KSP_OSRS_JS5_PROBE_PORT";
    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 43594;

    private KspJs5GamepackProbeConfig() {}

    public static boolean enabled()
    {
        return "true".equalsIgnoreCase(System.getenv(ENABLE_FLAG));
    }

    public static void validateConfiguration()
    {
        validate(enabled(), KspPrivateServerConfig.enabled(),
            System.getenv(PORT_FLAG));
    }

    static int validate(boolean probeEnabled, boolean privateEnabled, String portValue)
    {
        if (!probeEnabled)
        {
            return DEFAULT_PORT;
        }
        if (privateEnabled)
        {
            throw new IllegalArgumentException(
                "JS5 gamepack probe cannot run with the unfinished KSP private-login profile");
        }
        final int port;
        try
        {
            port = portValue == null || portValue.isBlank()
                ? DEFAULT_PORT : Integer.parseInt(portValue);
        }
        catch (NumberFormatException invalid)
        {
            throw new IllegalArgumentException("Invalid loopback JS5 probe port", invalid);
        }
        if (port < 1 || port > 65535)
        {
            throw new IllegalArgumentException("Loopback JS5 probe port must be 1..65535");
        }
        return port;
    }

    /** No actual networking; only replaces the applet-visible URL. */
    public static URL codeBase(URL original)
    {
        if (!enabled())
        {
            return original;
        }
        int port = validate(enabled(), KspPrivateServerConfig.enabled(), System.getenv(PORT_FLAG));
        try
        {
            return new URL("http", HOST, port, "/");
        }
        catch (MalformedURLException impossible)
        {
            throw new IllegalStateException("Cannot construct loopback codebase", impossible);
        }
    }

    /**
     * Candidate JS5 host/ports. Real revision-242 consumption is unverified.
     * A localhost address is used for every socket-hint candidate; there is
     * no fallback to upstream game worlds inside this helper.
     */
    public static String parameter(String name, String original)
    {
        if (!enabled())
        {
            return original;
        }
        int port = validate(enabled(), KspPrivateServerConfig.enabled(), System.getenv(PORT_FLAG));
        return resolveParameter(name, original, port);
    }

    static String resolveParameter(String name, String original, int port)
    {
        if (name == null)
        {
            return original;
        }
        switch (name)
        {
            case "server":
                return HOST;
            case "port1":
            case "port2":
                return Integer.toString(port);
            default:
                return original;
        }
    }
}
