package dev.ksp.osrs.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Strict read-only reader for the classic Jagex dat2/idxN sector store.
 * Returns raw archive group containers without decompressing or modifying bytes.
 */
public final class IndexedCacheReader implements AutoCloseable {
    private static final int SECTOR_SIZE = 520;
    private static final int INDEX_RECORD_SIZE = 6;
    private static final int MAX_GROUP_BYTES = 32 * 1024 * 1024;
    private final Path root;
    private final RandomAccessFile data;

    public IndexedCacheReader(Path directory) throws IOException {
        root = directory.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Not a cache directory");
        Path dat = root.resolve("main_file_cache.dat2");
        if (!Files.isRegularFile(dat) || Files.isSymbolicLink(dat))
            throw new IOException("No readable dat2 file");
        data = new RandomAccessFile(dat.toFile(), "r");
    }

    /** Returns the raw group container, or null if an index entry is missing. */
    public synchronized byte[] read(int archive, int group) throws IOException {
        if (archive < 0 || archive > 255 || group < 0)
            throw new IllegalArgumentException("Invalid archive/group");
        Path index = root.resolve("main_file_cache.idx" + archive);
        if (!Files.isRegularFile(index) || Files.isSymbolicLink(index)) return null;
        try (RandomAccessFile idx = new RandomAccessFile(index.toFile(), "r")) {
            long offset = (long) group * INDEX_RECORD_SIZE;
            if (offset + INDEX_RECORD_SIZE > idx.length()) return null;
            idx.seek(offset);
            int length = readMedium(idx);
            int nextSector = readMedium(idx);
            if (length == 0 && nextSector == 0) return null;
            if (length <= 0 || length > MAX_GROUP_BYTES || nextSector == 0)
                throw new IOException("Invalid cache group index");
            boolean extended = group > 65535;
            int headerSize = extended ? 10 : 8;
            int payloadSize = SECTOR_SIZE - headerSize;
            int expectedChunks = (length + payloadSize - 1) / payloadSize;
            if ((long) expectedChunks > data.length() / SECTOR_SIZE + 1)
                throw new IOException("Group exceeds sector file");
            byte[] result = new byte[length];
            int copied = 0;
            Set<Integer> seen = new HashSet<>();
            for (int chunk = 0; chunk < expectedChunks; chunk++) {
                if (nextSector <= 0 || !seen.add(nextSector))
                    throw new IOException("Invalid or cyclic cache sector chain");
                long position = (long) nextSector * SECTOR_SIZE;
                int count = Math.min(payloadSize, length - copied);
                if (position + headerSize + count > data.length())
                    throw new EOFException("Truncated sector");
                data.seek(position);
                long storedGroup = extended ? Integer.toUnsignedLong(data.readInt())
                    : data.readUnsignedShort();
                int storedChunk = data.readUnsignedShort();
                int followingSector = readMedium(data);
                int storedArchive = data.readUnsignedByte();
                if (storedGroup != Integer.toUnsignedLong(group)
                    || storedChunk != chunk || storedArchive != archive)
                    throw new IOException("Cache sector identity mismatch");
                if (chunk == expectedChunks - 1 && followingSector != 0)
                    throw new IOException("Unexpected extra cache sector");
                if (chunk < expectedChunks - 1 && followingSector == 0)
                    throw new IOException("Premature end of cache sectors");
                data.readFully(result, copied, count);
                copied += count;
                nextSector = followingSector;
            }
            return result;
        }
    }

    private static int readMedium(RandomAccessFile file) throws IOException {
        return (file.readUnsignedByte() << 16) | (file.readUnsignedByte() << 8)
            | file.readUnsignedByte();
    }

    @Override public void close() throws IOException { data.close(); }
}
