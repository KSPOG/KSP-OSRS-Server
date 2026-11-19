package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo;

final class NewPlayerProfile {
    static final int SPAWN_X = 3094;
    static final int SPAWN_Z = 3107;
    static final int DESIGN = 0;
    static final int GUIDE = 1;
    static final int SURVIVAL = 2;
    static final int CHOP_TREE = 3;
    static final int FIRE = 4;
    static final int FISH = 5;
    static final int COOK_SHRIMP = 6;
    static final int CHEF = 7;
    static final int MIX_DOUGH = 8;
    static final int BAKE_BREAD = 9;
    static final int QUEST_GUIDE = 10;
    static final int MINING_GUIDE = 11;
    static final int MINE_ORES = 12;
    static final int SMELT_BRONZE = 13;
    static final int SMITH_DAGGER = 14;
    static final int COMBAT_GUIDE = 15;
    // Keep persisted checkpoint numbers stable across releases.
    static final int SURVIVAL_TOOLS = 16;
    static final int COLLECT_HAMMER = 17;
    static final int VIEW_INVENTORY = 18;
    static final int VIEW_SKILLS = 19;
    static final int VIEW_QUESTS = 20;
    static final int QUEST_GUIDE_RETURN = 21;
    static final int GUIDE_SETTINGS = 22;
    static final int GUIDE_RETURN = 23;
    static final int EXPERIENCE_SELECTION = 24;
    static final int COMBAT_VIEW_EQUIPMENT = 25;
    static final int COMBAT_VIEW_STATS = 26;
    static final int COMBAT_WIELD_DAGGER = 27;
    static final int COMBAT_GUIDE_RETURN = 28;
    static final int COMBAT_EQUIP_MELEE = 29;
    static final int COMBAT_VIEW_OPTIONS = 30;
    static final int COMBAT_MELEE_RAT = 31;
    static final int COMBAT_RANGED_GUIDE = 32;
    static final int COMBAT_EQUIP_RANGED = 33;
    static final int COMBAT_RANGED_RAT = 34;
    static final int COMBAT_BANK = 35;
    private static final int[] COLOUR_PALETTE_LENGTHS = {12, 16, 16, 6, 8};
    private static final int[] COLOUR_COMPONENTS = {46, 50, 54, 58, 62};
    private final Path file;
    private final int[] kits = new int[7];
    private final int[] colours = new int[5];
    private boolean male = true;
    private int stage;
    // 0 = no answer, 1 = first-time, 2 = returning, 3 = experienced.
    private int experience;
    private int fireX;
    private int fireZ;
    private int x = SPAWN_X;
    private int z = SPAWN_Z;

    NewPlayerProfile(Path data, String username, LocalWorld world) throws IOException {
        Files.createDirectories(data.resolve("tutorial"));
        file = data.resolve("tutorial").resolve(username + ".properties");
        resetKits(world);
        if (!Files.isRegularFile(file)) return;
        var properties = new Properties();
        try (var input = Files.newInputStream(file)) { properties.load(input); }
        stage = parse(properties,"stage",DESIGN,0,COMBAT_BANK);
        experience = parse(properties,"experience",0,0,3);
        x = parse(properties,"x",SPAWN_X,3040,3149);
        z = parse(properties,"z",SPAWN_Z,3070,9544);
        if (!WorldAccess.tutorialTile(x,z)) throw new IOException("Tutorial position is outside the island");
        fireX = parse(properties,"fireX",0,0,3149);
        fireZ = parse(properties,"fireZ",0,0,3149);
        male = Boolean.parseBoolean(properties.getProperty("male","true"));
        resetKits(world);
        for (int slot = 0; slot < kits.length; slot++) {
            int kit = parse(properties,"kit." + slot,kits[slot],-1,65534);
            if (world.kits(slot,male).contains(kit)) kits[slot] = kit;
        }
        for (int slot = 0; slot < colours.length; slot++) {
            colours[slot] = parse(properties,"colour." + slot,0,0,COLOUR_PALETTE_LENGTHS[slot] - 1);
        }
    }

