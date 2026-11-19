package dev.ksp.osrs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Durable state for configured worlds. Unknown world IDs are deliberately ignored on load. */
public final class WorldStateStore {
    private WorldStateStore() {}

    public static void load(Path path, Set<Integer> known, Set<Integer> running) throws IOException {
        if (!Files.isRegularFile(path)) return;
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { properties.load(stream); }
        for (int id : known) {
            String state = properties.getProperty("world." + id);
            if (state == null) continue;
            if (!"true".equals(state) && !"false".equals(state))
                throw new IOException("Invalid persisted world state for " + id);
            if (Boolean.parseBoolean(state)) running.add(id);
            else running.remove(id);
        }
    }

    public static void save(Path path, Set<Integer> known, Set<Integer> running) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Properties properties = new Properties();
        for (int id : known) properties.setProperty("world." + id, Boolean.toString(running.contains(id)));
        Path temp = Files.createTempFile(directory, "world-state-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temp)) {
                properties.store(stream, "KSP world availability overrides");
            }
            try { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
