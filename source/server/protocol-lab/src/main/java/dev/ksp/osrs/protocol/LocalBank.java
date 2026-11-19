package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.function.IntFunction;
import net.runelite.cache.definitions.ItemDefinition;
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject;
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull;

/** Gameplay containers are saved together so transfers survive logout and world hops. */
final class LocalBank {
    static final int INVENTORY_SIZE = 28;
    static final int CAPACITY = 800;
    static final int ALL_BUT_ONE = -1;
    private static final int COINS = 995;
    private static final int EQUIPMENT_SIZE = 14;
    static final int WEAPON_SLOT = 3;
    private final Path file;
    private final Path lockFile;
    private final boolean starterCoins;
    private State displayed;
    private long lastKnownModification = Long.MIN_VALUE;

    LocalBank(String username) throws IOException {
        this(username,true);
    }

    LocalBank(String username, boolean starterCoins) throws IOException {
        this(Path.of(System.getenv().getOrDefault("KSP_PLAYER_DATA_DIR", "data"), "gameplay"), username, starterCoins);
    }

    LocalBank(Path directory, String username, boolean starterCoins) throws IOException {
        this.starterCoins = starterCoins;
        if (!username.matches("[A-Za-z0-9_]{1,12}")) throw new IOException("Invalid gameplay profile name");
        Files.createDirectories(directory);
        file = directory.resolve(username + ".properties");
        lockFile = directory.resolve(username + ".lock");
        refresh();
    }

    void refresh() throws IOException {
        displayed = load();
        lastKnownModification = modificationStamp();
    }

