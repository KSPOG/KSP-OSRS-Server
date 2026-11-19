package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentHandlerQueryTest {

	public static final class QueryEchoHandler extends AgentHandler {
		public QueryEchoHandler() {
			super(new Gson());
		}

		@Override
		public String getPath() {
			return "/query";
		}

		@Override
		protected void handleRequest(HttpExchange exchange) throws IOException {
			sendJson(exchange, 200, parseQuery(exchange.getRequestURI()));
		}
	}

	private HttpServer server;

	@Before
	public void setUp() throws IOException {
		AgentHandler.setTokenSupplier(null);
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/query", new QueryEchoHandler());
		server.start();
	}

	@After
	public void tearDown() {
		if (server != null) server.stop(0);
		AgentHandler.setTokenSupplier(null);
	}

	@Test
	public void encodedPercentIsDecodedOnce() throws Exception {
		Map<String, String> params = query("q=100%25");
		assertEquals("100%", params.get("q"));
	}

	@Test
	public void encodedDelimitersStayInsideValue() throws Exception {
		Map<String, String> params = query("q=a%26b%3Dc&next=1");
		assertEquals("a&b=c", params.get("q"));
		assertEquals("1", params.get("next"));
		assertEquals(2, params.size());
	}

	@Test
	public void encodedPlusIsKeptWhilePlainPlusIsSpace() throws Exception {
		Map<String, String> params = query("a=1%2B1&b=Iron+ore");
		assertEquals("1+1", params.get("a"));
		assertEquals("Iron ore", params.get("b"));
	}

	@Test
	public void keysAreDecodedAndFlagsHaveEmptyValues() throws Exception {
		Map<String, String> params = query("item%20name=Shark&verbose");
		assertEquals("Shark", params.get("item name"));
		assertEquals("", params.get("verbose"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> query(String rawQuery) throws IOException {
		String response = roundTrip("GET /query?" + rawQuery + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
		assertTrue("expected 200, got: " + response, response.startsWith("HTTP/1.1 200"));
		String body = response.substring(response.indexOf("\r\n\r\n") + 4);
		return new Gson().fromJson(body, Map.class);
	}

	private String roundTrip(String request) throws IOException {
		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getAddress().getPort())) {
			OutputStream out = socket.getOutputStream();
			out.write(request.getBytes(StandardCharsets.ISO_8859_1));
			out.flush();
			InputStream in = socket.getInputStream();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
