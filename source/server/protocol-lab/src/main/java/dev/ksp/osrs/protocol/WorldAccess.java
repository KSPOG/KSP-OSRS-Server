package dev.ksp.osrs.protocol;

/** Initial free-world geographic policy. Boundaries are deliberately explicit and auditable. */
final class WorldAccess {
    private WorldAccess() {}

    static boolean freeTile(int x, int z) {
        // Mainland, northeastern Karamja, and the free Wilderness surface.
        return in(x, z, 2944, 3136, 3328, 3520)
            || in(x, z, 2944, 3520, 3392, 3968)
            || in(x, z, 2880, 3136, 2960, 3200)
            || in(x, z, 2496, 2816, 2560, 2880)
            || tutorialTile(x, z)
            || in(x, z, 3200, 9600, 3217, 9629); // Lumbridge Castle cellar.
    }

    static boolean tutorialTile(int x, int z) {
        return in(x,z,3050,3070,3150,3140) || in(x,z,3060,9490,3130,9545);
    }

    private static boolean in(int x, int z, int west, int south, int east, int north) {
        return x >= west && x < east && z >= south && z < north;
    }
}
