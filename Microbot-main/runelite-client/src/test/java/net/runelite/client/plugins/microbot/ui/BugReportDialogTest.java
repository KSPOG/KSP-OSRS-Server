package net.runelite.client.plugins.microbot.ui;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotApi;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BugReportDialogTest
{
	@PluginDescriptor(name = "<html>Zulrah <font color=red>[Beta]</font></html>")
	static class ZulrahPlugin extends Plugin
	{
	}

	@PluginDescriptor(name = "Agility")
	static class AgilityPlugin extends Plugin
	{
	}

	@PluginDescriptor(name = "Quest Helper")
	static class QuestHelperPlugin extends Plugin
	{
	}

	@Test
	public void choicesAreSortedWithoutHtmlAndIncludeTheOpenedPlugin()
	{
		QuestHelperPlugin builtIn = new QuestHelperPlugin();

		List<BugReportDialog.PluginChoice> choices = BugReportDialog.choices(List.of(new ZulrahPlugin(), new AgilityPlugin()), builtIn);

		assertEquals(List.of("Agility", "Quest Helper", "Zulrah [Beta]"),
			choices.stream().map(choice -> choice.label).collect(Collectors.toList()));
		assertEquals(builtIn, choices.get(1).plugin);
	}

	@Test
	public void choicesDoNotRepeatAnInstalledPlugin()
	{
		AgilityPlugin agility = new AgilityPlugin();

		assertEquals(1, BugReportDialog.choices(List.of(agility), agility).size());
	}

	@Test
	public void failureMessagesExplainWhatToDo()
	{
		assertEquals("Couldn't reach Microbot. Check your internet and try again.", message(0, null));
		assertTrue(message(403, null).contains("isn't ready to take reports yet"));
		assertTrue(message(413, null).contains("too large"));
		assertTrue(message(429, "daily-limit").contains("most reports allowed today"));
		assertTrue(message(429, null).contains("Wait a few minutes"));
		assertEquals("This report couldn't be accepted. Untick 'Screenshot of the game' and try again.", message(400, null, true));
		assertEquals("This report couldn't be accepted. Click Copy report and paste it in Discord.", message(400, null, false));
		assertEquals("The report couldn't be sent (error 502). Try again later.", message(502, null));
		assertEquals("The report couldn't be sent. Try again later.", BugReportDialog.failureMessage(new IllegalStateException(), false));
	}

	private static String message(int status, String reason)
	{
		return message(status, reason, false);
	}

	private static String message(int status, String reason, boolean withScreenshot)
	{
		return BugReportDialog.failureMessage(new CompletionException(new MicrobotApi.BugReportRejectedException(status, reason)), withScreenshot);
	}

	@Test
	public void theBugButtonSaysHowManyReportUpdatesWait()
	{
		assertEquals("Report a problem", MicrobotHelpMenu.tooltip(0));
		assertEquals("Report a problem (1 report update)", MicrobotHelpMenu.tooltip(1));
		assertEquals("Report a problem (3 report updates)", MicrobotHelpMenu.tooltip(3));
	}
}
