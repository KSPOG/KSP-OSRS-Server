package dev.ksp.osrs.protocol;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import net.runelite.cache.definitions.ItemDefinition;

public final class InventoryEquipmentTest {
    public static void main(String[] args) throws Exception {
        var sword = item(1279, LocalBank.WEAPON_SLOT, -1, false, 2, "Wield");
        var shield = item(1171, LocalBank.SHIELD_SLOT, -1, false, 2, "Wield");
        var bow = item(841, LocalBank.WEAPON_SLOT, LocalBank.SHIELD_SLOT, false, 2, "Wield");
        var darts = item(806, LocalBank.WEAPON_SLOT, -1, true, 2, "Wield");
        var cape = item(1007, 1, -1, false, 3, "Wear");
        var definitions = Map.of(sword.id, sword, shield.id, shield, bow.id, bow, darts.id, darts, cape.id, cape);

        require(LocalGameListener.isEquipmentOperation(sword, 2), "Native Wield should be accepted");
        require(LocalGameListener.isEquipmentOperation(cape, 3), "Wear may use a different operation slot");
        require(!LocalGameListener.isEquipmentOperation(sword, 5), "Drop must never equip the item");
        require(!LocalGameListener.isEquipmentOperation(cape, 2), "Action must match its cache operation");
        var note = item(1280, 3, -1, false, 2, "Wield");
        note.notedTemplate = 799;
        require(!LocalGameListener.isEquipmentOperation(note, 2), "Notes must never be equipped");

        var directory = Files.createTempDirectory("ksp-inventory-equipment-");
        try {
            var profile = directory.resolve("equipment.properties");
            Files.writeString(profile, """
                inv.0=1279:1
                inv.1=1171:1
                inv.2=841:1
                inv.3=806:4
                inv.4=806:7
                inv.5=1007:1
                """);
            var bank = new LocalBank(directory, "equipment", false);
            require(bank.equip(0, sword, definitions::get), "An ordinary sword should equip");
            require(bank.equip(1, shield, definitions::get), "Shield should equip");
            require(bank.equip(2, bow, definitions::get), "Bow should equip and displace shield");
            require(bank.equippedWeapon() == bow.id && bank.equippedItem(LocalBank.SHIELD_SLOT) == -1,
                "Two-handed bow and shield cannot coexist");
            require(bank.hasTutorialItem(2, sword.id), "Previous weapon should occupy the vacated inventory slot");
            require(bank.equip(0, shield, definitions::get), "Equipping a shield must displace the bow");
            require(bank.equippedWeapon() == -1 && bank.hasTutorialItem(0, bow.id), "Bow should return to inventory");
            require(bank.equip(3, darts, definitions::get), "Stackable wielded weapons should equip");
            require(bank.equip(4, darts, definitions::get), "A second stack should merge with equipped darts");
            require(bank.equip(5, cape, definitions::get), "Other wearable slots should be supported");
            var saved = new Properties();
            try (var input = Files.newInputStream(profile)) { saved.load(input); }
            require("806:11".equals(saved.getProperty("equip.3")), "The full dart quantity should persist");
            var reloaded = new LocalBank(directory, "equipment", false);
            require(reloaded.equippedWeapon() == darts.id && reloaded.equippedItem(1) == cape.id,
                "Equipment should survive relog");
            require(!reloaded.equip(0, sword, definitions::get), "A stale item id should not change inventory");

            var full = new StringBuilder("equip.3=1279:1\nequip.5=1171:1\ninv.0=841:1\n");
            for (int slot = 1; slot < LocalBank.INVENTORY_SIZE; slot++) full.append("inv.").append(slot).append("=995:1\n");
            Files.writeString(profile, full);
            reloaded.refresh();
            require(!reloaded.equip(0, bow, definitions::get), "A full inventory must reject a two-slot displacement");
            require(reloaded.equippedWeapon() == sword.id && reloaded.equippedItem(LocalBank.SHIELD_SLOT) == shield.id
                && reloaded.hasTutorialItem(0, bow.id), "Rejected equip must leave containers intact");
            require(Files.readString(profile).contentEquals(full), "Rejected equip must preserve saved data");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("Inventory equipment operations, swaps, stacking and persistence verified");
    }

    private static ItemDefinition item(int id, int slot, int hiddenSlot, boolean stackable, int option, String action) {
        var item = new ItemDefinition(id);
        item.wearPos1 = slot;
        item.wearPos2 = hiddenSlot;
        item.stackable = stackable ? 1 : 0;
        item.interfaceOptions[option - 1] = action;
        return item;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
