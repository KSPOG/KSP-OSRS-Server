package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Read-only local audit of decoded reference tables and a bounded deterministic
 * sample of groups. Never publishes cache payloads or a revision certification.
 */
public final class CacheIntegrityAudit {
    private static final int MAX_SAMPLES_PER_INDEX = 16;
    private CacheIntegrityAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: CacheIntegrityAudit <cache-folder>");
            System.exit(2);
        }
        Path root = Path.of(args[0]);
        long size = Files.size(root.resolve("main_file_cache.idx255"));
        if (size == 0 || size % 6 != 0 || size > 1536)
            throw new IOException("Invalid reference-table index size");
        int slots = (int)(size / 6);
        int decoded = 0, absent = 0, invalid = 0, matching = 0, missing = 0, mismatch = 0;
        long defined = 0;
        try (var reader = new IndexedCacheReader(root)) {
            for (int archive = 0; archive < slots; archive++) {
                try {
                    byte[] raw = reader.read(255, archive);
                    if (raw == null) { absent++; continue; }
                    var reference = CacheMetadata.parse(archive, CacheMetadata.unpack(raw));
                    decoded++;
                    defined += reference.groups().size();
                    int sampled = 0;
                    for (var group : reference.groups().values()) {
                        if (sampled++ >= MAX_SAMPLES_PER_INDEX) break;
                        byte[] payload = reader.read(archive, group.id());
                        if (payload == null) { missing++; continue; }
                        var container = CacheStructureCheck.validateContainer(payload);
                        CRC32 crc = new CRC32();
                        crc.update(payload, 0, container.crcLength());
                        if ((int)crc.getValue() == group.crc()) matching++;
                        else mismatch++;
                    }
                    System.out.println("Archive " + archive + ": reference v"
                        + reference.protocol() + " revision=" + reference.revision()
                        + " groups=" + reference.groups().size());
                } catch (IOException | RuntimeException ex) {
                    invalid++;
                    System.err.println("Archive " + archive + " failed: " + ex.getMessage());
                }
            }
        }
        System.out.println("Reference tables decoded=" + decoded + ", absent=" + absent
            + ", invalid=" + invalid + ", advertised groups=" + defined);
        System.out.println("Sampled group CRCs: matching=" + matching
            + ", missing=" + missing + ", mismatched=" + mismatch);
        System.out.println("OSRS revision 242 cache certification: NOT ESTABLISHED");
        if (invalid > 0 || mismatch > 0) System.exit(3);
    }
}
