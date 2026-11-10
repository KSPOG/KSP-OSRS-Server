package dev.ksp.osrs.protocol;

import java.nio.file.Path;

/** Read-only, on-device validation; never prints archive payloads. */
public final class CacheStructureCheck {
    private CacheStructureCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: CacheStructureCheck <cache-directory>");
            System.exit(2);
        }
        try (IndexedCacheReader reader = new IndexedCacheReader(Path.of(args[0]))) {
            byte[] master = reader.read(255, 0);
            if (master == null || master.length < 5) {
                System.err.println("Archive 0 reference table missing or too short; cache cannot be certified.");
                System.exit(3);
            }
            int compression = master[0] & 0xff;
            int packedLength = ((master[1] & 255) << 24) | ((master[2] & 255) << 16)
                | ((master[3] & 255) << 8) | (master[4] & 255);
            if (compression > 2 || packedLength < 0 || packedLength > master.length - 5) {
                System.err.println("Reference table container header invalid.");
                System.exit(4);
            }
            System.out.println("Archive 0 reference-table group sector chain: VALID");
            System.out.println("Compression type: " + compression + ", packed bytes: " + packedLength);
            System.out.println("Cache revision 242: UNVERIFIED (requires checksums/index matching)");
        }
    }
}
