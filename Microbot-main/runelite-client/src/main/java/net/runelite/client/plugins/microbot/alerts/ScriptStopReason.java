package net.runelite.client.plugins.microbot.alerts;

import java.util.Set;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListPanel;

final class ScriptStopReason
{
	static final String USER = "user";
	static final String DASHBOARD = "dashboard";
	static final String ERROR = "error";
	static final String UNKNOWN = "unknown";

	private static final Set<String> USER_STOP_PANELS = Set.of(
		"net.runelite.client.plugins.config.PluginListPanel",
		MicrobotPluginListPanel.class.getName());

	private ScriptStopReason()
	{
	}

	static String detect(StackTraceElement[] stack, boolean erroredRecently)
	{
		for (StackTraceElement frame : stack)
		{
			if (DashboardControl.class.getName().equals(frame.getClassName()))
			{
				return DASHBOARD;
			}
			if ("stopPlugin".equals(frame.getMethodName()) && USER_STOP_PANELS.contains(frame.getClassName()))
			{
				return USER;
			}
		}
		return erroredRecently ? ERROR : UNKNOWN;
	}
}
