package dev.ksp.osrs;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Auth is enforced by ServerMain before entering this handler. */
public final class BankAdminApi {
    private BankAdminApi() {}

    static void handle(HttpExchange exchange, PlayerBankStore store) throws IOException {
        String path=exchange.getRequestURI().getPath();
        String method=exchange.getRequestMethod();
        try {
            if(path.equals("/admin/api/players")) {
                if(method.equals("GET")) { send(exchange,200,PlayerBankStore.jsonAll(store.all()));return; }
                if(method.equals("POST")) {
                    Map<String,String> form=form(exchange);
                    String value=form.get("member");
                    if(!"true".equals(value)&&!"false".equals(value))throw new IllegalArgumentException("Invalid member flag");
                    send(exchange,201,PlayerBankStore.json(store.create(form.get("username"),Boolean.parseBoolean(value))));return;
                }
            }
            String[] segments=path.split("/");
            if(segments.length==6&&segments[1].equals("admin")&&segments[2].equals("api")&&segments[3].equals("players")&&segments[5].equals("bank")) {
                if(method.equals("GET")) {
                    send(exchange,200,PlayerBankStore.json(store.require(segments[4])));return;
                }
                if(method.equals("POST")) {
                    Map<String,String> form=form(exchange);
                    long revision=Long.parseLong(required(form,"revision"));
                    int slot=Integer.parseInt(required(form,"slot"));
                    if("delete".equals(form.get("action"))) {
                        send(exchange,200,PlayerBankStore.json(store.delete(segments[4],revision,slot)));return;
                    }
                    if("set".equals(form.get("action"))) {
                        int itemId=Integer.parseInt(required(form,"itemId"));
                        int quantity=Integer.parseInt(required(form,"quantity"));
                        send(exchange,200,PlayerBankStore.json(store.set(segments[4],revision,slot,itemId,quantity)));return;
                    }
                    throw new IllegalArgumentException("Action must be set or delete");
                }
            }
            send(exchange,405,"{\"error\":\"Unsupported request\"}");
        } catch(IllegalStateException conflict) {
            send(exchange,409,"{\"error\":\"Bank revision conflict; refresh and retry\"}");
        } catch(IllegalArgumentException validation) {
            send(exchange,400,"{\"error\":\"Invalid player or bank operation\"}");
        } catch(IOException failure) {
            System.err.println("[BANK] Operation failed: "+failure.getMessage());
            send(exchange,500,"{\"error\":\"Storage operation failed\"}");
        }
    }

    private static String required(Map<String,String> values,String key) {
        String value=values.get(key);
        if(value==null)throw new IllegalArgumentException("Missing "+key);
        return value;
    }

    private static Map<String,String> form(HttpExchange exchange) throws IOException {
        String contentType=exchange.getRequestHeaders().getFirst("Content-Type");
        if(contentType==null||!contentType.startsWith("application/x-www-form-urlencoded"))
            throw new IllegalArgumentException("Form encoding required");
        byte[] bytes=exchange.getRequestBody().readNBytes(4097);
        if(bytes.length>4096)throw new IllegalArgumentException("Request too large");
        Map<String,String> values=new HashMap<>();
        String input=new String(bytes,StandardCharsets.UTF_8);
        for(String pair:input.split("&")) {
            int split=pair.indexOf('=');
            if(split<0)throw new IllegalArgumentException("Invalid form");
            String key=URLDecoder.decode(pair.substring(0,split),StandardCharsets.UTF_8);
            String val=URLDecoder.decode(pair.substring(split+1),StandardCharsets.UTF_8);
            if(values.putIfAbsent(key,val)!=null)throw new IllegalArgumentException("Duplicate parameter");
        }
        return values;
    }
    private static void send(HttpExchange exchange,int code,String json) throws IOException {
        byte[] body=json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.sendResponseHeaders(code,body.length);
        try(OutputStream out=exchange.getResponseBody()){out.write(body);}
    }
}
