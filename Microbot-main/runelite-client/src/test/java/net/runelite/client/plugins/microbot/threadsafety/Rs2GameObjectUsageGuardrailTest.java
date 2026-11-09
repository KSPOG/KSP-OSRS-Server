package net.runelite.client.plugins.microbot.threadsafety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class Rs2GameObjectUsageGuardrailTest
{
	private static final String MICROBOT = "net/runelite/client/plugins/microbot/";
	private static final byte[] RS2_GAME_OBJECT = (MICROBOT + "util/gameobject/Rs2GameObject").getBytes(StandardCharsets.UTF_8);

	private static final Set<String> ALLOWED = Set.of(
		MICROBOT + "util/gameobject/",
		MICROBOT + "util/tileobject/Rs2TileObjectModel"
	);

	private static final Set<String> PENDING_MIGRATION = Set.of();

	@Test
	public void noNewRs2GameObjectReferences() throws IOException
	{
		Path classes = resolveRepoRoot().resolve("runelite-client/build/classes/java/main");
		Assume.assumeTrue("Compiled main classes not found; run `./gradlew :client:compileJava` first.",
			Files.isDirectory(classes));

		Set<String> referencing = new TreeSet<>();
		try (Stream<Path> files = Files.walk(classes.resolve(MICROBOT)))
		{
			for (Path file : files.filter(f -> f.toString().endsWith(".class")).collect(Collectors.toList()))
			{
				String className = classes.relativize(file).toString().replace('\\', '/').replace(".class", "");
				if (ALLOWED.stream().noneMatch(className::startsWith) && contains(Files.readAllBytes(file), RS2_GAME_OBJECT))
				{
					referencing.add(topLevel(className));
				}
			}
		}

		Set<String> unexpected = new TreeSet<>(referencing);
		unexpected.removeAll(PENDING_MIGRATION);
		assertTrue("Rs2GameObject is deprecated for removal; use Microbot.getRs2TileObjectCache().query() "
			+ "(see api/QUERYABLE_API.md). New references in: " + unexpected, unexpected.isEmpty());

		Set<String> migrated = new TreeSet<>(PENDING_MIGRATION);
		migrated.removeAll(referencing);
		assertTrue("These classes no longer reference Rs2GameObject; remove them from PENDING_MIGRATION: " + migrated,
			migrated.isEmpty());
	}

	private static String topLevel(String className)
	{
		int nested = className.indexOf('$');
		return nested < 0 ? className : className.substring(0, nested);
	}

	private static boolean contains(byte[] haystack, byte[] needle)
	{
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++)
		{
			for (int j = 0; j < needle.length; j++)
			{
				if (haystack[i + j] != needle[j])
				{
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	private static Path resolveRepoRoot()
	{
		Path current = Paths.get("").toAbsolutePath();
		while (current != null)
		{
			if (Files.isDirectory(current.resolve("runelite-api")) && Files.isDirectory(current.resolve("runelite-client")))
			{
				return current;
			}
			current = current.getParent();
		}
		throw new IllegalStateException("Could not find the repository root from " + Paths.get("").toAbsolutePath());
	}
}
