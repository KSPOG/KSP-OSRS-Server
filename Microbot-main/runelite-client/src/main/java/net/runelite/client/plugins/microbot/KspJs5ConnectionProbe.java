package net.runelite.client.plugins.microbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Explicit one-shot JS5 transport diagnostic in the Microbot CLIENT branch.
 * Does not modify the injected game's network settings, launch RuneLite,
 * handle login credentials, or contact public RuneScape servers.
 */
public final class KspJs5ConnectionProbe
{
    private static final String PIN = "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38";
    private KspJs5ConnectionProbe() {}

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            System.err.println("Usage: KspJs5ConnectionProbe <OpenRS2-255-255.dat>");
            System.exit(2);
        }
        Path archived = Path.of(args[0]);
        if (!Files.isRegularFile(archived) || Files.isSymbolicLink(archived)
            || Files.size(archived) != 205)
            throw new IOException("Refusing missing, symbolic-link or incorrectly sized master");
        byte[] master = Files.readAllBytes(archived);
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(master));
        if (!PIN.equals(fingerprint))
            throw new IOException("OpenRS2 revision-242 archived master fingerprint does not match");
        try (Socket socket = new Socket())
        {
            socket.connect(new InetSocketAddress("127.0.0.1", 43596), 2500);
            socket.setSoTimeout(7000);
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            output.write(ByteBuffer.allocate(21).put((byte)15).putInt(242)
                .putInt(0).putInt(1).putInt(2).putInt(3).array());
            output.flush();
            if (input.read() != 0)
                throw new IOException("Revision-242 JS5 handshake was not acknowledged");
            output.write(new byte[]{1, (byte)255, 0, (byte)255});
            output.flush();
            byte[] actual = input.readNBytes(master.length + 3);
            if (actual.length != master.length + 3
                || (actual[0] & 255) != 255 || (actual[1] & 255) != 0
                || (actual[2] & 255) != 255
                || !Arrays.equals(Arrays.copyOfRange(actual, 3, actual.length), master))
                throw new IOException("Server's master 255/255 response does not match OpenRS2 reference");

            output.write(new byte[]{4, 0x5a, 0, 0});
            output.write(new byte[]{1, (byte)255, 0, 24});
            output.flush();
            // First 3 bytes of the XOR-encoded group must carry archive=255, group=24.
            byte[] prefix = input.readNBytes(3);
            if (prefix.length != 3 || (prefix[0] ^ 0x5a & 255) != 255
                || ((prefix[1] ^ 0x5a) & 255) != 0
                || ((prefix[2] ^ 0x5a) & 255) != 24)
                throw new IOException("Group 255/24 XOR-encoded response prefix mismatch");
            // Read the bounded remaining 2728 bytes of the known independent reference.
            byte[] remainder = input.readNBytes(2728);
            if (remainder.length != 2728)
                throw new IOException("Incomplete revision-242 reference table response");
            System.out.println("KSP_JS5_CLIENT_BRANCH_DIAGNOSTIC_PASS"
                + " revision=242 masterBytes=208 refBytes=2731 xorKey=0x5A");
            System.out.println("INJECTED_GAME_CLIENT_ACCEPTANCE=UNVERIFIED");
        }
    }
}
