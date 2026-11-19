package net.runelite.client.plugins.microbot.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.plugins.microbot.alerts.ClientAlertReporter;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientLogUploadTest
{
	private static final String STALL = "Client thread did not run task within 10000ms (caller thread Timer-0); further timeouts suppressed for 30s";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final LoggerContext context = new LoggerContext();
	private ScheduledExecutorService executor;
	private ScriptErrorReporter reporter;

	@Before
	public void setUp()
	{
		executor = mock(ScheduledExecutorService.class);
		reporter = reporter(false);
	}

	private ScriptErrorReporter reporter(boolean disableTelemetry)
	{
		ClientSessionManager sessions = mock(ClientSessionManager.class);
		when(sessions.getMicrobotSessionId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
		ScriptErrorReporter reporter = new ScriptErrorReporter(mock(MicrobotApi.class), mock(MicrobotPluginManager.class), executor, sessions, mock(ClientAlertReporter.class), disableTelemetry);
		reporter.setContext(context);
		reporter.start();
		return reporter;
	}

	private void log(Level level, String message)
	{
		reporter.doAppend(new LoggingEvent("x", context.getLogger("net.runelite.client.callback.ClientThread"), level, message, null, null));
	}

	@Test
	public void tailReturnsWholeFileWhenItFitsTheLimit() throws IOException
	{
		File file = folder.newFile("client.log");
		Files.write(file.toPath(), "first line\nsecond line\nthird line\n".getBytes(StandardCharsets.UTF_8));

		assertEquals("first line\nsecond line\nthird line\n", ScriptErrorReporter.tail(file, 1024));
	}

	@Test
	public void tailDropsPartialFirstLine()
		throws IOException
	{
		File file = folder.newFile("client.log");
		Files.write(file.toPath(), "first line\nsecond line\nthird line\n".getBytes(StandardCharsets.UTF_8));

		assertEquals("third line\n", ScriptErrorReporter.tail(file, 12));
	}

	@Test
	public void tailKeepsLineThatStartsExactlyAtTheLimit()
		throws IOException
	{
		File file = folder.newFile("client.log");
		Files.write(file.toPath(), "first line\nsecond line\nthird line\n".getBytes(StandardCharsets.UTF_8));

		assertEquals("third line\n", ScriptErrorReporter.tail(file, 11));
	}

	@Test
	public void scrubLogRemovesPlayerNameWhenNameHasNonBreakingSpace()
	{
		String scrubbed = reporter.scrubLog("login Bob Smith ok\n", "Bob Smith");

		assertTrue(scrubbed.contains("[player]"));
		assertFalse(scrubbed.contains("Bob"));
	}

	@Test
	public void scrubLogRemovesPlayerNameWhenLogHasNonBreakingSpace()
	{
		String scrubbed = reporter.scrubLog("login Bob Smith ok\n", "Bob Smith");

		assertTrue(scrubbed.contains("[player]"));
		assertFalse(scrubbed.contains("Bob"));
	}

	@Test
	public void scrubLogRedactsIpAddresses()
	{
		String scrubbed = reporter.scrubLog("connected to 203.0.113.7 port 43594\n", null);

		assertTrue(scrubbed.contains("[ip]"));
		assertFalse(scrubbed.contains("203.0.113.7"));
	}

	@Test
	public void scrubLogRedactsSessionIds()
	{
		String scrubbed = reporter.scrubLog("session 00000000-0000-0000-0000-000000000001 started\n", null);

		assertTrue(scrubbed.contains("[redacted]"));
		assertFalse(scrubbed.contains("0000-0000"));
	}

	@Test
	public void clientThreadStallSchedulesLogUploadAndIsNotReportedAsError()
	{
		log(Level.WARN, STALL);

		verify(executor).schedule(any(Runnable.class), eq(ScriptErrorReporter.LOG_UPLOAD_DELAY_SECONDS), eq(TimeUnit.SECONDS));
		assertEquals(0, reporter.pendingCount());
	}

	@Test
	public void otherWarningsDoNotScheduleLogUpload()
	{
		log(Level.WARN, "Something else went wrong");

		verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
	}

	@Test
	public void clientThreadStallWithTelemetryDisabledDoesNotScheduleUpload()
	{
		reporter = reporter(true);

		log(Level.WARN, STALL);

		verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
	}
}
