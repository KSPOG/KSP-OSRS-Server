package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class QuestHelperPluginStartQuestHelperTest
{
	@Mock private QuestHelperConfig config;
	@Mock private QuestManager questManager;
	@InjectMocks private QuestHelperPlugin plugin;

	private QuestHelper helper;
	private QuestHelperQuest originalQuest;
	private QuestHelperConfig originalConfig;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
		helper = QuestHelperQuest.COOKS_ASSISTANT.getQuestHelper();
		originalQuest = helper.getQuest();
		originalConfig = helper.getConfig();
	}

	@After
	public void tearDown()
	{
		helper.setQuest(originalQuest);
		helper.setConfig(originalConfig);
	}

	@Test
	public void refusesHelperThatWasNeverInstantiated()
	{
		helper.setQuest(null);
		helper.setConfig(null);

		assertFalse(plugin.startQuestHelper(QuestHelperQuest.COOKS_ASSISTANT.getName()));
		verify(questManager, never()).startUpQuest(any(), anyBoolean());
	}

	@Test
	public void refusesHelperWithoutConfig()
	{
		helper.setQuest(QuestHelperQuest.COOKS_ASSISTANT);
		helper.setConfig(null);

		assertFalse(plugin.startQuestHelper(QuestHelperQuest.COOKS_ASSISTANT.getName()));
		verify(questManager, never()).startUpQuest(any(), anyBoolean());
	}

	@Test
	public void startsInstantiatedHelper()
	{
		helper.setQuest(QuestHelperQuest.COOKS_ASSISTANT);
		helper.setConfig(config);

		assertTrue(plugin.startQuestHelper(QuestHelperQuest.COOKS_ASSISTANT.getName()));
		verify(questManager).startUpQuest(helper, true);
	}

	@Test
	public void refusesUnknownQuestName()
	{
		assertFalse(plugin.startQuestHelper("Not A Real Quest"));
		verify(questManager, never()).startUpQuest(any(), anyBoolean());
	}
}
