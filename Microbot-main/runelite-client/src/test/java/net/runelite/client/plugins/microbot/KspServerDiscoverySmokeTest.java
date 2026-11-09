package net.runelite.client.plugins.microbot;

import java.io.IOException;

public final class KspServerDiscoverySmokeTest
{
    public static void main(String[] args) throws Exception
    {
        rejects("{\"gameProtocolReady\":false,\"javConfigReady\":false}");
        rejects("{\"gameProtocolReady\":true,\"javConfigReady\":false}");
        KspServerDiscovery.evaluate("{\"gameProtocolReady\":true,\"javConfigReady\":true}");
        System.out.println("Private discovery readiness tests passed");
    }

    private static void rejects(String body)
    {
        try
        {
            KspServerDiscovery.evaluate(body);
            throw new AssertionError("Expected fail-closed response");
        }
        catch (IOException expected)
        {
            // Correct.
        }
    }
}
