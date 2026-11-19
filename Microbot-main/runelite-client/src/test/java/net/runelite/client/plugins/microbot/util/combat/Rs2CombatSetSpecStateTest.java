package net.runelite.client.plugins.microbot.util.combat;

import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2CombatSetSpecStateTest {

    private static final int SPEC_WIDGET_ID = 10485795;

    private MockedStatic<Microbot> microbot;
    private Client client;
    private Mouse mouse;
    private Widget specWidget;
    private Widget specOrb;

    @Before
    public void setUp() {
        client = mock(Client.class);
        mouse = mock(Mouse.class);
        specWidget = mock(Widget.class);
        specOrb = mock(Widget.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return Optional.ofNullable(callable.call());
        });
        when(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT)).thenReturn(1000);
        when(client.getWidget(WidgetInfo.MINIMAP_SPEC_ORB.getId() + 4)).thenReturn(specOrb);
        when(specOrb.getSpriteId()).thenReturn(0);

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getMouse).thenReturn(mouse);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void returnsFalseWithoutClickingWhenSpecWidgetIsMissing() {
        when(client.getWidget(SPEC_WIDGET_ID)).thenReturn(null);

        assertFalse(Rs2Combat.setSpecState(true, 500));

        verify(mouse, never()).click(any(Rectangle.class));
    }

    @Test
    public void returnsFalseWithoutClickingWhenSpecWidgetIsHidden() {
        when(client.getWidget(SPEC_WIDGET_ID)).thenReturn(specWidget);
        when(specWidget.isHidden()).thenReturn(true);

        assertFalse(Rs2Combat.setSpecState(true, 500));

        verify(mouse, never()).click(any(Rectangle.class));
    }

    @Test
    public void returnsFalseWithoutClickingWhenSpecWidgetHasNoBounds() {
        when(client.getWidget(SPEC_WIDGET_ID)).thenReturn(specWidget);
        when(specWidget.getBounds()).thenReturn(null);

        assertFalse(Rs2Combat.setSpecState(true, 500));

        verify(mouse, never()).click(any(Rectangle.class));
    }

    @Test
    public void clicksBoundsOfTheCheckedWidgetInstance() {
        Rectangle bounds = new Rectangle(10, 20, 30, 40);
        when(client.getWidget(SPEC_WIDGET_ID)).thenReturn(specWidget, (Widget) null);
        when(specWidget.getBounds()).thenReturn(bounds);

        assertTrue(Rs2Combat.setSpecState(true, 500));

        verify(mouse, times(1)).click(bounds);
        verify(client, times(1)).getWidget(SPEC_WIDGET_ID);
    }

    @Test
    public void returnsFalseWithoutClickingWhenEnergyIsTooLow() {
        when(client.getWidget(SPEC_WIDGET_ID)).thenReturn(specWidget);
        when(specWidget.getBounds()).thenReturn(new Rectangle(10, 20, 30, 40));
        when(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT)).thenReturn(200);

        assertFalse(Rs2Combat.setSpecState(true, 500));

        verify(mouse, never()).click(any(Rectangle.class));
    }
}
