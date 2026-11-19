package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Local, offline verification of a candidate VERSIONED master index.
 *
 * The VERSIONED layout is not asserted to be the revision-242 wire format.
 * It must never be installed as the active master index until client-side
 * format/signature requirements have been established.
 */
public final class Js5MasterIndexAudit {
    private Js5MasterIndexAudit() {}

    public record Report(int entries, int present, int absent, int crcVerified,
                         int revisionVerified) {}

    public static Report audit(Path cache) throws IOException {
        Js5MasterIndexCandidate.Summary built = Js5MasterIndexCandidate.build(cache);
        byte[] container = built.container();
        CacheStructureCheck.ContainerInfo info =
            CacheStructureCheck.validateContainer(container);
        if (info.type() != 0 || info.crcLength() != container.length)
            throw new IOException("Candidate must be an uncompressed, trailer-free JS5 container");
        if (info.packedLength() != built.entries() * 8)
            throw new IOException("Candidate entry count does not match container length");

        ByteBuffer entries = ByteBuffer.wrap(container).order(ByteOrder.BIG_ENDIAN);
        entries.position(5);
        int present = 0, absent = 0, crcVerified = 0, revisionVerified = 0;
        try (IndexedCacheReader reader = new IndexedCacheReader(cache)) {
            for (int archive = 0; archive < built.entries(); archive++) {
                int crc = entries.getInt();
                int revision = entries.getInt();
                byte[] raw = reader.read(255, archive);
                if (raw == null) {
                    absent++;
                    if (crc != 0 || revision != 0)
                        throw new IOException("Absent reference table has nonzero master entry");
                    continue;
                }
                present++;
                CRC32 checksum = new CRC32();
                checksum.update(raw);
                if (crc != (int) checksum.getValue())
                    throw new IOException("Master candidate CRC mismatch for archive " + archive);
                crcVerified++;
                var ref = CacheMetadata.parse(archive, CacheMetadata.unpack(raw));
                if (revision != ref.revision())
                    throw new IOException("Master candidate revision mismatch for archive " + archive);
                revisionVerified++;
            }
        }
        if (entries.hasRemaining()
            || present != built.present()
            || absent != built.absent())
            throw new IOException("Master index entry counts are inconsistent");
        return new Report(built.entries(), present, absent, crcVerified, revisionVerified);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Js5MasterIndexAudit <cache-directory>");
            System.exit(2);
        }
        Report r = audit(Path.of(args[0]));
        System.out.println("Master candidate VERSIONED: entries=" + r.entries
            + " present=" + r.present + " absent=" + r.absent);
        System.out.println("Master candidate local checks: referenceCRCs=" + r.crcVerified
            + " referenceRevisions=" + r.revisionVerified);
        System.out.println("Revision-242 master-index format/signature: UNVERIFIED");
        System.out.println("Network delivery: DISABLED");
    }
}
