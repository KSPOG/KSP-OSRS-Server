package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.MapDefinition.Tile;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.KitLoader;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.definitions.loaders.MapLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.index.ArchiveData;
import net.runelite.cache.index.IndexData;

/** Builds collision and location indices from the pinned cache as the player moves. */
final class LocalWorld {
    static final int SPAWN_X = 3222;
    static final int SPAWN_Z = 3218;
    static final int SIZE = 104;
    private static final int OCCUPIED = 1 << 8;
    private static final int[] DX = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final Map<Integer, Integer> OPEN_DOORS = Map.of(1535, 1536, 1540, 1541, 1543, 1544);

    private final Path cache;
    private final Map<Integer, ArchiveData> maps = new HashMap<>();
    private final LinkedHashMap<Integer, Region> regions = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<Integer, ObjectDefinition> definitions = new HashMap<>();
    private final Map<Location, Location> opened = new HashMap<>();
    private final ArchiveFiles objects;
    private final ArchiveFiles items;
    private final List<List<Integer>> bodyKits = new ArrayList<>(14);
    private final ItemLoader itemLoader = new ItemLoader();
    private final Map<Integer, ItemDefinition> itemDefinitions = new HashMap<>();
    private final ObjectLoader objectLoader;

    private LocalWorld(Path cache) throws IOException {
        this.cache = cache;
        try (var reader = new IndexedCacheReader(cache)) {
            for (var entry : readIndex(reader, 5).getArchives()) maps.put(entry.getId(), entry);
            var archive = Arrays.stream(readIndex(reader, 2).getArchives())
                .filter(entry -> entry.getId() == 6).findFirst().orElseThrow();
            objects = files(reader, 2, archive);
            var itemArchive = Arrays.stream(readIndex(reader, 2).getArchives())
                .filter(entry -> entry.getId() == 10).findFirst().orElseThrow();
            items = files(reader, 2, itemArchive);
            var kitArchive = Arrays.stream(readIndex(reader, 2).getArchives())
                .filter(entry -> entry.getId() == 3).findFirst().orElseThrow();
            var kitFiles = files(reader, 2, kitArchive);
            var loader = new KitLoader();
            for (int slot = 0; slot < 14; slot++) bodyKits.add(new ArrayList<>());
            for (var kitFile : kitArchive.getFiles()) {
                int id = kitFile.getId();
                var kit = loader.load(id, kitFiles.findFile(id).getContents());
                int part = kit.getBodyPartId();
                if (id < 1792 && !kit.isNonSelectable() && part >= 0 && part < bodyKits.size()) {
                    bodyKits.get(part).add(id);
                }
            }
            objectLoader = new ObjectLoader().configureForRevision(archive.getRevision());
        }
    }

    static LocalWorld load(Path cache) throws IOException {
        return new LocalWorld(cache);
    }

    ObjectDefinition definition(int id) {
        return definitions.computeIfAbsent(id, key -> {
            FSFile file = objects.findFile(key);
            return file == null ? null : objectLoader.load(key, file.getContents());
        });
    }

    List<Integer> kits(int slot, boolean male) {
        return bodyKits.get(slot + (male ? 0 : 7));
    }

    ItemDefinition itemDefinition(int id) {
        if (id < 0) return null;
        return itemDefinitions.computeIfAbsent(id, key -> {
            var file = items.findFile(key);
            return file == null ? null : itemLoader.load(key, file.getContents());
        });
    }

    boolean stackable(int id) {
        var item = itemDefinition(id);
        return item != null && item.stackable == 1;
    }

    Scene build(int centerX, int centerZ, boolean members) throws IOException {
        var scene = new Scene(centerX, centerZ, members);
        try (var reader = new IndexedCacheReader(cache)) {
            // Neighboring placements can clip tiles inside the scene.
            for (int rx = Math.max(0, (scene.baseX - 16) >> 6); rx <= (scene.baseX + SIZE - 1) >> 6; rx++) {
                for (int rz = Math.max(0, (scene.baseZ - 16) >> 6); rz <= (scene.baseZ + SIZE - 1) >> 6; rz++) {
                    Region region = region(reader, rx, rz);
                    if (region == null) continue;
                    scene.addTerrain(rx << 6, rz << 6, region.tiles);
                    for (Location loc : region.locations) {
                        scene.addLocation(opened.getOrDefault(loc, loc));
                    }
                }
            }
        }
        return scene;
    }

