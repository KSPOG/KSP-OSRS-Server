package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WidgetDescribeHandlerTest {

	private static final Gson GSON = new GsonBuilder().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	private static HttpServer server;
	private static int port;

	@BeforeClass
	public static void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(2));
		WidgetDescribeHandler handler = new WidgetDescribeHandler(GSON);
		server.createContext(handler.getPath(), handler);
		server.start();
		port = server.getAddress().getPort();
	}

	@AfterClass
	public static void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	public void negativeChildIdReturns400() throws IOException {
		Response resp = get("/widgets/describe?groupId=161&childId=-1");
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).contains("non-negative"));
	}

	@Test
	public void negativeGroupIdReturns400() throws IOException {
		Response resp = get("/widgets/describe?groupId=-5&childId=0");
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).contains("non-negative"));
	}

	@Test
	public void nonIntegerIdReturns400() throws IOException {
		Response resp = get("/widgets/describe?groupId=abc&childId=0");
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).contains("integers"));
	}

	private Response get(String path) throws IOException {
		URL url = new URL("http://127.0.0.1:" + port + path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setRequestMethod("GET");
		int code = conn.getResponseCode();
		InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
		String json;
		try (Scanner sc = new Scanner(stream, StandardCharsets.UTF_8.name())) {
			json = sc.useDelimiter("\\A").hasNext() ? sc.next() : "{}";
		}
		Map<String, Object> body = GSON.fromJson(json, MAP_TYPE);
		return new Response(code, body);
	}

	private static class Response {
		final int code;
		final Map<String, Object> body;

		Response(int code, Map<String, Object> body) {
			this.code = code;
			this.body = body;
		}
	}
}
