package net.runelite.client.plugins.microbot;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final String CONFIG_FLAG = "KSP_OSRS_JS5_LOCAL_JAV_CONFIG_URL";
    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 43594;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    // Historical RuneLite fixture keys, used for diagnostics only. These are
    // NOT asserted to be sufficient or required by the revision-242 gamepack.
    private static final String[] REFERENCE_PARAMETER_KEYS = {
        "1", "3", "4", "5", "6", "7", "8", "10", "12", "14", "15", "16"
    };
    private static final java.util.concurrent.atomic.AtomicInteger REPORTED_ACCESS =
        new java.util.concurrent.atomic.AtomicInteger();
    private static final int MAX_PARAMETER_ACCESS_LOGS = 64;

    private KspJs5GamepackProbeConfig() {}

    public static boolean enabled()
    {
        return "true".equalsIgnoreCase(System.getenv(ENABLE_FLAG));
    }

    /**
     * Strict opt-in network boundary for the JS5-only probe: never fetch
     * production jav_config or use its official backup/world fallback.
     * Caller must supply a separate local HTTP configuration service.
     * This is NOT a process-wide network sandbox: gamepack/other plugins
     * may use additional endpoints, which must be audited independently.
     */
    public static String localJavConfigUrl()
    {
        if (!enabled())
        {
            return null;
        }
        return requireLocalJavConfigUrl(System.getenv(CONFIG_FLAG));
    }

    public static String requireLocalJavConfigUrl(String address)
    {
        if (address == null || address.isBlank())
        {
            throw new IllegalArgumentException(
                "JS5 probe refuses official jav_config. Set KSP_OSRS_JS5_LOCAL_JAV_CONFIG_URL to a loopback HTTP config service.");
        }
        try
        {
            URI uri = new URI(address);
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || host == null
                || !("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                || uri.getPort() < 1
                || uri.getFragment() != null)
            {
                throw new IllegalArgumentException("JS5 local jav_config URL must be loopback HTTP with an explicit port");
            }
            return uri.toString();
        }
        catch (URISyntaxException invalid)
        {
            throw new IllegalArgumentException("Malformed JS5 local jav_config URL", invalid);
        }
    }

    /**
     * Refuse untrusted production endpoints from a local configuration.
     * This protects config-origin and advertised URLs only. It is not
     * proof that embedded gamepack sockets cannot reach external hosts.
     */
    public static void validateLocalConfig(String codebase,
                                            Map<String, String> loaderProperties,
                                            Map<String, String> appletProperties)
    {
        requireLocalJavConfigUrl(codebase);
        validateLocalConfigValues(loaderProperties);
        validateLocalConfigValues(appletProperties);
    }

    private static void validateLocalConfigValues(Map<String, String> values)
    {
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            String value = entry.getValue();
            if (value == null)
            {
                continue;
            }
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("runescape.com") || lower.contains("jagex.com")
                || lower.contains("runescape.net") || lower.contains("jagex.net"))
            {
                throw new IllegalArgumentException(
                    "JS5 local jav_config contains a production endpoint in parameter " + entry.getKey());
            }
            if (lower.startsWith("http://") || lower.startsWith("https://"))
            {
                requireLocalJavConfigUrl(value);
            }
        }
    }

    /**
     * Report configuration completeness without printing parameter values.
     * In particular, an isolated fixture with zero numeric parameters is
     * insufficient to reproduce ordinary RuneLite applet startup.
     */
    public static void reportLocalConfigSummary(Map<String, String> parameters)
    {
        if (!enabled())
        {
            return;
        }
        int numericCount = 0;
        for (String key : parameters.keySet())
        {
            if (key != null && key.matches("[0-9]{1,3}"))
            {
                numericCount++;
            }
        }
        java.util.List<String> absentReferenceKeys = new java.util.ArrayList<>();
        for (String key : REFERENCE_PARAMETER_KEYS)
        {
            if (!parameters.containsKey(key))
            {
                absentReferenceKeys.add(key);
            }
        }
        System.out.println("KSP_JS5_APPLET_CONFIG_SUMMARY | totalKeys="
            + parameters.size() + " numericKeys=" + numericCount
            + " missingHistoricalReferenceKeys=" + String.join(",", absentReferenceKeys)
            + " valuesLogged=false");
    }

    static String safeDiagnosticName(String name)
    {
        return name != null && name.matches("(?:[0-9]{1,3}|server|port1|port2)")
            ? name : "other";
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
            if (REPORTED.add("codebase"))
            {
                System.out.println("KSP_JS5_GAMEPACK_CODEBASE | target=127.0.0.1:" + port);
            }
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
        String resolved = resolveParameter(name, original, port);
        // Record only safe key names and null status, never URLs, tokens or
        // parameter values. The gamepack calls this during client.init.
        String diagnosticName = safeDiagnosticName(name);
        if (REPORTED.add("access:" + diagnosticName)
            && REPORTED_ACCESS.incrementAndGet() <= MAX_PARAMETER_ACCESS_LOGS)
        {
            System.out.println("KSP_JS5_GAMEPACK_PARAM_ACCESS | name=" + diagnosticName
                + " provided=" + (original != null) + " resolved=" + (resolved != null)
                + " valuesLogged=false");
        }
        if (name != null && ("server".equals(name) || "port1".equals(name) || "port2".equals(name))
            && REPORTED.add("param:" + name))
        {
            System.out.println("KSP_JS5_GAMEPACK_PARAM | name=" + name + " redirected=true");
        }
        return resolved;
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
