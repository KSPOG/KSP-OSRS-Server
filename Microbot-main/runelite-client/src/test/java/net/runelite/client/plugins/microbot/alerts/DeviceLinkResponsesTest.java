package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class DeviceLinkResponsesTest
{
	private static final Gson GSON = new Gson();
	private static final String DEVICE_CODE = "mbd_" + "d".repeat(43);
	private static final String TOKEN = ClientLinkTokenTest.CONFIG_TOKEN;

	private static String started(String deviceCode, String userCode, String url)
	{
		return "{\"deviceCode\":\"" + deviceCode + "\",\"userCode\":\"" + userCode + "\",\"verificationUrl\":\"" + url
			+ "\",\"expiresIn\":600,\"interval\":5}";
	}

	@Test
	public void startResponseIsParsed() throws IOException
	{
		DeviceLinkStart start = DeviceLinkStart.parse(GSON, 200,
			started(DEVICE_CODE, "TEST-CODE", "https://dashboard.example.test/link?code=TEST-CODE"));

		assertEquals(DEVICE_CODE, start.getDeviceCode());
		assertEquals("TEST-CODE", start.getUserCode());
		assertEquals("https://dashboard.example.test/link?code=TEST-CODE", start.getVerificationUrl());
		assertEquals(600, start.getExpiresIn());
		assertEquals(5, start.getInterval());
		assertFalse(start.toString().contains(DEVICE_CODE));
	}

	@Test
	public void verificationUrlIsNormalizedSoTheBrowserCanOpenIt() throws IOException
	{
		DeviceLinkStart start = DeviceLinkStart.parse(GSON, 200,
			started(DEVICE_CODE, "TEST-CODE", "https://dashboard.example.test/link?code=TEST CODE"));

		assertEquals("https://dashboard.example.test/link?code=TEST%20CODE", start.getVerificationUrl());
		URI.create(start.getVerificationUrl());
	}

	@Test
	public void tooManyPendingRequestsIsReportedSeparately()
	{
		fails(DeviceLinkStart.ThrottledException.class,
			() -> DeviceLinkStart.parse(GSON, 429, "{\"code\":\"too_many_pending\"}"));
	}

	@Test
	public void unexpectedStartResponsesFail()
	{
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 503, ""));
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 200, ""));
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 200, "[1]"));
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 200, "not json"));
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 200,
			started(DEVICE_CODE, "TEST-CODE", "http://dashboard.example.test/link")));
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 200,
			started(DEVICE_CODE, "<b>x</b>", "https://dashboard.example.test/link")));
		fails(IOException.class, () -> DeviceLinkStart.parse(GSON, 200,
			started("mbd_short", "TEST-CODE", "https://dashboard.example.test/link")));
	}

	@Test
	public void pollErrorsMapToStatuses() throws IOException
	{
		assertEquals(DeviceLinkPoll.Status.PENDING, poll400("authorization_pending"));
		assertEquals(DeviceLinkPoll.Status.SLOW_DOWN, poll400("slow_down"));
		assertEquals(DeviceLinkPoll.Status.DENIED, poll400("access_denied"));
		assertEquals(DeviceLinkPoll.Status.EXPIRED, poll400("expired_token"));
		assertEquals(DeviceLinkPoll.Status.TOO_MANY_LINKS, poll400("too_many_links"));
		assertEquals(DeviceLinkPoll.Status.SLOW_DOWN, DeviceLinkPoll.parse(GSON, 429, "").getStatus());
	}

	@Test
	public void approvedPollCarriesTokenAndLabel() throws IOException
	{
		DeviceLinkPoll poll = DeviceLinkPoll.parse(GSON, 200,
			"{\"token\":\"" + TOKEN + "\",\"linkId\":\"00000000-0000-0000-0000-000000000001\",\"label\":\"Desk PC\"}");

		assertEquals(DeviceLinkPoll.Status.LINKED, poll.getStatus());
		assertEquals(TOKEN, poll.getToken());
		assertEquals("Desk PC", poll.getLabel());
		assertFalse(poll.toString().contains(TOKEN));
		assertTrue(poll.toString().contains("LINKED"));
	}

	@Test
	public void unexpectedPollResponsesFail()
	{
		fails(IOException.class, () -> DeviceLinkPoll.parse(GSON, 200, "{\"token\":\"mbc_short\"}"));
		fails(IOException.class, () -> DeviceLinkPoll.parse(GSON, 400, "{\"error\":\"something_new\"}"));
		fails(IOException.class, () -> DeviceLinkPoll.parse(GSON, 400, "{}"));
		fails(IOException.class, () -> DeviceLinkPoll.parse(GSON, 502, "<html></html>"));
	}

	private interface Parse
	{
		void run() throws IOException;
	}

	private static void fails(Class<? extends IOException> expected, Parse parse)
	{
		try
		{
			parse.run();
			fail("Expected " + expected.getSimpleName());
		}
		catch (IOException e)
		{
			assertTrue(expected.isInstance(e));
		}
	}

	private static DeviceLinkPoll.Status poll400(String error) throws IOException
	{
		return DeviceLinkPoll.parse(GSON, 400, "{\"error\":\"" + error + "\"}").getStatus();
	}
}
