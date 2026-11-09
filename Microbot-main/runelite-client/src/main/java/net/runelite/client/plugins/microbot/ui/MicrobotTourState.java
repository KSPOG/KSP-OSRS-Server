package net.runelite.client.plugins.microbot.ui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MicrobotTourState
{
	static final String CLOUD_BADGE = "cloud";
	static final int MAX_AUTO_SHOWS = 3;
	private static final String TOUR_SEEN_VERSION = "tour.seenVersion";
	private static final String TOUR_AUTO_SHOWN = "tour.autoShown.";

	private final Path file;
	private final Properties properties = new Properties();
	private boolean readFailed;
	private boolean writeWarningLogged;

	MicrobotTourState(Path file)
	{
		this.file = file;
	}

	synchronized void load()
	{
		properties.clear();
		readFailed = false;
		if (!Files.exists(file))
		{
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		catch (NoSuchFileException e)
		{
			properties.clear();
		}
		catch (IOException | IllegalArgumentException e)
		{
			properties.clear();
			readFailed = true;
			log.warn("Could not read the Microbot onboarding state ({})", e.getClass().getSimpleName());
		}
	}

	synchronized void save()
	{
		if (readFailed)
		{
			return;
		}
		Path directory = file.toAbsolutePath().getParent();
		Path temp = null;
		try
		{
			Files.createDirectories(directory);
			temp = Files.createTempFile(directory, "onboarding", ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
			{
				properties.store(writer, null);
			}
			try
			{
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			if (!writeWarningLogged)
			{
				writeWarningLogged = true;
				log.warn("Could not save the Microbot onboarding state ({})", e.getClass().getSimpleName());
			}
			if (temp != null)
			{
				try
				{
					Files.deleteIfExists(temp);
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	synchronized boolean shouldAutoShow(int tourVersion)
	{
		return !readFailed && seenVersion() < tourVersion && autoShown(tourVersion) < MAX_AUTO_SHOWS;
	}

	synchronized void recordAutoShow(int tourVersion)
	{
		properties.setProperty(TOUR_AUTO_SHOWN + tourVersion, Integer.toString(autoShown(tourVersion) + 1));
	}

	synchronized void markTourSeen(int tourVersion)
	{
		if (seenVersion() < tourVersion)
		{
			properties.setProperty(TOUR_SEEN_VERSION, Integer.toString(tourVersion));
		}
	}

	synchronized boolean isBadgeSeen(String id)
	{
		return "true".equalsIgnoreCase(properties.getProperty(badgeKey(id)));
	}

	synchronized void markBadgeSeen(String id)
	{
		properties.setProperty(badgeKey(id), "true");
	}

	boolean isCloudBadgeSeen()
	{
		return isBadgeSeen(CLOUD_BADGE);
	}

	void markCloudBadgeSeen()
	{
		markBadgeSeen(CLOUD_BADGE);
	}

	static boolean cloudBadgeVisible(boolean seen, boolean linked)
	{
		return !seen && !linked;
	}

	private int seenVersion()
	{
		return intProperty(TOUR_SEEN_VERSION);
	}

	private int autoShown(int tourVersion)
	{
		return intProperty(TOUR_AUTO_SHOWN + tourVersion);
	}

	private int intProperty(String key)
	{
		String value = properties.getProperty(key);
		if (value == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static String badgeKey(String id)
	{
		return "badge." + id + ".seen";
	}
}
