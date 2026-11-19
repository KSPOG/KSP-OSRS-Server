package dev.ksp.osrs.protocol;

public final class TutorialTabPolicyTest {
    private static void assertTab(int stage, int tab, boolean expected) {
        boolean actual = TutorialTabPolicy.allowed(TutorialTabPolicy.allowedMask(stage), tab);
        if (actual != expected) throw new AssertionError("stage="+stage+" tab="+tab+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args) {
        for (int stage = 0; stage <= 24; stage++) {
            assertTab(stage, TutorialTabPolicy.PRAYER, false);
            assertTab(stage, TutorialTabPolicy.MAGIC, false);
        }
        assertTab(NewPlayerProfile.DESIGN, TutorialTabPolicy.SETTINGS,false);
        assertTab(NewPlayerProfile.EXPERIENCE_SELECTION,TutorialTabPolicy.SETTINGS,false);
        assertTab(NewPlayerProfile.GUIDE,TutorialTabPolicy.SETTINGS,false);
        assertTab(NewPlayerProfile.GUIDE_SETTINGS,TutorialTabPolicy.SETTINGS,true);
        assertTab(NewPlayerProfile.GUIDE_SETTINGS,TutorialTabPolicy.INVENTORY,false);
        assertTab(NewPlayerProfile.VIEW_INVENTORY,TutorialTabPolicy.INVENTORY,true);
        assertTab(NewPlayerProfile.VIEW_INVENTORY,TutorialTabPolicy.SKILLS,false);
        assertTab(NewPlayerProfile.FISH,TutorialTabPolicy.SKILLS,false);
        assertTab(NewPlayerProfile.VIEW_SKILLS,TutorialTabPolicy.SKILLS,true);
        assertTab(NewPlayerProfile.QUEST_GUIDE,TutorialTabPolicy.QUESTS,false);
        assertTab(NewPlayerProfile.VIEW_QUESTS,TutorialTabPolicy.QUESTS,true);
        assertTab(NewPlayerProfile.MINING_GUIDE,TutorialTabPolicy.QUESTS,true);
        assertTab(NewPlayerProfile.COMBAT_GUIDE,TutorialTabPolicy.COMBAT,true);
        assertTab(NewPlayerProfile.COMBAT_GUIDE,TutorialTabPolicy.EQUIPMENT,true);
        assertTab(NewPlayerProfile.CHOP_TREE,TutorialTabPolicy.COMBAT,false);
        for (int tab=0;tab<14;tab++) assertTab(-1,tab,true);
        System.out.println("KSP_TUTORIAL_TAB_POLICY_PASS");
    }
}
