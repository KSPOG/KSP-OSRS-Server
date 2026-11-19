import java.util.Arrays;

public final class LocalSceneTrace {
    private static int previousState = -1;
    private static String previousWait;
    private static boolean waitingForModels;

    private LocalSceneTrace() {}

    public static synchronized void state(int state) {
        if (state == previousState) return;
        previousState = state;
        mark("game_state=" + state);
    }

    public static void prepareLocalLoginWindow(int state) {
        if (state != 20) return;
        // Set the local gamepack's canvas mode before it writes the login block.
        // It reports this mode to the server, which opens the matching HUD.
        client.nk(2);
        mark("window_mode=resizable");
    }

    public static synchronized void rebuild(int zoneX, int zoneZ, int[] regions) {
        previousWait = null;
        waitingForModels = false;
        mark("rebuild_received zone_x=" + zoneX + " zone_z=" + zoneZ
            + " map_groups=" + Arrays.toString(regions));
    }

    public static synchronized void mapWait(int[] regions, byte[][] terrain, byte[][] locations) {
        var pending = new StringBuilder();
        for (int i = 0; i < regions.length; i++) {
            if (regions[i] < 0 || (terrain[i] != null && locations[i] != null)) continue;
            if (!pending.isEmpty()) pending.append(',');
            pending.append(regions[i]).append(':');
            if (terrain[i] == null) pending.append('0');
            if (locations[i] == null) pending.append('1');
        }
        String status = pending.toString();
        if (status.equals(previousWait)) return;
        previousWait = status;
        mark("waiting_for_map_files groups=" + status);
    }

    public static synchronized void modelWait() {
        if (waitingForModels) return;
        waitingForModels = true;
        mark("waiting_for_object_models");
    }

    public static void mark(String stage) {
        System.out.println("KSP_LOCAL_SCENE " + stage);
    }

    public static void failure(String stage, Throwable error) {
        mark(stage + " failed=" + error.getClass().getName());
        for (int i = 0; i < Math.min(8, error.getStackTrace().length); i++) {
            mark("stack=" + error.getStackTrace()[i]);
        }
    }
}
