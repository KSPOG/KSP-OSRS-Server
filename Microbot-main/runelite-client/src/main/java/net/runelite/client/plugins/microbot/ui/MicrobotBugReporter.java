package net.runelite.client.plugins.microbot.ui;

import java.awt.Component;
import java.awt.Window;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.alerts.ClientLinkToken;
import net.runelite.client.plugins.microbot.alerts.DeviceLinkFlow;
import net.runelite.client.plugins.microbot.diagnostics.BugReportCollector;
import net.runelite.client.plugins.microbot.diagnostics.BugReportTracker;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;

@Singleton
public class MicrobotBugReporter
{
	private final BugReportCollector collector;
	private final BugReportTracker tracker;
	private final MicrobotPluginManager microbotPluginManager;
	private final PluginManager pluginManager;
	private final ClientLinkToken clientLinkToken;
	private final Provider<DeviceLinkFlow> deviceLinkFlow;
	private BugReportDialog dialog;
	private MyReportsDialog reportsDialog;

	@Inject
	MicrobotBugReporter(BugReportCollector collector, BugReportTracker tracker, MicrobotPluginManager microbotPluginManager,
		PluginManager pluginManager, ClientLinkToken clientLinkToken, Provider<DeviceLinkFlow> deviceLinkFlow)
	{
		this.collector = collector;
		this.tracker = tracker;
		this.microbotPluginManager = microbotPluginManager;
		this.pluginManager = pluginManager;
		this.clientLinkToken = clientLinkToken;
		this.deviceLinkFlow = deviceLinkFlow;
	}

	JMenuItem menuItem(Component parent, Plugin plugin)
	{
		if (plugin == null)
		{
			return null;
		}
		JMenuItem item = new JMenuItem(BugReportDialog.TITLE + "...");
		item.setToolTipText("Send a bug report for this plugin with your client details.");
		item.addActionListener(e -> open(parent, plugin));
		return item;
	}

	int unseenReports()
	{
		return tracker.unseenCount();
	}

	boolean hasReports()
	{
		return !tracker.reports().isEmpty();
	}

	public void open(Component parent, Plugin plugin)
	{
		if (dialog != null && dialog.isDisplayable() && !dialog.isDone())
		{
			if (plugin != null)
			{
				dialog.select(plugin);
			}
			dialog.toFront();
			return;
		}
		if (dialog != null)
		{
			dialog.dispose();
		}
		List<Plugin> installed = microbotPluginManager.getInstalledPlugins();
		Plugin selected = plugin != null ? plugin : runningPlugin(installed);
		dialog = new BugReportDialog(owner(parent), collector, tracker, this::isLinked, this::canLink, this::linkAccount, installed, selected);
		dialog.setVisible(true);
	}

	void openMyReports(Component parent)
	{
		if (reportsDialog != null && reportsDialog.isDisplayable())
		{
			reportsDialog.toFront();
			return;
		}
		reportsDialog = new MyReportsDialog(owner(parent), tracker, this::isLinked, this::canLink, this::linkAccount);
		reportsDialog.setVisible(true);
	}

	private Plugin runningPlugin(List<Plugin> installed)
	{
		List<Plugin> running = installed.stream().filter(pluginManager::isPluginActive).collect(Collectors.toList());
		return running.size() == 1 ? running.get(0) : null;
	}

	private boolean isLinked()
	{
		return clientLinkToken.get().isPresent();
	}

	private boolean canLink()
	{
		return !clientLinkToken.isTelemetryDisabled() && !clientLinkToken.isFromEnvironment();
	}

	private void linkAccount()
	{
		if (canLink())
		{
			deviceLinkFlow.get().start();
		}
	}

	private static Window owner(Component parent)
	{
		return parent instanceof Window ? (Window) parent : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
	}
}
