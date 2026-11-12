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
            KspJs5LoopbackGuard.install();
            check(System.getSecurityManager() instanceof KspJs5LoopbackGuard,
                "Loopback guard was not installed");
            boolean networkBlocked = false;
            try { System.getSecurityManager().checkConnect("oldschool.runescape.com", 43594); }
            catch (SecurityException expected) { networkBlocked = true; }
            check(networkBlocked, "Official network socket was not blocked by actual guard");
            System.getSecurityManager().checkConnect("127.0.0.1", 43594);

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
            // Neither config summaries nor parameter access traces may expose
            // values from applet properties (which may contain credentials).
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOutput = System.out;
            try
            {
                System.setOut(new java.io.PrintStream(buffer, true,
                    java.nio.charset.StandardCharsets.UTF_8));
                KspJs5GamepackProbeConfig.reportLocalConfigSummary(
                    java.util.Map.of("1", "sensitive-placeholder", "11", "secret-assertion"));
                KspJs5GamepackProbeConfig.parameter("11", "secret-assertion");
                KspJs5GamepackProbeConfig.parameter("credential-token-name", "secret-assertion");
            }
            finally
            {
                System.setOut(originalOutput);
            }
            String trace = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
            check(trace.contains("KSP_JS5_APPLET_CONFIG_SUMMARY"),
                "Enabled probe did not report local parameter coverage");
            check(trace.contains("KSP_JS5_GAMEPACK_PARAM_ACCESS"),
                "Enabled probe did not report parameter access");
            check(!trace.contains("secret-assertion") && !trace.contains("sensitive-placeholder")
                && !trace.contains("credential-token-name"),
                "Probe trace exposed sensitive applet configuration");
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
        check("12".equals(KspJs5GamepackProbeConfig.safeDiagnosticName("12")),
            "Numeric diagnostic key not preserved");
        check("port1".equals(KspJs5GamepackProbeConfig.safeDiagnosticName("port1")),
            "Endpoint hint diagnostic key not preserved");
        check("other".equals(KspJs5GamepackProbeConfig.safeDiagnosticName("secret")),
            "Arbitrary diagnostic key exposed");
        check(KspJs5LoopbackGuard.isLoopbackHost("127.0.0.1"), "Loopback IPv4 refused");
        check(KspJs5LoopbackGuard.isLoopbackHost("::1"), "Loopback IPv6 refused");
        check(!KspJs5LoopbackGuard.isLoopbackHost("oldschool.runescape.com"),
            "Official RuneScape host permitted by guard");
        check(!KspJs5LoopbackGuard.isLoopbackHost("192.168.1.1"),
            "Nonloopback private LAN host permitted by guard");
        check(!KspJs5LoopbackGuard.isLoopbackHost(null), "Null socket host permitted");
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
