package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;

/**
 * Explicitly post-handshake, localhost-only TCP diagnostic transport.
 *
 * This is NOT a revision-242 client-compatible JS5 server. No client
 * initialization handshake, XOR, priority scheduling, or 255/255 master index.
 * It accepts one local connection, at most 32 four-byte request packets and
 * closes on an unverified/unsupported request rather than emitting fake data.
 */
public final class Js5LocalDiagnosticServer {
    public static final int MAX_REQUESTS = 32;
    private static final int TIMEOUT_MS = 7000;

    private Js5LocalDiagnosticServer() {}

    public record Report(int requests, int responses, long responseBytes,
                         String termination) {}

    public static Report serveOne(ServerSocket listener, Path cache) throws IOException {
        if (!listener.getInetAddress().isLoopbackAddress())
            throw new IOException("JS5 diagnostic listener must bind loopback only");
        listener.setSoTimeout(TIMEOUT_MS);
        try (var provider = new VerifiedJs5GroupProvider(cache);
             Socket client = listener.accept()) {
            if (!client.getInetAddress().isLoopbackAddress())
                throw new IOException("Non-loopback client refused");
            client.setSoTimeout(TIMEOUT_MS);
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();
            int requests = 0;
            int responses = 0;
            long bytes = 0;
            for (; requests < MAX_REQUESTS; ) {
                byte[] request = new byte[4];
                int count;
                try {
                    count = readPacket(input, request);
                } catch (SocketTimeoutException timeout) {
                    return new Report(requests, responses, bytes, "CLIENT_TIMEOUT");
                }
                if (count < 0) return new Report(requests, responses, bytes, "CLIENT_CLOSED");
                requests++;
                int opcode = request[0] & 255;
                int archive = request[1] & 255;
                int group = (request[2] & 255) << 8 | request[3] & 255;
                if (opcode != 0 && opcode != 1)
                    return new Report(requests, responses, bytes, "CONTROL_UNSUPPORTED");
                if (archive == 255 && group == 255)
                    return new Report(requests, responses, bytes, "MASTER_NOT_VERIFIED");
                ByteBuf response = provider.provide(archive, group);
                if (response == null)
                    return new Report(requests, responses, bytes, "GROUP_UNAVAILABLE");
                try {
                    byte[] wire = new byte[response.readableBytes()];
                    response.readBytes(wire);
                    output.write(wire);
                    output.flush();
                    bytes += wire.length;
                    responses++;
                } finally {
                    response.release();
                }
            }
            return new Report(requests, responses, bytes, "REQUEST_LIMIT");
        }
    }

    private static int readPacket(InputStream input, byte[] buffer) throws IOException {
        int position = 0;
        while (position < buffer.length) {
            int read = input.read(buffer, position, buffer.length - position);
            if (read < 0) {
                if (position == 0) return -1;
                throw new EOFException("Partial 4-byte JS5 request");
            }
            position += read;
        }
        return position;
    }

    public static ServerSocket bind(int port) throws IOException {
        if (port < 0 || port > 65535)
            throw new IOException("Invalid diagnostic port");
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);
        return server;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            System.err.println("Usage: Js5LocalDiagnosticServer <cache-directory> <port> [--one-session]");
            System.exit(2);
        }
        int port = Integer.parseInt(args[1]);
        if (args.length == 3 && !"--one-session".equals(args[2]))
            throw new IllegalArgumentException("Only --one-session is supported");
        try (ServerSocket listener = bind(port)) {
            System.out.println("JS5 diagnostic listening on "
                + listener.getInetAddress().getHostAddress() + ":" + listener.getLocalPort());
            System.out.println("POST-HANDSHAKE SYNTHETIC REQUESTS ONLY; authentic client compatibility UNVERIFIED");
            Report report = serveOne(listener, Path.of(args[0]));
            System.out.println("Diagnostic results: " + report);
        }
    }
}
