package dev.ksp.osrs.protocol;

import java.util.ArrayList;
import java.util.List;
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar;
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatarFactory;

final class LumbridgeNpcs {
    private static final int HANS = 0;
    private static final List<Spawn> SPAWNS = List.of(
        new Spawn(3105, 0, 3218, 3219, "Hans", "Hello! You are in the courtyard of Lumbridge Castle."),
        new Spawn(306, 0, 3230, 3223, "Lumbridge Guide", "Welcome! The castle is just west of here."),
        new Spawn(4626, 0, 3209, 3213, "Cook", "Hello there. Mind the pots in the kitchen!"),
        new Spawn(2897, 2, 3209, 3221, "Banker", "Good day. How can I help you with your bank?"),
        new Spawn(815, 1, 3211, 3222, "Duke Horacio", "Welcome to Lumbridge Castle."),
        new Spawn(6773, 0, 3231, 3224, "Doomsayer", "Keep an eye out for danger signs on your travels."),
        new Spawn(3308, 0, 3093, 3107, "Gielinor Guide", "Welcome to Gielinor. Open your Settings using the wrench icon, then come and talk to me again."),
        new Spawn(8503, 0, 3104, 3095, "Survival Expert", "I can show you how to gather wood, make a fire, and fish."),
        new Spawn(3305, 0, 3076, 3085, "Master Chef", "This kitchen is where you will learn to make bread."),
        new Spawn(3312, 0, 3086, 3122, "Quest Guide", "Open your Quest List to see your adventures."),
        new Spawn(3310, 0, 3127, 3124, "Account Guide", "I can explain how to look after your account."),
        new Spawn(3317, 0, 3102, 3093, "Fishing spot", ""),
        new Spawn(3311, 0, 3081, 9504, "Mining Instructor", "These rocks provide the ores you need to smith bronze."),
        new Spawn(3307, 0, 3104, 9506, "Combat Instructor", "These caverns are used to practise combat."),
        new Spawn(3313, 0, 3105, 9514, "Giant rat", ""),
        new Spawn(3319, 0, 3124, 3107, "Brother Brace", "This chapel is where you learn about Prayer."),
        new Spawn(3309, 0, 3140, 3085, "Magic Instructor", "I teach new adventurers how to cast spells.")
    );
    private final NpcAvatarFactory factory;
    private final List<NpcAvatar> avatars;
    private final Patrol hansPatrol;
    private final Patrol doomsayerPatrol;

    void despawnTutorialRat(int index, NpcAvatar expected) {
        if (spawn(index).id() != 3313 || expected == null || avatars.get(index) != expected) return;
        factory.release(expected);
        avatars.set(index, null);
    }

    void respawnTutorialRat(int index) {
        Spawn npc = spawn(index);
        if (npc.id() != 3313 || avatars.get(index) != null) return;
        avatars.set(index, factory.alloc(index, npc.id(), npc.plane(), npc.x(), npc.z()));
    }

    LumbridgeNpcs(NpcAvatarFactory factory, LocalWorld.Scene scene) {
        this.factory = factory;
        var allocated = new ArrayList<NpcAvatar>();
        for (int index = 0; index < SPAWNS.size(); index++) {
            Spawn npc = SPAWNS.get(index);
            allocated.add(factory.alloc(index, npc.id, npc.plane, npc.x, npc.z));
        }
        avatars = allocated;
        // Route shapes follow the documented roaming areas. Tile paths are approximate.
        hansPatrol = patrol(HANS,scene,List.of(new LocalWorld.Step(3221,3219),new LocalWorld.Step(3221,3223),
            new LocalWorld.Step(3217,3223),new LocalWorld.Step(3217,3219),new LocalWorld.Step(3218,3219)));
        doomsayerPatrol = patrol(5,scene,List.of(new LocalWorld.Step(3232,3224),new LocalWorld.Step(3232,3225),
            new LocalWorld.Step(3231,3225),new LocalWorld.Step(3231,3224)));
    }

    private Patrol patrol(int index, LocalWorld.Scene scene, List<LocalWorld.Step> corners) {
        NpcAvatar npc = avatar(index);
        int x = npc.x(), z = npc.z();
        var steps = new ArrayList<LocalWorld.Step>();
        for (var corner : corners) {
            var leg = scene.route(0,x,z,(tx,tz) -> tx == corner.x() && tz == corner.z());
            if (leg.isEmpty()) return new Patrol(npc,List.of());
            steps.addAll(leg);
            x = corner.x();
            z = corner.z();
        }
        return new Patrol(npc,List.copyOf(steps));
    }

    private static final class Patrol {
        private final NpcAvatar npc;
        private final List<LocalWorld.Step> steps;
        private int next;
        private Patrol(NpcAvatar npc,List<LocalWorld.Step> steps) {
            this.npc = npc;
            this.steps = steps;
        }
        private void advance() {
            if (steps.isEmpty()) return;
            var step = steps.get(next);
            npc.walk(step.x() - npc.x(),step.z() - npc.z());
            next = (next + 1) % steps.size();
        }
    }

    NpcAvatar avatar(int index) {
        return avatars.get(index);
    }

    int size() {
        return avatars.size();
    }

    void tick(java.util.function.IntPredicate talkingTo) {
        if (!talkingTo.test(HANS)) hansPatrol.advance();
        if (!talkingTo.test(5)) doomsayerPatrol.advance();
    }

    Spawn spawn(int index) {
        return SPAWNS.get(index);
    }

    record Spawn(int id, int plane, int x, int z, String name, String greeting) {
        boolean banker() { return id == 2897; }
    }
}
