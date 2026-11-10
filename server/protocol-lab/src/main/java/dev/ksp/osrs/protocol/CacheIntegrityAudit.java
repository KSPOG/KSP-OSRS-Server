package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Separately audits decoded reference tables and disk-cache group CRC integrity.
 * Group CRC validation does not require decrypting or decompressing game assets.
 * Never writes to the cache or claims authoritative OSRS revision certification.
 */
public final class CacheIntegrityAudit {
    private static final int MAX_SAMPLES_PER_INDEX = 16;
    private CacheIntegrityAudit() {}

    public record GroupCrc(boolean matched, boolean hasVersionTrailer, long fullCrc,
        long withoutTrailerCrc) {}

    /**
     * The RuneLite cache format stores a two-byte group revision trailer in many
     * archive groups. CRC32 is computed over the group container excluding that
     * trailer. Some local groups omit it. The reference table CRC is authoritative
     * for deciding which representation matches.
     *
     * Encryption may hide a decompression header, but cannot invalidate this
     * CRC comparison: CRC32 checks the on-disk container bytes directly.
     */
    public static GroupCrc compareGroupCrc(byte[] raw, int expectedCrc) {
        CRC32 full = new CRC32();
        full.update(raw);
        long without = -1;
        if (raw.length >= 2) {
            CRC32 truncated = new CRC32();
            truncated.update(raw, 0, raw.length - 2);
            without = truncated.getValue();
        }
        long wanted = Integer.toUnsignedLong(expectedCrc);
        boolean withTrailer = without == wanted;
        boolean matched = withTrailer || full.getValue() == wanted;
        return new GroupCrc(matched, withTrailer, full.getValue(), without);
    }

    /**
     * A metadata-only description of the first failing group per index.
     * Does not print raw asset bytes, filenames, or account details.
     */
    static String describeGroup(byte[] raw, int expectedVersion) {
        if (raw.length < 5) return "header=too-short bytes=" + raw.length;
        int type = raw[0] & 255;
        long packed = ((raw[1] & 255L) << 24) | ((raw[2] & 255L) << 16)
            | ((raw[3] & 255L) << 8) | (raw[4] & 255L);
        long expectedContainerBytes = (type == 0 ? 5L : 9L) + packed;
        long suffixBytes = raw.length - expectedContainerBytes;
        int trailer = raw.length >= 2
            ? ((raw[raw.length - 2] & 255) << 8) | (raw[raw.length - 1] & 255)
            : -1;
        StringBuilder result = new StringBuilder();
        result.append("type=").append(type)
            .append(" packed=").append(packed)
            .append(" length=").append(raw.length)
            .append(" suffixBytes=").append(suffixBytes)
            .append(" trailingVersion=").append(trailer)
            .append(" expectedVersionLow16=").append(expectedVersion & 65535);
        return result.toString();
    }

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
        int decoded = 0, absent = 0, referenceInvalid = 0;
        int matching = 0, missing = 0, mismatch = 0, groupReadErrors = 0;
        int withTrailer = 0, withoutTrailer = 0;
        long defined = 0;
        try (var reader = new IndexedCacheReader(root)) {
            for (int archive = 0; archive < slots; archive++) {
                CacheMetadata.Reference reference;
                try {
                    byte[] raw = reader.read(255, archive);
                    if (raw == null) { absent++; continue; }
                    reference = CacheMetadata.parse(archive, CacheMetadata.unpack(raw));
                    decoded++;
                    defined += reference.groups().size();
                } catch (IOException | RuntimeException ex) {
                    referenceInvalid++;
                    System.err.println("Reference table " + archive + " invalid: " + ex.getMessage());
                    continue;
                }

                int sampled = 0;
                int localMatching = 0, localMissing = 0, localMismatch = 0, localReadErrors = 0;
                for (var group : reference.groups().values()) {
                    if (sampled++ >= MAX_SAMPLES_PER_INDEX) break;
                    try {
                        byte[] raw = reader.read(archive, group.id());
                        if (raw == null) { missing++; localMissing++; continue; }
                        GroupCrc result = compareGroupCrc(raw, group.crc());
                        if (result.matched()) {
                            matching++; localMatching++;
                            if (result.hasVersionTrailer()) withTrailer++;
                            else withoutTrailer++;
                        } else {
                            mismatch++; localMismatch++;
                            if (localMismatch <= 2)
                                System.err.println("CRC mismatch archive=" + archive
                                    + " group=" + group.id()
                                    + " groupBytes=" + raw.length
                                    + " expected=" + Integer.toUnsignedString(group.crc())
                                    + " crcFull=" + result.fullCrc()
                                    + " crcMinusTwo=" + result.withoutTrailerCrc()
                                    + " " + describeGroup(raw, group.revision()));
                        }
                    } catch (IOException | RuntimeException ex) {
                        groupReadErrors++; localReadErrors++;
                        if (localReadErrors <= 2)
                            System.err.println("Group read error archive=" + archive
                                + " group=" + group.id() + ": " + ex.getMessage());
                    }
                }
                System.out.println("Archive " + archive + ": reference v"
                    + reference.protocol() + " revision=" + reference.revision()
                    + " groups=" + reference.groups().size()
                    + " sampledMatching=" + localMatching
                    + " missing=" + localMissing
                    + " mismatched=" + localMismatch
                    + " readErrors=" + localReadErrors);
            }
        }
        System.out.println("Reference tables decoded=" + decoded + ", absent=" + absent
            + ", invalid=" + referenceInvalid + ", advertised groups=" + defined);
        System.out.println("Sampled group CRCs: matching=" + matching
            + ", missing=" + missing + ", mismatched=" + mismatch
            + ", readErrors=" + groupReadErrors);
        System.out.println("CRC match framing: withoutVersionTrailer=" + withTrailer
            + ", withFullContainer=" + withoutTrailer);
        System.out.println("OSRS revision 242 cache certification: NOT ESTABLISHED");
        if (referenceInvalid != 0 || mismatch != 0 || groupReadErrors != 0)
            System.exit(3);
    }
}