    private static int parse(Properties properties, String key, int fallback, int min, int max) throws IOException {
        String stored = properties.getProperty(key);
        if (stored == null) return fallback;
        try {
            int value = Integer.parseInt(stored);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid tutorial profile field " + key, failure);
        }
    }

    void apply(PlayerAvatarExtendedInfo appearance) {
        appearance.setBodyType(male ? 0 : 1);
        appearance.setPronoun(male ? 0 : 1);
        for (int i = 0; i < kits.length; i++) appearance.setIdentKit(i,kits[i]);
        for (int i = 0; i < colours.length; i++) appearance.setColour(i,colours[i]);
    }

    boolean edit(int component, LocalWorld world) {
        if (component == 68 || component == 69) {
            male = component == 68;
            resetKits(world);
            return true;
        }
        for (int slot = 0; slot < kits.length; slot++) {
            int left = 15 + slot * 4;
            if (component == left || component == left + 1) {
                List<Integer> available = world.kits(slot,male);
                if (!available.isEmpty()) {
                    int index = available.indexOf(kits[slot]);
                    kits[slot] = available.get(Math.floorMod(index + (component == left ? -1 : 1),available.size()));
                }
                return true;
            }
        }
        for (int slot = 0; slot < colours.length; slot++) {
            int left = COLOUR_COMPONENTS[slot];
            if (component == left || component == left + 1) {
                colours[slot] = Math.floorMod(colours[slot] + (component == left ? -1 : 1),COLOUR_PALETTE_LENGTHS[slot]);
                return true;
            }
        }
        return false;
    }

    private void resetKits(LocalWorld world) {
        Arrays.fill(kits,-1);
        // Cache body-part order is head, jaw, torso, arms, hands, legs, feet.
        for (int slot = 0; slot < kits.length; slot++) {
            var available = world.kits(slot,male);
            if (!available.isEmpty()) kits[slot] = available.getFirst();
        }
    }

    void save(int newStage, int newX, int newZ) throws IOException {
        var properties = new Properties();
        properties.setProperty("stage",Integer.toString(newStage));
        properties.setProperty("experience",Integer.toString(experience));
        properties.setProperty("x",Integer.toString(newX));
        properties.setProperty("z",Integer.toString(newZ));
        properties.setProperty("male",Boolean.toString(male));
        int newFireX = stage == FIRE && (newStage == FISH || newStage == COOK_SHRIMP) ? newX : fireX;
        int newFireZ = stage == FIRE && (newStage == FISH || newStage == COOK_SHRIMP) ? newZ : fireZ;
        properties.setProperty("fireX",Integer.toString(newFireX));
        properties.setProperty("fireZ",Integer.toString(newFireZ));
        for (int slot = 0; slot < kits.length; slot++) properties.setProperty("kit." + slot,Integer.toString(kits[slot]));
        for (int slot = 0; slot < colours.length; slot++) properties.setProperty("colour." + slot,Integer.toString(colours[slot]));
        Path temporary = Files.createTempFile(file.getParent(),"profile-",".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { properties.store(output,"KSP tutorial progress"); }
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        stage = newStage;
        fireX = newFireX;
        fireZ = newFireZ;
        x = newX;
        z = newZ;
    }

    static int lessonOrder(int checkpoint) {
        return switch (checkpoint) {
            case EXPERIENCE_SELECTION -> 4;
            case GUIDE_SETTINGS -> 12;
            case GUIDE_RETURN -> 15;
            case VIEW_INVENTORY -> 21;
            case FISH -> 22;
            case VIEW_SKILLS -> 23;
            case SURVIVAL_TOOLS -> 25;
            case VIEW_QUESTS -> 102;
            case QUEST_GUIDE_RETURN -> 105;
            case COLLECT_HAMMER -> 135;
            default -> checkpoint * 10;
        };
    }

    boolean chooseExperience(int option) {
        if (stage != EXPERIENCE_SELECTION || experience != 0 || option < 1 || option > 3) return false;
        experience = option;
        return true;
    }

    int experience() { return experience; }
    int fireX() { return fireX; }
    int fireZ() { return fireZ; }
    int stage() { return stage; }
    int x() { return x; }
    int z() { return z; }
}
