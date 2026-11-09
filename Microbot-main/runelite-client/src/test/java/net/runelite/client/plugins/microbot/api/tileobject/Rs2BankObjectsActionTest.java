package net.runelite.client.plugins.microbot.api.tileobject;

import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2BankObjectsActionTest {

    private static final int OBJECT_ID = 10357;

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private Rs2TileObjectModel objectWith(String... actions) {
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(actions);
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(composition);
        TileObject object = mock(TileObject.class);
        when(object.getId()).thenReturn(OBJECT_ID);
        return new Rs2TileObjectModel(object);
    }

    @Test
    public void findActionPrefersAnExactMatch() {
        Rs2TileObjectModel booth = objectWith("Bank", "Collect", "Deposit-box", null, null);

        assertEquals("Bank", booth.findAction("bank"));
        assertEquals("Deposit-box", booth.findAction("Deposit"));
        assertNull(booth.findAction("Use"));
        assertNull(booth.findAction(" "));
    }

    @Test
    public void findActionStripsColourTags() {
        Rs2TileObjectModel altar = objectWith("<col=ff9040>Pray-at</col>", null, null, null, null);

        assertEquals("Pray-at", altar.findAction("Pray"));
    }

    @Test
    public void bankBoothsUseTheirOwnAction() {
        Rs2TileObjectModel booth = objectWith("Bank", "Collect", "Deposit-box", null, null);

        assertEquals("Bank", Rs2BankObjects.actionFor(booth, "Bank"));
        assertEquals("Collect", Rs2BankObjects.actionFor(booth, "Collect"));
        assertEquals("Deposit-box", Rs2BankObjects.actionFor(booth, "Deposit"));
    }

    @Test
    public void bankChestsAreOpenedWithUse() {
        Rs2TileObjectModel chest = objectWith("Use", "Collect", null, null, null);

        assertEquals("Use", Rs2BankObjects.actionFor(chest, "Bank"));
        assertEquals("Collect", Rs2BankObjects.actionFor(chest, "Collect"));
    }

    @Test
    public void anObjectWithNeitherFallsBackToItsFirstOption() {
        Rs2TileObjectModel other = objectWith("Search", null, null, null, null);

        assertEquals("", Rs2BankObjects.actionFor(other, "Bank"));
    }
}
