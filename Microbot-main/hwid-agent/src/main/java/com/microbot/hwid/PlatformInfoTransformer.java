package com.microbot.hwid;

import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class PlatformInfoTransformer implements ClassFileTransformer
{
	private static final Logger log = Logger.getLogger(PlatformInfoTransformer.class.getName());

	private static final String[] FINGERPRINT_MARKERS = {
		"wmic csproduct get UUID",
		"system_profiler SPHardwareDataType",
		"/etc/machine-id"
	};

	private static final String HOOK_CLASS_INTERNAL = "com/microbot/hwid/HwidAgent";
	private static final String HOOK_METHOD = "deviceId";
	private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

	private volatile boolean matched;

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
		ProtectionDomain protectionDomain, byte[] classfileBuffer)
	{
		if (matched || classfileBuffer == null || looksUnlikely(className))
		{
			return null;
		}

		if (!containsBytes(classfileBuffer, "csproduct")
			|| !containsBytes(classfileBuffer, "/etc/machine-id"))
		{
			return null;
		}

		try
		{
			byte[] patched = patch(loader, classfileBuffer);
			if (patched == null)
			{
				return null;
			}
			matched = true;
			HwidAgent.markDeviceHookInstalled();
			log.info("Device id spoof installed on " + className);
			return patched;
		}
		catch (Throwable t)
		{
			log.warning("Device id patch failed for " + className + ": " + t);
			return null;
		}
	}

	private static boolean looksUnlikely(String className)
	{
		return className == null
			|| className.startsWith("java/")
			|| className.startsWith("javax/")
			|| className.startsWith("sun/")
			|| className.startsWith("com/sun/")
			|| className.startsWith("org/")
			|| className.startsWith("io/")
			|| className.startsWith("okhttp")
			|| className.startsWith("okio")
			|| className.startsWith("kotlin")
			|| className.startsWith("net/")
			|| className.startsWith("com/")
			|| className.startsWith("au/")
			|| className.startsWith("[");
	}

	private static boolean containsBytes(byte[] bytes, String marker)
	{
		byte[] pattern = marker.getBytes(StandardCharsets.US_ASCII);
		outer:
		for (int i = 0; i <= bytes.length - pattern.length; i++)
		{
			for (int j = 0; j < pattern.length; j++)
			{
				if (bytes[i + j] != pattern[j])
				{
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	static boolean referencesMarkers(MethodNode method)
	{
		for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions)
		{
			if (insn instanceof LdcInsnNode
				&& ((LdcInsnNode) insn).cst instanceof String)
			{
				String value = (String) ((LdcInsnNode) insn).cst;
				for (String marker : FINGERPRINT_MARKERS)
				{
					if (value.contains(marker))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean endsWithIntParameter(MethodNode method)
	{
		Type[] arguments = Type.getArgumentTypes(method.desc);
		return arguments.length > 0 && arguments[arguments.length - 1].getSort() == Type.INT;
	}

	private static int intParamSlot(MethodNode method)
	{
		Type[] arguments = Type.getArgumentTypes(method.desc);
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < arguments.length - 1; i++)
		{
			slot += arguments[i].getSize();
		}
		return slot;
	}

	private static byte[] patch(ClassLoader loader, byte[] classfileBuffer) throws Exception
	{
		ClassReader reader = new ClassReader(classfileBuffer);
		ClassNode node = new ClassNode();
		reader.accept(node, ClassReader.SKIP_FRAMES);

		boolean patched = false;
		for (MethodNode method : node.methods)
		{
			if (!Type.getReturnType(method.desc).getDescriptor().equals(STRING_DESCRIPTOR))
			{
				continue;
			}
			if (method.instructions == null || method.instructions.size() == 0)
			{
				continue;
			}
			if (!referencesMarkers(method) || !endsWithIntParameter(method))
			{
				continue;
			}

			int paramSlot = intParamSlot(method);

			InsnList replacement = new InsnList();
			replacement.add(new VarInsnNode(Opcodes.ILOAD, paramSlot));
			replacement.add(new MethodInsnNode(
				Opcodes.INVOKESTATIC, HOOK_CLASS_INTERNAL, HOOK_METHOD, "(I)" + STRING_DESCRIPTOR, false));
			replacement.add(new InsnNode(Opcodes.ARETURN));

			method.instructions.clear();
			method.instructions.add(replacement);
			if (method.tryCatchBlocks != null)
			{
				method.tryCatchBlocks.clear();
			}
			if (method.localVariables != null)
			{
				method.localVariables.clear();
			}
			if (method.visibleLocalVariableAnnotations != null)
			{
				method.visibleLocalVariableAnnotations.clear();
			}
			if (method.invisibleLocalVariableAnnotations != null)
			{
				method.invisibleLocalVariableAnnotations.clear();
			}
			patched = true;
		}

		if (!patched)
		{
			return null;
		}

		FrameClassWriter writer = new FrameClassWriter(loader);
		node.accept(writer);
		return writer.toByteArray();
	}
}
