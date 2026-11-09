package net.runelite.client.plugins.microbot.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.plugins.microbot.ui.MicrobotTourPlacement.Result;
import net.runelite.client.plugins.microbot.ui.MicrobotTourPlacement.Side;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MicrobotTourPlacementTest
{
	private static final Rectangle FRAME = new Rectangle(0, 0, 1200, 800);
	private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);
	private static final Dimension SIZE = new Dimension(300, 150);
	private static final int GAP = MicrobotTourPlacement.GAP;

	@Test
	public void sidebarTabGoesLeftOfTheSidebar()
	{
		Rectangle sidebar = new Rectangle(950, 0, 250, 800);
		Rectangle tab = new Rectangle(1170, 300, 26, 26);
		Result result = MicrobotTourPlacement.place(tab, sidebar, SIZE, FRAME, SCREEN, Side.LEFT, GAP);
		assertEquals(Side.LEFT, result.getSide());
		assertEquals(new Point(950 - GAP - 300, 300 + 13 - 75), result.getLocation());
	}

	@Test
	public void menuBarButtonGoesBelowRightAligned()
	{
		Rectangle button = new Rectangle(1150, 2, 23, 23);
		Result result = MicrobotTourPlacement.place(button, null, SIZE, FRAME, SCREEN, Side.BELOW, GAP);
		assertEquals(Side.BELOW, result.getSide());
		assertEquals(new Point(1173 - 300, 25 + GAP), result.getLocation());
	}

	@Test
	public void fallsBackBelowThenAboveThenRight()
	{
		Result below = MicrobotTourPlacement.place(new Rectangle(100, 300, 50, 50), null, SIZE, FRAME, SCREEN, Side.LEFT, GAP);
		assertEquals(Side.BELOW, below.getSide());
		assertEquals(new Point(100, 350 + GAP), below.getLocation());

		Result above = MicrobotTourPlacement.place(new Rectangle(100, 700, 50, 50), null, SIZE, FRAME, SCREEN, Side.LEFT, GAP);
		assertEquals(Side.ABOVE, above.getSide());
		assertEquals(new Point(100, 700 - GAP - 150), above.getLocation());

		Result right = MicrobotTourPlacement.place(new Rectangle(100, 0, 50, 800), null, SIZE, FRAME, SCREEN, Side.LEFT, GAP);
		assertEquals(Side.RIGHT, right.getSide());
		assertEquals(150 + GAP, right.getLocation().x);
	}

	@Test
	public void resultIsClampedIntoTheFrame()
	{
		Rectangle frame = new Rectangle(200, 100, 1000, 700);
		Rectangle[] targets = {
			new Rectangle(1170, 100, 26, 26),
			new Rectangle(1170, 780, 26, 20),
			new Rectangle(200, 100, 30, 30),
			new Rectangle(600, 400, 40, 40),
		};
		for (Rectangle target : targets)
		{
			Result result = MicrobotTourPlacement.place(target, null, SIZE, frame, SCREEN, Side.LEFT, GAP);
			assertTrue(frame.contains(new Rectangle(result.getLocation(), SIZE)));
		}
	}

	@Test
	public void calloutBiggerThanTheFrameIsClampedIntoTheScreen()
	{
		Rectangle frame = new Rectangle(0, 0, 200, 100);
		Result result = MicrobotTourPlacement.place(null, null, SIZE, frame, SCREEN, Side.LEFT, GAP);
		assertEquals(new Point(0, 0), result.getLocation());
		assertTrue(SCREEN.contains(new Rectangle(result.getLocation(), SIZE)));
	}

	@Test
	public void nullTargetIsCentered()
	{
		Result result = MicrobotTourPlacement.place(null, null, SIZE, FRAME, SCREEN, Side.LEFT, GAP);
		assertEquals(Side.CENTER, result.getSide());
		assertEquals(new Point(450, 325), result.getLocation());
	}

	@Test
	public void neverCoversTheTargetWhenASideFits()
	{
		Rectangle sidebar = new Rectangle(950, 0, 250, 800);
		for (int x = 0; x < 1150; x += 97)
		{
			for (int y = 0; y < 760; y += 83)
			{
				Rectangle target = new Rectangle(x, y, 40, 40);
				for (Side preferred : Side.values())
				{
					Result result = MicrobotTourPlacement.place(target, x >= 950 ? sidebar : null, SIZE, FRAME, SCREEN, preferred, GAP);
					Rectangle inflated = new Rectangle(target);
					inflated.grow(3, 3);
					assertFalse(target + " " + preferred, inflated.intersects(new Rectangle(result.getLocation(), SIZE)));
				}
			}
		}
	}
}
