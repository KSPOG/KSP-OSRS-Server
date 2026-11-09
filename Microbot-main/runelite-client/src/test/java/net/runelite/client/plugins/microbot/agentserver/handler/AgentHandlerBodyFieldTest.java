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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentHandlerBodyFieldTest {

	private static final class QuantityHandler extends AgentHandler {
		QuantityHandler() {
			super(new Gson());
		}

		@Override
		public String getPath() {
			return "/quantity";
		}

		@Override
		protected void handleRequest(HttpExchange exchange) throws IOException {
			Map<String, Object> body = readJsonBody(exchange);
			Integer quantity = getIntField(body, "quantity");
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("quantity", quantity);
			sendJson(exchange, 200, result);
		}
	}

	private static final class RawCastHandler extends AgentHandler {
		RawCastHandler() {
			super(new Gson());
		}

		@Override
		public String getPath() {
			return "/raw";
		}

		@Override
		protected void handleRequest(HttpExchange exchange) throws IOException {
			Map<String, Object> body = readJsonBody(exchange);
			Number quantity = (Number) body.get("quantity");
			sendJson(exchange, 200, Map.of("quantity", quantity.intValue()));
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
		return exchangeWithBody("/quantity", body);
	}

	private HttpExchange exchangeWithBody(String path, String body) {
		HttpExchange exchange = mock(HttpExchange.class);
		Headers requestHeaders = new Headers();
		requestHeaders.add("Host", "127.0.0.1:8081");
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getRequestURI()).thenReturn(URI.create(path));
		when(exchange.getRequestMethod()).thenReturn("POST");
		when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		when(exchange.getResponseBody()).thenReturn(responseBody);
		return exchange;
	}

	private String response() {
		return responseBody.toString(StandardCharsets.UTF_8);
	}

	@Test
	public void numericQuantityIsAccepted() throws Exception {
		HttpExchange exchange = exchangeWithBody("{\"quantity\":28}");

		new QuantityHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertEquals("{\"quantity\":28}", response());
	}

	@Test
	public void numericStringQuantityIsAccepted() throws Exception {
		HttpExchange exchange = exchangeWithBody("{\"quantity\":\"28\"}");

		new QuantityHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertEquals("{\"quantity\":28}", response());
	}

	@Test
	public void nonNumericQuantityReturnsBadRequest() throws Exception {
		HttpExchange exchange = exchangeWithBody("{\"quantity\":\"all\"}");

		new QuantityHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(400), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
		assertEquals("Field 'quantity' must be a number", new Gson().fromJson(response(), Map.class).get("error"));
	}

	@Test
	public void bankWithdrawWithNonNumericQuantityReturnsBadRequest() throws Exception {
		HttpExchange exchange = exchangeWithBody("/bank/withdraw", "{\"quantity\":\"x\",\"name\":\"y\"}");

		new BankHandler(new Gson()).handle(exchange);

		verify(exchange).sendResponseHeaders(eq(400), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
		assertEquals("Field 'quantity' must be a number", new Gson().fromJson(response(), Map.class).get("error"));
	}

	@Test
	public void internalClassCastExceptionStillReturnsServerError() throws Exception {
		HttpExchange exchange = exchangeWithBody("{\"quantity\":\"28\"}");

		new RawCastHandler().handle(exchange);

		verify(exchange).sendResponseHeaders(eq(500), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(400), anyLong());
	}

	@Test
	public void accessorsReturnNullForMissingFields() {
		Map<String, Object> body = new LinkedHashMap<>();

		assertNull(AgentHandler.getIntField(body, "id"));
		assertNull(AgentHandler.getStringField(body, "name"));
		assertNull(AgentHandler.getBooleanField(body, "all"));
	}

	@Test
	public void stringAndBooleanAccessorsValidateType() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", 995.0);
		body.put("all", "yes");
		body.put("wait", "true");

		try {
			AgentHandler.getStringField(body, "name");
			fail("expected BadRequestException");
		} catch (AgentHandler.BadRequestException e) {
			assertEquals("Field 'name' must be a string", e.getMessage());
		}
		try {
			AgentHandler.getBooleanField(body, "all");
			fail("expected BadRequestException");
		} catch (AgentHandler.BadRequestException e) {
			assertEquals("Field 'all' must be a boolean", e.getMessage());
		}
		assertEquals(Boolean.TRUE, AgentHandler.getBooleanField(body, "wait"));
	}
}
