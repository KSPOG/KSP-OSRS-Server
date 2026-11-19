package net.runelite.client.plugins.microbot.util.widget;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2WidgetInspectorDescribeBoundsTest {

	private MockedStatic<Microbot> microbot;
	private Client client;

	@Before
	public void setUp() throws Exception {
		client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
				Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);
	}

	@After
	public void tearDown() {
		microbot.close();
	}

	@Test
	public void negativeChildIdReturnsEmptyWithoutQueryingClient() {
		when(client.getWidget(anyInt(), anyInt())).thenThrow(new ArrayIndexOutOfBoundsException("Index -1 out of bounds for length 34"));
		assertTrue(Rs2WidgetInspector.describeWidget(161, -1).isEmpty());
		verify(client, never()).getWidget(anyInt(), anyInt());
	}

	@Test
	public void negativeGroupIdReturnsEmptyWithoutQueryingClient() {
		assertTrue(Rs2WidgetInspector.describeWidget(-1, 0).isEmpty());
		verify(client, never()).getWidget(anyInt(), anyInt());
	}

	@Test
	public void childIdAboveRangeReturnsEmpty() {
		when(client.getWidget(161, 99)).thenThrow(new ArrayIndexOutOfBoundsException("Index 99 out of bounds for length 34"));
		assertTrue(Rs2WidgetInspector.describeWidget(161, 99).isEmpty());
	}
}
