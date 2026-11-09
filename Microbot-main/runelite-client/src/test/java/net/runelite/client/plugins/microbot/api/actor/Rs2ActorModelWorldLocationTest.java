package net.runelite.client.plugins.microbot.api.actor;

import java.util.function.Supplier;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2ActorModelWorldLocationTest {

    private MockedStatic<Microbot> microbot;
    private NPC actor;

    @Before
    public void setUp() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        actor = mock(NPC.class);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void nullWorldViewReturnsNullWithoutTouchingActorLocation() {
        when(actor.getWorldView()).thenReturn(null);

        assertNull(new Rs2ActorModel(actor).getWorldLocation());
        verify(actor, never()).getWorldLocation();
    }

    @Test
    public void topLevelWorldViewReturnsActorLocation() {
        WorldView worldView = mock(WorldView.class);
        when(worldView.isTopLevel()).thenReturn(true);
        when(actor.getWorldView()).thenReturn(worldView);
        WorldPoint location = new WorldPoint(3200, 3200, 0);
        when(actor.getWorldLocation()).thenReturn(location);

        assertEquals(location, new Rs2ActorModel(actor).getWorldLocation());
    }
}
