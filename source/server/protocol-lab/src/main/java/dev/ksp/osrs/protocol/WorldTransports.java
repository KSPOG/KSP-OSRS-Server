package dev.ksp.osrs.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads requirement-free climb routes from the pinned Microbot transport table. */
final class WorldTransports {
    private final Map<Integer, List<Climb>> climbs = new HashMap<>();

    WorldTransports() throws IOException {
        var source = WorldTransports.class.getResourceAsStream("climb-transports.tsv");
        if (source == null) throw new IOException("Missing climb transport table");
        try (var rows = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            String line;
            while ((line = rows.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] fields = line.split("\t");
                var from = new Tile(integer(fields, 0), integer(fields, 1), integer(fields, 2));
                var to = new Tile(integer(fields, 3), integer(fields, 4), integer(fields, 5));
                climbs.computeIfAbsent(integer(fields, 6), id -> new ArrayList<>())
                    .add(new Climb(from, to, fields[7], Boolean.parseBoolean(fields[8])));
            }
        }
    }

    List<Climb> routes(LocalWorld.Scene scene, LocalWorld.Location location, String action, boolean members) {
        return climbs.getOrDefault(location.id(), List.of()).stream().filter(route ->
            route.action.equals(action) && route.from.plane == location.plane()
                && scene.reaches(location, route.from.x, route.from.z)
                && (members || !route.members && WorldAccess.freeTile(route.from.x, route.from.z)
                    && WorldAccess.freeTile(route.to.x, route.to.z))).toList();
    }

    private static int integer(String[] fields, int index) {
        return Integer.parseInt(fields[index]);
    }

    record Tile(int x, int z, int plane) {}
    record Climb(Tile from, Tile to, String action, boolean members) {}
}
