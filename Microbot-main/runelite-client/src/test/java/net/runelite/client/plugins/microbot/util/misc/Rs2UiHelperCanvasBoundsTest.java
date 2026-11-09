package net.runelite.client.plugins.microbot.util.misc;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2UiHelperCanvasBoundsTest {

	private Object previousClient;

	@Before
	public void before() throws Exception {
		Client client = mock(Client.class);
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		previousClient = swapStatic("client", client);
		PointerState.reset();
	}

	@After
	public void after() throws Exception {
		swapStatic("client", previousClient);
		PointerState.reset();
	}

	@Test
	public void nullRectangleIsNotWithinCanvas() {
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(null));
	}

	@Test
	public void rectangleHangingOffTheTopLeftIsNotWithinCanvas() {
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(new Rectangle(-20, 100, 50, 50)));
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(new Rectangle(100, -20, 50, 50)));
		assertTrue(Rs2UiHelper.isRectangleWithinCanvas(new Rectangle(0, 0, 800, 600)));
	}

	@Test
	public void clipToCanvasKeepsOnlyTheVisiblePart() {
		assertEquals(new Rectangle(0, 100, 30, 50), Rs2UiHelper.clipToCanvas(new Rectangle(-20, 100, 50, 50)));
		assertEquals(new Rectangle(780, 590, 20, 10), Rs2UiHelper.clipToCanvas(new Rectangle(780, 590, 50, 50)));
		assertNull(Rs2UiHelper.clipToCanvas(new Rectangle(900, 100, 50, 50)));
		assertNull(Rs2UiHelper.clipToCanvas(null));
	}

	@Test
	public void anUnresolvedTargetIsClickedWherePointerRestsNotInTheCorner() {
		PointerState.setFromBot(300, 200);

		assertEquals(new Point(300, 200), Rs2UiHelper.getClickingPoint(null, true));
		assertEquals(new Point(300, 200), Rs2UiHelper.getClickingPoint(new Rectangle(1, 1), true));
		assertEquals(new Point(300, 200), Rs2UiHelper.getClickingPoint(new Rectangle(1, 1, 800, 600), true));
	}

	@Test
	public void aTargetClippedToTheOriginIsClickedInsideItNotTreatedAsFallback() {
		PointerState.setFromBot(700, 500);
		Rectangle visible = Rs2UiHelper.clipToCanvas(new Rectangle(-30, -30, 60, 60));
		assertEquals(new Rectangle(0, 0, 30, 30), visible);

		for (int i = 0; i < 50; i++) {
			Point point = Rs2UiHelper.getClickingPoint(visible, true, false);
			assertTrue(point.toString(), point.getX() >= 0 && point.getX() <= 30 && point.getY() >= 0 && point.getY() <= 30);
		}
	}

	@Test
	public void theDefaultRectangleIsAlwaysTheFallbackSentinel() {
		for (int i = 0; i < 20; i++) {
			assertTrue(Rs2UiHelper.isFallbackRectangle(Rs2UiHelper.getDefaultRectangle()));
		}
		assertFalse(Rs2UiHelper.isFallbackRectangle(new Rectangle(40, 60, 32, 32)));
		assertTrue(Rs2UiHelper.isFallbackRectangle(null));
	}

	@Test
	public void aPointerParkedOffAnEdgeComesBackInAlongThatEdge() {
		java.util.Set<Integer> xs = new java.util.HashSet<>();
		for (int i = 0; i < 100; i++) {
			PointerState.setFromBot(-1, 250);
			Point fromLeft = Rs2UiHelper.getClickingPoint(null, true);
			assertEquals(250, fromLeft.getY());
			assertTrue(fromLeft.toString(), fromLeft.getX() >= Rs2UiHelper.RESTING_POINT_MARGIN && fromLeft.getX() < 400);
			xs.add(fromLeft.getX());

			PointerState.setFromBot(400, 601);
			Point fromBottom = Rs2UiHelper.getClickingPoint(null, true);
			assertEquals(400, fromBottom.getX());
			assertTrue(fromBottom.toString(), fromBottom.getY() > 300 && fromBottom.getY() <= 600 - 1 - Rs2UiHelper.RESTING_POINT_MARGIN);
		}
		assertTrue("the re-entry depth must vary, not sit on the edge line: " + xs.size(), xs.size() > 10);
	}

	@Test
	public void anUnsetPointerRestsAroundTheCentreNotInACorner() {
		java.util.Set<Point> seen = new java.util.HashSet<>();
		for (int i = 0; i < 100; i++) {
			PointerState.reset();
			Point point = Rs2UiHelper.getClickingPoint(null, true);
			assertTrue(point.toString(), point.getX() >= Rs2UiHelper.RESTING_POINT_MARGIN && point.getX() <= 800 - 1 - Rs2UiHelper.RESTING_POINT_MARGIN);
			assertTrue(point.toString(), point.getY() >= Rs2UiHelper.RESTING_POINT_MARGIN && point.getY() <= 600 - 1 - Rs2UiHelper.RESTING_POINT_MARGIN);
			seen.add(point);
		}
		assertTrue(seen.size() > 50);
	}

	private static Object swapStatic(String name, Object value) throws Exception {
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
