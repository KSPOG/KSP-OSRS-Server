package net.runelite.client.plugins.microbot;

import java.io.IOException;

/** Isolated revision-242 discovery contract smoke test. */
public final class KspServerDiscoverySmokeTest
{
    public static void main(String[] args) throws Exception
    {
        String prefix = "{\"clientRevision\":242,\"revisionVerified\":true,";
        rejects("{\"gameProtocolReady\":false,\"javConfigReady\":false}");
        rejects("{\"clientRevision\":241,\"revisionVerified\":true,\"gameProtocolReady\":true,\"javConfigReady\":true}");
        rejects("{\"clientRevision\":242,\"revisionVerified\":false,\"gameProtocolReady\":true,\"javConfigReady\":true}");
        rejects(prefix + "\"gameProtocolReady\":false,\"javConfigReady\":false}");
        rejects(prefix + "\"gameProtocolReady\":true,\"javConfigReady\":false}");
        KspServerDiscovery.evaluate(prefix + "\"gameProtocolReady\":true,\"javConfigReady\":true}");
        System.out.println("Private discovery revision/readiness tests passed");
    }

    private static void rejects(String body)
    {
        try
        {
            KspServerDiscovery.evaluate(body);
            throw new AssertionError("Expected fail-closed response: " + body);
        }
        catch (IOException expected)
        {
            // Correct: wrong revision, unverified revision, or incomplete services.
        }
    }
}
