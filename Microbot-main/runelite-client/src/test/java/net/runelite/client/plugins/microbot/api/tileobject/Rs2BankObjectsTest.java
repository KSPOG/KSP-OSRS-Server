package net.runelite.client.plugins.microbot.api.tileobject;

import java.util.function.Supplier;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2BankObjectsTest {

    private static final int BANK_BOOTH = 10583;
    private static final int GE_BOOTH = 10060;
    private static final WorldPoint PLAYER = new WorldPoint(3185, 3440, 0);

    private static WorldView playerWorldView;

    private MockedStatic<Microbot> microbot;
    private Rs2TileObjectCache cache;

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        WorldView worldView = mock(WorldView.class);
        when(worldView.isTopLevel()).thenReturn(true);
        when(worldView.getId()).thenReturn(-1);
        playerWorldView = worldView;
        when(player.getWorldView()).thenReturn(worldView);
        when(player.getWorldLocation()).thenReturn(PLAYER);
        when(client.getLocalPlayer()).thenReturn(player);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenAnswer(inv -> new Rs2TileObjectQueryable());

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private void scene(Rs2TileObjectModel... objects) {
        when(cache.getStream()).thenAnswer(inv -> Stream.of(objects));
    }

    private static Rs2TileObjectModel booth(int id, TileObjectType type, WorldPoint location, String... actions) {
        Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
        when(model.getId()).thenReturn(id);
        when(model.getTileObjectType()).thenReturn(type);
        when(model.getWorldLocation()).thenReturn(location);
        when(model.getRuneLiteWorldLocation()).thenReturn(location);
        when(model.getWorldView()).thenReturn(playerWorldView);
        when(model.hasActionContaining(anyString())).thenAnswer(inv -> {
            String wanted = ((String) inv.getArgument(0)).toLowerCase();
            for (String action : actions) {
                if (action.toLowerCase().contains(wanted)) {
                    return true;
                }
            }
            return false;
        });
        return model;
    }

    @Test
    public void findBankReturnsTheNearestBoothWithABankAction() {
        Rs2TileObjectModel far = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(6), "Bank", "Collect");
        Rs2TileObjectModel near = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(2), "Bank", "Collect");
        Rs2TileObjectModel closedBooth = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(1));
        scene(far, closedBooth, near);

        assertSame(near, Rs2BankObjects.findBank());
    }

    @Test
    public void findBankIgnoresBoothsInAnotherWorldView() {
        Rs2TileObjectModel onBoat = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(1), "Bank");
        WorldView boat = mock(WorldView.class);
        when(boat.getId()).thenReturn(7);
        when(onBoat.getWorldView()).thenReturn(boat);
        Rs2TileObjectModel ashore = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(5), "Bank");
        scene(onBoat, ashore);

        assertSame(ashore, Rs2BankObjects.findBank());
    }

    @Test
    public void findBankIgnoresBoothsOutsideTheRadius() {
        scene(booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(30), "Bank"));

        assertNull(Rs2BankObjects.findBank(20));
    }

    @Test
    public void findBankSkipsTheCooksGuildBoothWithoutRequirements() {
        Rs2TileObjectModel cooksGuild = booth(BANK_BOOTH, TileObjectType.GAME, new WorldPoint(3147, 3449, 0), "Bank");
        Rs2TileObjectModel fallback = booth(BANK_BOOTH, TileObjectType.GAME, new WorldPoint(3155, 3449, 0), "Bank");
        Player player = Microbot.getClient().getLocalPlayer();
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3146, 3449, 0));
        scene(fallback, cooksGuild);

        try (MockedStatic<Rs2Player> rs2Player = Mockito.mockStatic(Rs2Player.class)) {
            rs2Player.when(Rs2Player::isMember).thenReturn(false);

            assertSame(fallback, Rs2BankObjects.findBank());
        }
    }

    @Test
    public void ordinaryBankTilesNeedNoRequirementChecks() {
        try (MockedStatic<Rs2Player> rs2Player = Mockito.mockStatic(Rs2Player.class)) {
            assertTrue(Rs2BankObjects.isUsableBank(PLAYER));
            rs2Player.verifyNoInteractions();
        }
        assertFalse(Rs2BankObjects.isUsableBank(null));
    }

    @Test
    public void findDepositBoxNeedsADepositAction() {
        Rs2TileObjectModel bankOnly = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(1), "Bank");
        Rs2TileObjectModel depositBox = booth(BANK_BOOTH, TileObjectType.GAME, PLAYER.dx(4), "Deposit");
        scene(bankOnly, depositBox);

        assertSame(depositBox, Rs2BankObjects.findDepositBox());
    }

    @Test
    public void findGrandExchangeBoothSkipsUnreachableBooths() {
        WorldPoint behindCounter = PLAYER.dx(1);
        WorldPoint open = PLAYER.dx(3);
        Rs2TileObjectModel blocked = booth(GE_BOOTH, TileObjectType.WALL, behindCounter);
        Rs2TileObjectModel reachable = booth(GE_BOOTH, TileObjectType.WALL, open);
        scene(blocked, reachable);

        try (MockedStatic<Rs2Tile> tile = Mockito.mockStatic(Rs2Tile.class)) {
            tile.when(() -> Rs2Tile.isTileReachable(behindCounter)).thenReturn(false);
            tile.when(() -> Rs2Tile.isTileReachable(open)).thenReturn(true);

            assertSame(reachable, Rs2BankObjects.findGrandExchangeBooth());
        }
    }
}
