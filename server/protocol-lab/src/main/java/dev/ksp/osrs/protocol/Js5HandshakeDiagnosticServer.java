package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Bounded experimental revision-242 JS5 initial connection test transport.
 *
 * Confirms RSProt-242 LOGIN opcode 15 + fixed 20-byte payload + successful
 * login response 0x00 before processing JS5 requests. Loopback only, one
 * connection. This remains NON-PRODUCTION: no master-index authorization,
 * signature handling, priority scheduling or full client acceptance.
 */
public final class Js5HandshakeDiagnosticServer {
    public static final int INIT_JS5_OPCODE = 15;
    private static final int READ_TIMEOUT_MS = 7000;
    private static final int REQUEST_LIMIT = 32;
    static final int MAX_CONTROLLED_REQUESTS = 200_000;
    private static final int EXTENDED_IDLE_TIMEOUT_MS = 30_000;

    private Js5HandshakeDiagnosticServer() {}

    public record Report(boolean handshakeAccepted, int requests, int responses,
                         long responseBytes, String termination) {}

    public static Report serveOne(ServerSocket listener, Path cache) throws IOException {
        return serveOne(listener, cache, null);
    }

    /** Trusted master must be admitted by an independent pin check before this call. */
    static Report serveOne(ServerSocket listener, Path cache, byte[] verifiedMaster)
            throws IOException {
        return serveOne(listener, cache, verifiedMaster, READ_TIMEOUT_MS);
    }

    /** Accept-timeout may be longer for manually started local clients. */
    static Report serveOne(ServerSocket listener, Path cache, byte[] verifiedMaster,
                           int acceptTimeoutMs) throws IOException {
        return serveOne(listener, cache, verifiedMaster, acceptTimeoutMs, ignored -> {});
    }

    /** Emits protocol-level events only; never logs seed words or account data. */
    static Report serveOne(ServerSocket listener, Path cache, byte[] verifiedMaster,
                           int acceptTimeoutMs, Consumer<String> eventLog) throws IOException {
        return serveOne(listener, cache, verifiedMaster, acceptTimeoutMs,
            REQUEST_LIMIT, 0L, eventLog);
    }

