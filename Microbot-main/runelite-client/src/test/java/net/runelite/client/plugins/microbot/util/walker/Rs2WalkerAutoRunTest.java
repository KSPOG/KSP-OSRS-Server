package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyBoolean;

public class Rs2WalkerAutoRunTest {

	private MockedStatic<Rs2Player> player;
	private boolean previous;

	@Before
	public void setUp() {
		previous = Microbot.enableAutoRunOn;
		player = Mockito.mockStatic(Rs2Player.class);
	}

	@After
	public void tearDown() {
		player.close();
		Microbot.enableAutoRunOn = previous;
	}

	@Test
	public void autoRunIsSkippedWhenOptionIsOff() {
		Microbot.enableAutoRunOn = false;
		Rs2Walker.requestAutoRun();
		player.verify(() -> Rs2Player.toggleRunEnergy(anyBoolean()), Mockito.never());
	}

	@Test
	public void autoRunEnablesRunWhenOptionIsOn() {
		Microbot.enableAutoRunOn = true;
		Rs2Walker.requestAutoRun();
		player.verify(() -> Rs2Player.toggleRunEnergy(true));
	}
}
