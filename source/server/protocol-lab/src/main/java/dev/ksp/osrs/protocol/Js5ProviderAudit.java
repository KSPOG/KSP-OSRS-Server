package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Local-only provider smoke test. Reports metadata, never exports group bytes.
 * No server sockets are opened or enabled.
 */
public final class Js5ProviderAudit {
    private Js5ProviderAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Js5ProviderAudit <cache-directory>");
            System.exit(2);
        }
        int inspected = 0, good = 0, missing = 0, invalid = 0;
        try (var provider = new VerifiedJs5GroupProvider(Path.of(args[0]));
             var reader = new IndexedCacheReader(Path.of(args[0]))) {
            System.out.println("Reference tables loaded: " + provider.availableReferenceTables());
            for (int archive = 0; archive < 255; archive++) {
                byte[] ref = reader.read(255, archive);
                if (ref == null) continue;
                var index = CacheMetadata.parse(archive, CacheMetadata.unpack(ref));
                int sampled = 0;
                for (var group : index.groups().values()) {
                    if (sampled++ == 8) break;
                    inspected++;
                    ByteBuf framed = provider.provide(archive, group.id());
                    if (framed == null) { missing++; continue; }
                    try {
                        if (framed.readableBytes() < 8
                            || framed.getUnsignedByte(0) != archive
                            || framed.getUnsignedShort(1) != group.id()) {
                            invalid++;
                            continue;
                        }
                        boolean valid = true;
                        for (int offset = 512; offset < framed.readableBytes(); offset += 512) {
                            if (framed.getUnsignedByte(offset) != 255) {
                                valid = false;
                                break;
                            }
                        }
                        if (valid) good++;
                        else invalid++;
                    } finally { framed.release(); }
                }
            }
        }
        System.out.println("JS5 provider local smoke: inspected=" + inspected
            + " framed=" + good + " unavailable=" + missing + " invalid=" + invalid);
        System.out.println("Master index (255/255): NOT IMPLEMENTED");
        System.out.println("OSRS revision 242 cache certification: NOT ESTABLISHED");
        System.out.println("Live network listeners: DISABLED");
        if (inspected == 0 || missing != 0 || invalid != 0) System.exit(3);
    }
}
