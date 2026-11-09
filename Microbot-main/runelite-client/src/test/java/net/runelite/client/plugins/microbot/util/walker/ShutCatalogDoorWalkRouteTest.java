package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorProbe;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ShutCatalogDoorWalkRouteTest
{
	private static final int GUIDE_HOUSE_DOOR_ID = 9398;
	private static final WorldPoint INSIDE = new WorldPoint(3097, 3107, 0);
	private static final WorldPoint DOOR_TILE = new WorldPoint(3098, 3107, 0);
	private static final WorldPoint OUTSIDE = new WorldPoint(3099, 3106, 0);
	private static final List<WorldPoint> PATH = List.of(INSIDE, DOOR_TILE, OUTSIDE);

	private static final Map<WorldPoint, List<Rs2TransportEdge>> CATALOG_EDGES = new HashMap<>();
	private static Rs2TransportEdge doorEdge;

	private MockedStatic<Rs2PathApi> pathApi;

	@BeforeClass
	public static void loadShippedCatalog()
	{
		Map<WorldPoint, Set<Transport>> catalog = Transport.loadAllFromResources();
		for (int x = INSIDE.getX() - 2; x <= OUTSIDE.getX() + 2; x++)
		{
			for (int y = OUTSIDE.getY() - 2; y <= INSIDE.getY() + 2; y++)
			{
				WorldPoint origin = new WorldPoint(x, y, 0);
				CATALOG_EDGES.put(origin, Rs2PathApi.getCatalogTransportEdges(catalog, origin));
			}
		}
		doorEdge = CATALOG_EDGES.get(INSIDE).stream()
			.filter(edge -> edge.getObjectId() == GUIDE_HOUSE_DOOR_ID && DOOR_TILE.equals(edge.getDestination()))
			.findFirst()
			.orElse(null);
	}

	@After
	public void tearDown()
	{
		if (pathApi != null)
		{
			pathApi.close();
		}
	}

	@Test
	public void guideHouseDoorShipsAsAnObjectExecutorDoor()
	{
		assertNotNull("transports.tsv must still carry the Gielinor Guide house door", doorEdge);
		assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(doorEdge));
		assertEquals(Rs2TransportExecutor.OBJECT, doorEdge.getExecutor());
	}

	@Test
	public void shutCatalogDoorOnAWalkingRouteIsHandedToTheDoorPipeline()
	{
		activeRoute(List.of(Rs2RouteStep.walk(INSIDE, DOOR_TILE), Rs2RouteStep.walk(DOOR_TILE, OUTSIDE)));

		assertFalse("a walking edge must not defer the door to a transport that was never selected",
			Rs2WalkerDoors.shouldDeferDoorHandlingToTransport(PATH, 0));
		assertFalse("the shut door must stay a door-probe candidate",
			Rs2DoorProbe.isCatalogTransportObject(guideHouseDoor()));
	}

	@Test
	public void selectedCatalogDoorTransportKeepsItsObjectExecutor()
	{
		activeRoute(List.of(Rs2RouteStep.transport(INSIDE, DOOR_TILE, doorEdge), Rs2RouteStep.walk(DOOR_TILE, OUTSIDE)));

		assertTrue(Rs2WalkerDoors.shouldDeferDoorHandlingToTransport(PATH, 0));
		assertTrue(Rs2DoorProbe.isCatalogTransportObject(guideHouseDoor()));
	}

	private void activeRoute(List<Rs2RouteStep> steps)
	{
		Rs2RouteResult route = new Rs2RouteResult(INSIDE, Set.of(OUTSIDE), PATH, steps,
			Rs2RouteTermination.TARGET_REACHED, new Rs2RouteMetrics(1L, 2L, 3L, 0L));
		pathApi = Mockito.mockStatic(Rs2PathApi.class);
		pathApi.when(Rs2PathApi::getActiveRoute).thenReturn(Optional.of(route));
		pathApi.when(() -> Rs2PathApi.getActiveTransportEdge(any(WorldPoint.class), any(WorldPoint.class)))
			.thenAnswer(inv -> route.getTransportEdge(inv.getArgument(0), inv.getArgument(1)));
		pathApi.when(() -> Rs2PathApi.getCatalogTransportEdges(any(WorldPoint.class)))
			.thenAnswer(inv -> CATALOG_EDGES.getOrDefault(inv.getArgument(0), List.of()));
		pathApi.when(() -> Rs2PathApi.hasCatalogTransportEdge(any(WorldPoint.class), any(WorldPoint.class)))
			.thenAnswer(inv -> CATALOG_EDGES.getOrDefault(inv.getArgument(0), List.of()).stream()
				.anyMatch(edge -> inv.getArgument(1).equals(edge.getDestination())));
	}

	private static WallObject guideHouseDoor()
	{
		WallObject door = mock(WallObject.class);
		when(door.getId()).thenReturn(GUIDE_HOUSE_DOOR_ID);
		when(door.getWorldLocation()).thenReturn(DOOR_TILE);
		return door;
	}
}
