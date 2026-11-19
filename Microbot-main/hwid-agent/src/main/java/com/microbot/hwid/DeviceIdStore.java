package com.microbot.hwid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

final class DeviceIdStore
{
	private static final Logger log = Logger.getLogger(DeviceIdStore.class.getName());

	private static final Properties STORE = new Properties();
	private static volatile boolean loaded;
	private static File storeFile;

	private DeviceIdStore()
	{
	}

	static synchronized String getOrCreate(String identifier)
	{
		load();
		String key = keyFor(identifier);
		String uuid = STORE.getProperty(key);
		if (uuid != null && !uuid.isBlank())
		{
			return uuid;
		}

		uuid = UUID.randomUUID().toString();
		STORE.setProperty(key, uuid);
		save();
		log.info("Generated new device id");
		return uuid;
	}

	private static String keyFor(String identifier)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(identifier.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash)
			{
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static File storeFile()
	{
		if (storeFile != null)
		{
			return storeFile;
		}
		String override = System.getProperty("microbot.hwid.store");
		if (override != null && !override.isBlank())
		{
			storeFile = new File(override);
			return storeFile;
		}
		File home = new File(System.getProperty("runelite.home", new File(System.getProperty("user.home"), ".runelite").getPath()));
		storeFile = new File(home, "spoofed-device-ids.properties");
		return storeFile;
	}

	private static void load()
	{
		if (loaded)
		{
			return;
		}
		loaded = true;
		File file = storeFile();
		if (!file.isFile())
		{
			return;
		}
		try (InputStream in = java.nio.file.Files.newInputStream(file.toPath()))
		{
			STORE.load(in);
		}
		catch (IOException e)
		{
			log.warning("Could not read device id store: " + e);
		}
	}

	private static void save()
	{
		load();
		File file = storeFile();
		try
		{
			File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs())
			{
				throw new IOException("could not create " + parent);
			}
			try (OutputStream out = new FileOutputStream(file))
			{
				STORE.store(out, "hwid-agent");
			}
		}
		catch (IOException e)
		{
			log.warning("Could not save device id store: " + e);
		}
	}

	static void setStoreFileForTesting(File file)
	{
		storeFile = file;
	}

	static synchronized void reset()
	{
		STORE.clear();
		loaded = false;
	}
}
