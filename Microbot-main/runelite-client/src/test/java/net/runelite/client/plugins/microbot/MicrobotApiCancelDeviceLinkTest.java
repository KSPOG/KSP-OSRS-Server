package net.runelite.client.plugins.microbot;

import com.google.gson.Gson;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.alerts.ClientLinkToken;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class MicrobotApiCancelDeviceLinkTest
{
	private static final String DEVICE_CODE = "mbd_" + "d".repeat(43);

	private final MockWebServer server = new MockWebServer();
	private MicrobotApi api;

	@Before
	public void setUp() throws Exception
	{
		server.start();
		String previous = System.getProperty("microbot.apiUrl");
		System.setProperty("microbot.apiUrl", server.url("/api").toString());
		try
		{
			api = new MicrobotApi(new OkHttpClient(), new Gson(), mock(ClientLinkToken.class));
		}
		finally
		{
			if (previous == null)
			{
				System.clearProperty("microbot.apiUrl");
			}
			else
			{
				System.setProperty("microbot.apiUrl", previous);
			}
		}
	}

	@After
	public void tearDown() throws Exception
	{
		server.shutdown();
	}

	@Test
	public void postsTheDeviceCodeAnonymously() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(204));

		api.cancelDeviceLink(DEVICE_CODE).get(5, TimeUnit.SECONDS);

		RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
		assertEquals("POST", request.getMethod());
		assertEquals("/api/alerts/device-links/cancel", request.getPath());
		assertEquals("{\"deviceCode\":\"" + DEVICE_CODE + "\"}", request.getBody().readUtf8());
		assertNull(request.getHeader(ClientLinkToken.HEADER));
	}

	@Test
	public void anEndpointThatIsNotDeployedYetCompletesNormally() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(404));

		api.cancelDeviceLink(DEVICE_CODE).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void aSlowServerGivesUpWithinTheTimeout() throws Exception
	{
		server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
		long started = System.nanoTime();

		CompletableFuture<Void> done = api.cancelDeviceLink(DEVICE_CODE);
		done.get(5, TimeUnit.SECONDS);

		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		assertTrue("took " + elapsedMillis + " ms", elapsedMillis < TimeUnit.SECONDS.toMillis(MicrobotApi.CANCEL_DEVICE_LINK_TIMEOUT_SECONDS) + 1000);
	}
}
