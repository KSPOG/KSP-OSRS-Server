package dev.ksp.osrs;

import com.sun.net.httpserver.HttpExchange;
import dev.ksp.osrs.accounts.LocalAccounts;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Authenticated editor for the REAL gameplay bank (95) and inventory (93). */
final class GameplayAdminApi {
    private GameplayAdminApi() { }

    static void handle(HttpExchange exchange, Path dataRoot, LocalAccounts accounts,
                       Map<String,Integer> online) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        try {
            if (path.equals("/admin/api/game-players") && exchange.getRequestMethod().equals("GET")) {
                var json = new StringBuilder("[");
                for (var account : accounts.list()) {
                    if (json.length() > 1) json.append(',');
                    Integer world = online.get(account.key());
                    json.append("{\"username\":\"").append(account.key()).append("\",\"displayName\":\"")
                        .append(escape(account.displayName())).append("\",\"online\":")
                        .append(world != null).append(",\"world\":").append(world == null ? "null" : world)
                        .append('}');
                }
                send(exchange,200,json.append(']').toString());
                return;
            }
            if (parts.length == 6 && "admin".equals(parts[1]) && "api".equals(parts[2])
                    && "game-players".equals(parts[3]) && "containers".equals(parts[5])) {
                String user = LocalAccounts.canonical(URLDecoder.decode(parts[4],StandardCharsets.UTF_8));
                boolean exists = accounts.list().stream().anyMatch(a -> a.key().equals(user));
                if (!exists) { send(exchange,404,"{\"error\":\"Account not found\"}"); return; }
                Path directory = dataRoot.resolve("gameplay");
                Path file = directory.resolve(user + ".properties");
                Path lockFile = directory.resolve(user + ".lock");
                Files.createDirectories(directory);
                if (exchange.getRequestMethod().equals("GET")) {
                    // Read while holding the same lock used by in-game bank actions.
                    try (var channel = FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                         var lock = channel.lock()) {
                        send(exchange,200,snapshot(user,file,online.get(user)));
                    }
                    return;
                }
                if (exchange.getRequestMethod().equals("POST")) {
                    var input = form(exchange);
                    String area = input.get("container");
                    if (!"bank".equals(area) && !"inventory".equals(area))
                        throw new IllegalArgumentException("Unknown container");
                    int capacity = area.equals("bank") ? 800 : 28;
                    String prefix = area.equals("bank") ? "bank." : "inv.";
                    int slot = Integer.parseInt(required(input,"slot"));
                    if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("Invalid slot");
                    String action = required(input,"action");
                    if (!"set".equals(action) && !"delete".equals(action)) throw new IllegalArgumentException("Invalid action");
                    try (var channel = FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                         var lock = channel.lock()) {
                        String current = version(file);
                        if (!current.equals(required(input,"revision"))) {
                            send(exchange,409,"{\"error\":\"Inventory changed. Refresh and retry.\"}");
                            return;
                        }
                        Properties props = load(file);
                        String key = prefix + slot;
                        if (action.equals("delete")) {
                            props.remove(key);
                        } else {
                            int item = Integer.parseInt(required(input,"itemId"));
                            int qty = Integer.parseInt(required(input,"quantity"));
                            if (item < 0 || item >= 65535 || qty <= 0)
                                throw new IllegalArgumentException("Invalid item or quantity");
                            props.setProperty(key,item + ":" + qty);
                        }
                        Path temp = Files.createTempFile(directory,"gameplay-admin-",".tmp");
                        try {
                            try (OutputStream stream = Files.newOutputStream(temp)) {
                                props.store(stream,"KSP gameplay bank and inventory");
                            }
                            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
                        } finally {
                            Files.deleteIfExists(temp);
                        }
                        Files.writeString(dataRoot.resolve("gameplay-admin-audit.jsonl"),
                            "{\"at\":\"" + Instant.now() + "\",\"username\":\"" + user
                                + "\",\"container\":\"" + area + "\",\"slot\":" + slot
                                + ",\"action\":\"" + action + "\"}\n",
                            StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                        send(exchange,200,snapshot(user,file,online.get(user)));
                    }
                    return;
                }
            }
            send(exchange,405,"{\"error\":\"Unsupported request\"}");
        } catch (IllegalArgumentException invalid) {
            send(exchange,400,"{\"error\":\"Invalid container operation or parameters\"}");
        }
    }

    private static String required(Map<String,String> input,String key) {
        String value = input.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static Map<String,String> form(HttpExchange exchange) throws IOException {
        String contentType=exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType==null || !contentType.startsWith("application/x-www-form-urlencoded"))
            throw new IllegalArgumentException("Expected form");
        byte[] bytes=exchange.getRequestBody().readNBytes(4097);
        if (bytes.length > 4096) throw new IllegalArgumentException("Request too large");
        Map<String,String> values=new HashMap<>();
        for (String pair : new String(bytes,StandardCharsets.UTF_8).split("&")) {
            int sep=pair.indexOf('=');
            if (sep<0) throw new IllegalArgumentException("Invalid field");
            String key=URLDecoder.decode(pair.substring(0,sep),StandardCharsets.UTF_8);
            String value=URLDecoder.decode(pair.substring(sep+1),StandardCharsets.UTF_8);
            if (values.putIfAbsent(key,value)!=null) throw new IllegalArgumentException("Duplicate field");
        }
        return values;
    }

    private static Properties load(Path file) throws IOException {
        Properties props=new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in=Files.newInputStream(file)) { props.load(in); }
        }
        return props;
    }

    private static String version(Path path) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            byte[] bytes=Files.isRegularFile(path) ? Files.readAllBytes(path) : new byte[0];
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String snapshot(String user,Path file,Integer world) throws IOException {
        Properties props=load(file);
        var json=new StringBuilder("{\"username\":\"").append(user)
            .append("\",\"online\":").append(world != null)
            .append(",\"world\":").append(world == null ? "null" : world)
            .append(",\"revision\":\"").append(version(file)).append("\"");
        for (String area : new String[]{"bank","inventory"}) {
            json.append(",\"").append(area).append("\":[");
            int size=area.equals("bank") ? 800 : 28;
            String prefix=area.equals("bank") ? "bank." : "inv.";
            boolean first=true;
            for (int slot=0;slot<size;slot++) {
                String saved=props.getProperty(prefix+slot);
                if (saved == null) continue;
                String[] parts=saved.split(":",-1);
                if (parts.length!=2) continue;
                int item,quantity;
                try { item=Integer.parseInt(parts[0]); quantity=Integer.parseInt(parts[1]); }
                catch (NumberFormatException malformed) { continue; }
                if (!first) json.append(',');
                first=false;
                json.append("{\"slot\":").append(slot).append(",\"itemId\":").append(item)
                    .append(",\"quantity\":").append(quantity).append('}');
            }
            json.append(']');
        }
        return json.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\","\\\\").replace("\"","\\\"").replace("<","\\u003c")
            .replace("\n"," ").replace("\r"," ");
    }

    private static void send(HttpExchange exchange,int code,String json) throws IOException {
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.sendResponseHeaders(code,bytes.length);
        try (OutputStream out=exchange.getResponseBody()) { out.write(bytes); }
    }
}
