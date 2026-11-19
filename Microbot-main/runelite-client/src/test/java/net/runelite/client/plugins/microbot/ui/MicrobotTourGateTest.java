package net.runelite.client.plugins.microbot.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MicrobotTourGateTest
{
	@Test
	public void autoStartsOnlyOnANormalFirstStart()
	{
		assertTrue(MicrobotTour.shouldAutoStart(false, false, null, false, true));
		assertTrue(MicrobotTour.shouldAutoStart(false, false, "true", false, true));
		assertFalse(MicrobotTour.shouldAutoStart(true, false, null, false, true));
		assertFalse(MicrobotTour.shouldAutoStart(false, true, null, false, true));
		assertFalse(MicrobotTour.shouldAutoStart(false, false, "false", false, true));
		assertFalse(MicrobotTour.shouldAutoStart(false, false, "FALSE", false, true));
		assertFalse(MicrobotTour.shouldAutoStart(false, false, null, true, true));
		assertFalse(MicrobotTour.shouldAutoStart(false, false, null, false, false));
	}

	@Test
	public void readyNeedsAShowingSettledFrameWithoutFocus()
	{
		long settled = MicrobotTour.SETTLE_MS;
		assertTrue(MicrobotTour.isReady(true, false, false, true, settled));
		assertFalse(MicrobotTour.isReady(false, false, false, true, settled));
		assertFalse(MicrobotTour.isReady(true, true, false, true, settled));
		assertFalse(MicrobotTour.isReady(true, false, true, true, settled));
		assertFalse(MicrobotTour.isReady(true, false, false, false, settled));
		assertFalse(MicrobotTour.isReady(true, false, false, true, settled - 1));
	}
}
