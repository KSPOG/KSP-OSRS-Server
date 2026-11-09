package net.runelite.client.plugins.microbot;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Opt-in private jav_config profile. This redirects configuration loading only,
 * not OSRS game networking or authentication. Default remains official.
 */
public final class KspPrivateServerConfig
{
    private KspPrivateServerConfig() {}

    public static boolean enabled()
    {
        return "true".equalsIgnoreCase(System.getenv("KSP_OSRS_PRIVATE_SERVER"));
    }

    public static String resolveJavConfigUrl(String officialUrl)
    {
        return resolve(officialUrl,
            System.getenv("KSP_OSRS_PRIVATE_SERVER"),
            System.getenv("KSP_OSRS_PRIVATE_JAV_CONFIG_URL"));
    }

    /** Pure helper for deterministic testing. */
    public static String resolve(String official, String enabled, String privateUrl)
    {
        if (!"true".equalsIgnoreCase(enabled)) return official;
        if (privateUrl == null || privateUrl.isBlank())
            throw new IllegalArgumentException("KSP_OSRS_PRIVATE_JAV_CONFIG_URL required in private mode");
        try
        {
            URI uri = new URI(privateUrl);
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null
                || host == null
                || !("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                || uri.getFragment() != null)
                throw new IllegalArgumentException("Private jav_config must be a loopback HTTP URL without credentials");
            return uri.toString();
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalArgumentException("Malformed private jav_config URL", ex);
        }
    }
}
