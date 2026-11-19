package net.runelite.client.plugins.microbot;
public final class KspPrivateServerConfigSmokeTest {
    public static void main(String[] args) {
        String official="https://official.example/jav_config.ws";
        require(official.equals(KspPrivateServerConfig.resolve(official,"false","http://localhost:8090/jav_config.ws")), "default preserved");
        require("http://127.0.0.1:8090/jav_config.ws".equals(KspPrivateServerConfig.resolve(official,"true","http://127.0.0.1:8090/jav_config.ws")), "private selected");
        reject(official,"true",null);
        reject(official,"true","https://localhost:8090/jav_config.ws");
        reject(official,"true","http://example.com:8090/jav_config.ws");
        reject(official,"true","http://user:pass@localhost:8090/jav_config.ws");
        require("http://127.0.0.1:8087/client/worlds.js".equals(
            KspPrivateServerConfig.resolve(official,"true","http://127.0.0.1:8087/client/worlds.js")), "world directory URL");
        System.out.println("Private config smoke tests passed");
    }
    private static void require(boolean ok,String reason) { if(!ok)throw new AssertionError(reason); }
    private static void reject(String official,String enabled,String privateUrl) {
        try { KspPrivateServerConfig.resolve(official,enabled,privateUrl); throw new AssertionError("Expected rejection: "+privateUrl); }
        catch(IllegalArgumentException expected) {}
    }
}
