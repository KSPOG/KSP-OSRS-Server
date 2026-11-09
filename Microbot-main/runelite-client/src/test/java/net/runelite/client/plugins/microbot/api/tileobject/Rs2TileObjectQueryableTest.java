package net.runelite.client.plugins.microbot.api.tileobject;

import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2TileObjectQueryableTest {

    private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Microbot> microbot;
    private Rs2TileObjectCache cache;

    @Before
    public void setUp() {
        cache = mock(Rs2TileObjectCache.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private Rs2TileObjectQueryable queryOver(Rs2TileObjectModel... objects) {
        when(cache.getStream()).thenReturn(Stream.of(objects));
        return new Rs2TileObjectQueryable();
    }

    private static Rs2TileObjectModel object(WorldPoint location, boolean reachable) {
        Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
        when(model.getWorldLocation()).thenReturn(location);
        when(model.isReachable()).thenReturn(reachable);
        return model;
    }

    @Test
    public void interactionTargetPrefersReachableOverNearerUnreachable() {
        Rs2TileObjectModel nearBehindDoor = object(new WorldPoint(3201, 3200, 0), false);
        Rs2TileObjectModel fartherReachable = object(new WorldPoint(3205, 3200, 0), true);

        assertSame(fartherReachable, queryOver(nearBehindDoor, fartherReachable).interactionTarget(PLAYER, 20));
    }

    @Test
    public void interactionTargetFallsBackToNearestWhenNothingIsReachable() {
        Rs2TileObjectModel near = object(new WorldPoint(3202, 3200, 0), false);
        Rs2TileObjectModel far = object(new WorldPoint(3210, 3200, 0), false);

        assertSame(near, queryOver(far, near).interactionTarget(PLAYER, 20));
    }

    @Test
    public void interactionTargetIgnoresObjectsBeyondMaxDistance() {
        Rs2TileObjectModel far = object(new WorldPoint(3230, 3200, 0), true);

        assertNull(queryOver(far).interactionTarget(PLAYER, 10));
    }

    @Test
    public void interactionTargetReturnsNullWithoutPlayerLocation() {
        Rs2TileObjectModel near = object(new WorldPoint(3201, 3200, 0), true);

        assertNull(queryOver(near).interactionTarget(null, 10));
    }

    @Test
    public void ofTypeKeepsOnlyRequestedTypes() {
        Rs2TileObjectModel wall = mock(Rs2TileObjectModel.class);
        when(wall.getTileObjectType()).thenReturn(TileObjectType.WALL);
        Rs2TileObjectModel game = mock(Rs2TileObjectModel.class);
        when(game.getTileObjectType()).thenReturn(TileObjectType.GAME);

        assertEquals(java.util.List.of(wall), queryOver(wall, game).ofType(TileObjectType.WALL).toList());
        assertTrue(queryOver(wall, game).ofType().toList().isEmpty());
    }

    @Test
    public void withActionUsesExactMatchAndWithActionContainsUsesSubstring() {
        Rs2TileObjectModel booth = mock(Rs2TileObjectModel.class);
        when(booth.hasAction("Bank")).thenReturn(true);
        when(booth.hasActionContaining(any())).thenReturn(true);
        Rs2TileObjectModel chest = mock(Rs2TileObjectModel.class);
        when(chest.hasAction("Bank")).thenReturn(false);
        when(chest.hasActionContaining("Ban")).thenReturn(true);

        assertEquals(java.util.List.of(booth), queryOver(booth, chest).withAction("Bank").toList());
        assertEquals(java.util.List.of(booth, chest), queryOver(booth, chest).withActionContains("Ban").toList());
        assertTrue(queryOver(booth, chest).withAction("").toList().isEmpty());
    }

    @Test
    public void atMatchesObjectsWhoseFootprintCoversTheTile() {
        WorldPoint tile = new WorldPoint(3201, 3201, 0);
        Rs2TileObjectModel covering = mock(Rs2TileObjectModel.class);
        when(covering.occupies(tile)).thenReturn(true);
        Rs2TileObjectModel elsewhere = mock(Rs2TileObjectModel.class);

        assertEquals(java.util.List.of(covering), queryOver(covering, elsewhere).at(tile).toList());
        assertTrue(queryOver(covering).at(null).toList().isEmpty());
    }
}
