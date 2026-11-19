package net.runelite.client.plugins.microbot.diagnostics;

import lombok.Value;

@Value
public class BugReportReceipt
{
	String code;
	String followUpToken;
}
