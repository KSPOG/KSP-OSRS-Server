package net.runelite.client.plugins.microbot.util.prayer;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2PrayerQuickClickTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Widget> widgets;

    @Before
    public void setUp() {
        microbot = Mockito.mockStatic(Microbot.class);
        widgets = Mockito.mockStatic(Rs2Widget.class);
        Client client = mock(Client.class);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);
        microbot.when(Microbot::getClient).thenReturn(client);
        widgets.when(() -> Rs2Widget.isHidden(anyInt())).thenReturn(true);
    }

    @After
    public void tearDown() {
        widgets.close();
        microbot.close();
    }

    @Test
    public void aPrayerToggleUsesTheQuickTap() {
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE);

        microbot.verify(() -> Microbot.doInvokeQuick(any(), any()));
        microbot.verify(() -> Microbot.doInvoke(any(), any()), never());
    }

    @Test
    public void theQuickPrayerOrbUsesTheQuickTap() {
        microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(1);

        Rs2Prayer.toggleQuickPrayer();

        microbot.verify(() -> Microbot.doInvokeQuick(any(), any()));
        microbot.verify(() -> Microbot.doInvoke(any(), any()), never());
    }
}