    /** Extended native-client capture with explicit caps; legacy tests retain 32-request behavior. */
    static Report serveOne(ServerSocket listener, Path cache, byte[] verifiedMaster,
                           int acceptTimeoutMs, int maxRequests, long maxSessionMs,
                           Consumer<String> eventLog) throws IOException {
        if (maxRequests < 1 || maxRequests > MAX_CONTROLLED_REQUESTS)
            throw new IllegalArgumentException("Request limit must be 1.." + MAX_CONTROLLED_REQUESTS);
        if (maxSessionMs < 0 || maxSessionMs > 30 * 60_000L)
            throw new IllegalArgumentException("Session duration must be 0..1800000ms");
        if (eventLog == null) throw new IllegalArgumentException("Event log must not be null");
        if (acceptTimeoutMs < 1 || acceptTimeoutMs > 180_000)
            throw new IllegalArgumentException("Invalid local accept timeout");
        if (!listener.getInetAddress().isLoopbackAddress())
            throw new IOException("Refusing non-loopback listener");
        listener.setSoTimeout(acceptTimeoutMs);
        // A missing client is an expected diagnostic outcome, not a server crash.
        Socket accepted;
        try {
            accepted = listener.accept();
        } catch (SocketTimeoutException timeout) {
            return new Report(false, 0, 0, 0, "NO_CLIENT_CONNECTED");
        }
        try (Socket client = accepted;
             var provider = new VerifiedJs5GroupProvider(cache)) {
            if (!client.getInetAddress().isLoopbackAddress())
                throw new IOException("Refusing non-loopback client");
            // Native cache transfers can pause between batches. Only the
            // explicitly extended, time-bounded session gets a longer idle timeout.
            client.setSoTimeout(maxSessionMs > 0 ? EXTENDED_IDLE_TIMEOUT_MS : READ_TIMEOUT_MS);
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();

            int opcode;
            try {
                opcode = input.read();
            } catch (SocketTimeoutException ex) {
                return new Report(false, 0, 0, 0, "HANDSHAKE_TIMEOUT");
            }
            if (opcode != INIT_JS5_OPCODE)
                return new Report(false, 0, 0, 0, "UNSUPPORTED_LOGIN_OPCODE");

            byte[] payload = new byte[Js5HandshakePayload.PAYLOAD_BYTES];
            try {
                if (!readFully(input, payload))
                    return new Report(false, 0, 0, 0, "INCOMPLETE_HANDSHAKE");
            } catch (SocketTimeoutException | EOFException ex) {
                return new Report(false, 0, 0, 0, "INCOMPLETE_HANDSHAKE");
            }
            Js5HandshakePayload handshake = Js5HandshakePayload.decode(payload);
            if (!handshake.revisionMatches())
                return new Report(false, 0, 0, 0, "REVISION_MISMATCH");

            // RSProt/RSProx: a successful JS5 initialization is acknowledged
            // by a single byte 0x00 before the request stream begins.
            output.write(0);
            output.flush();
            eventLog.accept("JS5_HANDSHAKE_ACCEPTED revision=242");
            int requests = 0, responses = 0;
            int xorKey = 0;
            long bytes = 0;
            final long deadline = maxSessionMs == 0 ? Long.MAX_VALUE :
                System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(maxSessionMs);
            while (requests < maxRequests) {
                if (System.nanoTime() >= deadline)
                    return new Report(true, requests, responses, bytes, "SESSION_TIME_LIMIT");
                byte[] message = new byte[4];
                try {
                    if (!readFully(input, message))
                        return new Report(true, requests, responses, bytes, "CLIENT_CLOSED");
                } catch (SocketTimeoutException ex) {
                    return new Report(true, requests, responses, bytes, "REQUEST_TIMEOUT");
                } catch (EOFException ex) {
                    return new Report(true, requests, responses, bytes, "PARTIAL_REQUEST");
                }
                requests++;
                int requestOpcode = message[0] & 255;
                int archive = message[1] & 255;
                int group = ((message[2] & 255) << 8) | (message[3] & 255);
                if (maxSessionMs == 0 || requests <= 32 || (requests % 1000) == 0)
                    eventLog.accept("JS5_REQUEST ordinal=" + requests + " opcode="
                    + requestOpcode + " archive=" + archive + " group=" + group);
                if (requestOpcode > 4)
                    return new Report(true, requests, responses, bytes, "UNKNOWN_REQUEST");
                if (requestOpcode == 2 || requestOpcode == 3)
                    continue; // Supported priority controls; no queue in this harness.
                if (requestOpcode == 4) {
                    // RSProt-242 XorChangeDecoder: first byte is key;
                    // remaining two bytes are skipped (not validated).
                    xorKey = archive;
                    continue;
                }
                if (archive == 255 && group == 255 && verifiedMaster == null)
                    return new Report(true, requests, responses, bytes, "MASTER_NOT_VERIFIED");

                ByteBuf response = archive == 255 && group == 255
                    ? Js5WireFraming.prepare(255, 255, verifiedMaster)
                    : provider.provide(archive, group);
                if (response == null)
                    return new Report(true, requests, responses, bytes, "GROUP_UNAVAILABLE");
                try {
                    byte[] frame = new byte[response.readableBytes()];
                    response.readBytes(frame);
                    // RSProt-242 Js5GroupResponseEncoder XORs the entire
                    // outgoing framed payload, including archive/group header.
                    Js5ResponseXor.apply(frame, xorKey);
                    output.write(frame);
                    output.flush();
                    responses++;
                    bytes += frame.length;
                    if (maxSessionMs == 0 || requests <= 32 || (requests % 1000) == 0)
                        eventLog.accept("JS5_RESPONSE ordinal=" + requests + " archive="
                        + archive + " group=" + group + " bytes=" + frame.length);
                } finally {
                    response.release();
                }
            }
            return new Report(true, requests, responses, bytes, "REQUEST_LIMIT");
        }
    }

    private static boolean readFully(InputStream input, byte[] buffer) throws IOException {
        int pos = 0;
        while (pos < buffer.length) {
            int received = input.read(buffer, pos, buffer.length - pos);
            if (received < 0) {
                if (pos == 0) return false;
                throw new EOFException("Partial packet");
            }
            pos += received;
        }
        return true;
    }
}
