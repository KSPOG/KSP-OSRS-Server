package dev.ksp.osrs.protocol;

/**
 * Exact revision-242 root-widget icon components, verified from the user's
 * RuneLite cache audit (tutorial-viewport-widgets.tsv).
 *
 * IMPORTANT: These are icon overlays, not the stone hitboxes or backgrounds.
 * Fixed/Classic have 7 upper icons and 7 lower icons. Modern has six upper
 * icons: inventory (tab 3) does not have a separate icon in the audited root.
 */
final class TutorialTabIcons {
    private TutorialTabIcons() { }

    static int component(int root, int tab) {
        if (tab < 0 || tab >= 14) return -1;
        return switch (root) {
            case 548 -> tab < 7 ? 55 + tab : 71 + tab - 7;
            case 161 -> tab < 7 ? 50 + tab : 66 + tab - 7;
            case 164 -> switch (tab) {
                case 0, 1, 2 -> 44 + tab;
                case 3 -> -1; // No independent icon in revision-242 root 164.
                case 4, 5, 6 -> 43 + tab;
                default -> 59 + tab - 7;
            };
            default -> -1;
        };
    }

    static int expectedSprite(int tab) {
        return switch (tab) {
            case 0 -> 904; case 1 -> 1709; case 2 -> 905;
            case 3 -> 907; case 4 -> 908; case 5 -> 909; case 6 -> 910;
            case 7 -> 168; case 8 -> 898; case 9 -> 899; case 10 -> 900;
            case 11 -> 901; case 12 -> 902; case 13 -> 903;
            default -> -1;
        };
    }
}
