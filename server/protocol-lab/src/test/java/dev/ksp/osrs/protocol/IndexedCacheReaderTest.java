package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class IndexedCacheReaderTest {
    private IndexedCacheReaderTest() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ksp-cache-test-");
        try {
            testNormal(root);
            testExtended(root);
            testCorruption(root);
            System.out.println("Sector cache reader tests passed");
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch(IOException e) { throw new RuntimeException(e); }
                });
            }
        }
    }
    private static void testNormal(Path root) throws Exception {
        byte[] payload = new byte[1000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i & 255);
        writeGroup(root, 255, 255, payload, false, false);
        try (var reader = new IndexedCacheReader(root)) {
            if (!Arrays.equals(payload, reader.read(255, 255)))
                throw new AssertionError("Normal cache group mismatch");
            if (reader.read(255, 256) != null)
                throw new AssertionError("Unexpected cache group");
        }
    }
    private static void testExtended(Path root) throws Exception {
        byte[] payload = new byte[1500];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte)(255-(i&255));
        writeGroup(root, 7, 70000, payload, true, false);
        try (var reader = new IndexedCacheReader(root)) {
            if (!Arrays.equals(payload, reader.read(7, 70000)))
                throw new AssertionError("Extended cache group mismatch");
        }
    }
    private static void testCorruption(Path root) throws Exception {
        writeGroup(root, 3, 12, new byte[900], false, true);
        try (var reader = new IndexedCacheReader(root)) {
            try {
                reader.read(3, 12);
                throw new AssertionError("Accepted corrupted cache chain");
            } catch(IOException expected) { /* corrupt group must fail */ }
        }
    }
    private static void writeGroup(Path root, int archive, int group, byte[] bytes,
        boolean extended, boolean corrupt) throws Exception {
        Path dat = root.resolve("main_file_cache.dat2");
        Path idx = root.resolve("main_file_cache.idx"+archive);
        int header = extended ? 10 : 8, block = 520 - header;
        try (var df = new RandomAccessFile(dat.toFile(), "rw");
             var ix = new RandomAccessFile(idx.toFile(), "rw")) {
            int sector = (int)((df.length()+519)/520);
            if (sector == 0) sector = 1;
            ix.seek((long)group*6);
            medium(ix,bytes.length);medium(ix,sector);
            for (int off=0,chunk=0;off<bytes.length;chunk++) {
                int count = Math.min(block,bytes.length-off);
                int next = off+count<bytes.length ? sector+1 : 0;
                df.seek((long)sector*520);
                if (extended) df.writeInt(group); else df.writeShort(group);
                df.writeShort(corrupt&&chunk==1?chunk+1:chunk);
                medium(df,next);df.writeByte(archive);
                df.write(bytes,off,count);
                off+=count;sector++;
            }
        }
    }
    private static void medium(RandomAccessFile file, int value) throws IOException {
        file.writeByte(value>>>16);file.writeByte(value>>>8);file.writeByte(value);
    }
}
