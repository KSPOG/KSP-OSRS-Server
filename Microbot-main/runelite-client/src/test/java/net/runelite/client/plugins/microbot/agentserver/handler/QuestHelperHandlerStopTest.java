package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestHelperHandlerStopTest
{
	private static final Gson GSON = new GsonBuilder().create();

	private MockedStatic<Microbot> microbot;
	private ClientThread clientThread;
	private QuestManager questManager;
	private HttpExchange exchange;
	private ByteArrayOutputStream responseBody;
	private final AtomicBoolean onClientThread = new AtomicBoolean();

	@Before
	public void setUp()
	{
		questManager = mock(QuestManager.class);
		QuestHelperPlugin plugin = mock(QuestHelperPlugin.class);
		when(plugin.getQuestManager()).thenReturn(questManager);
		clientThread = mock(ClientThread.class);

		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(() -> Microbot.getPlugin(QuestHelperPlugin.class)).thenReturn(plugin);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);

		responseBody = new ByteArrayOutputStream();
		exchange = mock(HttpExchange.class);
		when(exchange.getRequestMethod()).thenReturn("POST");
		when(exchange.getRequestURI()).thenReturn(URI.create("/quest-helper/stop"));
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getResponseBody()).thenReturn(responseBody);
	}

	@After
	public void tearDown()
	{
		microbot.close();
	}

	@Test
	public void stopShutsDownQuestOnClientThread() throws Exception
	{
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
		{
			Callable<?> callable = inv.getArgument(0);
			onClientThread.set(true);
			try
			{
				return Optional.ofNullable(callable.call());
			}
			finally
			{
				onClientThread.set(false);
			}
		});
		doAnswer(inv ->
		{
			assertTrue("shutDownQuest must run on the client thread", onClientThread.get());
			return null;
		}).when(questManager).shutDownQuest(true);

		new QuestHelperHandler(GSON).handleRequest(exchange);

		verify(questManager).shutDownQuest(true);
		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertTrue(responseBody.toString(StandardCharsets.UTF_8).contains("\"success\":true"));
	}

	@Test
	public void stopReturns500WhenClientThreadCallFails() throws Exception
	{
		when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

		new QuestHelperHandler(GSON).handleRequest(exchange);

		verify(questManager, never()).shutDownQuest(true);
		verify(exchange).sendResponseHeaders(eq(500), anyLong());
		assertEquals("{\"error\":\"Failed to stop quest helper\"}", responseBody.toString(StandardCharsets.UTF_8));
	}
}
