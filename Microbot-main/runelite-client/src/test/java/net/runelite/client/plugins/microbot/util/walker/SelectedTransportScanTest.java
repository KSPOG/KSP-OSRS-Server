package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorDetection;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportDispatchTrace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class SelectedTransportScanTest
{
	private static final int REMAINING_ROUTE_TILES = 30;

	private final List<MockedStatic<?>> statics = new ArrayList<>();
	private MockedStatic<Rs2WalkerSceneObjects> gameObjects;
	private MockedStatic<Rs2PathApi> pathApi;
	private MockedStatic<Rs2Npc> npcs;
	private WorldPoint playerLocation;

	@Before
	public void setUp()
	{
		Rs2Walker.TERMINAL_TRAVEL_ATTEMPTED_EDGES.clear();
	}

	@After
	public void tearDown()
	{
		Rs2Walker.TERMINAL_TRAVEL_ATTEMPTED_EDGES.clear();
		for (int i = statics.size() - 1; i >= 0; i--)
		{
			statics.get(i).close();
		}
		statics.clear();
	}

	@Test
	public void missingTransportObjectIsScannedOnceNotOncePerRemainingRouteTile() throws Exception
	{
		WorldPoint origin = new WorldPoint(3222, 3218, 0);
		WorldPoint destination = new WorldPoint(3223, 3218, 0);
		Transport door = new Transport(origin, destination, "", TransportType.TRANSPORT, false,
			"Open", "Door", 1530, 1);
		Rs2PathApi.ActiveTransportSelection selection = selectionFor(door);
		install(origin);

		Rs2WalkerTransports.DispatchResult result = dispatch(selection, route(origin, destination));

		assertFalse(result.handled);
		assertEquals(TransportDispatchTrace.Refusal.OBJECT_MISSING, result.trace.refusal());
		gameObjects.verify(() -> Rs2WalkerSceneObjects.near(eq(TileObject.class), any(), eq(origin), eq(10)), times(1));
	}

	@Test
	public void spiritTreeTravelSwitchedOffIsCheckedOnceNotOncePerRemainingRouteTile() throws Exception
	{
		WorldPoint origin = new WorldPoint(2542, 3170, 0);
		WorldPoint destination = new WorldPoint(3184, 3508, 0);
		Transport spiritTree = new Transport(origin, destination, "Grand Exchange",
			TransportType.SPIRIT_TREE, true, "Travel", "Spirit tree", 1293, 1);
		Rs2PathApi.ActiveTransportSelection selection = selectionFor(spiritTree);
		install(origin);

		Rs2WalkerTransports.DispatchResult result = dispatch(selection, route(origin, destination));

		assertEquals(TransportDispatchTrace.Verdict.DELIBERATE_SKIP, result.verdict);
		pathApi.verify(Rs2PathApi::isSpiritTreeTravelEnabled, times(1));
		gameObjects.verify(() -> Rs2WalkerSceneObjects.near(eq(TileObject.class), any(), any(WorldPoint.class), eq(10)), never());
	}

	@Test
	public void refusedNpcTransportStopsWithoutTheGenericObjectScan() throws Exception
	{
		WorldPoint origin = new WorldPoint(1487, 3232, 0);
		WorldPoint destination = new WorldPoint(1361, 3309, 0);
		Transport mountainGuide = new Transport(origin, destination, "Auburn Valley", TransportType.NPC, true,
			"Follow", "Mountain Guide", 14529, 8);
		Rs2PathApi.ActiveTransportSelection selection = selectionFor(mountainGuide);
		install(origin);
		Rs2NpcModel guide = mock(Rs2NpcModel.class);
		npcs.when(() -> Rs2Npc.getNpc(anyString())).thenReturn(guide);
		npcs.when(() -> Rs2Npc.canWalkTo(any(Rs2NpcModel.class), anyInt())).thenReturn(true);
		npcs.when(() -> Rs2Npc.getAvailableAction(any(Rs2NpcModel.class), anyList())).thenReturn("Follow");
		npcs.when(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString())).thenReturn(false);

		Rs2WalkerTransports.DispatchResult result = dispatch(selection, route(origin, destination));

		assertFalse(result.handled);
		npcs.verify(() -> Rs2Npc.interact(guide, "Follow"), times(1));
		gameObjects.verify(() -> Rs2WalkerSceneObjects.near(eq(TileObject.class), any(), any(WorldPoint.class), eq(10)), never());
	}

	private static List<WorldPoint> route(WorldPoint origin, WorldPoint destination)
	{
		List<WorldPoint> path = new ArrayList<>();
		path.add(origin);
		path.add(destination);
		for (int step = 1; step <= REMAINING_ROUTE_TILES; step++)
		{
			path.add(destination.dx(step));
		}
		return path;
	}

	private static Rs2WalkerTransports.DispatchResult dispatch(Rs2PathApi.ActiveTransportSelection selection,
		List<WorldPoint> path)
	{
		return Rs2WalkerTransports.dispatchSelectedTransport(path, 0, selection, false);
	}

	private void install(WorldPoint playerAt)
	{
		playerLocation = playerAt;

		Client client = mock(Client.class);
		WorldView worldView = mock(WorldView.class);
		Scene scene = mock(Scene.class);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		when(client.isClientThread()).thenReturn(true);
		when(worldView.getScene()).thenReturn(scene);
		when(worldView.contains(any(WorldPoint.class))).thenReturn(true);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
			Callable<?> callable = inv.getArgument(0);
			return Optional.ofNullable(callable.call());
		});

		MockedStatic<Microbot> microbot = open(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);

		MockedStatic<Rs2Player> player = open(Rs2Player.class);
		player.when(Rs2Player::getWorldLocation).thenAnswer(inv -> playerLocation);

		pathApi = open(Rs2PathApi.class);
		pathApi.when(Rs2PathApi::isSpiritTreeTravelEnabled).thenReturn(false);

		open(Rs2LeaguesTransport.class);
		open(Rs2DoorDetection.class);
		open(Rs2Tile.class);
		npcs = open(Rs2Npc.class);

		gameObjects = open(Rs2WalkerSceneObjects.class);
		gameObjects.when(() -> Rs2WalkerSceneObjects.near(eq(TileObject.class), any(), any(WorldPoint.class), anyInt())).thenReturn(List.of());
	}

	private <T> MockedStatic<T> open(Class<T> type)
	{
		MockedStatic<T> mocked = Mockito.mockStatic(type);
		statics.add(mocked);
		return mocked;
	}

	private static Rs2PathApi.ActiveTransportSelection selectionFor(Transport transport) throws Exception
	{
		Method toEdge = Rs2PathApi.class.getDeclaredMethod("toTransportEdge", Transport.class);
		toEdge.setAccessible(true);
		Object edge = toEdge.invoke(null, transport);
		Constructor<Rs2PathApi.ActiveTransportSelection> ctor = Rs2PathApi.ActiveTransportSelection.class
			.getDeclaredConstructor(int.class, edge.getClass(), Transport.class);
		ctor.setAccessible(true);
		return ctor.newInstance(0, edge, transport);
	}
}
