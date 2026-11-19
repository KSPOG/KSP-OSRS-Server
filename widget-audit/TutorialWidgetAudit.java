package dev.ksp.osrs.protocol;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.index.IndexData;

/** Read-only revision-242 cache audit; does not change the game client or cache. */
public final class TutorialWidgetAudit {
    private static final int[] VIEWPORTS = {548,161,164};
    private TutorialWidgetAudit() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: TutorialWidgetAudit <revision-242/cache> <output.tsv>");
        }
        Path cache = Path.of(args[0]);
        Path output = Path.of(args[1]);
        try (var reader = new IndexedCacheReader(cache)) {
            var indexBytes = reader.read(255,3);
            if (indexBytes == null) throw new IOException("Cache index 3 not found");
            var index = new IndexData();
            index.load(CacheMetadata.unpack(indexBytes));
            try (BufferedWriter out = Files.newBufferedWriter(output,StandardCharsets.UTF_8)) {
                out.write("viewport\tcomponent\tparent\ttype\tsprite\tx\ty\twidth\theight\thidden\tclickMask\tname\tactions\tonOp");
                out.newLine();
                for (int viewport : VIEWPORTS) {
                    var archive = Arrays.stream(index.getArchives())
                        .filter(a -> a.getId() == viewport).findFirst()
                        .orElseThrow(() -> new IOException("Cache interface group missing: " + viewport));
                    var contents = reader.read(3,viewport);
                    if (contents == null) throw new IOException("Cache interface payload missing: " + viewport);
                    var files = new ArchiveFiles();
                    for (var entry : archive.getFiles()) files.addFile(new FSFile(entry.getId()));
                    files.loadContents(CacheMetadata.unpack(contents));
                    var loader = new InterfaceLoader().configureForRevision(index.getRevision());
                    for (var entry : files.getFiles()) {
                        InterfaceDefinition def = loader.load((viewport << 16) | entry.getFileId(),entry.getContents());
                        out.write(String.join("\t",
                            Integer.toString(viewport), Integer.toString(entry.getFileId()),
                            Integer.toString(def.parentId), Integer.toString(def.type),
                            Integer.toString(def.spriteId), Integer.toString(def.originalX),
                            Integer.toString(def.originalY), Integer.toString(def.originalWidth),
                            Integer.toString(def.originalHeight), Boolean.toString(def.isHidden),
                            Integer.toString(def.clickMask), clean(def.name),
                            clean(Arrays.toString(def.actions)),
                            Boolean.toString(def.onOpListener != null)));
                        out.newLine();
                    }
                }
            }
        }
        System.out.println("KSP_WIDGET_AUDIT_READY " + output.toAbsolutePath());
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t',' ').replace('\r',' ').replace('\n',' ');
    }
}
