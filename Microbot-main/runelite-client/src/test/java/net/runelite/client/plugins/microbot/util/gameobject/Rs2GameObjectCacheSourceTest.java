package net.runelite.client.plugins.microbot.util.gameobject;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2GameObjectCacheSourceTest {

    private static final LocalPoint PLAYER = local(50, 50);

    private MockedStatic<Microbot> microbot;
    private Rs2TileObjectCache cache;
    private WorldView playerWorldView;
    private WorldView otherWorldView;

    private static LocalPoint local(int sceneX, int sceneY) {
        return new LocalPoint(sceneX * 128 + 64, sceneY * 128 + 64, -1);
    }

    @Before
    public void setUp() {
        playerWorldView = mock(WorldView.class);
        when(playerWorldView.getId()).thenReturn(-1);
        otherWorldView = mock(WorldView.class);
        when(otherWorldView.getId()).thenReturn(7);
        Player player = mock(Player.class);
        when(player.getWorldView()).thenReturn(playerWorldView);
        when(player.getLocalLocation()).thenReturn(PLAYER);
        Client client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(player);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        cache = mock(Rs2TileObjectCache.class);

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private void scene(TileObject... objects) {
        when(cache.getStream()).thenAnswer(inv -> Stream.of(objects).map(Rs2TileObjectModel::new));
    }

    private <T extends TileObject> T object(Class<T> type, int id, LocalPoint location, WorldView worldView) {
        T object = mock(type);
        when(object.getId()).thenReturn(id);
        when(object.getLocalLocation()).thenReturn(location);
        when(object.getWorldView()).thenReturn(worldView);
        return object;
    }

    @Test
    public void gameObjectQueriesReturnTheRawObjectsFromTheCacheNearestFirst() {
        GameObject far = object(GameObject.class, 1, local(58, 50), playerWorldView);
        GameObject near = object(GameObject.class, 2, local(52, 50), playerWorldView);
        WallObject wall = object(WallObject.class, 3, local(51, 50), playerWorldView);
        scene(far, wall, near);

        List<GameObject> found = Rs2GameObject.getGameObjects(o -> true, PLAYER, 20 * 128);

        assertEquals(List.of(near, far), found);
    }

    @Test
    public void tileObjectQueriesLeaveGameObjectsOut() {
        GameObject booth = object(GameObject.class, 1, local(52, 50), playerWorldView);
        WallObject wall = object(WallObject.class, 2, local(51, 50), playerWorldView);
        GroundObject ground = object(GroundObject.class, 3, local(53, 50), playerWorldView);
        scene(booth, wall, ground);

        assertEquals(List.of(wall, ground), Rs2GameObject.getTileObjects(o -> true, PLAYER, 20 * 128));
        assertEquals(List.of(wall), Rs2GameObject.getWallObjects(o -> true, PLAYER, 20 * 128));
    }

    @Test
    public void objectsInAnotherWorldViewAreIgnored() {
        GameObject onBoat = object(GameObject.class, 1, local(51, 50), otherWorldView);
        GameObject onLand = object(GameObject.class, 2, local(55, 50), playerWorldView);
        scene(onBoat, onLand);

        assertEquals(List.of(onLand), Rs2GameObject.getGameObjects(o -> true, PLAYER, 20 * 128));
    }

    @Test
    public void wallObjectOnTheLastTileOfASmallWorldViewIsFound() {
        LocalPoint lastTile = local(7, 7);
        WallObject wall = object(WallObject.class, 1, lastTile, playerWorldView);
        scene(wall);

        List<WallObject> walls = Rs2GameObject.getWallObjects(o -> true, lastTile, 10 * 128);

        assertEquals(1, walls.size());
        assertSame(wall, walls.get(0));
    }

    @Test
    public void nothingIsReturnedWithoutAPlayerWorldView() {
        Player player = Microbot.getClient().getLocalPlayer();
        when(player.getWorldView()).thenReturn(null);
        scene(object(GameObject.class, 1, local(51, 50), playerWorldView));

        assertTrue(Rs2GameObject.getGameObjects(o -> true, PLAYER, 20 * 128).isEmpty());
    }
}
