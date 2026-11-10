package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Bounded, read-only validation of every stored reference-table container.
 * A valid cache layout does not prove the cache belongs to OSRS revision 242.
 */
public final class CacheStructureCheck {
    private static final int MAX_PACKED_BYTES = 32 * 1024 * 1024;
    private CacheStructureCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: CacheStructureCheck <cache-directory>");
            System.exit(2);
        }
        try {
            Report report = inspect(Path.of(args[0]));
            System.out.println("Stored reference tables: " + report.valid + " valid, "
                + report.missing + " absent, " + report.invalid + " invalid ("
                + report.slots + " index entries)");
            System.out.println("Archive 0 reference-table group sector chain: "
                + (report.archive0Valid ? "VALID" : "NOT VALID"));
            if (report.archive0Valid) {
                System.out.println("Compression type: " + report.archive0Type
                    + ", packed bytes: " + report.archive0PackedBytes);
            }
            System.out.println("Reference-table CRC32 fingerprints: computed in memory only; no asset bytes exported.");
            System.out.println("Cache revision 242: UNVERIFIED (requires authoritative checksums/index matching)");
            if (report.invalid != 0 || !report.archive0Valid) System.exit(3);
        } catch (IOException badCache) {
            System.err.println("Cache validation failed: " + badCache.getMessage());
            System.exit(3);
        }
    }

    static Report inspect(Path root) throws IOException {
        Path idx255 = root.resolve("main_file_cache.idx255");
        if (!Files.isRegularFile(idx255) || Files.isSymbolicLink(idx255))
            throw new IOException("Missing reference-table index idx255");
        long bytes = Files.size(idx255);
        if (bytes % 6 != 0 || bytes == 0 || bytes > 256L * 6)
            throw new IOException("Invalid idx255 length");
        int slots = Math.toIntExact(bytes / 6);
        int valid = 0, missing = 0, invalid = 0;
        boolean archive0Valid = false;
        int archive0Type = -1, archive0PackedBytes = -1;
        try (IndexedCacheReader reader = new IndexedCacheReader(root)) {
            for (int archive = 0; archive < slots; archive++) {
                try {
                    byte[] group = reader.read(255, archive);
                    if (group == null) {
                        missing++;
                        continue;
                    }
                    ContainerInfo header = validateContainer(group);
                    CRC32 crc = new CRC32();
                    crc.update(group, 0, header.crcLength());
                    // Do not emit CRCs as official/cache-revision evidence;
                    // this test only establishes consistent local bytes.
                    crc.getValue();
                    valid++;
                    if (archive == 0) {
                        archive0Valid = true;
                        archive0Type = header.type();
                        archive0PackedBytes = header.packedLength();
                    }
                } catch (IOException malformed) {
                    invalid++;
                    System.err.println("Invalid reference table for archive " + archive
                        + ": " + malformed.getMessage());
                }
            }
        }
        return new Report(slots, valid, missing, invalid, archive0Valid,
            archive0Type, archive0PackedBytes);
    }

    static ContainerInfo validateContainer(byte[] container) throws IOException {
        if (container.length < 5) throw new IOException("Container shorter than 5 bytes");
        int type = container[0] & 255;
        if (type > 2) throw new IOException("Unsupported compression type: " + type);
        long packed = unsignedInt(container, 1);
        if (packed > MAX_PACKED_BYTES) throw new IOException("Packed data exceeds safety limit");
        int header = type == 0 ? 5 : 9;
        if (container.length < header) throw new IOException("Truncated compressed header");
        if (type != 0 && unsignedInt(container, 5) > MAX_PACKED_BYTES)
            throw new IOException("Uncompressed data exceeds safety limit");
        // Classic cache containers can have an optional 2-byte group-version trailer.
        long trailing = container.length - (packed + header);
        if (trailing != 0 && trailing != 2)
            throw new IOException("Invalid container length/trailer");
        return new ContainerInfo(type, (int)packed, (int)(header + packed));
    }

    private static long unsignedInt(byte[] src, int at) {
        return ((src[at] & 255L) << 24) | ((src[at + 1] & 255L) << 16)
            | ((src[at + 2] & 255L) << 8) | (src[at + 3] & 255L);
    }

    record ContainerInfo(int type, int packedLength, int crcLength) {}
    record Report(int slots, int valid, int missing, int invalid,
        boolean archive0Valid, int archive0Type, int archive0PackedBytes) {}
}
