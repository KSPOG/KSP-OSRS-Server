package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Offline analysis of a independently obtained, raw JS5 (255,255) response.
 *
 * Accepts the on-wire 3-byte archive/group prefix and 512-byte blocks
 * separated by 0xff. Does not open sockets, intercept clients, or infer
 * signature validity. The fixture must be captured externally and pinned
 * by SHA-256 using Js5MasterReferenceComparator.
 */
public final class Js5MasterWireFixtureAudit {
    private static final int MAX_WIRE_BYTES = 1024 * 1024;
    private Js5MasterWireFixtureAudit() {}

    public record Decoded(byte[] container, int continuationMarkers,
                          int compressionType, int payloadLength,
                          int extraBytes) {
        public Decoded { container = container.clone(); }
        @Override public byte[] container() { return container.clone(); }
    }

    public static Decoded decode(byte[] wire) throws IOException {
        if (wire == null || wire.length < 8 || wire.length > MAX_WIRE_BYTES)
            throw new IOException("Invalid wire fixture length");
        if ((wire[0] & 255) != 255 || (wire[1] & 255) != 0
            || (wire[2] & 255) != 255)
            throw new IOException("Not a JS5 255/255 response");
        byte[] raw = new byte[wire.length - 3];
        int out = 0;
        int markers = 0;
        int consumed = 3;
        int blockOffset = 3;
        while (consumed < wire.length) {
            if (blockOffset == 512) {
                if ((wire[consumed++] & 255) != 255)
                    throw new IOException("Invalid 512-byte continuation marker");
                markers++;
                blockOffset = 1;
                if (consumed == wire.length)
                    throw new IOException("Dangling continuation marker");
            }
            int count = Math.min(wire.length - consumed, 512 - blockOffset);
            System.arraycopy(wire, consumed, raw, out, count);
            out += count;
            consumed += count;
            blockOffset += count;
        }
        raw = Arrays.copyOf(raw, out);
        if (raw.length < 5)
            throw new IOException("Truncated JS5 container header");
        int compression = raw[0] & 255;
        if (compression > 2) throw new IOException("Unsupported container compression");
        long packed = Integer.toUnsignedLong(ByteBuffer.wrap(raw, 1, 4)
            .order(ByteOrder.BIG_ENDIAN).getInt());
        long headerSize = compression == 0 ? 5 : 9;
        if (packed > MAX_WIRE_BYTES || raw.length < headerSize)
            throw new IOException("Invalid container payload size");
        long expected = headerSize + packed;
        if (expected > raw.length)
            throw new IOException("Truncated JS5 container payload");
        return new Decoded(raw, markers, compression, (int) packed,
            (int) (raw.length - expected));
    }

    public static Js5MasterReferenceComparator.Report compare(
            Path cache, Path fixture, String sha256) throws IOException {
        if (!Files.isRegularFile(fixture) || Files.isSymbolicLink(fixture)
            || Files.size(fixture) < 8 || Files.size(fixture) > MAX_WIRE_BYTES)
            throw new IOException("Wire fixture missing, oversized or symlinked");
        Decoded decoded = decode(Files.readAllBytes(fixture));
        byte[] candidate = Js5MasterIndexCandidate.build(cache).container();
        // Must be a pure classic VERSIONED container for a byte-exact match.
        // Any signature or additional fields produce an explicit mismatch;
        // this audit does not assert that the client's protocol requires this
        // specific classic layout.
        return Js5MasterReferenceComparator.compare(candidate, decoded.container(), sha256);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: Js5MasterWireFixtureAudit <cache> <captured-wire-response> <container-sha256>");
            System.exit(2);
        }
        Path fixture = Path.of(args[1]);
        Decoded d = decode(Files.readAllBytes(fixture));
        System.out.println("Captured JS5 255/255: wireBytes=" + Files.size(fixture)
            + " containerBytes=" + d.container().length + " compression=" + d.compressionType()
            + " continuationMarkers=" + d.continuationMarkers()
            + " extraBytes=" + d.extraBytes());
        var result = compare(Path.of(args[0]), fixture, args[2]);
        System.out.println("Pinned SHA-256: " + result.referenceSha256());
        System.out.println("Byte-exact candidate comparison: " + (result.exactMatch() ? "MATCH" : "MISMATCH")
            + " firstDifference=" + result.firstDifference());
        System.out.println("CRC differences=" + result.crcDifferences()
            + " version differences=" + result.versionDifferences());
        System.out.println("Client signature and acceptance: UNVERIFIED");
        System.out.println("Network listeners: DISABLED");
        if (!result.exactMatch()) System.exit(3);
    }
}
