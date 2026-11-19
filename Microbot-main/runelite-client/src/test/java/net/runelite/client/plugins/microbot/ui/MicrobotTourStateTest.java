package net.runelite.client.plugins.microbot.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MicrobotTourStateTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Path file() throws IOException
	{
		return new File(new File(folder.getRoot(), "microbot"), "onboarding.properties").toPath();
	}

	@Test
	public void missingFileShowsTourAndBadge() throws IOException
	{
		MicrobotTourState state = new MicrobotTourState(file());
		state.load();
		assertTrue(state.shouldAutoShow(1));
		assertFalse(state.isCloudBadgeSeen());
		assertTrue(MicrobotTourState.cloudBadgeVisible(state.isCloudBadgeSeen(), false));
	}

	@Test
	public void saveThenLoadRoundTrips() throws IOException
	{
		Path file = file();
		MicrobotTourState state = new MicrobotTourState(file);
		state.load();
		state.markTourSeen(1);
		state.markCloudBadgeSeen();
		state.save();

		assertTrue(Files.isRegularFile(file));
		try (Stream<Path> files = Files.list(file.getParent()))
		{
			assertEquals(0, files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count());
		}

		MicrobotTourState reloaded = new MicrobotTourState(file);
		reloaded.load();
		assertFalse(reloaded.shouldAutoShow(1));
		assertTrue(reloaded.isCloudBadgeSeen());
	}

	@Test
	public void skippedTourComesBackAtMostThreeTimes() throws IOException
	{
		Path file = file();
		for (int start = 0; start < MicrobotTourState.MAX_AUTO_SHOWS; start++)
		{
			MicrobotTourState state = new MicrobotTourState(file);
			state.load();
			assertTrue(state.shouldAutoShow(1));
			state.recordAutoShow(1);
			state.save();
		}

		MicrobotTourState state = new MicrobotTourState(file);
		state.load();
		assertFalse(state.shouldAutoShow(1));
		assertTrue(state.shouldAutoShow(2));
	}

	@Test
	public void versionedReShow() throws IOException
	{
		Path file = file();
		write(file, "tour.seenVersion=2\n");
		MicrobotTourState newer = new MicrobotTourState(file);
		newer.load();
		assertFalse(newer.shouldAutoShow(1));

		write(file, "tour.seenVersion=1\n");
		MicrobotTourState older = new MicrobotTourState(file);
		older.load();
		assertTrue(older.shouldAutoShow(2));
	}

	@Test
	public void nonNumericVersionCountsAsZero() throws IOException
	{
		Path file = file();
		write(file, "tour.seenVersion=abc\nbadge.cloud.seen=maybe\n");
		MicrobotTourState state = new MicrobotTourState(file);
		state.load();
		assertTrue(state.shouldAutoShow(1));
		assertFalse(state.isCloudBadgeSeen());
	}

	@Test
	public void unreadableFileNeverAutoShows() throws IOException
	{
		Path file = file();
		Files.createDirectories(file);
		MicrobotTourState state = new MicrobotTourState(file);
		state.load();
		assertFalse(state.shouldAutoShow(1));
		state.markTourSeen(1);
		state.save();
		assertTrue(Files.isDirectory(file));
	}

	@Test
	public void linkedUsersNeverSeeTheBadge()
	{
		assertFalse(MicrobotTourState.cloudBadgeVisible(false, true));
		assertFalse(MicrobotTourState.cloudBadgeVisible(true, false));
		assertTrue(MicrobotTourState.cloudBadgeVisible(false, false));
	}

	@Test
	public void markTourSeenNeverLowersTheVersion() throws IOException
	{
		Path file = file();
		write(file, "tour.seenVersion=3\n");
		MicrobotTourState state = new MicrobotTourState(file);
		state.load();
		state.markTourSeen(1);
		assertFalse(state.shouldAutoShow(3));
	}

	private static void write(Path file, String content) throws IOException
	{
		Files.createDirectories(file.getParent());
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
	}
}