    private Region region(IndexedCacheReader reader, int x, int z) throws IOException {
        if (x > 255 || z > 255) return null;
        int key = x << 8 | z;
        Region known = regions.get(key);
        if (known != null) return known;
        ArchiveData archive = maps.get(key);
        if (archive == null) return null;
        ArchiveFiles data = files(reader, 5, archive);
        FSFile terrain = data.findFile(0);
        if (terrain == null) return null;
        Tile[][][] tiles = new MapLoader().load(x, z, terrain.getContents()).getTiles();
        List<Location> locations = new ArrayList<>();
        FSFile placements = data.findFile(1);
        if (placements != null) {
            for (var placement : new LocationsLoader().load(x, z, placements.getContents()).getLocations()) {
                var point = placement.getPosition();
                int lx = point.getX(), lz = point.getY(), plane = point.getZ();
                int collisionPlane = plane - ((tiles[1][lx][lz].settings & 2) == 0 ? 0 : 1);
                locations.add(new Location(placement.getId(), (x << 6) + lx, (z << 6) + lz,
                    plane, collisionPlane, placement.getType(), placement.getOrientation()));
            }
        }
        Region decoded = new Region(tiles, List.copyOf(locations));
        regions.put(key, decoded);
        if (regions.size() > 36) regions.pollFirstEntry();
        return decoded;
    }

    String action(Location location, int slot) {
        var def = definition(location.id);
        if (def == null) return "";
        var ops = def.getOps().getOps();
        if (slot < 1 || slot > ops.size() || ops.get(slot - 1) == null) return "";
        return ops.get(slot - 1).text;
    }

    Location swapDoor(Location location, String action) {
        if (location.shape != 0) return null;
        Integer openId = switch (location.id) {
            case 9398, 9470, 9708, 9709, 9710, 9716, 9717, 9718 -> location.id;
            default -> OPEN_DOORS.get(location.id);
        };
        if (openId != null && "Open".equals(action)) {
            if (opened.containsValue(location)) return location;
            Location replacement = new Location(openId, location.x, location.z, location.plane,
                location.collisionPlane, location.shape, (location.rotation + 1) & 3);
            opened.put(location, replacement);
            return replacement;
        }
        if ("Close".equals(action)) {
            for (var pair : opened.entrySet()) {
                if (!pair.getValue().equals(location)) continue;
                Location original = pair.getKey();
                opened.remove(original);
                return original;
            }
        }
        return null;
    }

    List<Location> shiftedDoors(Scene scene, int plane) {
        return opened.values().stream().filter(loc -> loc.plane == plane && scene.contains(loc.x, loc.z)).toList();
    }

    final class Scene {
        final int baseX;
        final int baseZ;
        private final boolean members;
        private final int[][] flags = new int[4][SIZE * SIZE];
        private final boolean[][] terrainPresent = new boolean[4][SIZE * SIZE];
        private final Map<Integer, List<Location>> locations = new HashMap<>();

        private Scene(int centerX, int centerZ, boolean members) {
            baseX = ((centerX >> 3) - 6) << 3;
            baseZ = ((centerZ >> 3) - 6) << 3;
            this.members = members;
        }

        boolean contains(int x, int z) { return index(x, z) >= 0; }

        boolean nearEdge(int x, int z) {
            return x - baseX < 16 || z - baseZ < 16 || baseX + SIZE - x <= 16 || baseZ + SIZE - z <= 16;
        }

        private int index(int x, int z) {
            int lx = x - baseX, lz = z - baseZ;
            return lx >= 0 && lx < SIZE && lz >= 0 && lz < SIZE ? lx * SIZE + lz : -1;
        }

        private void addTerrain(int regionX, int regionZ, Tile[][][] tiles) {
            for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                int tile = index(regionX + x, regionZ + z);
                if (tile < 0) continue;
                boolean accessible = members || WorldAccess.freeTile(regionX + x, regionZ + z);
                int shift = (tiles[1][x][z].settings & 2) == 0 ? 0 : 1;
                for (int plane = 0; plane + shift < 4; plane++) {
                    Tile surface = tiles[plane + shift][x][z];
                    terrainPresent[plane][tile] = accessible && (plane == 0
                        || surface.underlayId != 0 || surface.overlayId != 0);
                    if ((surface.settings & 1) != 0) flags[plane][tile] |= OCCUPIED;
                }
            }
        }

        Location location(int id, int plane, int x, int z) {
            int tile = index(x, z);
            if (tile < 0 || !members && !WorldAccess.freeTile(x, z)) return null;
            for (Location candidate : locations.getOrDefault(tile, List.of())) {
                if (candidate.id == id && candidate.plane == plane) return candidate;
            }
            return null;
        }

        private void addLocation(Location loc) {
            int tile = index(loc.x, loc.z);
            if (tile >= 0) locations.computeIfAbsent(tile, ignored -> new ArrayList<>()).add(loc);
            int p = loc.collisionPlane;
            if (p < 0) return;
            ObjectDefinition def = definition(loc.id);
            if (def == null || def.getInteractType() == 0 || def.isHollow()) return;
            int wall = loc.rotation * 2;
            switch (loc.shape) {
                case 0 -> addWall(p, loc.x, loc.z, wall);
                case 1, 3 -> addWall(p, loc.x, loc.z, (wall + 1) & 7);
                case 2 -> {
                    addWall(p, loc.x, loc.z, wall);
                    addWall(p, loc.x, loc.z, (wall + 2) & 7);
                }
                case 22 -> {
                    if (def.getInteractType() == 1) addFlag(p, loc.x, loc.z, OCCUPIED);
                }
                default -> {
                    if (loc.shape < 9 || loc.shape > 21) return;
                    for (int dx = 0; dx < width(loc, def); dx++) {
                        for (int dz = 0; dz < depth(loc, def); dz++) addFlag(p, loc.x + dx, loc.z + dz, OCCUPIED);
                    }
                }
            }
        }

