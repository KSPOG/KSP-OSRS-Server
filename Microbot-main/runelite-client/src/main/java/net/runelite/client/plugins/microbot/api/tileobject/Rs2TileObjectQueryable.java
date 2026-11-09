package net.runelite.client.plugins.microbot.api.tileobject;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Rs2TileObjectQueryable extends AbstractEntityQueryable<Rs2TileObjectQueryable, Rs2TileObjectModel>
        implements IEntityQueryable<Rs2TileObjectQueryable, Rs2TileObjectModel> {

    public Rs2TileObjectQueryable() {
        super();
    }

    @Override
    protected Stream<Rs2TileObjectModel> initialSource() {
        return Microbot.getRs2TileObjectCache().getStream();
    }

    public Rs2TileObjectQueryable ofType(TileObjectType... types) {
        if (types == null || types.length == 0) {
            this.source = Stream.empty();
            return this;
        }
        Set<TileObjectType> wanted = EnumSet.copyOf(Arrays.asList(types));
        this.source = this.source.filter(o -> wanted.contains(o.getTileObjectType()));
        return this;
    }

    public Rs2TileObjectQueryable withAction(String action) {
        if (action == null || action.isEmpty()) {
            this.source = Stream.empty();
            return this;
        }
        this.source = this.source.filter(o -> o.hasAction(action));
        return this;
    }

    public Rs2TileObjectQueryable withActionContains(String action) {
        if (action == null || action.isEmpty()) {
            this.source = Stream.empty();
            return this;
        }
        this.source = this.source.filter(o -> o.hasActionContaining(action));
        return this;
    }

    public Rs2TileObjectQueryable at(WorldPoint point) {
        if (point == null) {
            this.source = Stream.empty();
            return this;
        }
        this.source = this.source.filter(o -> o.occupies(point));
        return this;
    }

    @Override
    public boolean interact() {
        return clickTarget(null, Integer.MAX_VALUE);
    }

    @Override
    public boolean interact(String action) {
        return clickTarget(action, Integer.MAX_VALUE);
    }

    @Override
    public boolean interact(String action, int maxDistance) {
        return clickTarget(action, maxDistance);
    }

    @Override
    public boolean interact(int id) {
        return withId(id).clickTarget(null, Integer.MAX_VALUE);
    }

    @Override
    public boolean interact(int id, String action) {
        return withId(id).clickTarget(action, Integer.MAX_VALUE);
    }

    @Override
    public boolean interact(int id, String action, int maxDistance) {
        return withId(id).clickTarget(action, maxDistance);
    }

    private boolean clickTarget(String action, int maxDistance) {
        Rs2TileObjectModel target = Microbot.getClientThread()
                .invoke(() -> interactionTarget(new Rs2PlayerModel().getWorldLocation(), maxDistance));
        if (target == null) {
            return false;
        }
        return action == null ? target.click() : target.click(action);
    }

    Rs2TileObjectModel interactionTarget(WorldPoint playerLocation, int maxDistance) {
        if (playerLocation == null) {
            return null;
        }
        List<Rs2TileObjectModel> candidates = source
                .filter(o -> {
                    WorldPoint location = o.getWorldLocation();
                    return location != null && location.distanceTo(playerLocation) <= maxDistance;
                })
                .sorted(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(playerLocation)))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(Rs2TileObjectModel::isReachable)
                .findFirst()
                .orElse(candidates.get(0));
    }
}
