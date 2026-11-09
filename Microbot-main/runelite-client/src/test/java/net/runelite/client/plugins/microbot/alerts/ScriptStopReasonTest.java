package net.runelite.client.plugins.microbot.alerts;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ScriptStopReasonTest
{
	private static StackTraceElement frame(String className, String method)
	{
		return new StackTraceElement(className, method, "Source.java", 1);
	}

	private static final StackTraceElement[] SCRIPT_STOP = {
		frame("net.runelite.client.plugins.PluginManager", "stopPlugin"),
		frame("net.runelite.client.plugins.microbot.Microbot", "lambda$togglePlugin$0"),
		frame("java.awt.EventQueue", "dispatchEvent")
	};

	@Test
	public void stopFromTheMicrobotPluginListIsTheUser()
	{
		StackTraceElement[] stack = {
			frame("net.runelite.client.plugins.PluginManager", "stopPlugin"),
			frame("net.runelite.client.plugins.microbot.ui.MicrobotPluginListPanel", "stopPlugin"),
			frame("net.runelite.client.plugins.microbot.ui.MicrobotPluginListItem", "lambda$new$1")
		};
		assertEquals(ScriptStopReason.USER, ScriptStopReason.detect(stack, false));
		assertEquals(ScriptStopReason.USER, ScriptStopReason.detect(stack, true));
	}

	@Test
	public void stopFromTheRuneLitePluginListIsTheUser()
	{
		StackTraceElement[] stack = {
			frame("net.runelite.client.plugins.PluginManager", "stopPlugin"),
			frame("net.runelite.client.plugins.config.PluginListPanel", "stopPlugin")
		};
		assertEquals(ScriptStopReason.USER, ScriptStopReason.detect(stack, false));
	}

	@Test
	public void stopFromTheDashboardIsTheDashboard()
	{
		StackTraceElement[] stack = {
			frame("net.runelite.client.plugins.PluginManager", "stopPlugin"),
			frame("net.runelite.client.plugins.microbot.Microbot", "togglePlugin"),
			frame("net.runelite.client.plugins.microbot.alerts.DashboardControl", "stopRunningScripts"),
			frame("java.awt.EventQueue", "dispatchEvent")
		};
		assertEquals(ScriptStopReason.DASHBOARD, ScriptStopReason.detect(stack, false));
		assertEquals(ScriptStopReason.DASHBOARD, ScriptStopReason.detect(stack, true));
	}

	@Test
	public void stopShortlyAfterAnErrorIsAnError()
	{
		assertEquals(ScriptStopReason.ERROR, ScriptStopReason.detect(SCRIPT_STOP, true));
	}

	@Test
	public void otherStopsAreUnknown()
	{
		assertEquals(ScriptStopReason.UNKNOWN, ScriptStopReason.detect(SCRIPT_STOP, false));
		assertEquals(ScriptStopReason.UNKNOWN, ScriptStopReason.detect(new StackTraceElement[0], false));
	}
}
