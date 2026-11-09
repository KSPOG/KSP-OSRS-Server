package net.runelite.client.plugins.microbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.alerts.ClientLinkToken;
import net.runelite.client.plugins.microbot.diagnostics.BugReportReceipt;
import net.runelite.client.plugins.microbot.diagnostics.BugReportStatus;
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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MicrobotApiBugReportTest
{
	private static final String LINK_TOKEN = "mbc_" + "t".repeat(43);

	private final MockWebServer server = new MockWebServer();
	private ClientLinkToken linkToken;
	private MicrobotApi api;

	@Before
	public void setUp() throws Exception
	{
		server.start();
		linkToken = mock(ClientLinkToken.class);
		when(linkToken.get()).thenReturn(Optional.empty());
		String previous = System.getProperty("microbot.apiUrl");
		System.setProperty("microbot.apiUrl", server.url("/api").toString());
		try
		{
			api = new MicrobotApi(new OkHttpClient(), new Gson(), linkToken);
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
		server.close();
	}

	private static JsonObject payload()
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("description", "stuck");
		return payload;
	}

	@Test
	public void sendsTheReportAndReturnsItsCode() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{\"code\":\"MB-7F3K9Q\",\"followUpToken\":\"secret\"}"));

		BugReportReceipt receipt = api.submitBugReport(payload()).get(5, TimeUnit.SECONDS);
		assertEquals("MB-7F3K9Q", receipt.getCode());
		assertEquals("secret", receipt.getFollowUpToken());

		RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
		assertEquals("/api/plugintelemetry/bug-reports", request.getPath());
		assertEquals("{\"description\":\"stuck\"}", request.getBody().readUtf8());
		assertNull(request.getHeader(ClientLinkToken.HEADER));
	}

	@Test
	public void aLinkedClientSendsItsLinkToken() throws Exception
	{
		when(linkToken.get()).thenReturn(Optional.of(LINK_TOKEN));
		server.enqueue(new MockResponse().setBody("{\"code\":\"MB-7F3K9Q\"}"));

		api.submitBugReport(payload()).get(5, TimeUnit.SECONDS);

		assertEquals(LINK_TOKEN, server.takeRequest(5, TimeUnit.SECONDS).getHeader(ClientLinkToken.HEADER));
	}

	@Test
	public void theDailyLimitIsToldApartFromTheShortRateLimit() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"message\":\"quota\",\"reason\":\"daily-limit\"}"));
		server.enqueue(new MockResponse().setResponseCode(429));

		MicrobotApi.BugReportRejectedException daily = rejection();
		MicrobotApi.BugReportRejectedException burst = rejection();

		assertEquals(429, daily.getStatus());
		assertEquals("daily-limit", daily.getReason());
		assertEquals(429, burst.getStatus());
		assertNull(burst.getReason());
	}

	@Test
	public void aSuccessWithoutACodeOrAnHtmlErrorIsARejection() throws Exception
	{
		server.enqueue(new MockResponse().setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"));

		assertEquals(200, rejection().getStatus());
		assertEquals(502, rejection().getStatus());
	}

	@Test
	public void aDroppedConnectionIsStatusZero() throws Exception
	{
		server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

		assertEquals(0, rejection().getStatus());
	}

	@Test
	public void statusesAreReadForTheGivenCodes() throws Exception
	{
		server.enqueue(new MockResponse().setBody("[{\"code\":\"MB-7F3K9Q\",\"plugin\":\"WebWalkerPlugin\",\"status\":\"fixed\",\"fixedInVersion\":\"2.6.43\",\"fixedInPlugin\":true,\"note\":\"Door check fixed.\"}]"));

		List<BugReportStatus> statuses = api.getBugReportStatuses(Map.of("MB-7F3K9Q", "secret")).get(5, TimeUnit.SECONDS);

		assertEquals(1, statuses.size());
		assertEquals("fixed", statuses.get(0).getStatus());
		assertEquals("2.6.43", statuses.get(0).getFixedInVersion());
		assertTrue(statuses.get(0).isFixedInPlugin());
		assertEquals("Door check fixed.", statuses.get(0).getNote());
		RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
		assertEquals("/api/plugintelemetry/bug-reports/status", request.getPath());
		assertEquals("{\"reports\":[{\"code\":\"MB-7F3K9Q\",\"token\":\"secret\"}]}", request.getBody().readUtf8());
	}

	@Test
	public void aRejectedStatusCheckFails() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(404));

		try
		{
			api.getBugReportStatuses(Map.of("MB-7F3K9Q", "secret")).get(5, TimeUnit.SECONDS);
			fail();
		}
		catch (ExecutionException expected)
		{
		}
	}

	@Test
	public void anAnswerIsPostedWithTheFollowUpToken() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(204));
		server.enqueue(new MockResponse().setResponseCode(409));

		api.replyToBugReport("MB-7F3K9Q", "secret", "The GE bank too.").get(5, TimeUnit.SECONDS);

		RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
		assertEquals("/api/plugintelemetry/bug-reports/MB-7F3K9Q/reply", request.getPath());
		assertEquals("{\"token\":\"secret\",\"message\":\"The GE bank too.\"}", request.getBody().readUtf8());
		try
		{
			api.replyToBugReport("MB-7F3K9Q", "secret", "Again").get(5, TimeUnit.SECONDS);
			fail();
		}
		catch (ExecutionException e)
		{
			assertEquals(409, ((MicrobotApi.BugReportRejectedException) e.getCause()).getStatus());
		}
	}

	private MicrobotApi.BugReportRejectedException rejection() throws Exception
	{
		try
		{
			api.submitBugReport(payload()).get(10, TimeUnit.SECONDS);
		}
		catch (ExecutionException e)
		{
			return (MicrobotApi.BugReportRejectedException) e.getCause();
		}
		throw new AssertionError("expected a rejection");
	}
}
