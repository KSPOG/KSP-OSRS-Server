package dev.ksp.osrs.protocol;

/** Source-level progression contract; does not simulate a client or NPC combat. */
public final class TutorialSequenceTest {
    public static void main(String[] args) {
        var required = TutorialIslandMilestones.ORDERED;
        int stage = 0;
        if (TutorialIslandMilestones.apply(stage, TutorialIslandMilestones.Validation.RAT_KILLED_MELEE) != 0)
            throw new AssertionError("Cannot bypass guide with rat kill");
        if (TutorialIslandMilestones.complete(stage)) throw new AssertionError("Not complete at spawn");
        for (var event : required) {
            int next = TutorialIslandMilestones.apply(stage,event);
            if (next != stage + 1) throw new AssertionError(event + " did not advance");
            stage = next;
        }
        if (!TutorialIslandMilestones.complete(stage)) throw new AssertionError("Not complete after all checks");
        if (TutorialIslandMilestones.apply(stage,required.get(0)) != stage)
            throw new AssertionError("Completed quest must not replay");
        System.out.println("KSP_TUTORIAL_MILESTONES_TEST_PASS count=" + required.size());
    }
}
