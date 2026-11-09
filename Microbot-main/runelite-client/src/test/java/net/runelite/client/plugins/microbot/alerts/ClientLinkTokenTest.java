package net.runelite.client.plugins.microbot.alerts;

import java.util.Optional;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientLinkTokenTest
{
	static final String ENV_TOKEN = "mbc_" + "e".repeat(43);
	static final String CONFIG_TOKEN = "mbc_" + "c".repeat(43);

	@Test
	public void environmentVariableWinsOverConfig()
	{
		assertEquals(Optional.of(ENV_TOKEN), ClientLinkToken.resolve(ENV_TOKEN, CONFIG_TOKEN));
	}

	@Test
	public void configIsUsedWhenEnvironmentVariableIsMissingOrBlank()
	{
		assertEquals(Optional.of(CONFIG_TOKEN), ClientLinkToken.resolve(null, CONFIG_TOKEN));
		assertEquals(Optional.of(CONFIG_TOKEN), ClientLinkToken.resolve("  ", CONFIG_TOKEN));
	}

	@Test
	public void malformedValuesAreIgnoredAndWhitespaceTrimmed()
	{
		assertEquals(Optional.of(CONFIG_TOKEN), ClientLinkToken.resolve("not-a-token", " " + CONFIG_TOKEN + "\n"));
		assertEquals(Optional.empty(), ClientLinkToken.resolve("mbc_short", "mbc_" + "c".repeat(44)));
		assertEquals(Optional.empty(), ClientLinkToken.resolve(null, null));
	}

	@Test
	public void rejectedTokenStaysOffUntilANewOneIsSet()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken)).thenReturn(CONFIG_TOKEN);
		ClientLinkToken token = new ClientLinkToken(configManager, false);
		assertEquals(Optional.of(CONFIG_TOKEN), token.get());

		assertTrue(token.reject(CONFIG_TOKEN));
		assertFalse(token.reject(CONFIG_TOKEN));
		assertEquals(Optional.empty(), token.get());
		assertEquals(Optional.of(CONFIG_TOKEN), token.configured());

		String replacement = "mbc_" + "r".repeat(43);
		when(configManager.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken)).thenReturn(replacement);
		assertEquals(Optional.of(replacement), token.get());
	}

	@Test
	public void disabledTelemetryTurnsAlertsOff()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken)).thenReturn(CONFIG_TOKEN);
		ClientLinkToken token = new ClientLinkToken(configManager, true);
		assertEquals(Optional.empty(), token.get());
		assertTrue(token.isTelemetryDisabled());
	}
}
