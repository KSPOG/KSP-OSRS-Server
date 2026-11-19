package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.rsprot.protocol.api.js5.Js5GroupProvider;

/**
 * A read-only, fail-closed candidate provider for RSProt's JS5 API.
 *
 * Validates each group against its decoded local reference table before returning
 * RSProt-compatible framed group responses, WITHOUT the on-disk revision suffix.
 * This class is not registered with a live network service.
 *
 * Does not synthesize the special JS5 master index (255/255).
 * Does not establish that the installed cache matches OSRS revision 242.
 */
public final class VerifiedJs5GroupProvider implements Js5GroupProvider, AutoCloseable {
    private final IndexedCacheReader reader;
    private final Map<Integer, CacheMetadata.Reference> references = new HashMap<>();
    private boolean closed;

    public VerifiedJs5GroupProvider(Path root) throws IOException {
        reader = new IndexedCacheReader(root);
        boolean success = false;
        try {
            Path index = root.resolve("main_file_cache.idx255");
            long bytes = Files.size(index);
            if (bytes == 0 || bytes % 6 != 0 || bytes > 1536)
                throw new IOException("Invalid JS5 idx255 length");
            for (int archive = 0; archive < bytes / 6; archive++) {
                byte[] container = reader.read(255, archive);
                if (container == null) continue;
                references.put(archive,
                    CacheMetadata.parse(archive, CacheMetadata.unpack(container)));
            }
            if (!references.containsKey(0))
                throw new IOException("Archive 0 reference index is unavailable");
            success = true;
        } finally {
            if (!success) reader.close();
        }
    }

    /**
     * Returns a fresh, already JS5-framed ByteBuf per call, or null on invalid
     * groups. Response begins with archive/group bytes and uses 0xFF markers.
     * ByteBuf ownership transfers to the RSProt caller.
     */
    @Override public synchronized ByteBuf provide(int archive, int group) {
        if (closed) throw new IllegalStateException("JS5 provider closed");
        if (archive < 0 || archive > 255 || group < 0 || group > 65535) return null;
        if (archive == 255 && group == 255) return null;
        try {
            byte[] raw;
            if (archive == 255) {
                if (!references.containsKey(group)) return null;
                raw = reader.read(255, group);
                if (raw == null) return null;
                // Reference table containers themselves have no game group
                // CRC in the child reference table, so validate framing.
                var info = CacheStructureCheck.validateContainer(raw);
                if (raw.length != info.crcLength()) return null;
                return Js5WireFraming.prepare(archive, group, raw);
            }
            CacheMetadata.Reference index = references.get(archive);
            if (index == null) return null;
            CacheMetadata.Group metadata = index.groups().get(group);
            if (metadata == null) return null;
            raw = reader.read(archive, group);
            if (raw == null) return null;
            var match = CacheIntegrityAudit.compareGroupCrc(raw, metadata.crc());
            if (!match.matched()) return null;
            int suffix = match.excludedSuffixBytes();
            if (raw.length <= suffix) return null;
            // Group trailers, if present, must agree with the reference-table
            // group version. A valid container CRC excludes that trailer and
            // therefore cannot detect its corruption by itself.
            if (suffix >= 2) {
                int trailer = ((raw[raw.length - 2] & 255) << 8)
                    | (raw[raw.length - 1] & 255);
                if (trailer != (metadata.revision() & 0xffff)) return null;
            }
            byte[] container = java.util.Arrays.copyOf(raw, raw.length - suffix);
            var info = CacheStructureCheck.validateContainer(container);
            if (container.length != info.crcLength()) return null;
            return Js5WireFraming.prepare(archive, group, container);
        } catch (IOException | IllegalArgumentException ex) {
            return null;
        }
    }

    public synchronized int availableReferenceTables() {
        if (closed) throw new IllegalStateException("JS5 provider closed");
        return references.size();
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        references.clear();
        reader.close();
    }
}
