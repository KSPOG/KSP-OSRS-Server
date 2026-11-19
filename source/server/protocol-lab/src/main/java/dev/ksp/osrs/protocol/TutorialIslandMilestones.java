package dev.ksp.osrs.protocol;

import java.util.List;

/**
 * Reference sequence for the modern OSRS Tutorial Island / Learning the Ropes.
 * This catalog is a compliance target, NOT an implementation of game mechanics.
 * Server progression must only be authorized by the corresponding game event.
 * Source: https://oldschool.runescape.wiki/w/Tutorial_Island
 * Source: https://oldschool.runescape.wiki/w/Learning_the_Ropes
 */
final class TutorialIslandMilestones {
    enum Validation {
        CHARACTER_CONFIRMED,
        EXPERIENCE_SELECTED,
        GUIDE_CONVERSATION_ONE,
        SETTINGS_TAB_OPENED,
        GUIDE_CONVERSATION_TWO,
        STARTING_DOOR_TRAVERSED,
        SURVIVAL_NET_RECEIVED,
        INVENTORY_TAB_OPENED,
        SHRIMP_CAUGHT,
        SKILLS_TAB_OPENED,
        SURVIVAL_TOOLS_RECEIVED,
        LOGS_CHOPPED,
        LOGS_LIT,
        SHRIMP_COOKED,
        CHEF_INGREDIENTS_RECEIVED,
        BREAD_DOUGH_MIXED,
        BREAD_BAKED,
        QUEST_GUIDE_ONE,
        QUEST_TAB_OPENED,
        QUEST_GUIDE_TWO,
        MINE_LADDER_DESCENDED,
        PICKAXE_RECEIVED,
        COPPER_MINED,
        TIN_MINED,
        BRONZE_BAR_SMELTED,
        HAMMER_RECEIVED,
        DAGGER_SMITHED,
        COMBAT_INSTRUCTOR_ONE,
        EQUIPMENT_TAB_OPENED,
        EQUIPMENT_STATS_OPENED,
        DAGGER_EQUIPPED,
        SWORD_SHIELD_RECEIVED,
        SWORD_SHIELD_EQUIPPED,
        COMBAT_TAB_OPENED,
        RAT_KILLED_MELEE,
        BOW_ARROWS_RECEIVED,
        RAT_KILLED_RANGED,
        BANK_BOOTH_OPENED,
        POLL_BOOTH_VIEWED,
        ACCOUNT_GUIDE_ONE,
        ACCOUNT_TAB_OPENED,
        ACCOUNT_GUIDE_TWO,
        PRAYER_INSTRUCTOR_ONE,
        PRAYER_TAB_OPENED,
        PRAYER_INSTRUCTOR_TWO,
        MAGIC_INSTRUCTOR_ONE,
        SPELLBOOK_TAB_OPENED,
        MAGIC_RUNES_RECEIVED,
        CHICKEN_HIT_WIND_STRIKE,
        MAGIC_INSTRUCTOR_TWO,
        IRONMAN_DECISION_CONFIRMED,
        HOME_TELEPORT_CAST,
        LUMBRIDGE_ARRIVED
    }

    static final List<Validation> ORDERED = List.of(Validation.values());

    private TutorialIslandMilestones() {}

    /** Rejects an out-of-order event. A separate combat and action system must supply trusted evidence. */
    static int apply(int completed, Validation event) {
        if (completed < 0 || completed >= ORDERED.size()) return completed;
        // The tin/copper mine events are interchangeable; no other steps are.
        Validation expected = ORDERED.get(completed);
        if (event == expected) return completed + 1;
        if (completed == ORDERED.indexOf(Validation.COPPER_MINED)
            && event == Validation.TIN_MINED) return completed + 1;
        if (completed == ORDERED.indexOf(Validation.TIN_MINED)
            && event == Validation.COPPER_MINED) return completed + 1;
        return completed;
    }

    /** True only after the authoritative server validates every required step. */
    static boolean complete(int completed) {
        return completed == ORDERED.size();
    }
}
