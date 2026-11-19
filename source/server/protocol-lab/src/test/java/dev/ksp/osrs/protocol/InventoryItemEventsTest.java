package dev.ksp.osrs.protocol;

public final class InventoryItemEventsTest {
    public static void main(String[] args) {
        var events = LocalGameListener.inventoryItemEvents();
        if (events.getInterfaceId() != 149 || events.getComponentId() != 0) {
            throw new AssertionError("Inventory events must target the regular item container");
        }
        if (events.getStart() != 0 || events.getEnd() != 27) {
            throw new AssertionError("Every inventory slot must receive item events");
        }
        if (events.getEvents1() != 0x213000) {
            throw new AssertionError("Inventory must support Use on NPCs, locations, and eligible widgets");
        }
        if (events.getEvents2() != 0x3ff) {
            throw new AssertionError("The client must be allowed to show its native item operations");
        }
        System.out.println("Inventory item events verified");
    }
}
