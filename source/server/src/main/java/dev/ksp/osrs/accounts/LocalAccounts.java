package dev.ksp.osrs.accounts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class LocalAccounts {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path directory;

    public LocalAccounts(Path data) {
        directory = data.resolve("accounts");
    }

    public static String canonical(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,12}")) {
            throw new IllegalArgumentException("Use 1–12 letters, digits, or underscores.");
        }
        return username.toLowerCase(Locale.ROOT);
    }

    public synchronized void register(String username, char[] password) throws IOException {
        String canonical = canonical(username);
        // The revision 242 legacy client accepts printable ASCII up to 20 characters.
        if (password.length < 8 || password.length > 20) throw new IllegalArgumentException(
            "Use an 8–20 character password with printable ASCII characters.");
        for (char c : password) if (c < 32 || c > 126) throw new IllegalArgumentException(
            "Use an 8–20 character password with printable ASCII characters.");
        Path target = directory.resolve(canonical + ".properties");
        Files.createDirectories(directory);
        if (Files.exists(target)) throw new IllegalArgumentException("That username is already in use.");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = hash(password, salt);
        try {
            var properties = new Properties();
            properties.setProperty("name", username);
            properties.setProperty("salt", Base64.getEncoder().encodeToString(salt));
            properties.setProperty("hash", Base64.getEncoder().encodeToString(hash));
            properties.setProperty("member", "false");
            save(target,properties);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    public Account authenticate(String username, byte[] password) throws IOException {
        String canonical;
        try { canonical = canonical(username); }
        catch (IllegalArgumentException invalid) { return null; }
        Path file = directory.resolve(canonical + ".properties");
        if (!Files.isRegularFile(file)) return null;
        var properties = load(file);
        byte[] salt = Base64.getDecoder().decode(properties.getProperty("salt"));
        byte[] expected = Base64.getDecoder().decode(properties.getProperty("hash"));
        char[] chars = new char[password.length];
        for (int i = 0; i < password.length; i++) chars[i] = (char) Byte.toUnsignedInt(password[i]);
        byte[] actual;
        try { actual = hash(chars, salt); }
        finally { Arrays.fill(chars, '\0'); }
        try {
            return MessageDigest.isEqual(expected, actual)
                ? new Account(canonical, properties.getProperty("name"), Boolean.parseBoolean(properties.getProperty("member"))) : null;
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    public synchronized List<Account> list() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        var accounts = new ArrayList<Account>();
        try (var paths = Files.list(directory)) {
            for (var file : paths.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.matches("[a-z0-9_]{1,12}\\.properties") || !Files.isRegularFile(file)) continue;
                var properties = load(file);
                accounts.add(new Account(name.substring(0,name.indexOf('.')),properties.getProperty("name"),
                    Boolean.parseBoolean(properties.getProperty("member"))));
            }
        }
        return List.copyOf(accounts);
    }

    public synchronized void membership(String username, boolean member) throws IOException {
        Path target = directory.resolve(canonical(username) + ".properties");
        if (!Files.isRegularFile(target)) throw new IllegalArgumentException("Account not found.");
        var properties = load(target);
        properties.setProperty("member",Boolean.toString(member));
        save(target,properties);
    }

    private static Properties load(Path file) throws IOException {
        var properties = new Properties();
        try (var input = Files.newInputStream(file)) { properties.load(input); }
        return properties;
    }

    private void save(Path target, Properties properties) throws IOException {
        Path temporary = Files.createTempFile(directory,"account-",".tmp");
        try {
            try (var stream = Files.newOutputStream(temporary,StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(stream,"KSP local game account");
            }
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] hash(char[] password, byte[] salt) throws IOException {
        var key = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new IOException("Could not derive the account key", failure);
        } finally {
            key.clearPassword();
        }
    }

    public record Account(String key, String displayName, boolean member) {}
}
