import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Observes the native menu event in the pinned local-only gamepack. */
final class LocalDisplayInstrumentation {
    private LocalDisplayInstrumentation() {}

    static byte[] addScriptEntry(byte[] bytes) throws IOException {
        var reader = new ClassReader(bytes);
        var output = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] hooks = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
                var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("ai") || !descriptor.equals("(Lze;Laz;III)V")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "LocalDisplaySettings", "onScriptExecution",
                            "(Ljava/lang/Object;)V", false);
                        hooks[0]++;
                    }
                };
            }
        }, 0);
        if (hooks[0] != 1) throw new IOException("Unexpected native script execution entry");
        return output.toByteArray();
    }

    static byte[] add(byte[] bytes) throws IOException {
        var reader = new ClassReader(bytes);
        var output = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] hooks = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
                var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("ei")
                    || !descriptor.equals("(IIIIIILjava/lang/String;Ljava/lang/String;IIB)V")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String called, String desc,
                        boolean isInterface) {
                        if (opcode == Opcodes.INVOKEINTERFACE && called.equals("post")
                            && owner.equals("net/runelite/api/hooks/Callbacks")
                            && desc.equals("(Ljava/lang/Object;)V")) {
                            // Keep the event on the stack until RuneLite subscribers have handled it.
                            super.visitInsn(Opcodes.DUP_X1);
                            super.visitMethodInsn(opcode, owner, called, desc, isInterface);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "LocalDisplaySettings", "onMenuAction",
                                "(Ljava/lang/Object;)V", false);
                            hooks[0]++;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, called, desc, isInterface);
                    }
                };
            }
        }, 0);
        if (hooks[0] != 1) throw new IOException("Unexpected display settings menu hook layout");
        return output.toByteArray();
    }
}
