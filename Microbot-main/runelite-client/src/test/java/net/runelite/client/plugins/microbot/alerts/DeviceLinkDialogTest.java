package net.runelite.client.plugins.microbot.alerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Map;
import org.junit.Test;

public class DeviceLinkDialogTest
{
	private static final String URL = "https://dashboard.example.test/link?code=ABCD-EFGH&x=1";

	@Test
	public void turnsTheVerificationUrlIntoALink()
	{
		String html = DeviceLinkDialog.toHtml("Code: ABCD-EFGH\n\nIf no page opened, go to " + URL, URL);

		assertEquals("<html>Code: ABCD-EFGH<br><br>If no page opened, go to "
			+ "<a href=\"https://dashboard.example.test/link?code=ABCD-EFGH&amp;x=1\">"
			+ "https://dashboard.example.test/link?code=ABCD-EFGH&amp;x=1</a></html>", html);
	}

	@Test
	public void linksTheAlertsPageInTheLinkedMessage()
	{
		String html = DeviceLinkDialog.toHtml(DeviceLinkFlow.ALREADY_LINKED, DeviceLinkFlow.ALERTS_PAGE);

		assertTrue(html.contains("<a href=\"" + DeviceLinkFlow.ALERTS_PAGE + "\">" + DeviceLinkFlow.ALERTS_PAGE + "</a>"));
	}

	@Test
	public void escapesTheMessage()
	{
		assertEquals("<html>&lt;b&gt;hi&lt;/b&gt;</html>", DeviceLinkDialog.toHtml("<b>hi</b>", null));
	}

	@Test
	public void closingTheWindowWithoutAValueRunsNoAction()
	{
		Runnable cancel = () -> {};

		assertNull(DeviceLinkDialog.actionFor(Map.of("Cancel linking", cancel), null));
	}

	@Test
	public void returnsTheActionForTheChosenOption()
	{
		Runnable relink = () -> {};

		assertSame(relink, DeviceLinkDialog.actionFor(Map.of("Link to another account", relink), "Link to another account"));
		assertNull(DeviceLinkDialog.actionFor(Map.of("Link to another account", relink), "Close"));
	}
}
