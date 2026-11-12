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

    public static void main(String[] args) throws Exception
    {
        if (args.length == 1 && "--enabled".equals(args[0]))
        {
            check(KspJs5GamepackProbeConfig.enabled(), "Probe was not activated by environment");
            check("127.0.0.1".equals(KspJs5GamepackProbeConfig.codeBase(
                new java.net.URL("https://world.example/")).getHost()),
                "Enabled probe codebase did not use localhost");
            check(KspJs5GamepackProbeConfig.codeBase(
                new java.net.URL("https://world.example/")).getPort() == 43594,
                "Enabled probe codebase used wrong port");
            check("127.0.0.1".equals(KspJs5GamepackProbeConfig.parameter("server", "world.example")),
                "Enabled probe server parameter did not use localhost");
            check("43594".equals(KspJs5GamepackProbeConfig.parameter("port1", "43594")),
                "Enabled probe port1 parameter did not use 43594");
            System.out.println("KspJs5GamepackProbeConfigSmokeTest: ENABLED PASS");
            return;
        }
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
        check("http://127.0.0.1:43595/jav_config.ws".equals(
            KspJs5GamepackProbeConfig.requireLocalJavConfigUrl(
                "http://127.0.0.1:43595/jav_config.ws")), "Loopback configuration refused");
        boolean blocked = false;
        try {
            KspJs5GamepackProbeConfig.requireLocalJavConfigUrl(
                "https://oldschool.runescape.com/jav_config.ws");
        } catch (IllegalArgumentException expected) { blocked = true; }
        check(blocked, "Official configuration URL was accepted");
        blocked = false;
        try {
            KspJs5GamepackProbeConfig.requireLocalJavConfigUrl(null);
        } catch (IllegalArgumentException expected) { blocked = true; }
        check(blocked, "Missing local configuration URL was accepted");
        blocked = false;
        try {
            KspJs5GamepackProbeConfig.validateLocalConfig(
                "http://127.0.0.1:43594/",
                java.util.Map.of("initial_class", "client.class"),
                java.util.Map.of("11", "https://auth.jagex.com/"));
        } catch (IllegalArgumentException expected) { blocked = true; }
        check(blocked, "Production authentication endpoint was accepted");
        KspJs5GamepackProbeConfig.validateLocalConfig(
            "http://127.0.0.1:43594/",
            java.util.Map.of("initial_class", "client.class"),
            java.util.Map.of("11", "http://127.0.0.1:43595/"));

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
