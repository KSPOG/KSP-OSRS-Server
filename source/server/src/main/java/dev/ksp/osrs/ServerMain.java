package dev.ksp.osrs;
import com.sun.net.httpserver.*;
import dev.ksp.osrs.accounts.LocalAccounts;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.io.*;

/** Local administration, world directory, and game process lifecycle. */
public final class ServerMain {
    record World(int id,String type,int capacity) {}
    private static final Map<Integer,World> worlds=new TreeMap<>();
    private static final Set<Integer> running=new HashSet<>();
    private static Path worldStatePath;
    private static WorldRuntime sockets;
    private static String configTemplate;
    private static final int ADMIN_PORT = 8087;
    private static final boolean WORLD_999_ENABLED = "true".equalsIgnoreCase(System.getenv("KSP_ENABLE_WORLD_999"));
    private static boolean gameEnabled() { return configTemplate != null; }
    private static boolean selectable(int id) {
        return (id != 999 || WORLD_999_ENABLED) && gameEnabled() && sockets.listening(id);
    }
    private static PlayerBankStore bankStore;
    private static LocalAccounts gameAccounts;
    private static Path playerDataDir;
    private static final byte[] secret=System.getenv().getOrDefault("KSP_ADMIN_TOKEN","").getBytes(StandardCharsets.UTF_8);
    private static void send(HttpExchange ex,int status,String mime,String value)throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",mime);
        ex.getResponseHeaders().set("Cache-Control","no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        ex.getResponseHeaders().set("X-Frame-Options","DENY");
        ex.sendResponseHeaders(status,bytes.length);
        try(OutputStream out=ex.getResponseBody()){out.write(bytes);}
    }
    private static boolean authorized(HttpExchange ex){
        String value=ex.getRequestHeaders().getFirst("Authorization");
        return secret.length>=24&&MessageDigest.isEqual(secret,value!=null&&value.startsWith("Bearer ")?value.substring(7).getBytes(StandardCharsets.UTF_8):new byte[0]);
    }
    private static synchronized String worldsJson(){
        StringJoiner result=new StringJoiner(",","[","]");
        for(World w:worlds.values())result.add("{\"id\":"+w.id()+",\"type\":\""+w.type()+"\",\"capacity\":"+w.capacity()+",\"running\":"+running.contains(w.id())+",\"listenerActive\":"+sockets.listening(w.id())+",\"tcpPort\":"+sockets.port(w.id())+",\"rejectedConnections\":"+sockets.attempts(w.id())+",\"ticks\":"+sockets.ticks(w.id())+",\"population\":"+sockets.population(w.id())+",\"optInRequired\":"+(w.id()==999&&!WORLD_999_ENABLED)+"}");
        return result.toString();
    }
    /** RuneLite world-directory shape. Only online worlds are advertised. */
    private static synchronized String clientWorldsJson() {
        StringJoiner result = new StringJoiner(",", "{\"worlds\":[", "]}");
        for (World w : worlds.values()) {
            if (!selectable(w.id())) continue;
            String types = "P2P".equals(w.type()) ? "[\"MEMBERS\"]" : "[]";
            result.add("{\"id\":" + w.id() + ",\"types\":" + types
                + ",\"address\":\"127.0.0.1\",\"activity\":\"KSP " + w.type()
                + "\",\"location\":0,\"players\":" + sockets.population(w.id()) + ",\"tcpPort\":" + sockets.port(w.id()) + "}");
        }
        return result.toString();
    }
    private static synchronized void gameWorlds(HttpExchange exchange) throws IOException {
        if (!publicGet(exchange, "/client/worlds.bin")) return;
        var buffer = new ByteArrayOutputStream();
        try (var packet = new DataOutputStream(buffer)) {
            var available = worlds.values().stream().filter(w -> selectable(w.id())).toList();
            packet.writeShort(available.size());
            for (World world : available) {
                packet.writeShort(world.id());
                packet.writeInt(world.type().equals("P2P") ? 1 : 0);
                packet.writeBytes("127.0.0.1");
                packet.writeByte(0);
                packet.writeBytes(world.type().equals("P2P") ? "KSP Members" : "KSP Free-to-play");
                packet.writeByte(0);
                packet.writeByte(0);
                packet.writeShort(sockets.population(world.id()));
            }
        }
        var framed = new ByteArrayOutputStream();
        try (var packet = new DataOutputStream(framed)) {
            packet.writeInt(buffer.size());
            buffer.writeTo(packet);
        }
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, framed.size());
        try (var out = exchange.getResponseBody()) { framed.writeTo(out); }
    }

    private static void register(HttpExchange exchange, LocalAccounts accounts) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/register")) {
            send(exchange,404,"text/plain","Not found");
            return;
        }
        if (exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none';script-src 'unsafe-inline';style-src 'unsafe-inline';connect-src 'self';base-uri 'none';frame-ancestors 'none'");
            send(exchange,200,"text/html; charset=utf-8",Files.readString(Path.of("dashboard/register.html")));
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!exchange.getRequestMethod().equals("POST") || type == null
            || !type.startsWith("application/x-www-form-urlencoded")) {
            send(exchange,405,"text/plain","Method not allowed");
            return;
        }
        if (!("http://127.0.0.1:" + ADMIN_PORT).equals(origin)
            && !("http://localhost:" + ADMIN_PORT).equals(origin)) {
            send(exchange,403,"text/plain","Open the local registration page to create an account.");
            return;
        }
        try {
            var fields = new HashMap<String,String>();
            byte[] body = exchange.getRequestBody().readNBytes(1025);
            if (body.length > 1024) throw new IllegalArgumentException("Registration form is too large.");
            for (String field : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] pair = field.split("=",2);
                if (pair.length != 2 || fields.putIfAbsent(pair[0],
                    URLDecoder.decode(pair[1],StandardCharsets.UTF_8)) != null) {
                    throw new IllegalArgumentException("Invalid registration form.");
                }
            }
            String username = fields.get("username");
            String legacy = System.getenv("KSP_LOCAL_LOGIN_USERNAME");
            if (username != null && username.equalsIgnoreCase(legacy)) {
                throw new IllegalArgumentException("That username is already in use.");
            }
            var password = fields.getOrDefault("password","").toCharArray();
            try { accounts.register(username,password); }
            finally { Arrays.fill(password,'\0'); }
            send(exchange,201,"text/plain; charset=utf-8","Account created. Sign in to World 301 with your username and password.");
        } catch (IllegalArgumentException invalid) {
            send(exchange,400,"text/plain; charset=utf-8",invalid.getMessage());
        } catch (IOException failure) {
            send(exchange,500,"text/plain","The server could not save your account.");
        }
    }

    private static void accountAdmin(HttpExchange exchange, LocalAccounts accounts) throws IOException {
        if (!authorized(exchange)) {
            send(exchange,401,"text/plain","Enter the dashboard admin token.");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (method.equals("GET") && path.equals("/admin/api/accounts")) {
                var results = new StringJoiner(",","[","]");
                for (var account : accounts.list()) {
                    LocalAccounts.canonical(account.displayName());
                    results.add("{\"username\":\"" + account.displayName() + "\",\"member\":" + account.member() + "}");
                }
                send(exchange,200,"application/json",results.toString());
                return;
            }
            String[] parts = path.split("/");
            if (method.equals("POST") && parts.length == 6 && parts[4].matches("[A-Za-z0-9_]{1,12}")
                && (parts[5].equals("members") || parts[5].equals("free"))) {
                accounts.membership(parts[4],parts[5].equals("members"));
                send(exchange,200,"text/plain","Membership updated. Sign out and back in to apply it.");
                return;
            }
            send(exchange,404,"text/plain","Account action not found.");
        } catch (IllegalArgumentException invalid) {
            send(exchange,400,"text/plain",invalid.getMessage());
        } catch (IOException failure) {
            send(exchange,500,"text/plain","Account data could not be read or saved.");
        }
    }

    private static boolean publicGet(HttpExchange exchange, String path) throws IOException {
        if (!exchange.getRequestURI().getPath().equals(path) || !exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, "text/plain", "Method not allowed");
            return false;
        }
        return true;
    }

    private static synchronized void gameConfig(HttpExchange exchange) throws IOException {
        if (!publicGet(exchange, "/client/jav_config.ws")) return;
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || !query.matches("world=[0-9]{1,4}")) {
            send(exchange, 400, "text/plain", "Supply a configured world ID");
            return;
        }
        int id = Integer.parseInt(query.substring(6));
        World world = worlds.get(id);
        if (world == null || !selectable(id)) {
            send(exchange, 404, "text/plain", "World is offline or unavailable");
            return;
        }
        String config = configTemplate.replace("@GAME_PORT@", Integer.toString(sockets.port(id)))
            .replace("@WORLD_ID@", Integer.toString(id))
            .replace("@WORLD_FLAGS@", world.type().equals("P2P") ? "1" : "0")
            .replace("@WORLD_MEMBER@", Boolean.toString(world.type().equals("P2P")));
        send(exchange, 200, "text/plain; charset=utf-8", config);
    }

    private static synchronized void admin(HttpExchange ex)throws IOException {
        if(!authorized(ex)){send(ex,secret.length<24?503:401,"application/json","{\"error\":\"Admin authorization required\"}");return;}
        String path=ex.getRequestURI().getPath();
        if(path.startsWith("/admin/api/game-players")) {
            Map<String,Integer> online = sockets instanceof GameWorldProcessManager managed
                ? managed.onlinePlayers() : Map.of();
            GameplayAdminApi.handle(ex,playerDataDir,gameAccounts,online);
            return;
        }
        if(path.startsWith("/admin/api/players")) {
            BankAdminApi.handle(ex,bankStore);
            return;
        }
        if(path.equals("/admin/api/worlds")&&ex.getRequestMethod().equals("GET")){send(ex,200,"application/json",worldsJson());return;}
        if(path.matches("/admin/api/worlds/[0-9]+/(start|stop)")&&ex.getRequestMethod().equals("POST")){
            String[] parts=path.split("/");
            int id;
            try{id=Integer.parseInt(parts[4]);}catch(NumberFormatException e){send(ex,400,"application/json","{\"error\":\"Invalid world ID\"}");return;}
            if(!worlds.containsKey(id)){send(ex,404,"application/json","{\"error\":\"Unknown world\"}");return;}
            String action=parts[5];
            if (id == 999 && action.equals("start") && !WORLD_999_ENABLED) {
                send(ex, 403, "application/json", "{\"error\":\"World 999 requires explicit opt-in at startup\"}");
                return;
            }
            boolean wasRunning=running.contains(id);
            try {
                if(action.equals("start")) {
                    sockets.start(id);
                    running.add(id);
                } else {
                    sockets.stop(id);
                    running.remove(id);
                }
                WorldStateStore.save(worldStatePath, worlds.keySet(), running);
            } catch (IOException failure) {
                // Restore the previous authoritative state as far as possible.
                try {
                    if(wasRunning) sockets.start(id); else sockets.stop(id);
                } catch(IOException restorationFailure) {
                    System.err.println("[WORLD_CONTROL] failed to restore world "+id+": "+restorationFailure.getMessage());
                }
                if(wasRunning)running.add(id);else running.remove(id);
                System.err.println("[WORLD_CONTROL] world="+id+" action="+action+" failed: "+failure.getMessage());
                send(ex,500,"application/json","{\"error\":\"World lifecycle operation failed\"}");
                return;
            }
            System.out.println("[WORLD_CONTROL] world="+id+" action="+action);
            send(ex,200,"application/json",worldsJson());return;
        }
        send(ex,404,"application/json","{\"error\":\"Unknown endpoint\"}");
    }
    public static void main(String[] args)throws Exception {
        if (args.length != 0 && args.length != 1 && args.length != 4) {
            throw new IllegalArgumentException("Usage: ServerMain [worlds.csv [cache master private-key]]");
        }
        Path config=Path.of(args.length==0?"config/worlds.csv":args[0]);
        for(String raw:Files.readAllLines(config)){
            String line=raw.trim();
            if(line.isEmpty()||line.startsWith("#"))continue;
            String[] f=line.split(",");
            if(f.length!=4)throw new IllegalArgumentException("Expected id,type,capacity,running");
            int id=Integer.parseInt(f[0].trim()),capacity=Integer.parseInt(f[2].trim());
            String type=f[1].trim(),enabled=f[3].trim();
            if(id<1||id>2000||capacity<1||capacity>2000||!(type.equals("F2P")||type.equals("P2P"))||!(enabled.equals("true")||enabled.equals("false"))||worlds.containsKey(id))throw new IllegalArgumentException("Invalid world configuration: "+line);
            worlds.put(id,new World(id,type,capacity));
            if(Boolean.parseBoolean(enabled))running.add(id);
        }
        if(worlds.isEmpty())throw new IllegalArgumentException("No worlds configured");
        worldStatePath=Path.of(System.getenv().getOrDefault("KSP_WORLD_STATE_FILE","data/world-states.properties"));
        WorldStateStore.load(worldStatePath, worlds.keySet(), running);
        if (!WORLD_999_ENABLED) running.remove(999);
        if (args.length == 4) {
            configTemplate = Files.readString(Path.of("config/jav_config.local.ws"));
            Set<Integer> members = new HashSet<>();
            for (World world : worlds.values()) if (world.type().equals("P2P")) members.add(world.id());
            sockets = new GameWorldProcessManager(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), members);
        } else {
            sockets = new WorldSocketManager(Integer.parseInt(System.getenv().getOrDefault("KSP_WORLD_PORT_BASE", "43000")));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(sockets::close, "ksp-world-shutdown"));
        try {
            for(int id:running) sockets.start(id);
        } catch(IOException bindError) {
            sockets.close();
            throw new IOException("Unable to start configured world TCP listeners",bindError);
        }
        playerDataDir = Path.of(System.getenv().getOrDefault("KSP_PLAYER_DATA_DIR","data"));
        bankStore = new PlayerBankStore(playerDataDir);
        HttpServer http=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),ADMIN_PORT),0);
        LocalAccounts accounts = new LocalAccounts(playerDataDir);
        gameAccounts = accounts;
        if (gameEnabled()) {
            http.createContext("/register", ex -> register(ex, accounts));
            http.createContext("/admin/api/accounts", ex -> accountAdmin(ex, accounts));
            http.createContext("/admin/accounts", ex -> {
                if (!publicGet(ex,"/admin/accounts")) return;
                ex.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'none';script-src 'unsafe-inline';style-src 'unsafe-inline';connect-src 'self';base-uri 'none';frame-ancestors 'none'");
                send(ex,200,"text/html; charset=utf-8",Files.readString(Path.of("dashboard/accounts.html")));
            });
        }
        http.createContext("/health",ex->send(ex,200,"application/json","{\"status\":\"ok\",\"gameProtocol\":\"" + (gameEnabled() ? "LUMBRIDGE" : "UNAVAILABLE") + "\"}"));
        http.createContext("/client/manifest",ex->{
            if(!ex.getRequestURI().getPath().equals("/client/manifest")||!ex.getRequestMethod().equals("GET")){
                send(ex,405,"application/json","{\"error\":\"Method not allowed\"}");return;
            }
            send(ex,200,"application/json","{\"schemaVersion\":1,\"gameProtocolReady\":" + gameEnabled() + ",\"javConfigReady\":" + gameEnabled() + ",\"clientRevision\":242,\"clientBuildId\":\"41605773992.307\",\"revisionVerified\":true,\"worlds\":"+worldsJson()+"}");
        });
        http.createContext("/client/worlds.js",ex->{
            if(!ex.getRequestURI().getPath().equals("/client/worlds.js")
                || !ex.getRequestMethod().equals("GET")) {
                send(ex,405,"application/json","{\"error\":\"Method not allowed\"}");return;
            }
            send(ex,200,"application/json",clientWorldsJson());
        });
        http.createContext("/client/worlds.bin", ServerMain::gameWorlds);
        http.createContext("/client/jav_config.ws", ServerMain::gameConfig);
        http.createContext("/admin/api",ServerMain::admin);
        http.createContext("/admin/bank",ex->{
            if(!ex.getRequestURI().getPath().equals("/admin/bank")||!ex.getRequestMethod().equals("GET")) {
                send(ex,404,"text/plain","Not found");return;
            }
            ex.getResponseHeaders().set("Content-Security-Policy","default-src 'none';script-src 'unsafe-inline';style-src 'unsafe-inline';connect-src 'self';base-uri 'none';frame-ancestors 'none'");
            send(ex,200,"text/html; charset=utf-8",Files.readString(Path.of("dashboard/bank.html")));
        });
        http.createContext("/admin",ex->{
            if(!ex.getRequestURI().getPath().equals("/admin")||!ex.getRequestMethod().equals("GET")){send(ex,404,"text/plain","Not found");return;}
            ex.getResponseHeaders().set("Content-Security-Policy","default-src 'none';script-src 'unsafe-inline';style-src 'unsafe-inline';connect-src 'self';base-uri 'none';frame-ancestors 'none'");
            send(ex,200,"text/html; charset=utf-8",Files.readString(Path.of("dashboard/index.html")));
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> http.stop(0), "ksp-admin-shutdown"));
        http.start();
        System.out.println("World coordinator http://127.0.0.1:8087/admin");
    }
}