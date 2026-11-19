package dev.ksp.osrs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs an isolated revision 242 game process for each configured world. */
final class GameWorldProcessManager implements WorldRuntime {
    static final int PORT_BASE = 43000;
    private final Map<Integer, Worker> worlds = new HashMap<>();
    private final List<String> command;
    private final Set<Integer> membersWorlds;
    /** Live sessions reported by the authoritative world process. */
    private final Map<Integer, Map<String, String>> online = new HashMap<>();

    GameWorldProcessManager(Path cache, Path master, Path key, Set<Integer> membersWorlds) {
        this.membersWorlds = Set.copyOf(membersWorlds);
        command = List.of(ProcessHandle.current().info().command().orElseThrow(),
            "-cp", System.getProperty("java.class.path"), "dev.ksp.osrs.protocol.LocalGameListener",
            cache.toAbsolutePath().toString(), master.toAbsolutePath().toString(), key.toAbsolutePath().toString());
    }

    @Override
    public int port(int worldId) {
        return PORT_BASE + worldId;
    }

    @Override
    public synchronized void start(int worldId) throws IOException {
        if (listening(worldId)) return;
        var args = new ArrayList<>(command);
        args.add(Integer.toString(port(worldId)));
        var builder = new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().remove("KSP_ADMIN_TOKEN");
        builder.environment().put("KSP_LOCAL_WORLD_TYPE", membersWorlds.contains(worldId) ? "P2P" : "F2P");
        Worker worker = new Worker(worldId, builder.start());
        try {
            worker.ready.get(20, TimeUnit.SECONDS);
            if (!worker.process.isAlive()) throw new IOException("Game world exited during startup");
            worlds.put(worldId, worker);
            online.put(worldId, new HashMap<>());
        } catch (InterruptedException interrupted) {
            worker.process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting game world " + worldId, interrupted);
        } catch (ExecutionException | TimeoutException | IOException failure) {
            worker.process.destroyForcibly();
            throw new IOException("Game world " + worldId + " did not become ready. See its console output.", failure);
        }
    }

    @Override
    public synchronized void stop(int id) throws IOException {
        Worker worker = worlds.get(id);
        if (worker == null) return;
        worker.process.destroy();
        try {
            if (!worker.process.waitFor(8, TimeUnit.SECONDS)) {
                worker.process.destroyForcibly();
                if (!worker.process.waitFor(2, TimeUnit.SECONDS)) {
                    throw new IOException("Game world " + id + " did not stop");
                }
            }
            worlds.remove(id);
            online.remove(id);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping game world " + id, interrupted);
        }
    }

    @Override
    public synchronized boolean listening(int id) {
        Worker worker = worlds.get(id);
        return worker != null && worker.process.isAlive();
    }

    @Override
    public long attempts(int id) {
        return 0;
    }

    @Override
    public synchronized long ticks(int id) {
        Worker worker = worlds.get(id);
        return worker == null ? 0 : worker.ticks;
    }

    @Override
    public synchronized int population(int id) {
        return listening(id) ? worlds.get(id).population : 0;
    }

    @Override
    public synchronized void close() {
        for (int id : List.copyOf(worlds.keySet())) {
            try {
                stop(id);
            } catch (IOException failure) {
                worlds.get(id).process.destroyForcibly();
            }
        }
        worlds.clear();
        online.clear();
    }

    synchronized Map<String, Integer> onlinePlayers() {
        var result = new java.util.TreeMap<String, Integer>();
        for (var entry : online.entrySet()) {
            if (!listening(entry.getKey())) continue;
            for (var name : entry.getValue().keySet()) result.put(name, entry.getKey());
        }
        return result;
    }

    private synchronized void recordPlayer(int world, String line) {
        // Fixed untrusted-output grammar; never expose raw log text through admin JSON.
        String[] fields = line.split("=", 2);
        if (fields.length != 2 || !fields[0].matches("[a-z0-9_]{1,12}")) return;
        var roster = online.get(world);
        if (roster == null) return;
        if ("ONLINE".equals(fields[1])) roster.put(fields[0], fields[0]);
        else if ("OFFLINE".equals(fields[1])) roster.remove(fields[0]);
    }

    private final class Worker {
        private final Process process;
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private volatile int population;
        private volatile long ticks;

        private Worker(int id, Process process) {
            this.process = process;
            Thread.ofVirtual().name("ksp-game-world-" + id).start(() -> readOutput(id));
        }

        private void readOutput(int id) {
            try (var out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = out.readLine()) != null) {
                    if (line.startsWith("KSP_GAME_PLAYER=")) {
                        recordPlayer(id, line.substring("KSP_GAME_PLAYER=".length()));
                        continue;
                    }
                    if (line.startsWith("KSP_LOCAL_WORLD_TICKS=")) {
                        ticks = Long.parseLong(line.substring("KSP_LOCAL_WORLD_TICKS=".length()));
                        continue;
                    }
                    if (line.startsWith("KSP_LOCAL_WORLD_POPULATION=")) {
                        population = Integer.parseInt(line.substring("KSP_LOCAL_WORLD_POPULATION=".length()));
                        continue;
                    }
                    System.out.println("[WORLD " + id + "] " + line);
                    if (line.startsWith("KSP_LOCAL_GAME_LISTENER_READY ")) ready.complete(null);
                }
            } catch (IOException | RuntimeException failure) {
                ready.completeExceptionally(failure);
                System.err.println("[WORLD " + id + "] Could not read game output: " + failure.getMessage());
            } finally {
                ready.completeExceptionally(new IOException("Game world output ended"));
            }
        }
    }
}