        private void addWall(int plane, int x, int z, int direction) {
            addFlag(plane, x, z, 1 << direction);
            addFlag(plane, x + DX[direction], z + DZ[direction], 1 << ((direction + 4) & 7));
        }

        private void addFlag(int plane, int x, int z, int flag) {
            int tile = index(x, z);
            if (tile >= 0) flags[plane][tile] |= flag;
        }

        boolean walkable(int plane, int x, int z) {
            int tile = index(x, z);
            return tile >= 0 && terrainPresent[plane][tile] && (flags[plane][tile] & OCCUPIED) == 0;
        }

        private boolean canCross(int plane, int x, int z, int direction) {
            int from = index(x, z);
            int to = index(x + DX[direction], z + DZ[direction]);
            return from >= 0 && to >= 0 && terrainPresent[plane][to] && (flags[plane][from] & (1 << direction)) == 0
                && (flags[plane][to] & (OCCUPIED | 1 << ((direction + 4) & 7))) == 0;
        }

        boolean canStep(int plane, int x, int z, int direction) {
            if (!canCross(plane, x, z, direction)) return false;
            if ((direction & 1) == 0) return true;
            int first = (direction + 7) & 7, second = (direction + 1) & 7;
            return canCross(plane, x, z, first) && canCross(plane, x, z, second)
                && canCross(plane, x + DX[first], z + DZ[first], second)
                && canCross(plane, x + DX[second], z + DZ[second], first);
        }

        boolean canTalk(int plane, int x, int z, int targetX, int targetZ) {
            for (int d = 0; d < DX.length; d++) {
                if (x + DX[d] == targetX && z + DZ[d] == targetZ) return canStep(plane, x, z, d);
            }
            return false;
        }

        boolean reaches(Location loc, int x, int z) {
            ObjectDefinition def = definition(loc.id);
            int dx = Math.max(0, Math.max(loc.x - x, x - loc.x - width(loc, def) + 1));
            int dz = Math.max(0, Math.max(loc.z - z, z - loc.z - depth(loc, def) + 1));
            return dx + dz == 1 || loc.shape == 0 && dx + dz == 0;
        }

        List<Step> route(int plane, int x, int z, TileTarget target) {
            int start = index(x, z);
            if (start < 0) return List.of();
            int[] previous = new int[SIZE * SIZE];
            Arrays.fill(previous, -1);
            previous[start] = start;
            int[] frontier = new int[previous.length];
            frontier[0] = start;
            int end = 1;
            for (int current = 0; current < end; current++) {
                int tile = frontier[current], tx = baseX + tile / SIZE, tz = baseZ + tile % SIZE;
                if (target.reached(tx, tz)) {
                    var path = new ArrayDeque<Step>();
                    for (int step = tile; step != start; step = previous[step]) {
                        path.addFirst(new Step(baseX + step / SIZE, baseZ + step % SIZE));
                    }
                    return List.copyOf(path);
                }
                for (int d = 0; d < DX.length; d++) {
                    int next = index(tx + DX[d], tz + DZ[d]);
                    if (next < 0 || previous[next] >= 0 || !canStep(plane, tx, tz, d)) continue;
                    previous[next] = tile;
                    frontier[end++] = next;
                }
            }
            return List.of();
        }
    }

    private static int width(Location loc, ObjectDefinition def) {
        return (loc.rotation & 1) == 0 ? def.getSizeX() : def.getSizeY();
    }

    private static int depth(Location loc, ObjectDefinition def) {
        return (loc.rotation & 1) == 0 ? def.getSizeY() : def.getSizeX();
    }

    private static IndexData readIndex(IndexedCacheReader reader, int archive) throws IOException {
        var index = new IndexData();
        index.load(CacheMetadata.unpack(reader.read(255, archive)));
        return index;
    }

    private static ArchiveFiles files(IndexedCacheReader reader, int archive, ArchiveData group) throws IOException {
        var files = new ArchiveFiles();
        for (var file : group.getFiles()) files.addFile(new FSFile(file.getId()));
        files.loadContents(CacheMetadata.unpack(reader.read(archive, group.getId())));
        return files;
    }

    interface TileTarget { boolean reached(int x, int z); }

    private record Region(Tile[][][] tiles, List<Location> locations) {}
    record Step(int x, int z) {}
    record Location(int id, int x, int z, int plane, int collisionPlane, int shape, int rotation) {}
}
