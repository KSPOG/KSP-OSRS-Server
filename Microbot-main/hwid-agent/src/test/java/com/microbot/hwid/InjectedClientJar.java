package com.microbot.hwid;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InjectedClientJar
{
	private static final Pattern VERSION = Pattern.compile("injected-client-(.+)\\.jar$");

	private InjectedClientJar()
	{
	}

	static File locate()
	{
		for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator))
		{
			if (entry.endsWith(".jar") && entry.contains("injected-client"))
			{
				return new File(entry);
			}
		}
		ClassLoader loader = InjectedClientJar.class.getClassLoader();
		while (loader != null)
		{
			if (loader instanceof URLClassLoader)
			{
				for (URL url : ((URLClassLoader) loader).getURLs())
				{
					String path = url.getPath();
					if (path.endsWith(".jar") && path.contains("injected-client"))
					{
						return new File(URLDecoder.decode(path, StandardCharsets.UTF_8));
					}
				}
			}
			loader = loader.getParent();
		}
		return null;
	}

	static String version(File jar)
	{
		Matcher matcher = VERSION.matcher(jar.getName());
		return matcher.find() ? matcher.group(1) : jar.getName();
	}
}
