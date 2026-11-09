package net.runelite.client.plugins.microbot.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.microbot.alerts.BotAlertStatus;
import net.runelite.client.plugins.microbot.alerts.DeviceLinkFlow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MicrobotCloudPanelBadgeTest
{
	@Test
	public void badgeTogglesAndHeaderClickReportsUse() throws Exception
	{
		DeviceLinkFlow flow = mock(DeviceLinkFlow.class);
		when(flow.status()).thenReturn(BotAlertStatus.NOT_LINKED);
		SwingUtilities.invokeAndWait(() ->
		{
			MicrobotCloudPanel panel = new MicrobotCloudPanel(flow);
			assertFalse(panel.isNewBadgeVisible());
			panel.setNewBadgeVisible(true);
			assertTrue(panel.isNewBadgeVisible());

			AtomicInteger used = new AtomicInteger();
			panel.onUsed(used::incrementAndGet);
			JLabel title = findLabel(panel, "Microbot Cloud");
			assertNotNull(title);
			MouseEvent click = new MouseEvent(title, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false);
			for (MouseListener listener : title.getMouseListeners())
			{
				listener.mouseClicked(click);
			}
			assertEquals(1, used.get());

			panel.setNewBadgeVisible(false);
			assertFalse(panel.isNewBadgeVisible());
		});
	}

	@Test
	public void linkingHidesTheBadge() throws Exception
	{
		DeviceLinkFlow flow = mock(DeviceLinkFlow.class);
		when(flow.status()).thenReturn(BotAlertStatus.NOT_LINKED);
		SwingUtilities.invokeAndWait(() ->
		{
			MicrobotCloudPanel panel = new MicrobotCloudPanel(flow);
			panel.setNewBadgeVisible(true);
			AtomicInteger used = new AtomicInteger();
			panel.onUsed(used::incrementAndGet);
			when(flow.status()).thenReturn(BotAlertStatus.LINKED);
			panel.setActive(true);
			panel.setActive(false);
			assertFalse(panel.isNewBadgeVisible());
			assertEquals(1, used.get());
		});
	}

	private static JLabel findLabel(Container container, String text)
	{
		for (Component component : container.getComponents())
		{
			if (component instanceof JLabel && text.equals(((JLabel) component).getText()))
			{
				return (JLabel) component;
			}
			if (component instanceof Container)
			{
				JLabel found = findLabel((Container) component, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
