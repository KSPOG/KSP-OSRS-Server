package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shortestpath.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Optional;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/** Executes an already-unlocked hot-air-balloon network edge. */
final class Rs2HotAirBalloon
{
	private static final int MAP_WAIT_POLL_MS = 100;
	private static final int MAP_WAIT_TIMEOUT_MS = 5_000;
	private static final int BASKET_SEARCH_RADIUS = 8;
	private static final List<Integer> BASKET_OBJECT_IDS = List.of(
		ObjectID.ZEP_BASKET_ENTRANA,
		ObjectID.ZEP_BASKET,
		ObjectID.ZEP_MULTI_BASKET_ENTRANA,
		ObjectID.ZEP_MULTI_BASKET_TAV,
		ObjectID.ZEP_MULTI_BASKET_CAST,
		ObjectID.ZEP_MULTI_BASKET_GNO,
		ObjectID.ZEP_MULTI_BASKET_CRAFT,
		ObjectID.ZEP_MULTI_BASKET_VARR);

	private Rs2HotAirBalloon()
	{
	}

	static boolean handle(Rs2TransportEdge transport)
	{
		if (transport == null || transport.getOrigin() == null)
		{
			return false;
		}
		Optional<TransportExecutionRegistry.BalloonDestination> destination =
			TransportExecutionRegistry.balloonDestinationFor(transport.getDisplayInfo());
		if (destination.isEmpty())
		{
			return false;
		}

		if (!isMapVisible())
		{
			Rs2TileObjectModel basket = findBasket(transport.getOrigin());
			if (basket == null || !basket.click(transport.getAction()))
			{
				return false;
			}
			if (!sleepUntilTrue(Rs2HotAirBalloon::isMapVisible,
				MAP_WAIT_POLL_MS, MAP_WAIT_TIMEOUT_MS))
			{
				return false;
			}
		}

		return Rs2Widget.clickWidget(destinationButton(destination.get()));
	}

	static boolean isBasketObjectId(int objectId)
	{
		return BASKET_OBJECT_IDS.contains(objectId);
	}

	private static Rs2TileObjectModel findBasket(WorldPoint origin)
	{
		for (int objectId : BASKET_OBJECT_IDS)
		{
			Rs2TileObjectModel basket = Microbot.getRs2TileObjectCache().query()
				.fromWorldView()
				.ofType(TileObjectType.GAME)
				.withId(objectId)
				.nearestOnClientThread(origin, BASKET_SEARCH_RADIUS);
			if (basket != null)
			{
				return basket;
			}
		}
		return null;
	}

	static int destinationButton(TransportExecutionRegistry.BalloonDestination destination)
	{
		if (destination == null)
		{
			return -1;
		}
		switch (destination)
		{
			case CASTLE_WARS:
				return InterfaceID.ZepBalloonMap.BTN_CAST;
			case GRAND_TREE:
				return InterfaceID.ZepBalloonMap.BTN_GNO;
			case CRAFTING_GUILD:
				return InterfaceID.ZepBalloonMap.BTN_CRAFT;
			case ENTRANA:
				return InterfaceID.ZepBalloonMap.BTN_ENT;
			case TAVERLEY:
				return InterfaceID.ZepBalloonMap.BTN_TAV;
			case VARROCK:
				return InterfaceID.ZepBalloonMap.BTN_VARR;
			default:
				return -1;
		}
	}

	private static boolean isMapVisible()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.ZepBalloonMap.ROOT_RECT0);
	}
}
