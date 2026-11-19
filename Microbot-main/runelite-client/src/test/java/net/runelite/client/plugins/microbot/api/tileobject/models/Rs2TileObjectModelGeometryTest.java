package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2TileObjectModelGeometryTest {

    private static final WorldPoint SOUTH_WEST = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getClient).thenReturn(client);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private static GameObject gameObject(int sizeX, int sizeY) {
        GameObject object = mock(GameObject.class);
        when(object.getWorldLocation()).thenReturn(SOUTH_WEST);
        when(object.sizeX()).thenReturn(sizeX);
        when(object.sizeY()).thenReturn(sizeY);
        return object;
    }

    @Test
    public void gameObjectReportsItsSizeAndArea() {
        Rs2TileObjectModel model = new Rs2TileObjectModel(gameObject(2, 3));

        assertEquals(2, model.sizeX());
        assertEquals(3, model.sizeY());
        WorldArea area = model.getWorldArea();
        assertEquals(SOUTH_WEST, area.toWorldPoint());
        assertEquals(2, area.getWidth());
        assertEquals(3, area.getHeight());
    }

    @Test
    public void wallObjectIsOneTile() {
        WallObject wall = mock(WallObject.class);
        when(wall.getWorldLocation()).thenReturn(SOUTH_WEST);
        Rs2TileObjectModel model = new Rs2TileObjectModel(wall);

        assertEquals(1, model.sizeX());
        assertEquals(1, model.sizeY());
        assertTrue(model.occupies(SOUTH_WEST));
        assertFalse(model.occupies(SOUTH_WEST.dx(1)));
    }

    @Test
    public void occupiesCoversTheWholeFootprint() {
        Rs2TileObjectModel model = new Rs2TileObjectModel(gameObject(2, 2));

        assertTrue(model.occupies(SOUTH_WEST));
        assertTrue(model.occupies(new WorldPoint(3201, 3201, 0)));
        assertFalse(model.occupies(new WorldPoint(3202, 3201, 0)));
        assertFalse(model.occupies(new WorldPoint(3199, 3200, 0)));
        assertFalse(model.occupies(new WorldPoint(3201, 3201, 1)));
        assertFalse(model.occupies(null));
    }

    @Test
    public void wrappingAModelUnwrapsToTheRealObject() {
        GameObject object = gameObject(2, 2);
        Rs2TileObjectModel rewrapped = new Rs2TileObjectModel((net.runelite.api.TileObject) new Rs2TileObjectModel(object));

        assertSame(object, rewrapped.getTileObject());
        assertEquals(TileObjectType.GAME, rewrapped.getTileObjectType());
    }

    @Test
    public void hasActionMatchesExactlyIgnoringCaseAndColourTags() {
        GameObject object = gameObject(1, 1);
        when(object.getId()).thenReturn(10583);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(new String[]{"<col=ff9040>Bank</col>", null, "Collect", null, null});
        when(client.getObjectDefinition(10583)).thenReturn(composition);
        Rs2TileObjectModel model = new Rs2TileObjectModel(object);

        assertTrue(model.hasAction("bank"));
        assertFalse(model.hasAction("Ban"));
        assertTrue(model.hasActionContaining("Ban"));
        assertTrue(model.hasActionContaining("lect"));
        assertFalse(model.hasActionContaining("Deposit"));
        assertFalse(model.hasAction(""));
    }

    @Test
    public void hasActionReadsTheImpostorComposition() {
        GameObject object = gameObject(1, 1);
        when(object.getId()).thenReturn(8550);
        ObjectComposition base = mock(ObjectComposition.class);
        ObjectComposition impostor = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getImpostor()).thenReturn(impostor);
        when(base.getActions()).thenReturn(new String[]{"Inspect", null, null, null, null});
        when(impostor.getActions()).thenReturn(new String[]{"Rake", "Inspect", null, null, null});
        when(client.getObjectDefinition(8550)).thenReturn(base);

        assertTrue(new Rs2TileObjectModel(object).hasAction("Rake"));
    }

    @Test
    public void hasActionIsFalseWhenTheImpostorIsHidden() {
        GameObject object = gameObject(1, 1);
        when(object.getId()).thenReturn(8550);
        ObjectComposition base = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getActions()).thenReturn(new String[]{"Rake", null, null, null, null});
        when(client.getObjectDefinition(8550)).thenReturn(base);

        assertFalse(new Rs2TileObjectModel(object).hasAction("Rake"));
    }

    @Test
    public void compositionOfResolvesTheImpostorUnlessAskedNotTo() {
        ObjectComposition base = mock(ObjectComposition.class);
        ObjectComposition impostor = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(base.getImpostor()).thenReturn(impostor);
        when(client.getObjectDefinition(8550)).thenReturn(base);

        assertSame(impostor, Rs2TileObjectModel.compositionOf(8550));
        assertSame(base, Rs2TileObjectModel.compositionOf(8550, true));
    }

    @Test
    public void compositionOfFallsBackToTheBaseWhenTheImpostorIsHidden() {
        ObjectComposition base = mock(ObjectComposition.class);
        when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(client.getObjectDefinition(8550)).thenReturn(base);

        assertSame(base, Rs2TileObjectModel.compositionOf(8550));
    }

    @Test
    public void hasLineOfSightIsFalseWithoutAPointOrWorldView() {
        GameObject object = gameObject(1, 1);
        Rs2TileObjectModel model = new Rs2TileObjectModel(object);

        assertFalse(model.hasLineOfSight(null));
        assertFalse(model.hasLineOfSight(SOUTH_WEST.dx(2)));
    }

    @Test
    public void hoverDoesNothingWithoutNaturalMouse() {
        boolean saved = net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.naturalMouse;
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.naturalMouse = false;
        try {
            assertFalse(new Rs2TileObjectModel(gameObject(1, 1)).hover());
        } finally {
            net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.naturalMouse = saved;
        }
    }
}
