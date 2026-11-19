package dev.ksp.osrs.protocol;

/**
 * Server-side tutorial tab availability. A tab's stone/background is always
 * owned by the viewport; only its subinterface is suppressed here.
 *
 * Never advance unlocks merely because the client clicked a tab. The
 * authoritative tutorial checkpoint must have been persisted first.
 */
final class TutorialTabPolicy {
    static final int COMBAT = 0;
    static final int SKILLS = 1;
    static final int QUESTS = 2;
    static final int INVENTORY = 3;
    static final int EQUIPMENT = 4;
    static final int PRAYER = 5;
    static final int MAGIC = 6;
    static final int SETTINGS = 11;
    private static final int ALL_TABS = (1 << 14) - 1;
    private TutorialTabPolicy() { }

    static int allowedMask(int stage) {
        if (stage < 0) return ALL_TABS; // Finished Tutorial Island / non-tutorial account.
        int mask = 0;
        if (stage != NewPlayerProfile.DESIGN && stage != NewPlayerProfile.EXPERIENCE_SELECTION
                && stage != NewPlayerProfile.GUIDE) mask |= 1 << SETTINGS;
        // Stages are saved historical identifiers, NOT a monotonic sequence.
        switch (stage) {
            case NewPlayerProfile.DESIGN, NewPlayerProfile.EXPERIENCE_SELECTION,
                 NewPlayerProfile.GUIDE, NewPlayerProfile.GUIDE_SETTINGS,
                 NewPlayerProfile.GUIDE_RETURN, NewPlayerProfile.SURVIVAL -> { }
            default -> mask |= 1 << INVENTORY;
        }
        switch (stage) {
            case NewPlayerProfile.VIEW_SKILLS, NewPlayerProfile.SURVIVAL_TOOLS,
                 NewPlayerProfile.CHOP_TREE, NewPlayerProfile.FIRE,
                 NewPlayerProfile.COOK_SHRIMP, NewPlayerProfile.CHEF,
                 NewPlayerProfile.MIX_DOUGH, NewPlayerProfile.BAKE_BREAD,
                 NewPlayerProfile.QUEST_GUIDE, NewPlayerProfile.VIEW_QUESTS,
                 NewPlayerProfile.QUEST_GUIDE_RETURN, NewPlayerProfile.MINING_GUIDE,
                 NewPlayerProfile.MINE_ORES, NewPlayerProfile.SMELT_BRONZE,
                 NewPlayerProfile.COLLECT_HAMMER, NewPlayerProfile.SMITH_DAGGER,
                 NewPlayerProfile.COMBAT_GUIDE -> mask |= 1 << SKILLS;
            default -> { }
        }
        switch (stage) {
            case NewPlayerProfile.VIEW_QUESTS, NewPlayerProfile.QUEST_GUIDE_RETURN,
                 NewPlayerProfile.MINING_GUIDE, NewPlayerProfile.MINE_ORES,
                 NewPlayerProfile.SMELT_BRONZE, NewPlayerProfile.COLLECT_HAMMER,
                 NewPlayerProfile.SMITH_DAGGER, NewPlayerProfile.COMBAT_GUIDE -> mask |= 1 << QUESTS;
            default -> { }
        }
        if (NewPlayerProfile.lessonOrder(stage) >= NewPlayerProfile.lessonOrder(NewPlayerProfile.COMBAT_VIEW_EQUIPMENT)) {
            mask |= (1 << SKILLS) | (1 << QUESTS) | (1 << EQUIPMENT);
        }
        if (NewPlayerProfile.lessonOrder(stage) >= NewPlayerProfile.lessonOrder(NewPlayerProfile.COMBAT_VIEW_OPTIONS)) {
            mask |= 1 << COMBAT;
        }
        // Prayer, spellbook, and all unrelated tabs remain locked: those lessons
        // are not yet implemented. Do not unlock prematurely.
        return mask;
    }

    static boolean allowed(int mask, int tab) {
        return tab >= 0 && tab < 14 && (mask & (1 << tab)) != 0;
    }
}
