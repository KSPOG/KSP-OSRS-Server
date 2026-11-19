package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.ItemID;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestScriptCoinsRequirementTest
{
	@Test
	public void coinsRequirementIsNeverBoughtOnTheGrandExchange()
	{
		assertTrue(QuestScript.isCoinsRequirement(Collections.singletonList(ItemID.COINS_995)));
	}

	@Test
	public void coinsAmongVariantsCountsAsCoins()
	{
		assertTrue(QuestScript.isCoinsRequirement(Arrays.asList(ItemID.COINS_8890, ItemID.COINS_995)));
	}

	@Test
	public void ordinaryItemIsNotCoins()
	{
		assertFalse(QuestScript.isCoinsRequirement(Collections.singletonList(ItemID.LOGS)));
	}

	@Test
	public void nullIdsAreNotCoins()
	{
		assertFalse(QuestScript.isCoinsRequirement(null));
	}
}
