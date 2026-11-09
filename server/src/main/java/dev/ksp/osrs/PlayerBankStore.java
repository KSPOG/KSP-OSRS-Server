package dev.ksp.osrs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Admin-only persistent player records. NOT a live OSRS account or bank service. */
public final class PlayerBankStore {
    public record Item(int itemId, int quantity) {}
    public record Player(String username, boolean member, long revision, Map<Integer,Item> bank) {}
    private final Path root;
    private final Path audit;
    private final Map<String,Player> players = new TreeMap<>();

    public PlayerBankStore(Path base) throws IOException {
        root = base.resolve("players");
        audit = base.resolve("bank-audit.jsonl");
        Files.createDirectories(root);
        try (var stream = Files.list(root)) {
            for (Path path : stream.filter(x -> x.getFileName().toString().matches("[A-Za-z0-9_]{1,12}\\.properties")).toList()) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(path)) { props.load(in); }
                String username = path.getFileName().toString().replace(".properties", "");
                long revision = Long.parseLong(props.getProperty("revision","0"));
                boolean member = Boolean.parseBoolean(props.getProperty("member","false"));
                Map<Integer,Item> bank = new TreeMap<>();
                for (String key : props.stringPropertyNames()) {
                    if (!key.startsWith("slot.")) continue;
                    int slot = Integer.parseInt(key.substring(5));
                    String[] pair = props.getProperty(key).split(":", -1);
                    if (pair.length!=2) throw new IOException("Invalid bank slot: " + key);
                    int itemId = Integer.parseInt(pair[0]), qty = Integer.parseInt(pair[1]);
                    validateItem(slot,itemId,qty);
                    bank.put(slot,new Item(itemId,qty));
                }
                players.put(username,new Player(username,member,revision,Map.copyOf(bank)));
            }
        }
    }

    private static String checkedName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]{1,12}"))
            throw new IllegalArgumentException("Username must be 1-12 letters, digits or underscores");
        return value;
    }
    private static void validateItem(int slot,int id,int qty) {
        if(slot<0||slot>=800||id<0||id>65535||qty<1)
            throw new IllegalArgumentException("Invalid bank slot, item ID or quantity");
    }
    public synchronized java.util.Collection<Player> all() { return java.util.List.copyOf(players.values()); }
    public synchronized Player require(String username) {
        Player p=players.get(checkedName(username));
        if(p==null) throw new IllegalArgumentException("Unknown player");
        return p;
    }
    public synchronized Player create(String username,boolean member) throws IOException {
        checkedName(username);
        if(players.containsKey(username))throw new IllegalArgumentException("Player already exists");
        Player p=new Player(username,member,0,Map.of());
        persist(p);
        players.put(username,p);
        audit("create",username,-1,null,null);
        return p;
    }
    public synchronized Player set(String username,long expected,int slot,int id,int qty) throws IOException {
        Player old=require(username);
        if(old.revision()!=expected)throw new IllegalStateException("Bank revision mismatch; refresh and retry");
        validateItem(slot,id,qty);
        for(var entry:old.bank().entrySet()) {
            if(entry.getKey()!=slot&&entry.getValue().itemId()==id)
                throw new IllegalArgumentException("Item ID already in another bank slot");
        }
        Map<Integer,Item> bank=new TreeMap<>(old.bank());
        Item before=bank.put(slot,new Item(id,qty));
        Player next=new Player(username,old.member(),expected+1,Map.copyOf(bank));
        persist(next);
        players.put(username,next);
        audit("set",username,slot,before,bank.get(slot));
        return next;
    }
    public synchronized Player delete(String username,long expected,int slot) throws IOException {
        Player old=require(username);
        if(old.revision()!=expected)throw new IllegalStateException("Bank revision mismatch; refresh and retry");
        if(slot<0||slot>=800)throw new IllegalArgumentException("Invalid slot");
        Map<Integer,Item> bank=new TreeMap<>(old.bank());
        Item before=bank.remove(slot);
        if(before==null)throw new IllegalArgumentException("Slot is already empty");
        Player next=new Player(username,old.member(),expected+1,Map.copyOf(bank));
        persist(next);
        players.put(username,next);
        audit("delete",username,slot,before,null);
        return next;
    }
    private void persist(Player p) throws IOException {
        Properties props=new Properties();
        props.setProperty("member",Boolean.toString(p.member()));
        props.setProperty("revision",Long.toString(p.revision()));
        p.bank().forEach((slot,item)->props.setProperty("slot."+slot,item.itemId()+":"+item.quantity()));
        Path temp=Files.createTempFile(root,"player-", ".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(temp)){ props.store(out,"KSP admin-only player"); }
            Path target=root.resolve(p.username()+".properties");
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private void audit(String action,String user,int slot,Item before,Item after) throws IOException {
        String event="{\"time\":\""+Instant.now()+"\",\"action\":\""+action+"\",\"player\":\""+user+
            "\",\"slot\":"+slot+",\"before\":"+itemJson(before)+",\"after\":"+itemJson(after)+"}\n";
        Files.writeString(audit,event,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private static String itemJson(Item i) {
        return i==null?"null":"{\"itemId\":"+i.itemId()+",\"quantity\":"+i.quantity()+"}";
    }
    public static String json(Player p) {
        StringBuilder sb=new StringBuilder("{\"username\":\""+p.username()+"\",\"member\":"+p.member()+
            ",\"revision\":"+p.revision()+",\"bank\":[");
        boolean first=true;
        for(var entry:new TreeMap<>(p.bank()).entrySet()) {
            if(!first)sb.append(',');
            first=false;
            sb.append("{\"slot\":").append(entry.getKey()).append(",\"itemId\":")
                .append(entry.getValue().itemId()).append(",\"quantity\":").append(entry.getValue().quantity()).append('}');
        }
        return sb.append("]}").toString();
    }
    public static String jsonAll(java.util.Collection<Player> list) {
        StringBuilder sb=new StringBuilder("[");
        boolean first=true;
        for(Player p:list) {
            if(!first)sb.append(',');
            first=false;
            sb.append(json(p));
        }
        return sb.append("]").toString();
    }
}
