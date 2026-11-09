package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.state.WalkerRouteState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WalkerPathEndpointArrivalTest
{
	private static final WorldPoint TARGET = new WorldPoint(3227, 3214, 0);
	private static final List<WorldPoint> PATH = List.of(
		new WorldPoint(3224, 3214, 0),
		new WorldPoint(3225, 3214, 0),
		new WorldPoint(3226, 3214, 0),
		TARGET);
	private static final String REACHED_ENDPOINT = "rs2walker:processWalk:reached-path-endpoint";

	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Player> player;
	private MockedStatic<Rs2Tile> tile;
	private MockedStatic<Rs2PathApi> pathApi;
	private ShortestPathConfig originalConfig;
	private WorldPoint originalTarget;
	private Rs2ActiveRouteStatus status;

	@Before
	public void setUp()
	{
		originalConfig = Rs2Walker.config;
		originalTarget = Rs2Walker.currentTarget;
		Rs2Walker.config = mock(ShortestPathConfig.class);
		Rs2Walker.clearWalkerDedupeForTesting();
		WalkerRouteState routeState = Rs2Walker.routeStateForTesting();
		routeState.lastRouteClearReason = "";
		routeState.interimTargetWp = null;
		routeState.interimTargetIdx = -1;
		routeState.stuckCount = 0;
		routeState.lastMovedTimeMs = System.currentTimeMillis();

		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
		microbot.when(Microbot::isLoggedIn).thenReturn(true);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
			Callable<?> callable = invocation.getArgument(0);
			return Optional.ofNullable(callable.call());
		});
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);

		player = Mockito.mockStatic(Rs2Player.class);
		player.when(Rs2Player::getWorldLocation).thenReturn(TARGET);

		tile = Mockito.mockStatic(Rs2Tile.class);
		tile.when(() -> Rs2Tile.getReachableTilesFromTile(any(WorldPoint.class), anyInt())).thenReturn(new HashMap<>());

		status = mock(Rs2ActiveRouteStatus.class);
		when(status.isPresent()).thenReturn(true);
		when(status.isReady()).thenReturn(true);
		when(status.getRawPath()).thenReturn(PATH);
		when(status.getWalkablePath()).thenReturn(PATH);
		when(status.getMetrics()).thenReturn(Optional.empty());
		pathApi = Mockito.mockStatic(Rs2PathApi.class);
		pathApi.when(Rs2PathApi::getActiveRouteStatus).thenReturn(status);
		pathApi.when(() -> Rs2PathApi.hasWalkableTileWithin(any(WorldPoint.class), anyInt())).thenReturn(true);

		Rs2Walker.currentTarget = TARGET;
	}

	@After
	public void tearDown()
	{
		pathApi.close();
		tile.close();
		player.close();
		microbot.close();
		Rs2Walker.currentTarget = originalTarget;
		Rs2Walker.config = originalConfig;
		Rs2Walker.clearWalkerDedupeForTesting();
	}

	@Test
	public void standingOnTheEndpointThatIsTheTargetArrives()
	{
		WalkerState state = Rs2Walker.processWalkForTesting(TARGET, 0);

		assertEquals(WalkerState.ARRIVED, state);
		assertEquals(REACHED_ENDPOINT, Rs2Walker.getLastRouteClearReason());
		assertNull(Rs2Walker.currentTarget);
	}

	@Test
	public void externalCancelBeforeTheEndpointCheckStillExits()
	{
		player.when(Rs2Player::isInteracting).thenAnswer(invocation -> {
			Rs2Walker.currentTarget = null;
			return false;
		});

		WalkerState state = Rs2Walker.processWalkForTesting(TARGET, 0);

		assertEquals(WalkerState.EXIT, state);
		assertNotEquals(REACHED_ENDPOINT, Rs2Walker.getLastRouteClearReason());
	}

	@Test
	public void endpointShortOfTheTightFinishThresholdDoesNotArrive()
	{
		WorldPoint farTarget = new WorldPoint(3230, 3214, 0);
		Rs2Walker.currentTarget = farTarget;

		WalkerState state = Rs2Walker.processWalkForTesting(farTarget, 5);

		assertEquals(WalkerState.EXIT, state);
		assertEquals(REACHED_ENDPOINT, Rs2Walker.getLastRouteClearReason());
		assertNull(Rs2Walker.currentTarget);
	}
}
