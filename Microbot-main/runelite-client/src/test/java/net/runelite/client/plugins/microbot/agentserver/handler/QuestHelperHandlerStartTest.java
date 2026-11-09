package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class QuestHelperHandlerStartTest
{
	private static final Gson GSON = new GsonBuilder().create();

	private MockedStatic<Microbot> microbot;
	private QuestHelperPlugin plugin;
	private PluginManager pluginManager;
	private ConfigManager configManager;
	private HttpExchange exchange;
	private ByteArrayOutputStream responseBody;

	@Before
	public void setUp()
	{
		plugin = mock(QuestHelperPlugin.class);
		pluginManager = mock(PluginManager.class);
		configManager = mock(ConfigManager.class);

		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(() -> Microbot.getPlugin(QuestHelperPlugin.class)).thenReturn(plugin);
		microbot.when(Microbot::getPluginManager).thenReturn(pluginManager);
		microbot.when(Microbot::getConfigManager).thenReturn(configManager);

		responseBody = new ByteArrayOutputStream();
		exchange = mock(HttpExchange.class);
		when(exchange.getRequestMethod()).thenReturn("POST");
		when(exchange.getRequestURI()).thenReturn(URI.create("/quest-helper/start"));
		when(exchange.getRequestHeaders()).thenReturn(new Headers());
		when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(
			"{\"name\":\"A Taste of Hope\"}".getBytes(StandardCharsets.UTF_8)));
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getResponseBody()).thenReturn(responseBody);
	}

	@After
	public void tearDown()
	{
		microbot.close();
	}

	@Test
	public void startReturns409WhenPluginIsNotActive() throws Exception
	{
		when(pluginManager.isActive(plugin)).thenReturn(false);

		new QuestHelperHandler(GSON).handleRequest(exchange);

		verify(plugin, never()).startQuestHelper(anyString());
		verifyNoInteractions(configManager);
		verify(exchange).sendResponseHeaders(eq(409), anyLong());
		assertEquals("{\"error\":\"Quest Helper plugin is not active\"}", responseBody.toString(StandardCharsets.UTF_8));
	}

	@Test
	public void startReturns404WhenHelperIsNotLoaded() throws Exception
	{
		when(pluginManager.isActive(plugin)).thenReturn(true);
		when(plugin.startQuestHelper("A Taste of Hope")).thenReturn(false);

		new QuestHelperHandler(GSON).handleRequest(exchange);

		verifyNoInteractions(configManager);
		verify(exchange).sendResponseHeaders(eq(404), anyLong());
	}

	@Test
	public void startTurnsHelperOnWhenPluginIsActive() throws Exception
	{
		when(pluginManager.isActive(plugin)).thenReturn(true);
		when(plugin.startQuestHelper("A Taste of Hope")).thenReturn(true);

		new QuestHelperHandler(GSON).handleRequest(exchange);

		verify(configManager).setRSProfileConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", true);
		verify(exchange).sendResponseHeaders(eq(200), anyLong());
	}
}
