package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exhaustive, read-only inspection of every group advertised by idx255.
 * This is an offline integrity audit, not a client-compatibility assertion.
 */
public final class Js5ExhaustiveCacheAudit {
    private Js5ExhaustiveCacheAudit() {}

    public record Check(boolean valid, int suffixBytes, String reason) {}
    public record Report(long advertised, long valid, long missing, long corrupt,
                         long providerValid, long providerInvalid,
                         long suffixZero, long suffixTwo, long suffixFour,
                         int referenceTables, List<String> examples) {
        public Report { examples = List.copyOf(examples); }
        public boolean passed() {
            return advertised > 0 && advertised == valid && missing == 0 && corrupt == 0
                && providerInvalid == 0;
        }
    }

    /** Validates CRC, exact container boundary and low-16-bit group version. */
    public static Check checkGroup(byte[] raw, CacheMetadata.Group group) {
        if (raw == null) return new Check(false, -1, "MISSING");
        if (group == null) return new Check(false, -1, "NO_METADATA");
        var crc = CacheIntegrityAudit.compareGroupCrc(raw, group.crc());
        if (!crc.matched()) return new Check(false, -1, "CRC_MISMATCH");
        int suffix = crc.excludedSuffixBytes();
        if (suffix != 0 && suffix != 2 && suffix != 4)
            return new Check(false, suffix, "UNSUPPORTED_SUFFIX");
        if (raw.length <= suffix) return new Check(false, suffix, "TRUNCATED_CONTAINER");
        byte[] container = java.util.Arrays.copyOf(raw, raw.length - suffix);
        try {
            var info = CacheStructureCheck.validateContainer(container);
            if (info.crcLength() != container.length)
                return new Check(false, suffix, "UNEXPECTED_CONTAINER_TRAILER");
            if (suffix >= 2) {
                int trailer = (raw[raw.length - 2] & 255) << 8
                    | (raw[raw.length - 1] & 255);
                if (trailer != (group.revision() & 65535))
                    return new Check(false, suffix, "VERSION_TRAILER_MISMATCH");
            }
            return new Check(true, suffix, "OK");
        } catch (IOException e) {
            return new Check(false, suffix, "INVALID_CONTAINER");
        }
    }

    /** Checks every byte of the response, not only the 0xFF continuation markers. */
    static boolean matchesWire(ByteBuf framed, int archive, int group, byte[] container) {
        if (framed == null || framed.readableBytes() < 8
            || framed.getUnsignedByte(0) != archive
            || framed.getUnsignedShort(1) != group) return false;
        int at = 3;
        int blockOffset = 3;
        for (byte value : container) {
            if (blockOffset == 512) {
                if (at >= framed.readableBytes() || framed.getUnsignedByte(at++) != 255)
                    return false;
                blockOffset = 1;
            }
            if (at >= framed.readableBytes() || framed.getByte(at++) != value)
                return false;
            blockOffset++;
        }
        return at == framed.readableBytes();
    }

    public static Report audit(Path root, boolean verifyProvider) throws IOException {
        Path idx255 = root.resolve("main_file_cache.idx255");
        if (!Files.isRegularFile(idx255) || Files.isSymbolicLink(idx255))
            throw new IOException("Missing idx255");
        long size = Files.size(idx255);
        if (size == 0 || size % 6 != 0 || size > 255L * 6)
            throw new IOException("Invalid idx255 size");

        long advertised = 0, valid = 0, missing = 0, corrupt = 0;
        long providerValid = 0, providerInvalid = 0, suffixZero = 0, suffixTwo = 0, suffixFour = 0;
        int references = 0;
        List<String> examples = new ArrayList<>();
        try (IndexedCacheReader reader = new IndexedCacheReader(root);
             VerifiedJs5GroupProvider provider = verifyProvider
                 ? new VerifiedJs5GroupProvider(root) : null) {
            for (int archive = 0; archive < size / 6; archive++) {
                byte[] refRaw = reader.read(255, archive);
                if (refRaw == null) continue;
                var refInfo = CacheStructureCheck.validateContainer(refRaw);
                if (refInfo.crcLength() != refRaw.length)
                    throw new IOException("Reference table trailer in archive " + archive);
                CacheMetadata.Reference index = CacheMetadata.parse(archive, CacheMetadata.unpack(refRaw));
                references++;
                long archiveValid = 0;
                for (CacheMetadata.Group group : index.groups().values()) {
                    advertised++;
                    byte[] raw;
                    try {
                        raw = reader.read(archive, group.id());
                    } catch (IOException | RuntimeException error) {
                        corrupt++;
                        sample(examples, archive, group.id(), "READ_ERROR:" + error.getClass().getSimpleName());
                        continue;
                    }
                    Check check = checkGroup(raw, group);
                    if (!check.valid()) {
                        if ("MISSING".equals(check.reason())) missing++;
                        else corrupt++;
                        sample(examples, archive, group.id(), check.reason());
                        continue;
                    }
                    valid++;
                    archiveValid++;
                    switch (check.suffixBytes()) {
                        case 0 -> suffixZero++;
                        case 2 -> suffixTwo++;
                        case 4 -> suffixFour++;
                        default -> throw new IllegalStateException("Impossible suffix");
                    }
                    if (verifyProvider) {
                        ByteBuf frame = null;
                        try {
                            frame = provider.provide(archive, group.id());
                            byte[] container = java.util.Arrays.copyOf(raw, raw.length - check.suffixBytes());
                            if (matchesWire(frame, archive, group.id(), container)) providerValid++;
                            else {
                                providerInvalid++;
                                sample(examples, archive, group.id(), "PROVIDER_FRAME_MISMATCH");
                            }
                        } catch (RuntimeException ex) {
                            providerInvalid++;
                            sample(examples, archive, group.id(), "PROVIDER_ERROR:" + ex.getClass().getSimpleName());
                        } finally {
                            if (frame != null) frame.release();
                        }
                    }
                }
                System.out.println("archive=" + archive + " advertised=" + index.groups().size()
                    + " valid=" + archiveValid + " failed=" + (index.groups().size() - archiveValid));
            }
        }
        return new Report(advertised, valid, missing, corrupt, providerValid, providerInvalid,
            suffixZero, suffixTwo, suffixFour, references, examples);
    }

    private static void sample(List<String> examples, int archive, int group, String reason) {
        if (examples.size() < 40) examples.add("archive=" + archive + " group=" + group + " reason=" + reason);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2
            || (args.length == 2 && !args[1].equals("--verify-provider"))) {
            System.err.println("Usage: Js5ExhaustiveCacheAudit <cache-path> [--verify-provider]");
            System.exit(2);
        }
        Report r = audit(Path.of(args[0]), args.length == 2);
        System.out.println("Exhaustive JS5: referenceTables=" + r.referenceTables()
            + " advertised=" + r.advertised() + " valid=" + r.valid()
            + " missing=" + r.missing() + " corrupt=" + r.corrupt());
        System.out.println("Framed provider responses: valid=" + r.providerValid()
            + " invalid=" + r.providerInvalid());
        System.out.println("CRC framing suffix: zero=" + r.suffixZero()
            + " two=" + r.suffixTwo() + " four=" + r.suffixFour());
        for (String example : r.examples()) System.out.println("FAIL " + example);
        System.out.println("Exhaustive cache integrity: " + (r.passed() ? "PASS" : "FAIL"));
        System.out.println("Revision-242 master-index wire format/client acceptance: UNVERIFIED");
        System.out.println("Live network listeners: DISABLED");
        if (!r.passed()) System.exit(3);
    }
}
