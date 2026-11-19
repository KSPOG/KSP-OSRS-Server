package net.runelite.client.plugins.microbot.diagnostics;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TrackedBugReport
{
	private String code;
	private String followUpToken;
	private String plugin;
	private String pluginInternalName;
	private String summary;
	private long createdAt;
	private String status = BugReportStatus.OPEN;
	private String fixedInVersion;
	private boolean fixedInPlugin;
	private boolean question;
	private String note;
	private String answer;
	private boolean seen = true;
}
