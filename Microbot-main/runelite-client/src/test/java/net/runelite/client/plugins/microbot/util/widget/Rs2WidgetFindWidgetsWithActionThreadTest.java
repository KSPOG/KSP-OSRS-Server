package net.runelite.client.plugins.microbot.util.widget;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Rectangle;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2WidgetFindWidgetsWithActionThreadTest {

    private MockedStatic<Microbot> microbot;
    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private ClientThread clientThread;
    private Client client;
    private Mouse mouse;

    @Before
    public void setUp() {
        client = mock(Client.class);
        mouse = mock(Mouse.class);
        clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            onClientThread.set(true);
            try {
                return Optional.ofNullable(callable.call());
            } finally {
                onClientThread.set(false);
            }
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getMouse).thenReturn(mouse);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private Widget[] requireClientThread(Widget[] value) {
        if (!onClientThread.get()) {
            throw new IllegalStateException("must be called on client thread");
        }
        return value;
    }

    @Test
    public void traversesChildrenOnClientThreadAndClicksAfterwards() {
        Widget close = mock(Widget.class);
        when(close.getText()).thenReturn("");
        when(close.getName()).thenReturn("");
        when(close.getActions()).thenReturn(new String[]{"Close"});
        Rectangle bounds = new Rectangle(1, 2, 3, 4);
        when(close.getBounds()).thenReturn(bounds);

        Widget other = mock(Widget.class);
        when(other.getText()).thenReturn("Pay");
        when(other.getName()).thenReturn("");

        Widget root = mock(Widget.class);
        when(root.getChildren()).thenAnswer(inv -> requireClientThread(null));
        when(root.getNestedChildren()).thenAnswer(inv -> requireClientThread(new Widget[]{null, other, close}));
        when(root.getDynamicChildren()).thenAnswer(inv -> requireClientThread(null));
        when(root.getStaticChildren()).thenAnswer(inv -> requireClientThread(null));
        when(client.getWidget(669, 0)).thenReturn(root);

        AtomicBoolean clickedOnClientThread = new AtomicBoolean(true);
        when(mouse.click(any(Rectangle.class))).thenAnswer(inv -> {
            clickedOnClientThread.set(onClientThread.get());
            return mouse;
        });

        Map<Widget, String> result = Rs2Widget.findWidgetsWithAction("Close", 669, true);

        assertEquals(1, result.size());
        assertEquals("close", result.get(close));
        verify(mouse).click(bounds);
        assertFalse(clickedOnClientThread.get());
    }

    @Test
    public void returnsEmptyMapWhenClientThreadReturnsNothing() {
        doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());

        Map<Widget, String> result = Rs2Widget.findWidgetsWithAction("Close", 669, true);

        assertTrue(result.isEmpty());
        microbot.verify(Microbot::getMouse, Mockito.never());
    }
}
