package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentHandlerJsonBodyTest {

	private static final class EchoHandler extends AgentHandler {
		EchoHandler() {
			super(new Gson());
		}

		@Override
		public String getPath() {
			return "/echo";
		}

		@Override
		protected void handleRequest(HttpExchange exchange) throws IOException {
			Map<String, Object> body = readJsonBody(exchange);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("name", body.get("name"));
			result.put("size", body.size());
			sendJson(exchange, 200, result);
		}
	}

	private ByteArrayOutputStream responseBody;

	@Before
	public void setUp() {
		AgentHandler.setTokenSupplier(null);
		responseBody = new ByteArrayOutputStream();
	}

	@After
	public void tearDown() {
		AgentHandler.setTokenSupplier(null);
	}

	private HttpExchange exchangeWithBody(String body) {
		HttpExchange exchange = mock(HttpExchange.class);
		Headers requestHeaders = new Headers();
		requestHeaders.add("Host", "127.0.0.1:8081");
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getRequestURI()).thenReturn(URI.create("/echo"));
		when(exchange.getRequestMethod()).thenReturn("POST");
		when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		when(exchange.getResponseBody()).thenReturn(responseBody);
		return exchange;
	}

	private String response() {
		return responseBody.toString(StandardCharsets.UTF_8);
	}

	@Test
	public void emptyBodyIsTreatedAsEmptyObject() throws Exception {
		HttpExchange exchange = exchangeWithBody("");

		new EchoHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertTrue(response().contains("\"size\":0"));
	}

	@Test
	public void jsonNullBodyIsTreatedAsEmptyObject() throws Exception {
		HttpExchange exchange = exchangeWithBody("null");

		new EchoHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertTrue(response().contains("\"size\":0"));
	}

	@Test
	public void validBodyIsParsed() throws Exception {
		HttpExchange exchange = exchangeWithBody("{\"name\":\"Lobster\"}");

		new EchoHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertTrue(response().contains("\"name\":\"Lobster\""));
	}

	@Test
	public void malformedJsonReturnsBadRequest() throws Exception {
		HttpExchange exchange = exchangeWithBody("{\"path\":\"C:\\Users\\bot\\script.java\"}");

		new EchoHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(400), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
		assertEquals("{\"error\":\"Invalid JSON body\"}", response());
	}

	@Test
	public void nonObjectJsonReturnsBadRequest() throws Exception {
		HttpExchange exchange = exchangeWithBody("[1,2,3]");

		new EchoHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(400), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
	}

	@Test
	public void oversizedBodyReturnsBadRequest() throws Exception {
		HttpExchange exchange = exchangeWithBody("{}");
		exchange.getRequestHeaders().add("Content-Length", String.valueOf(1024 * 1024));

		new EchoHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(400), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
	}
}
