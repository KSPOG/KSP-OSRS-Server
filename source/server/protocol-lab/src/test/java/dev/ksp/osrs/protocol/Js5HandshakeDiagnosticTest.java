package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CI-ready socket tests with a minimal, temporary, synthetic cache.
 * Independently validates handshake success/refusal and provider delivery.
 */
public final class Js5HandshakeDiagnosticTest {
    private Js5HandshakeDiagnosticTest() {}

    private static void check(boolean value, String note) {
        if (!value) throw new AssertionError(note);
    }

    private static byte[] login(int opcode, int revision) {
        ByteBuffer b = ByteBuffer.allocate(21);
        b.put((byte)opcode).putInt(revision).putInt(0).putInt(1).putInt(2).putInt(3);
        return b.array();
    }

    private static void medium(RandomAccessFile f, int number) throws Exception {
        f.writeByte(number >>> 16); f.writeByte(number >>> 8); f.writeByte(number);
    }

    static Path fixture() throws Exception {
        Path root = Files.createTempDirectory("js5-handshake-test-");
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(contents)) {
            data.writeByte(7); data.writeInt(1); data.writeByte(0);
            data.writeShort(0); // zero groups, valid reference
        }
        byte[] ref;
        try (ByteArrayOutputStream raw = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(raw)) {
            data.writeByte(0);
            data.writeInt(contents.size());
            data.write(contents.toByteArray());
            data.flush();
            ref = raw.toByteArray();
        }
        try (RandomAccessFile dat = new RandomAccessFile(root.resolve("main_file_cache.dat2").toFile(), "rw");
             RandomAccessFile idx = new RandomAccessFile(root.resolve("main_file_cache.idx255").toFile(), "rw")) {
            medium(idx, ref.length); medium(idx, 1);
            dat.seek(520L); dat.writeShort(0); dat.writeShort(0);
            medium(dat, 0); dat.writeByte(255); dat.write(ref);
        }
        return root;
    }

    private static Js5HandshakeDiagnosticServer.Report session(
            Path root, byte[] login, boolean expectSuccess, byte[] expectedReference)
            throws Exception {
        try (ServerSocket server = Js5LocalDiagnosticServer.bind(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> Js5HandshakeDiagnosticServer.serveOne(server, root));
            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                client.setSoTimeout(7000);
                client.getOutputStream().write(login);
                client.getOutputStream().flush();
                if (expectSuccess) {
                    check(client.getInputStream().read() == 0, "missing login ack");
                    client.getOutputStream().write(new byte[]{1, (byte)255, 0, 0});
                    client.getOutputStream().flush();
                    byte[] actual = client.getInputStream().readNBytes(expectedReference.length);
                    check(Arrays.equals(actual, expectedReference), "reference response mismatch");
                    client.getOutputStream().write(new byte[]{1, (byte)255, 0, (byte)255});
                    client.getOutputStream().flush();
                    check(client.getInputStream().read() == -1, "unverified master was served");
                } else {
                    client.shutdownOutput();
                    check(client.getInputStream().read() == -1, "invalid handshake accepted");
                }
            }
            var report = future.get(10, TimeUnit.SECONDS);
            executor.shutdown();
            return report;
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = fixture();
        try {
            byte[] reference;
            try (var provider = new VerifiedJs5GroupProvider(root)) {
                ByteBuf b = provider.provide(255, 0);
                check(b != null, "fixture reference unavailable");
                try { reference = new byte[b.readableBytes()]; b.readBytes(reference); }
                finally { b.release(); }
            }
            var valid = session(root, login(15, 242), true, reference);
            check(valid.handshakeAccepted() && valid.requests() == 2
                && valid.responses() == 1 && valid.responseBytes() == reference.length
                && "MASTER_NOT_VERIFIED".equals(valid.termination()), "valid handshake failed");
            var wrongRevision = session(root, login(15, 241), false, reference);
            check(!wrongRevision.handshakeAccepted()
                && "REVISION_MISMATCH".equals(wrongRevision.termination()), "revision fail-open");
            var wrongOpcode = session(root, login(14, 242), false, reference);
            check(!wrongOpcode.handshakeAccepted()
                && "UNSUPPORTED_LOGIN_OPCODE".equals(wrongOpcode.termination()), "opcode fail-open");
            var partial = session(root, new byte[]{15, 0, 0, 0, (byte)242}, false, reference);
            check(!partial.handshakeAccepted()
                && "INCOMPLETE_HANDSHAKE".equals(partial.termination()), "truncation fail-open");
            System.out.println("Js5HandshakeDiagnosticTest: PASS");
        } finally {
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                });
            }
        }
    }
}
