package dev.ksp.osrs;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.io.*;

/** Local administration and world lifecycle coordinator. Not an OSRS game protocol endpoint. */
public final class ServerMain {
    record World(int id,String type,int capacity) {}
    private static final Map<Integer,World> worlds=new TreeMap<>();
    private static final Set<Integer> running=new HashSet<>();
    private static Path worldStatePath;
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
        for(World w:worlds.values())result.add("{\"id\":"+w.id()+",\"type\":\""+w.type()+"\",\"capacity\":"+w.capacity()+",\"running\":"+running.contains(w.id())+",\"population\":0}");
        return result.toString();
    }
    private static synchronized void admin(HttpExchange ex)throws IOException {
        if(!authorized(ex)){send(ex,secret.length<24?503:401,"application/json","{\"error\":\"Admin authorization required\"}");return;}
        String path=ex.getRequestURI().getPath();
        if(path.equals("/admin/api/worlds")&&ex.getRequestMethod().equals("GET")){send(ex,200,"application/json",worldsJson());return;}
        if(path.matches("/admin/api/worlds/[0-9]+/(start|stop)")&&ex.getRequestMethod().equals("POST")){
            String[] parts=path.split("/");
            int id;
            try{id=Integer.parseInt(parts[4]);}catch(NumberFormatException e){send(ex,400,"application/json","{\"error\":\"Invalid world ID\"}");return;}
            if(!worlds.containsKey(id)){send(ex,404,"application/json","{\"error\":\"Unknown world\"}");return;}
            String action=parts[5];
            boolean wasRunning=running.contains(id);
            if(action.equals("start"))running.add(id);else running.remove(id);
            try { WorldStateStore.save(worldStatePath, worlds.keySet(), running); }
            catch(IOException failure) {
                if(wasRunning)running.add(id);else running.remove(id);
                send(ex,500,"application/json","{\\"error\\":\\"Unable to persist world state\\"}");
                return;
            }
            System.out.println("[WORLD_CONTROL] world="+id+" action="+action);
            send(ex,200,"application/json",worldsJson());return;
        }
        send(ex,404,"application/json","{\"error\":\"Unknown endpoint\"}");
    }
    public static void main(String[] args)throws Exception {
        Path config=Path.of(args.length==0?"config/worlds.csv":args[0]);
        for(String raw:Files.readAllLines(config)){
            String line=raw.trim();
            if(line.isEmpty()||line.startsWith("#"))continue;
            String[] f=line.split(",");
            if(f.length!=4)throw new IllegalArgumentException("Expected id,type,capacity,running");
            int id=Integer.parseInt(f[0].trim()),capacity=Integer.parseInt(f[2].trim());
            String type=f[1].trim(),enabled=f[3].trim();
            if(id<1||id>65535||capacity<1||capacity>2000||!(type.equals("F2P")||type.equals("P2P"))||!(enabled.equals("true")||enabled.equals("false"))||worlds.containsKey(id))throw new IllegalArgumentException("Invalid world configuration: "+line);
            worlds.put(id,new World(id,type,capacity));
            if(Boolean.parseBoolean(enabled))running.add(id);
        }
        if(worlds.isEmpty())throw new IllegalArgumentException("No worlds configured");
        worldStatePath=Path.of(System.getenv().getOrDefault("KSP_WORLD_STATE_FILE","data/world-states.properties"));
        WorldStateStore.load(worldStatePath, worlds.keySet(), running);
        HttpServer http=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),8087),0);
        http.createContext("/health",ex->send(ex,200,"application/json","{\"status\":\"ok\",\"gameProtocol\":\"NOT_IMPLEMENTED\"}"));
        http.createContext("/client/manifest",ex->{
            if(!ex.getRequestURI().getPath().equals("/client/manifest")||!ex.getRequestMethod().equals("GET")){
                send(ex,405,"application/json","{\\"error\\":\\"Method not allowed\\"}");return;
            }
            send(ex,200,"application/json","{\\"schemaVersion\\":1,\\"gameProtocolReady\\":false,\\"javConfigReady\\":false,\\"clientRevision\\":null,\\"worlds\\":"+worldsJson()+"}");
        });
        http.createContext("/admin/api",ServerMain::admin);
        http.createContext("/admin",ex->{
            if(!ex.getRequestURI().getPath().equals("/admin")||!ex.getRequestMethod().equals("GET")){send(ex,404,"text/plain","Not found");return;}
            ex.getResponseHeaders().set("Content-Security-Policy","default-src 'none';script-src 'unsafe-inline';style-src 'unsafe-inline';connect-src 'self';base-uri 'none';frame-ancestors 'none'");
            send(ex,200,"text/html; charset=utf-8",Files.readString(Path.of("dashboard/index.html")));
        });
        Runtime.getRuntime().addShutdownHook(new Thread(()->http.stop(0)));
        http.start();
        System.out.println("World coordinator http://localhost:8087/admin (loopback only)");
        System.out.println("OSRS login/game protocol NOT IMPLEMENTED");
    }
}