package net.runelite.client.plugins.microbot.util.events;

import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.Microbot;

final class ClientThreadProbe
{
	static final long VALIDATE_TIMEOUT_MILLIS = 2000;

	private ClientThreadProbe()
	{
	}

	static boolean isVisible(BooleanSupplier onClientThread)
	{
		if (!Microbot.isLoggedIn())
		{
			return false;
		}
		return Microbot.getClientThread()
			.runOnClientThreadOptional(onClientThread::getAsBoolean, VALIDATE_TIMEOUT_MILLIS)
			.orElse(false);
	}
}
