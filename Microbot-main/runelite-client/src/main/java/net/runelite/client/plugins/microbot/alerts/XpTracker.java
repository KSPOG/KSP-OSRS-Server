package net.runelite.client.plugins.microbot.alerts;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

final class XpTracker
{
	private final Map<Skill, Integer> baseline = new EnumMap<>(Skill.class);
	private long accountHash = -1;
	private int account;

	synchronized long gained(long accountHash, Skill skill, int xp, boolean statsLoaded)
	{
		if (accountHash != this.accountHash)
		{
			baseline.clear();
			this.accountHash = accountHash;
			account++;
		}
		if (xp <= 0)
		{
			if (statsLoaded)
			{
				baseline.putIfAbsent(skill, 0);
			}
			return 0;
		}
		Integer start = baseline.get(skill);
		if (start == null || xp < start)
		{
			baseline.put(skill, xp);
			return 0;
		}
		return xp - start;
	}

	synchronized int account()
	{
		return account;
	}
}
