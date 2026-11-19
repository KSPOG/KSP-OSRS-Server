package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public final class Rs2WalkerSceneObjects {

    private Rs2WalkerSceneObjects() {
    }

    public static <T extends TileObject> List<T> near(Class<T> kind, WorldPoint anchor, int radiusTiles) {
        if (anchor == null) {
            return Collections.emptyList();
        }
        List<T> found = Microbot.getClientThread().invoke(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            WorldView worldView = player != null ? player.getWorldView() : null;
            if (worldView == null) {
                return Collections.<T>emptyList();
            }
            LocalPoint anchorLocal = LocalPoint.fromWorld(worldView, anchor);
            if (anchorLocal == null) {
                anchorLocal = player.getLocalLocation();
            }
            if (anchorLocal == null) {
                return Collections.<T>emptyList();
            }
            LocalPoint origin = anchorLocal;
            int maxDistance = Math.min(radiusTiles, Constants.SCENE_SIZE) * Perspective.LOCAL_TILE_SIZE;
            List<Rs2TileObjectModel> models = Microbot.getRs2TileObjectCache().query().fromWorldView().toList();
            TileObjectType type = typeOf(kind);
            if (type != null) {
                return sortedNear(models, type, kind, origin, maxDistance);
            }
            List<T> objects = new ArrayList<>(sortedNear(models, TileObjectType.GAME, kind, origin, maxDistance));
            objects.addAll(sortedNear(models, null, kind, origin, maxDistance));
            return objects;
        });
        return found != null ? found : Collections.emptyList();
    }

    private static <T extends TileObject> List<T> sortedNear(List<Rs2TileObjectModel> models, TileObjectType type,
                                                             Class<T> kind, LocalPoint origin, int maxDistance) {
        return models.stream()
                .filter(m -> type != null ? m.getTileObjectType() == type : m.getTileObjectType() != TileObjectType.GAME)
                .map(Rs2TileObjectModel::getTileObject)
                .filter(kind::isInstance)
                .map(kind::cast)
                .filter(o -> o.getLocalLocation() != null && isWithin(origin, o.getLocalLocation(), maxDistance))
                .sorted(Comparator.comparingInt(o -> o.getLocalLocation().distanceTo(origin)))
                .collect(Collectors.toList());
    }

    private static boolean isWithin(LocalPoint anchor, LocalPoint location, int maxDistance) {
        if (maxDistance == 0) {
            int dx = Math.abs(anchor.getX() - location.getX());
            int dy = Math.abs(anchor.getY() - location.getY());
            return (dx == Perspective.LOCAL_TILE_SIZE && dy == 0) || (dy == Perspective.LOCAL_TILE_SIZE && dx == 0);
        }
        return location.distanceTo(anchor) <= maxDistance;
    }

    public static boolean hasLineOfSight(WorldPoint point, TileObject object) {
        if (point == null || object == null) {
            return false;
        }
        return !inLineOfSight(point, Collections.singletonList(object)).isEmpty();
    }

    private static <T extends TileObject> List<T> inLineOfSight(WorldPoint point, List<T> candidates) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldView topLevel = Microbot.getClient().getTopLevelWorldView();
            List<T> visible = new ArrayList<>();
            for (T object : candidates) {
                boolean seen;
                if (object instanceof GameObject) {
                    WorldArea area = new Rs2TileObjectModel(object).getWorldArea();
                    seen = area != null && area.hasLineOfSightTo(topLevel, point.toWorldArea());
                } else {
                    seen = new WorldArea(object.getWorldLocation(), 2, 2)
                            .hasLineOfSightTo(topLevel, new WorldArea(point.getX(), point.getY(), 2, 2, point.getPlane()));
                }
                if (seen) {
                    visible.add(object);
                }
            }
            return visible;
        }).orElse(Collections.emptyList());
    }

    public static <T extends TileObject> List<T> near(Class<T> kind, Predicate<? super T> predicate, WorldPoint anchor, int radiusTiles) {
        return near(kind, anchor, radiusTiles).stream().filter(predicate).collect(Collectors.toList());
    }

    public static <T extends TileObject> T first(Class<T> kind, Predicate<? super T> predicate, WorldPoint anchor, int radiusTiles) {
        return near(kind, anchor, radiusTiles).stream().filter(predicate).findFirst().orElse(null);
    }

    public static <T extends TileObject> T firstNearPlayer(Class<T> kind, Predicate<? super T> predicate) {
        return first(kind, predicate, Rs2Player.getWorldLocation(), Constants.SCENE_SIZE / 2);
    }

    public static <T extends TileObject> List<T> nearPlayer(Class<T> kind, Predicate<? super T> predicate) {
        return near(kind, predicate, Rs2Player.getWorldLocation(), Constants.SCENE_SIZE / 2);
    }

    public static List<TileObject> all() {
        return near(TileObject.class, Rs2Player.getWorldLocation(), Constants.SCENE_SIZE);
    }

    public static List<TileObject> all(Predicate<? super TileObject> predicate) {
        return near(TileObject.class, predicate, Rs2Player.getWorldLocation(), Constants.SCENE_SIZE);
    }

    public static TileObject findById(int id) {
        List<TileObject> nearby = all();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (TileObject object : nearby) {
                if (object.getId() == id) {
                    return object;
                }
            }
            return null;
        }).orElse(null);
    }

    public static boolean interact(int id, String action) {
        return interact(findById(id), action);
    }

    public static ObjectComposition composition(TileObject object) {
        if (object == null) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition comp = Microbot.getClient().getObjectDefinition(object.getId());
            if (comp == null) {
                return null;
            }
            return comp.getImpostorIds() == null ? comp : comp.getImpostor();
        }).orElse(null);
    }

    public static ObjectComposition composition(int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition comp = Microbot.getClient().getObjectDefinition(id);
            if (comp == null) {
                return null;
            }
            return comp.getImpostorIds() == null ? comp : comp.getImpostor();
        }).orElse(null);
    }

    public static boolean hasAction(ObjectComposition composition, String action) {
        if (composition == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String[] actions = composition.getActions();
            if (actions == null) {
                return false;
            }
            for (String candidate : actions) {
                if (candidate != null && candidate.equalsIgnoreCase(action)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public static boolean interact(TileObject object, String action) {
        if (object == null) {
            return false;
        }
        Rs2TileObjectModel model = new Rs2TileObjectModel(object);
        String wanted = action == null ? "" : Rs2UiHelper.stripColTags(action).trim();
        if (wanted.isEmpty() || !model.hasAction(wanted)) {
            return model.click();
        }
        return model.click(action);
    }

    private static TileObjectType typeOf(Class<? extends TileObject> kind) {
        if (kind == WallObject.class) {
            return TileObjectType.WALL;
        }
        if (kind == GameObject.class) {
            return TileObjectType.GAME;
        }
        if (kind == GroundObject.class) {
            return TileObjectType.GROUND;
        }
        if (kind == DecorativeObject.class) {
            return TileObjectType.DECORATIVE;
        }
        return null;
    }
}
