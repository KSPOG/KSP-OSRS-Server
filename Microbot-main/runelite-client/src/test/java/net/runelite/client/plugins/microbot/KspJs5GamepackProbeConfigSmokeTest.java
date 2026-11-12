package net.runelite.client.plugins.microbot;

public final class KspJs5GamepackProbeConfigSmokeTest
{
    private KspJs5GamepackProbeConfigSmokeTest() {}

    private static void check(boolean yes, String explanation)
    {
        if (!yes) throw new AssertionError(explanation);
    }

    private static void reject(boolean probe, boolean privateMode, String port)
    {
        boolean rejected = false;
        try { KspJs5GamepackProbeConfig.validate(probe, privateMode, port); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Accepted unsafe or invalid probe settings");
    }

    public static void main(String[] args)
    {
        check(KspJs5GamepackProbeConfig.validate(false, true, "invalid") == 43594,
            "Disabled probe should not change normal/private settings");
        check(KspJs5GamepackProbeConfig.validate(true, false, null) == 43594,
            "Default probe port incorrect");
        check(KspJs5GamepackProbeConfig.validate(true, false, "23456") == 23456,
            "Custom probe port ignored");
        reject(true, true, null);
        reject(true, false, "invalid");
        reject(true, false, "0");
        reject(true, false, "65536");
        check("127.0.0.1".equals(
            KspJs5GamepackProbeConfig.resolveParameter("server", "world.example", 43596)),
            "Server not redirected");
        check("43596".equals(
            KspJs5GamepackProbeConfig.resolveParameter("port1", "43594", 43596)),
            "First port not redirected");
        check("43596".equals(
            KspJs5GamepackProbeConfig.resolveParameter("port2", "443", 43596)),
            "Second port not redirected");
        check("token-preserved".equals(
            KspJs5GamepackProbeConfig.resolveParameter("authparam", "token-preserved", 43596)),
            "Unrelated applet parameter changed");
        System.out.println("KspJs5GamepackProbeConfigSmokeTest: PASS");
        System.out.println("Real injected-client use of endpoint hints: UNVERIFIED");
    }
}
