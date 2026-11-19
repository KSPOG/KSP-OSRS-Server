package dev.ksp.osrs.protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/** Reads the coordinator's live, loopback-only world directory. */
final class LocalWorldDirectory {
    private static final URI DIRECTORY = URI.create("http://127.0.0.1:8087/client/worlds.bin");
    private static final int MAX_DIRECTORY_BYTES = 65_536;

    private LocalWorldDirectory() {}

    static World find(int worldId) throws IOException {
        var connection = (HttpURLConnection) DIRECTORY.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(1_000);
        connection.setReadTimeout(1_000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("World directory is unavailable");
            }
            try (var input = new DataInputStream(connection.getInputStream())) {
                int length = input.readInt();
                if (length < 2 || length > MAX_DIRECTORY_BYTES) throw new IOException("Invalid world directory size");
                byte[] payload = new byte[length];
                input.readFully(payload);
                return find(payload, worldId);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static World find(byte[] payload, int worldId) throws IOException {
        try (var data = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
            int count = data.readUnsignedShort();
            for (int row = 0; row < count; row++) {
                int id = data.readUnsignedShort();
                int properties = data.readInt();
                String host = readString(data);
                readString(data); // Activity name.
                data.readUnsignedByte(); // Location.
                int players = data.readShort();
                if (id == worldId && "127.0.0.1".equals(host) && players >= 0) {
                    return new World(id, properties, players);
                }
            }
            return null;
        }
    }

    private static String readString(DataInputStream data) throws IOException {
        var value = new StringBuilder();
        for (int ch; (ch = data.readUnsignedByte()) != 0;) {
            value.append((char) ch);
        }
        return value.toString();
    }

    record World(int id, int properties, int players) {
        boolean members() { return (properties & 1) != 0; }
    }
}
