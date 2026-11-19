package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2BankLootingBagViewTest {

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            return Optional.ofNullable(callable.call());
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private void lootingBagItems(Widget[] children) {
        Widget items = mock(Widget.class);
        when(items.getChildren()).thenReturn(children);
        when(client.getWidget(15, 11)).thenReturn(items);
    }

    @Test
    public void emptyWhenWidgetIsNotLoaded() {
        when(client.getWidget(15, 11)).thenReturn(null);
        assertTrue(Rs2Bank.isLootingBagViewEmpty());
    }

    @Test
    public void emptyWhenChildrenAreNullOrEmpty() {
        lootingBagItems(null);
        assertTrue(Rs2Bank.isLootingBagViewEmpty());
        lootingBagItems(new Widget[0]);
        assertTrue(Rs2Bank.isLootingBagViewEmpty());
        lootingBagItems(new Widget[]{null});
        assertTrue(Rs2Bank.isLootingBagViewEmpty());
    }

    @Test
    public void notEmptyWhileFirstChildIsPresent() {
        lootingBagItems(new Widget[]{mock(Widget.class)});
        assertFalse(Rs2Bank.isLootingBagViewEmpty());
    }
}
