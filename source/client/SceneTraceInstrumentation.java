import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class SceneTraceInstrumentation {
    private static final String TRACE = "LocalSceneTrace";

    private SceneTraceInstrumentation() {}

    static boolean targets(String entry) {
        return switch (entry) {
            case "sr.class", "je.class", "client.class", "rl4.class" -> true;
            default -> false;
        };
    }

    static byte[] add(String entry, byte[] bytes) throws IOException {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter output = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] counts = new int[2];
        reader.accept(new ClassVisitor(Opcodes.ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
                String target = entry + ":" + name + desc;
                return switch (target) {
                    case "sr.class:au(IB)V", "je.class:cl(Lxn;IIZI)V",
                        "client.class:nk(Lrl4;)V", "client.class:mo(Lrl4;)V",
                        "rl4.class:yy()V", "rl4.class:rw(Ljava/lang/String;Ljava/lang/Throwable;)V",
                        "rl4.class:iw()Z" -> new MethodVisitor(Opcodes.ASM9, method) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                counts[0]++;
                                traceEntry(this, target);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if ("rl4.class:iw()Z".equals(target) && opcode == Opcodes.IRETURN) {
                                    traceLoaderReturn(this, ++counts[1]);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    default -> method;
                };
            }
        }, 0);
        int methods = switch (entry) {
            case "client.class" -> 2;
            case "rl4.class" -> 3;
            default -> 1;
        };
        if (counts[0] != methods || ("rl4.class".equals(entry) && counts[1] != 4)) {
            throw new IOException("Unexpected scene trace layout for " + entry);
        }
        return output.toByteArray();
    }

    private static void traceEntry(MethodVisitor mv, String target) {
        switch (target) {
            case "sr.class:au(IB)V" -> {
                mv.visitVarInsn(Opcodes.ILOAD, 0);
                call(mv, "state", "(I)V");
                mv.visitVarInsn(Opcodes.ILOAD, 0);
                call(mv, "prepareLocalLoginWindow", "(I)V");
            }
            case "je.class:cl(Lxn;IIZI)V" -> {
                mv.visitVarInsn(Opcodes.ILOAD, 1);
                mv.visitVarInsn(Opcodes.ILOAD, 2);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD, "xn", "ao", "[I");
                call(mv, "rebuild", "(II[I)V");
            }
            case "client.class:nk(Lrl4;)V" -> mark(mv, "root_view_initialized");
            case "client.class:mo(Lrl4;)V" -> mark(mv, "root_scene_install");
            case "rl4.class:yy()V" -> mark(mv, "scene_task_scheduled");
            case "rl4.class:rw(Ljava/lang/String;Ljava/lang/Throwable;)V" -> {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                call(mv, "failure", "(Ljava/lang/String;Ljava/lang/Throwable;)V");
            }
            default -> { }
        }
    }

    private static void traceLoaderReturn(MethodVisitor mv, int returnSite) {
        if (returnSite == 1) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, "rl4", "wi", "Lxn;");
            mv.visitFieldInsn(Opcodes.GETFIELD, "xn", "ao", "[I");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, "rl4", "wa", "[[B");
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, "rl4", "bs", "[[B");
            call(mv, "mapWait", "([I[[B[[B)V");
        } else if (returnSite == 2) {
            call(mv, "modelWait", "()V");
        } else {
            mark(mv, "scene_assets_ready");
        }
    }

    private static void mark(MethodVisitor mv, String status) {
        mv.visitLdcInsn(status);
        call(mv, "mark", "(Ljava/lang/String;)V");
    }

    private static void call(MethodVisitor mv, String name, String descriptor) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, TRACE, name, descriptor, false);
    }
}
