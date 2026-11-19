import java.io.IOException;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Routes the pinned gamepack's JS5 and game sockets to the selected local world. */
final class LocalWorldPortInstrumentation {
    private static final Set<String> METHODS = Set.of(
        "client.iq(B)V", "client.adm()V", "ak.dp(Lcj;)V", "ak.dy(Lcj;)V", "jy.bf(Lcj;B)V");
    private static final int INITIAL_WORLD_ID_DECODER = -1871600313;
    private static final int SELECTED_WORLD_ID_DECODER = -1946425117;

    private LocalWorldPortInstrumentation() {}

    static boolean targets(String entry) {
        return entry.equals("client.class") || entry.equals("ak.class") || entry.equals("jy.class");
    }

    static byte[] route(byte[] bytes) throws IOException {
        var reader = new ClassReader(bytes);
        var output = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] ports = new int[2];
        reader.accept(new ClassVisitor(Opcodes.ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
                var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!METHODS.contains(reader.getClassName() + "." + name + descriptor)) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    private void localPort() {
                        if (reader.getClassName().equals("client")) {
                            super.visitFieldInsn(Opcodes.GETSTATIC, "client", "cx", "I");
                            super.visitLdcInsn(INITIAL_WORLD_ID_DECODER);
                        } else {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, "cj", "aj", "I");
                            super.visitLdcInsn(SELECTED_WORLD_ID_DECODER);
                        }
                        super.visitInsn(Opcodes.IMUL);
                        super.visitLdcInsn(43000);
                        super.visitInsn(Opcodes.IADD);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (Integer.valueOf(43594).equals(value)) {
                            localPort();
                            ports[0]++;
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int value) {
                        if (opcode == Opcodes.SIPUSH && value == 443) {
                            localPort();
                            ports[1]++;
                        } else {
                            super.visitIntInsn(opcode, value);
                        }
                    }
                };
            }
        }, 0);
        int expected = reader.getClassName().equals("jy") ? 1 : 2;
        if (ports[0] != expected || ports[1] != expected) {
            throw new IOException("Unexpected port layout in " + reader.getClassName());
        }
        return output.toByteArray();
    }
}
