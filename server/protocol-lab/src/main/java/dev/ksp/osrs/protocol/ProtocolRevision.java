package dev.ksp.osrs.protocol;

/**
 * Experimental protocol package selection; disconnected from live game listeners.
 * Before deployment, verify this revision matches Microbot's injected OSRS client.
 */
public final class ProtocolRevision {
    public static final int TARGET = 242;
    public static final String LIBRARY = "net.rsprot:osrs-242-api:1.0.0-ALPHA-20261106";
    public static final boolean LOGIN_IMPLEMENTED = false;

    private ProtocolRevision() {}
}
