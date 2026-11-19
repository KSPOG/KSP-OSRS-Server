package net.runelite.client.plugins.microbot;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MicrobotQuickInvokeTest {

    private Mouse mouse;
    private Object previousClient;
    private Object previousMouse;

    @Before
    public void setUp() throws Exception {
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        when(client.getCanvasWidth()).thenReturn(800);
        when(client.getCanvasHeight()).thenReturn(600);
        mouse = mock(Mouse.class);
        when(mouse.getMousePosition()).thenReturn(new java.awt.Point(400, 300));
        when(mouse.getLastClick()).thenReturn(new Point(400, 300));
        previousClient = swapStatic("client", client);
        previousMouse = swapStatic("mouse", mouse);
        PointerState.setFromBot(400, 300);
    }

    @After
    public void tearDown() throws Exception {
        swapStatic("client", previousClient);
        swapStatic("mouse", previousMouse);
        PointerState.reset();
    }

    private static NewMenuEntry widgetEntry() {
        return new NewMenuEntry().param0(-1).param1(1).opcode(MenuAction.CC_OP.getId()).identifier(1).option("Activate");
    }

    @Test
    public void aQuickInvokeOnVisibleBoundsTaps() {
        Microbot.doInvokeQuick(widgetEntry(), new Rectangle(500, 200, 30, 30));

        verify(mouse).quickClick(any(), any());
        verify(mouse, never()).click(any(Point.class), any(NewMenuEntry.class));
    }

    @Test
    public void aQuickInvokeWithoutBoundsTaps() {
        Microbot.doInvokeQuick(widgetEntry(), new Rectangle(1, 1));

        verify(mouse).quickClick(any(), any());
        verify(mouse, never()).click(any(Point.class), any(NewMenuEntry.class));
    }

    @Test
    public void aNormalInvokeClicks() {
        Microbot.doInvoke(widgetEntry(), new Rectangle(500, 200, 30, 30));

        verify(mouse).click(any(Point.class), any(NewMenuEntry.class));
        verify(mouse, never()).quickClick(any(), any());
    }

    private static Object swapStatic(String name, Object value) throws Exception {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
