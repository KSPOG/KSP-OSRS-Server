package dev.ksp.osrs.protocol;

import net.rsprot.protocol.api.NetworkService;
import net.rsprot.protocol.api.AbstractNetworkServiceFactory;
import net.rsprot.protocol.api.js5.Js5GroupProvider;

/**
 * Binary-link smoke test for the pinned RSProt 242 API.
 * Does not instantiate a network service until matching cache/keys are present.
 */
public final class ProtocolApiCheck {
    private ProtocolApiCheck() {}
    public static void main(String[] args) {
        require("net.rsprot.protocol.api.NetworkService", NetworkService.class);
        require("net.rsprot.protocol.api.AbstractNetworkServiceFactory", AbstractNetworkServiceFactory.class);
        require("net.rsprot.protocol.api.js5.Js5GroupProvider", Js5GroupProvider.class);
        System.out.println("RSProt 242 API binary linking passed; game login NOT implemented.");
    }
    private static void require(String expected, Class<?> actual) {
        if (!expected.equals(actual.getName()))
            throw new IllegalStateException("Unexpected API: "+actual.getName());
    }
}
