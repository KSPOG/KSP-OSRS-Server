package com.microbot.hwid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Logger;

final class RandomDatStore
{
	private static final Logger log = Logger.getLogger(RandomDatStore.class.getName());

	private static final Properties STORE = new Properties();
	private static volatile boolean loaded;
	private static File storeFile;

	private RandomDatStore()
	{
	}

	static synchronized byte[] getOrCreate(String identifier)
	{
		loaded();
		String key = keyFor(identifier);
		String value = STORE.getProperty(key);
		if (value != null && !value.isBlank())
		{
			try
			{
				byte[] data = Base64.getDecoder().decode(value.trim());
				if (data.length == 24)
				{
					return data;
				}
			}
			catch (IllegalArgumentException ignored)
			{
			}
		}

		byte[] data = new byte[24];
		new SecureRandom().nextBytes(data);
		STORE.setProperty(key, Base64.getEncoder().encodeToString(data));
		save();
		log.info("Generated new random.dat seed");
		return data;
	}

	static synchronized void put(String identifier, byte[] data)
	{
		if (identifier == null || data == null || data.length != 24)
		{
			return;
		}
		loaded();
		STORE.setProperty(keyFor(identifier), Base64.getEncoder().encodeToString(data));
		save();
		log.info("Stored server-assigned random.dat seed");
	}

	private static String keyFor(String identifier)
	{
		try
		{
			byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
				.digest(identifier.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash)
			{
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (java.security.NoSuchAlgorithmException e)
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
		String override = System.getProperty("microbot.hwid.randomdat.store");
		if (override != null && !override.isBlank())
		{
			storeFile = new File(override);
			return storeFile;
		}
		File home = new File(System.getProperty("runelite.home", new File(System.getProperty("user.home"), ".runelite").getPath()));
		storeFile = new File(home, "spoofed-random-dats.properties");
		return storeFile;
	}

	private static void loaded()
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
		try (InputStream in = Files.newInputStream(file.toPath()))
		{
			STORE.load(in);
		}
		catch (IOException e)
		{
			log.warning("Could not read random.dat store: " + e);
		}
	}

	private static void save()
	{
		loaded();
		File file = storeFile();
		try
		{
			File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs())
			{
				throw new IOException("could not create " + parent);
			}
			try (OutputStream out = Files.newOutputStream(file.toPath()))
			{
				STORE.store(out, "hwid-agent");
			}
		}
		catch (IOException e)
		{
			log.warning("Could not save random.dat store: " + e);
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
