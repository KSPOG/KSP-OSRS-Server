package dev.ksp.osrs.protocol;

import java.nio.file.*;
import java.util.*;

public final class TutorialTabIconsTest {
    public static void main(String[] args) throws Exception {
        var lines = Files.readAllLines(Path.of(args[0]));
        var known = new HashMap<String,Integer>();
        for (var line : lines.subList(1,lines.size())) {
            var cells = line.split("\t", -1);
            known.put(cells[0]+":"+cells[1],Integer.parseInt(cells[4]));
        }
        int mapped = 0;
        for (int root : new int[] {548, 161, 164}) {
            for (int tab=0; tab<14; tab++) {
                int child = TutorialTabIcons.component(root, tab);
                if (child < 0) {
                    if (root != 164 || tab != 3) throw new AssertionError("unexpected absent icon "+root+":"+tab);
                    continue;
                }
                Integer actual = known.get(root+":"+child);
                if (actual == null || actual != TutorialTabIcons.expectedSprite(tab)) {
                    throw new AssertionError("mismatch viewport="+root+" tab="+tab+" child="+child+" actual="+actual);
                }
                mapped++;
            }
        }
        System.out.println("KSP_TUTORIAL_ICON_MAP_VERIFIED mapped="+mapped+" modernInventoryAbsent=1");
    }
}
