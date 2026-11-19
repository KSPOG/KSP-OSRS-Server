package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;

import java.util.Map;

/**
 * Live-client implementation of {@link LiveScene} — the read side of the P2 obstacle plumbing
 * (docs/walker-p2-unification.md). Built once per resolution from a snapshot of the player tile and the
 * reachable-tiles map the walker already computes ({@code reachableTilesCache}); {@code transportsAt} and
 * {@code objectAt} read current game state and so must be used on the client thread during resolution.
 * Tests never use this — they supply in-memory {@link LiveScene} fakes, which is why resolver decision
 * logic stays headless-testable.
 */
public final class Rs2LiveScene implements LiveScene {

    private final WorldPoint player;
    private final Map<WorldPoint, Integer> reachable;

    public Rs2LiveScene(WorldPoint player, Map<WorldPoint, Integer> reachable) {
        this.player = player;
        this.reachable = reachable;
    }

    @Override
    public WorldPoint playerLocation() {
        return player;
    }

    @Override
    public boolean isReachable(WorldPoint tile) {
        return tile != null && reachable != null && reachable.containsKey(tile);
    }

    @Override
	public boolean hasTransportAt(WorldPoint tile) {
		return Rs2PathApi.hasCatalogTransportOrigin(tile);
	}

    @Override
    public TileObject objectAt(WorldPoint tile) {
        if (tile == null) {
            return null;
        }
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .ofType(TileObjectType.GAME)
                .where(o -> tile.equals(o.getRuneLiteWorldLocation()))
                .firstOnClientThread();
        return object != null ? object.getTileObject() : null;
    }
}