    /** Admin updates are written atomically; synchronize them with an active client. */
    boolean refreshIfModified() throws IOException {
        long stamp = modificationStamp();
        if (stamp == lastKnownModification) return false;
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            refresh();
        }
        return true;
    }

    private long modificationStamp() throws IOException {
        return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).to(java.util.concurrent.TimeUnit.NANOSECONDS) : -1;
    }

    UpdateInvFull packet(boolean bank) {
        return packet(bank ? 95 : 93, bank ? displayed.bank : displayed.inventory);
    }

    UpdateInvFull equipmentPacket() {
        return packet(94, displayed.equipment);
    }

    private static UpdateInvFull packet(int container, Item[] items) {
        int length = items.length;
        while (length > 0 && items[length - 1] == null) length--;
        return new UpdateInvFull(-1, container, length, slot ->
            items[slot] == null ? InventoryObject.NULL : InventoryObject.pack(items[slot].id, items[slot].count));
    }

    static final int SHIELD_SLOT = 5;
    static final int AMMO_SLOT = 13;
    static final int TUTORIAL_SHORTBOW = 841;

    int equippedWeapon() {
        return equippedItem(WEAPON_SLOT);
    }

    int equippedItem(int wornSlot) {
        Item item = displayed.equipment[wornSlot];
        return item == null ? -1 : item.id;
    }

    static boolean equipmentSlot(int slot) {
        return switch (slot) {
            case 0, 1, 2, WEAPON_SLOT, 4, SHIELD_SLOT, 7, 9, 10, 12, AMMO_SLOT -> true;
            default -> false;
        };
    }

    boolean equip(int slot, ItemDefinition definition, IntFunction<ItemDefinition> definitions) throws IOException {
        int wornSlot = definition.wearPos1;
        if (slot < 0 || slot >= INVENTORY_SIZE || !equipmentSlot(wornSlot)) return false;

        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            State state = load();
            Item item = state.inventory[slot];
            boolean stackable = definition.stackable == 1;
            if (item == null || item.id != definition.id || !item.equals(displayed.inventory[slot])
                    || !stackable && item.count != 1) return false;

            Item previous = state.equipment[wornSlot];
            if (stackable && previous != null && previous.id == item.id) {
                if (Integer.MAX_VALUE - previous.count < item.count) return false;
                state.inventory[slot] = null;
                state.equipment[wornSlot] = new Item(item.id, previous.count + item.count);
            } else {
                state.inventory[slot] = previous;
                state.equipment[wornSlot] = item;
            }

            int conflictSlot = conflictingEquipmentSlot(state, definition, definitions);
            if (conflictSlot != -1) {
                Item conflict = state.equipment[conflictSlot];
                var conflictDefinition = definitions.apply(conflict.id);
                int destination = conflictDefinition != null && conflictDefinition.stackable == 1
                    ? stackSlot(state.inventory, conflict.id) : firstEmptySlot(state.inventory);
                if (destination == -1) return false;
                Item stack = state.inventory[destination];
                if (stack != null && Integer.MAX_VALUE - stack.count < conflict.count) return false;
                state.inventory[destination] = stack == null ? conflict : new Item(conflict.id, stack.count + conflict.count);
                state.equipment[conflictSlot] = null;
            }

            save(state);
            displayed = state;
            lastKnownModification = modificationStamp();
            return true;
        }
    }

    private static int conflictingEquipmentSlot(State state, ItemDefinition item, IntFunction<ItemDefinition> definitions) {
        if (item.wearPos1 == WEAPON_SLOT && occupiesShieldSlot(item) && state.equipment[SHIELD_SLOT] != null) {
            return SHIELD_SLOT;
        }
        Item weapon = state.equipment[WEAPON_SLOT];
        if (item.wearPos1 == SHIELD_SLOT && weapon != null && occupiesShieldSlot(definitions.apply(weapon.id))) {
            return WEAPON_SLOT;
        }
        return -1;
    }

    private static boolean occupiesShieldSlot(ItemDefinition item) {
        return item != null && (item.wearPos2 == SHIELD_SLOT || item.wearPos3 == SHIELD_SLOT);
    }

    private static int firstEmptySlot(Item[] items) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) return i;
        }
        return -1;
    }

    static final int TUTORIAL_BRONZE_ARROW = 882;
    private static final int TUTORIAL_ARROW_GRANT = 50;

    boolean grantTutorialRangedKit() throws IOException {
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            State state = load();
            if (!ensureTutorialSupply(state, TUTORIAL_SHORTBOW, 1)
                    || !ensureTutorialSupply(state, TUTORIAL_BRONZE_ARROW, TUTORIAL_ARROW_GRANT)) return false;
            save(state);
            displayed = state;
            lastKnownModification = modificationStamp();
            return true;
        }
    }

    private static boolean ensureTutorialSupply(State state, int id, int quantity) {
        long existing = 0;
        for (Item[] container : new Item[][] {state.inventory, state.equipment, state.bank}) {
            for (Item item : container) {
                if (item != null && item.id == id) existing += item.count;
            }
        }
        if (existing >= quantity) return true;
        int slot = stackSlot(state.inventory, id);
        if (slot < 0) return false;
        Item current = state.inventory[slot];
        int missing = quantity - (int) existing;
        state.inventory[slot] = new Item(id, (current == null ? 0 : current.count) + missing);
        return true;
    }

    boolean consumeTutorialArrow() throws IOException {
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            State state = load();
            Item ammo = state.equipment[AMMO_SLOT];
            if (ammo == null || ammo.id != TUTORIAL_BRONZE_ARROW) return false;
            state.equipment[AMMO_SLOT] = ammo.count == 1 ? null : new Item(ammo.id, ammo.count - 1);
            save(state);
            displayed = state;
            lastKnownModification = modificationStamp();
            return true;
        }
    }

    int occupied() {
        int occupied = 0;
        for (Item item : displayed.bank) if (item != null) occupied++;
        return occupied;
    }

    void transfer(boolean withdraw, int slot, int amount, int expectedId, LocalWorld world) throws IOException {
        if (amount <= 0 && !(withdraw && amount == ALL_BUT_ONE) || slot >= (withdraw ? CAPACITY : INVENTORY_SIZE) || slot < -1 || withdraw && slot < 0) return;
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            State state = load();
            if (slot == -1) {
                for (int i = 0; i < INVENTORY_SIZE; i++) move(state, false, i, Integer.MAX_VALUE, world);
            } else {
                Item item = (withdraw ? state.bank : state.inventory)[slot];
                Item previous = (withdraw ? displayed.bank : displayed.inventory)[slot];
                if (item != null && item.equals(previous) && (expectedId < 0 || item.id == expectedId)) {
                    if (withdraw) {
                        move(state, true, slot, amount == ALL_BUT_ONE ? item.count - 1 : amount, world);
                    } else {
                        depositItem(state, slot, item.id, amount, world);
                    }
                }
            }
            save(state);
            displayed = state;
            lastKnownModification = modificationStamp();
        }
    }

    private static void depositItem(State state, int slot, int id, int amount, LocalWorld world) {
        int remaining = amount - move(state, false, slot, amount, world);
        for (int i = 0; i < INVENTORY_SIZE && remaining > 0; i++) {
            if (i != slot && state.inventory[i] != null && state.inventory[i].id == id) {
                remaining -= move(state, false, i, remaining, world);
            }
        }
    }

    private static int move(State state, boolean withdraw, int slot, int amount, LocalWorld world) {
        Item[] source = withdraw ? state.bank : state.inventory;
        Item[] destination = withdraw ? state.inventory : state.bank;
        Item item = source[slot];
        if (item == null || amount <= 0) return 0;
        int count = Math.min(item.count, amount);
        boolean stack = !withdraw || world.stackable(item.id);
        int remaining = count;
        if (stack) {
            int target = stackSlot(destination, item.id);
            if (target >= 0) {
                int previous = destination[target] == null ? 0 : destination[target].count;
                int transferred = Math.min(count, Integer.MAX_VALUE - previous);
                remaining -= transferred;
                destination[target] = new Item(item.id, previous + transferred);
            }
        } else {
            for (int i = 0; i < destination.length && remaining > 0; i++) {
                if (destination[i] == null) {
                    destination[i] = new Item(item.id, 1);
                    remaining--;
                }
            }
        }
        int left = item.count - count + remaining;
        source[slot] = left == 0 ? null : new Item(item.id, left);
        return count - remaining;
    }

    private static int stackSlot(Item[] items, int id) {
        int empty = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                if (empty == -1) empty = i;
            } else if (items[i].id == id) {
                return i;
            }
        }
        return empty;
    }

    boolean tutorialExchange(int[] inputs, int... outputs) throws IOException {
        try (var channel = FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            State state = load();
            for (int id : inputs) {
                int slot = stackSlot(state.inventory,id);
                if (slot < 0 || state.inventory[slot] == null) {
                    // Recover a checkpoint if the item write succeeded but the stage write did not.
                    for (int output : outputs) if (!contains(displayed.inventory,output)) return false;
                    return outputs.length > 0;
                }
                Item item = state.inventory[slot];
                state.inventory[slot] = item.count == 1 ? null : new Item(id,item.count - 1);
            }
            for (int id : outputs) {
                int slot = stackSlot(state.inventory,id);
                if (slot < 0) return false;
                if (state.inventory[slot] == null) state.inventory[slot] = new Item(id,1);
            }
            save(state);
            displayed = state;
            lastKnownModification = modificationStamp();
            return true;
        }
    }

    private static boolean contains(Item[] inventory, int id) {
        int slot = stackSlot(inventory,id);
        return slot >= 0 && inventory[slot] != null;
    }

    boolean hasTutorialItem(int id) {
        return contains(displayed.inventory,id);
    }

    boolean hasTutorialItem(int slot,int id) {
        return slot >= 0 && slot < INVENTORY_SIZE && displayed.inventory[slot] != null
            && displayed.inventory[slot].id == id;
    }

    private State load() throws IOException {
        var state = new State(new Item[CAPACITY], new Item[INVENTORY_SIZE], new Item[EQUIPMENT_SIZE]);
        if (!Files.exists(file)) {
            if (starterCoins) state.inventory[0] = new Item(COINS,25);
            return state;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) { properties.load(input); }
        readContainer(properties, "bank.", state.bank);
        readContainer(properties, "inv.", state.inventory);
        readContainer(properties, "equip.", state.equipment);
        return state;
    }

    private static void readContainer(Properties properties, String prefix, Item[] items) throws IOException {
        for (int i = 0; i < items.length; i++) {
            String value = properties.getProperty(prefix + i);
            if (value == null) continue;
            try {
                String[] fields = value.split(":", -1);
                int id = Integer.parseInt(fields[0]), count = Integer.parseInt(fields[1]);
                if (fields.length != 2 || id < 0 || id >= 65535 || count <= 0) throw new NumberFormatException();
                items[i] = new Item(id, count);
            } catch (NumberFormatException | IndexOutOfBoundsException invalid) {
                throw new IOException("Invalid saved item for " + prefix + i, invalid);
            }
        }
    }

    private void save(State state) throws IOException {
        Properties properties = new Properties();
        writeContainer(properties, "bank.", state.bank);
        writeContainer(properties, "inv.", state.inventory);
        writeContainer(properties, "equip.", state.equipment);
        Path temporary = Files.createTempFile(file.getParent(), "bank-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { properties.store(output, "KSP gameplay inventory"); }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeContainer(Properties properties, String prefix, Item[] items) {
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item != null) properties.setProperty(prefix + i, item.id + ":" + item.count);
        }
    }

    private record Item(int id, int count) {}
    private record State(Item[] bank, Item[] inventory, Item[] equipment) {}
}
