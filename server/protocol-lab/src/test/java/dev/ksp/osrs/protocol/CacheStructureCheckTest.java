package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class CacheStructureCheckTest {
    private CacheStructureCheckTest() {}
    public static void main(String[] args) throws Exception {
        if (CacheStructureCheck.validateContainer(new byte[]{0,0,0,0,1,99}).packedLength() != 1)
            throw new AssertionError("Raw container rejected");
        if (CacheStructureCheck.validateContainer(new byte[]{0,0,0,0,1,99,0,7}).crcLength() != 6)
            throw new AssertionError("Version trailer mishandled");
        if (CacheStructureCheck.validateContainer(new byte[]{1,0,0,0,1,0,0,0,4,99}).type() != 1)
            throw new AssertionError("Compressed container rejected");
        for (byte[] data : new byte[][]{
            {}, {3,0,0,0,0}, {0,0,0,0,3,1}, {1,0,0,0,1,0,0},
            {0,127,127,127,127}
        }) {
            try {
                CacheStructureCheck.validateContainer(data);
                throw new AssertionError("Accepted malformed container");
            } catch (IOException expected) { /* expected */ }
        }
        Path root = Files.createTempDirectory("ksp-reference-validation-");
        try {
            try (var dat = new RandomAccessFile(root.resolve("main_file_cache.dat2").toFile(), "rw");
                 var idx = new RandomAccessFile(root.resolve("main_file_cache.idx255").toFile(), "rw")) {
                byte[] data = {0,0,0,0,1,42};
                idx.write(new byte[]{0,0,6,0,0,1});
                dat.seek(520);
                dat.writeShort(0);
                dat.writeShort(0);
                dat.write(new byte[]{0,0,0,(byte)255});
                dat.write(data);
            }
            var result = CacheStructureCheck.inspect(root);
            if (result.slots() != 1 || result.valid() != 1 || !result.archive0Valid())
                throw new AssertionError("Failed to read synthetic reference table");
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch(IOException e) { throw new RuntimeException(e); }
                });
            }
        }
        System.out.println("Reference container validation tests passed");
    }
}
