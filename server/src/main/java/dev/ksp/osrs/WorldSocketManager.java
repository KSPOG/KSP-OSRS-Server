package dev.ksp.osrs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-world loopback-only TCP transport. Connections are rejected immediately
 * until the exact revision-specific game protocol has been implemented.
 * This is infrastructure, NOT a playable OSRS game listener.
 */
public final class WorldSocketManager implements AutoCloseable {
    private final Map<Integer, Listener> listeners = new HashMap<>();
    private final int basePort;

    public WorldSocketManager(int basePort) {
        if (basePort < 1024 || basePort > 63000)
            throw new IllegalArgumentException("Invalid world port base");
        this.basePort = basePort;
    }

    public int port(int worldId) {
        if (worldId < 1 || worldId > 2000 || basePort + worldId > 65535)
            throw new IllegalArgumentException("World ID cannot be mapped to a TCP port");
        return basePort + worldId;
    }

    public synchronized void start(int worldId) throws IOException {
        if (listeners.containsKey(worldId)) return;
        ServerSocket server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port(worldId)), 32);
        } catch (IOException failure) {
            try { server.close(); } catch (IOException ignored) {}
            throw failure;
        }
        Listener listener = new Listener(server, worldId);
        listeners.put(worldId, listener);
        listener.worker.start();
    }

    public synchronized void stop(int worldId) throws IOException {
        Listener listener = listeners.remove(worldId);
        if (listener != null) listener.server.close();
    }

    public synchronized boolean listening(int worldId) {
        Listener listener = listeners.get(worldId);
        return listener != null && !listener.server.isClosed();
    }

    public synchronized long attempts(int worldId) {
        Listener listener = listeners.get(worldId);
        return listener == null ? 0 : listener.attempts;
    }

    @Override public synchronized void close() {
        for (Listener listener : listeners.values()) {
            try { listener.server.close(); } catch (IOException ignored) {}
        }
        listeners.clear();
    }

    private static final class Listener {
        final ServerSocket server;
        final Thread worker;
        volatile long attempts;
        Listener(ServerSocket server, int worldId) {
            this.server = server;
            worker = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        attempts++;
                        // Fail closed: never pretend to speak the OSRS login protocol.
                    } catch (SocketException closing) {
                        if (!server.isClosed()) System.err.println("World " + worldId + " TCP socket error: " + closing);
                    } catch (IOException failure) {
                        if (!server.isClosed()) System.err.println("World " + worldId + " TCP accept failed: " + failure);
                    }
                }
            }, "ksp-world-transport-" + worldId);
            worker.setDaemon(true);
        }
    }
}
