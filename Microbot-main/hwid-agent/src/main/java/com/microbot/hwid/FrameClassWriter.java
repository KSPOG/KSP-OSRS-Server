package com.microbot.hwid;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

final class FrameClassWriter extends ClassWriter
{
	private static final String OBJECT = "java/lang/Object";

	private final ClassLoader loader;

	FrameClassWriter(ClassLoader loader)
	{
		super(COMPUTE_FRAMES);
		this.loader = loader != null ? loader : ClassLoader.getSystemClassLoader();
	}

	@Override
	protected String getCommonSuperClass(String type1, String type2)
	{
		if (type1.equals(type2))
		{
			return type1;
		}
		if (OBJECT.equals(type1) || OBJECT.equals(type2))
		{
			return OBJECT;
		}

		Set<String> supers1 = superChain(type1);
		if (supers1.contains(type2))
		{
			return type2;
		}
		for (String candidate = type2; candidate != null; candidate = superName(candidate))
		{
			if (supers1.contains(candidate))
			{
				return candidate;
			}
		}
		return OBJECT;
	}

	private Set<String> superChain(String type)
	{
		Set<String> chain = new LinkedHashSet<>();
		for (String current = type; current != null && chain.add(current); current = superName(current))
		{
			if (OBJECT.equals(current))
			{
				break;
			}
		}
		chain.add(OBJECT);
		return chain;
	}

	private String superName(String type)
	{
		if (type == null || OBJECT.equals(type) || type.charAt(0) == '[')
		{
			return null;
		}
		try (InputStream in = loader.getResourceAsStream(type + ".class"))
		{
			if (in == null)
			{
				return OBJECT;
			}
			return new ClassReader(in).getSuperName();
		}
		catch (Exception e)
		{
			return OBJECT;
		}
	}
}
