package net.runelite.client.plugins.microbot;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * A read-only fail-closed handshake with the KSP local server coordinator.
 * This does not implement, emulate, or replace the OSRS game protocol.
 */
public final class KspServerDiscovery
{
    private static final Pattern GAME_READY =
        Pattern.compile("\"gameProtocolReady\"\\s*:\\s*true");
    private static final Pattern CONFIG_READY =
        Pattern.compile("\"javConfigReady\"\\s*:\\s*true");
    public static final int EXPECTED_OSRS_REVISION = 242;
    private static final Pattern REVISION = Pattern.compile("\"clientRevision\"\\s*:\\s*([0-9]+)");
    private static final Pattern VERIFIED = Pattern.compile("\"revisionVerified\"\\s*:\\s*true");

    private static final int MAX_MANIFEST_BYTES = 64 * 1024;

    private KspServerDiscovery() {}

    public static void verifyReady() throws IOException
    {
        String address = KspPrivateServerConfig.discoveryUrl();
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(2500);
        connection.setRequestProperty("Accept", "application/json");
        try
        {
            if (connection.getResponseCode() != 200)
                throw new IOException("KSP private server discovery returned HTTP " + connection.getResponseCode());
            byte[] data;
            try (java.io.InputStream input = connection.getInputStream())
            {
                data = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            }
            if (data.length > MAX_MANIFEST_BYTES)
                throw new IOException("KSP discovery manifest too large");
            evaluate(new String(data, StandardCharsets.UTF_8));
        }
        finally
        {
            connection.disconnect();
        }
    }

    public static void evaluate(String manifest) throws IOException
    {
        java.util.regex.Matcher revision = REVISION.matcher(manifest);
        if (!VERIFIED.matcher(manifest).find() || !revision.find())
            throw new IOException("KSP server has no verified OSRS protocol revision");
        int advertised;
        try
        {
            advertised = Integer.parseInt(revision.group(1));
        }
        catch (NumberFormatException invalid)
        {
            throw new IOException("KSP server revision is invalid", invalid);
        }
        if (advertised != EXPECTED_OSRS_REVISION)
            throw new IOException("KSP OSRS protocol revision mismatch: expected "
                + EXPECTED_OSRS_REVISION + " but server advertises " + advertised);
        if (!GAME_READY.matcher(manifest).find())
            throw new IOException("KSP server is reachable but OSRS game protocol is not ready. "
                + "The dashboard controls world availability only; Microbot cannot log in yet.");
        if (!CONFIG_READY.matcher(manifest).find())
            throw new IOException("KSP server game protocol advertised ready, but private jav_config is unavailable.");
    }
}
