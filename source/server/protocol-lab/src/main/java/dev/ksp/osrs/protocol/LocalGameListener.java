package dev.ksp.osrs.protocol;

import dev.ksp.osrs.accounts.LocalAccounts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.cache.definitions.ItemDefinition;
import net.rsprot.compression.HuffmanCodec;
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider;
import net.rsprot.compression.provider.HuffmanCodecProvider;
import net.rsprot.crypto.rsa.RsaKeyPair;
import net.rsprot.crypto.xtea.XteaKey;
import net.rsprot.protocol.api.AbstractNetworkServiceFactory;
import net.rsprot.protocol.api.GameConnectionHandler;
import net.rsprot.protocol.api.NetworkService;
import net.rsprot.protocol.api.Session;
import net.rsprot.protocol.api.js5.Js5GroupProvider;
import net.rsprot.protocol.api.login.GameLoginResponseHandler;
import net.rsprot.protocol.common.client.OldSchoolClientType;
import net.rsprot.protocol.game.incoming.buttons.If1Button;
import net.rsprot.protocol.game.incoming.buttons.If3Button;
import net.rsprot.protocol.game.incoming.buttons.IfButtonT;
import net.rsprot.protocol.game.incoming.misc.client.WindowStatus;
import net.rsprot.protocol.game.incoming.misc.user.ClientCheat;
import net.rsprot.protocol.game.incoming.misc.user.SetChatFilterSettings;
import net.rsprot.protocol.game.incoming.misc.user.CloseModal;
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick;
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick;
import net.rsprot.protocol.game.incoming.npcs.OpNpcT;
import net.rsprot.protocol.game.incoming.npcs.OpNpcV2;
import net.rsprot.protocol.game.incoming.messaging.MessagePublic;
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton;
import net.rsprot.protocol.game.incoming.resumed.ResumePCountDialog;
import net.rsprot.protocol.game.incoming.locs.OpLocV2;
import net.rsprot.protocol.game.incoming.locs.OpLocV3;
import net.rsprot.protocol.game.incoming.locs.OpLocTV1;
import net.rsprot.protocol.game.incoming.locs.OpLocTV2;
import net.rsprot.protocol.game.incoming.locs.OpLoc6V1;
import net.rsprot.protocol.game.incoming.locs.OpLoc6V2;
import net.rsprot.protocol.game.incoming.misc.client.MapBuildComplete;
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar;
import net.rsprot.protocol.game.outgoing.info.InfoProtocols;
import net.rsprot.protocol.game.outgoing.info.Infos;
import net.rsprot.protocol.game.outgoing.info.util.PacketResultKt;
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub;
import net.rsprot.protocol.game.outgoing.interfaces.IfMoveSub;
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub;
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop;
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2;
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide;
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHead;
import net.rsprot.protocol.game.outgoing.interfaces.IfSetPlayerModelSelf;
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText;
import net.rsprot.protocol.game.outgoing.logout.Logout;
import net.rsprot.protocol.game.outgoing.logout.LogoutTransfer;
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2;
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2;
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialFollows;
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV3;
import net.rsprot.protocol.game.outgoing.zone.payload.LocDel;
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame;
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript;
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2;
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy;
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStatV2;
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow;
import net.rsprot.protocol.game.outgoing.varp.VarpLarge;
import net.rsprot.protocol.game.outgoing.varp.VarpSmall;
import net.rsprot.protocol.message.OutgoingGameMessage;
import net.rsprot.protocol.loginprot.incoming.util.AuthenticationType;
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock;
import net.rsprot.protocol.loginprot.outgoing.LoginResponse;
import net.rsprot.protocol.loginprot.outgoing.util.AuthenticatorResponse;
import net.rsprot.protocol.message.IncomingGameMessage;
import net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepository;
import net.rsprot.protocol.message.codec.incoming.MessageConsumer;
import net.rsprot.protocol.message.codec.incoming.provider.GameMessageConsumerRepositoryProvider;

/** A single-account localhost revision 242 listener for testing the initial game packets. */
public final class LocalGameListener implements GameConnectionHandler<LocalGameListener.Player> {
    private static final String HOST = "127.0.0.1";
    private static final int PLAYER_INDEX = 1;
    // Revision 242 Toplevel and ToplevelOsrsStretch component IDs.
    private static final HudLayout FIXED_HUD = new HudLayout(548, 11, 25, 81, 41, 43, 52, 59);
    private static final HudLayout CLASSIC_HUD = new HudLayout(161, 96, 33, 76, 16, 18, 47, 54);
    private static final HudLayout MODERN_HUD = new HudLayout(164, 93, 33, 73, 16, 18, 41, 47);
    private static final int LAYOUT_OPTIONS = 1055;
    private static final int MODERN_SIDE_PANELS = 1 << 8; // SIDE_PANELS varbit 4607.
    private static final int SET_DEFAULT_ZOOM_LIMITS = 626;
    private static final int SET_WINDOW_LAYOUT = 3998;
    private static final int CHATBOX_INTERFACE = 162;
    private static final int ORBS_INTERFACE = 160;
    private static final int MAP_ORB = 55;
    private static final int WORLD_MAP = 595;
    private static final int OVERLAY_INTERFACE = 1;
    private static final int LOGOUT_TAB = 10;
    private static final int LOGOUT_INTERFACE = 182;
    private static final int WORLD_SWITCHER = 69;
    private static final int WORLD_ROW_CONTAINER = 18;
    // The revision 242 switcher client script creates one child per world ID, 0 through 900.
    private static final int LAST_WORLD_ROW = 900;
    private static final int SETTINGS_SIDE = 116;
    private static final int SETTINGS = 134;
    private static final int OPTION_RUN = 173;
    private static final int BANK = 12;
    private static final int BANK_SIDE = 15;
    private static final int DEPOSIT_BOX = 192;
    private static final int BOX_INVENTORY = 24;
    private static final int BOX_DEPOSIT_ALL = 31;
    private static final int BANK_DEPOSIT_ALL = 47;
    private static final int BANK_QUANTITY_VARP = 1666;
    private static final int DEPOSIT_BOX_MODE_VARP = 867;
    private static final int REQUESTED_QUANTITY_VARP = 304;
    private static final int AMOUNT_PROMPT_SCRIPT = 108;
    private static final int BANK_ITEMS = 12;
    private static final int BANK_INVENTORY = 3;
    private static final int INVENTORY_TAB = 3;
    private static final int INVENTORY = 149;
    private static final int INVENTORY_ITEMS = 0;
    private static final int USE_ON_NPC = 1 << 12;
    private static final int USE_ON_LOCATION = 1 << 13;
    private static final int USE_ON_WIDGET = 1 << 16;
    private static final int ACCEPT_WIDGET_TARGET = 1 << 21;
    private static final int INVENTORY_OPERATIONS = (1 << 10) - 1;
    private static final int DIALOGUE_CONTAINER = 568;
    private static final int NPC_DIALOGUE = 231;
    private static final int DIALOGUE_CONTINUE = 5;
    private static final int DIALOGUE_CHOICES = 219;
    private static final int DIALOGUE_OPTIONS_COMPONENT = 1;
    private static final int DIALOGUE_OPTIONS_SCRIPT = 58;
    private static final int MAX_RUN_ENERGY = 10_000;
    private static final int RUN_DRAIN = 67;
    private static final int RUN_REST_REGEN = 16;
    private static final int[] SIDE_TAB_INTERFACES = {
        593, // Combat
        320, // Skills
        399, // Quest list
        INVENTORY,
        387, // Equipment
        541, // Prayer
        218, // Magic spellbook
        7,   // Friends chat
        701, // Clan
        429, // Friends
        LOGOUT_INTERFACE,
        SETTINGS_SIDE,
        216, // Emotes
        239  // Music
    };
    private static final int CHARACTER_DESIGN = 679;
    private static final int DESIGN_CONFIRM = 74;
    private static final int DESIGN_PREVIEW = 73;
    private static final int CHAT_OPTIONS = 1021;
    // Revision 242 varbit 8119 (HAS_DISPLAYNAME_TRANSMITTER) is bit 31 of varp 1737.
    private static final int DISPLAY_NAME_TRANSMITTER = 1737;
    private static final int HAS_DISPLAY_NAME = 1 << 31;
    private static final int MAX_CHAT_LENGTH = 80;
    private static final int CHAT_PROMPT_INIT = 223;
    private static final int FIT_CHAT_MODAL = 1 << 28;
    private static final int CHATBOX_RELAYOUT = 113;
    private static final int TUTORIAL_STAGE_VARP = 281;
    private static final int BRONZE_AXE = 1351;
    private static final int TINDERBOX = 590;
    private static final int LOGS = 1511;
    private static final int STARTING_DOOR = 9398;
    private static final int STARTING_DOOR_X = 3098;
    private static final int STARTING_DOOR_Z = 3107;
    private static final int CHEF_DOOR = 9709;
    private static final int CHEF_EXIT = 9710;
    private static final int SURVIVAL_GATE_LEFT = 9470;
    private static final int SURVIVAL_GATE_RIGHT = 9708;
    private static final int TUTORIAL_FIRE = 9735;
    private static final int TUTORIAL_RANGE = 9736;
    private static final int SMALL_FISHING_NET = 303;
    private static final int RAW_SHRIMP = 317;
    private static final int SHRIMP = 315;
    private static final int FLOUR = 1933;
    private static final int WATER = 1929;
    private static final int EMPTY_POT = 1931;
    private static final int EMPTY_BUCKET = 1925;
    private static final int BREAD_DOUGH = 2307;
    private static final int BREAD = 2309;
    private static final int COPPER_ROCK = 10079;
    private static final int TIN_ROCK = 10080;
    private static final int COPPER_ORE = 436;
    private static final int TIN_ORE = 438;
    private static final int TUTORIAL_FURNACE = 10082;
    private static final int TUTORIAL_ANVIL = 2097;
    private static final int BRONZE_PICKAXE = 1265;
    private static final int HAMMER = 2347;
    private static final int BRONZE_BAR = 2349;
    private static final int BRONZE_DAGGER = 1205;
    private static final int SMITHING = 312;
    private static final int SMITHING_DAGGER = 9;
    private static final int SMITHING_BAR_TYPE = 210;
    private static final int SPAWN_X = LocalWorld.SPAWN_X;
    private static final int SPAWN_Z = LocalWorld.SPAWN_Z;
    private static final int SKILL_COUNT = 24;
    private static final int HITPOINTS_SKILL = 3;
    private static final int FISHING_SKILL = 10;
    private static final int SHRIMP_FISHING_XP = 10;
    private static final int WOODCUTTING_SKILL = 8;
    private static final int NORMAL_TREE_XP = 25;
    // 22 October 2025 tutorial change: four game ticks at 600 ms each.
    private static final long TUTORIAL_WOODCUT_TICKS = 4;
    private static final long TUTORIAL_FISH_TICKS = 4;
    private static final long TUTORIAL_MINE_TICKS = 4;
    private static final long TUTORIAL_COOK_TICKS = 3;
    private static final long TUTORIAL_SMELT_TICKS = 3;
    // Revision-242 animations exposed through RSProt PlayerAvatarExtendedInfo#setSequence.
    // IDs are RuneLite AnimationID mappings; timings are tutorial approximations, not certified OSRS rates.
    private static final int ANIM_BRONZE_AXE = 879;
    private static final int ANIM_SMALL_NET = 621;
    private static final int ANIM_BRONZE_PICKAXE = 625;
    private static final int ANIM_FIREMAKING = 733;
    private static final int ANIM_RANGE_COOKING = 896;
    private static final int ANIM_FIRE_COOKING = 897;
    private static final int ANIM_SMELTING = 899;
    private static final int[] STARTER_IDENT_KITS = {0, 10, 18, 26, 33, 36, 42};
    private static final int MASTER_HEADER_BYTES = 5;
    private static final int MASTER_ENTRY_BYTES = 8;
    private static final int BINARY_ARCHIVE = 10;
    private static final int HUFFMAN_GROUP = 1;

    private final String username;
    private final byte[] password;
    private final int[] cacheCrcs;
    private final LocalWorld world;
    private final WorldTransports transports;
    private LumbridgeNpcs npcs;
    private final int worldId;
    private final ScheduledExecutorService gameThread =
        Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "ksp-local-game"));
    private final java.util.Map<Integer,Player> players = new java.util.LinkedHashMap<>();
    // One pending skill action per connected player. Generation guards invalidate stale callbacks.
    private final Map<Integer,Long> gatheringActions = new HashMap<>();
    private long gatheringGeneration;
    private final Path playerData = Path.of(System.getenv().getOrDefault("KSP_PLAYER_DATA_DIR","data"));
    private final LocalAccounts accounts = new LocalAccounts(playerData);
    private long ticks;
    private final boolean managedWorld = System.getenv("KSP_LOCAL_WORLD_TYPE") != null;
    private final boolean membersWorld = "P2P".equals(System.getenv("KSP_LOCAL_WORLD_TYPE"));
    private final boolean member = "true".equalsIgnoreCase(System.getenv("KSP_LOCAL_LOGIN_MEMBER"));

    private LocalGameListener(String username, byte[] password, byte[] master, LocalWorld world, WorldTransports transports, int port) {
        this.world = world;
        this.transports = transports;
        this.worldId = managedWorld ? port - 43000 : -1;
        this.username = username;
        this.password = password.clone();
        cacheCrcs = new int[(master.length - MASTER_HEADER_BYTES) / MASTER_ENTRY_BYTES];
        ByteBuffer bytes = ByteBuffer.wrap(master);
        for (int archive = 0; archive < cacheCrcs.length; archive++) {
            cacheCrcs[archive] = bytes.getInt(MASTER_HEADER_BYTES + archive * MASTER_ENTRY_BYTES);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--generate-keypair".equals(args[0])) {
            generateKeypair(Path.of(args[1]));
            return;
        }
        if (args.length != 4) {
            throw new IllegalArgumentException(
                "Usage: LocalGameListener <cache-dir> <archived-255-255.dat> <rsa-private-pkcs8.pem> <port>");
        }
        int port = Integer.parseInt(args[3]);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Port must be 1024..65535");
        Path cache = Path.of(args[0]);
        byte[] master = Js5VerifiedMasterProbe.admit(cache, Path.of(args[1]));
        RsaKeyPair rsa = loadPrivateKey(Path.of(args[2]));
        var world = LocalWorld.load(cache);
        String username = requireEnvironment("KSP_LOCAL_LOGIN_USERNAME");
        byte[] password = requireEnvironment("KSP_LOCAL_LOGIN_PASSWORD").getBytes(StandardCharsets.UTF_8);
        var listener = new LocalGameListener(username, password, master, world, new WorldTransports(), port);
        Arrays.fill(password, (byte) 0);

        try (var cacheProvider = new VerifiedJs5GroupProvider(cache)) {
            byte[] masterFrame = frameMaster(master);
            Js5GroupProvider groups = (archive, group) -> {
                ByteBuf frame = archive == 255 && group == 255
                    ? Unpooled.wrappedBuffer(masterFrame)
                    : cacheProvider.provide(archive, group);
                // RSProt writes non-retained slices of one frame across multiple socket writes.
                // Each slice is released by Netty. Keep these unpooled heap frames alive
                // until RSProt drops its references, then let the GC reclaim their storage.
                return frame == null ? null : Unpooled.unreleasableBuffer(frame.asReadOnly());
            };
            listener.serve(port, rsa, loadHuffman(cacheProvider), groups);
        } finally {
            Arrays.fill(listener.password, (byte) 0);
        }
    }

    private void serve(int port, RsaKeyPair rsa, HuffmanCodecProvider huffman, Js5GroupProvider groups)
        throws Exception {
        var network = buildNetwork(port, rsa, huffman, groups);
        try {
            gameThread.submit(() -> {
                network.setCommunicationThread(Thread.currentThread(), false);
                LocalWorld.Scene scene = world.build(SPAWN_X, SPAWN_Z, membersWorld);
                if (!scene.walkable(0, SPAWN_X, SPAWN_Z)) throw new IOException("Lumbridge spawn is blocked");
                npcs = new LumbridgeNpcs(network.getNpcAvatarFactory(), scene);
                System.out.printf("KSP_LOCAL_NPCS_READY count=%d%n", npcs.size());
                return null;
            }).get();
            network.start();
            gameThread.scheduleAtFixedRate(this::tick, 600, 600, TimeUnit.MILLISECONDS);
            System.out.println("KSP_CHAT_FIX_V2_READY");
            System.out.printf("KSP_LOCAL_GAME_LISTENER_READY address=%s port=%d revision=242%n", HOST, port);
            var shutdown = new CountDownLatch(1);
            Thread shutdownHook = new Thread(() -> {
                shutdown.countDown();
                try {
                    gameThread.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "ksp-local-game-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            shutdown.await();
        } finally {
            stop(network);
        }
    }

    private NetworkService<Player> buildNetwork(int port, RsaKeyPair rsa, HuffmanCodecProvider huffman,
        Js5GroupProvider groups) {
        return new AbstractNetworkServiceFactory<Player>() {
            @Override public String getHost() { return HOST; }
            @Override public List<Integer> getPorts() { return List.of(port); }
            @Override public List<OldSchoolClientType> getSupportedClientTypes() {
                return List.of(OldSchoolClientType.DESKTOP);
            }
            @Override public RsaKeyPair getRsaKeyPair() { return rsa; }
            @Override public HuffmanCodecProvider getHuffmanCodecProvider() { return huffman; }
            @Override public Js5GroupProvider getJs5GroupProvider() { return groups; }
            @Override public GameMessageConsumerRepositoryProvider<Player> getGameMessageConsumerRepositoryProvider() {
                MessageConsumer<Player, IncomingGameMessage> consume = LocalGameListener.this::consume;
                var repository = new GameMessageConsumerRepository<Player>(Map.ofEntries(
                    Map.entry(MoveGameClick.class, consume),
                    Map.entry(MoveMinimapClick.class, consume),
                    Map.entry(OpNpcV2.class, consume),
                    Map.entry(OpNpcT.class, consume),
                    Map.entry(MessagePublic.class, consume),
                    Map.entry(SetChatFilterSettings.class, consume),
                    Map.entry(ResumePauseButton.class, consume),
                    Map.entry(ResumePCountDialog.class, consume),
                    Map.entry(OpLocV2.class, consume),
                    Map.entry(OpLocV3.class, consume),
                    Map.entry(OpLoc6V1.class, consume),
                    Map.entry(OpLoc6V2.class, consume),
                    Map.entry(MapBuildComplete.class, consume),
                    Map.entry(If1Button.class, consume),
                    Map.entry(If3Button.class, consume),
                    Map.entry(IfButtonT.class, consume),
                    Map.entry(OpLocTV1.class, consume),
                    Map.entry(OpLocTV2.class, consume),
                    Map.entry(CloseModal.class, consume),
                    Map.entry(WindowStatus.class, consume),
                    Map.entry(ClientCheat.class, consume)
                ), List.of());
                return () -> repository;
            }
            @Override public GameConnectionHandler<Player> getGameConnectionHandler() {
                return LocalGameListener.this;
            }
        }.build();
    }

    @Override
    public void onLogin(GameLoginResponseHandler<Player> response, LoginBlock<AuthenticationType> block) {
        LocalAccounts.Account account = null;
        boolean legacy = username.equalsIgnoreCase(block.getUsername());
        AuthenticationType authentication = block.getAuthentication();
        try {
            if (authentication instanceof AuthenticationType.PasswordAuthentication attempt) {
                byte[] supplied = attempt.getPassword().getData();
                if (legacy && MessageDigest.isEqual(password, supplied)) {
                    account = new LocalAccounts.Account(username,username,member);
                } else if (!legacy) {
                    account = accounts.authenticate(block.getUsername(),supplied);
                }
            }
        } catch (IOException failure) {
            System.err.println("KSP_LOCAL_ACCOUNT_LOOKUP_FAILED " + failure.getMessage());
        } finally {
            authentication.clear();
        }
        if (account == null) {
            response.writeFailedResponse(LoginResponse.InvalidUsernameOrPassword.INSTANCE);
            return;
        }
        if (membersWorld && !account.member()) {
            response.writeFailedResponse(LoginResponse.NeedMembersAccount.INSTANCE);
            return;
        }
        if (!block.getCrc().validate(cacheCrcs)) {
            response.writeFailedResponse(LoginResponse.ClientOutOfDate.INSTANCE);
            return;
        }
        var authenticated = account;
        gameThread.execute(() -> bootstrap(response, block, authenticated, legacy));
    }

    @Override
    public void onReconnect(GameLoginResponseHandler<Player> response, LoginBlock<XteaKey> block) {
        response.writeFailedResponse(LoginResponse.BadSessionId.INSTANCE);
    }

    private void bootstrap(GameLoginResponseHandler<Player> response, LoginBlock<AuthenticationType> block,
                           LocalAccounts.Account account, boolean legacy) {
        if (!response.getCtx().channel().isActive()) return;
        if (players.values().stream().anyMatch(player -> player.account().key().equalsIgnoreCase(account.key()))) {
            response.writeFailedResponse(LoginResponse.Duplicate.INSTANCE);
            return;
        }
        int index = PLAYER_INDEX;
        while (players.containsKey(index)) index++;
        if (index >= 2048) {
            response.writeFailedResponse(LoginResponse.ServerFull.INSTANCE);
            return;
        }
        var protocol = response.getNetworkService().getInfoProtocols();
        Infos infos = null;
        RebuildLoginV2 rebuild = null;
        Player registered = null;
        try {
            NewPlayerProfile onboarding = legacy ? null : new NewPlayerProfile(playerData,account.key(),world);
            int x = onboarding == null ? SPAWN_X : onboarding.x();
            int z = onboarding == null ? SPAWN_Z : onboarding.z();
            var scene = world.build(x,z,membersWorld);
            if (!scene.walkable(0,x,z)) throw new IOException("Saved starting tile is blocked");
            infos = protocol.alloc(index,OldSchoolClientType.DESKTOP);
            infos.updateRootCoord(0,x,z);
            infos.updateRootBuildAreaCenteredOnPlayer(x,z);
            var appearance = infos.getPlayerInfo().getAvatar().getExtendedInfo();
            appearance.setName(account.displayName());
            appearance.setCombatLevel(3);
            appearance.setMoveSpeed(1);
            if (onboarding == null) {
                for (int slot = 0; slot < STARTER_IDENT_KITS.length; slot++) {
                    appearance.setIdentKit(slot,STARTER_IDENT_KITS[slot]);
                }
            } else {
                onboarding.apply(appearance);
            }
            appearance.setBaseAnimationSet(808,823,819,820,821,822,824);
            rebuild = new RebuildLoginV2(x >> 3,z >> 3,0,infos.getPlayerInfo());
            var success = new LoginResponse.Ok(
                AuthenticatorResponse.NoAuthenticator.INSTANCE,0,false,index,account.member(),1L,1L,1L);
            LocalBank bank = new LocalBank(account.key(),legacy);
            Session<Player> session = response.writeSuccessfulResponse(success,block);
            HudLayout hud = block.getResizable() ? CLASSIC_HUD : FIXED_HUD;
            var player = new Player(index,account,session,protocol,infos,new Movement(scene,x,z),new Widgets(hud),bank,onboarding);
            registered = player;
            players.put(index,player);
            session.setDisconnectionHook(() -> gameThread.execute(() -> release(player)));
            session.queue(rebuild);
            rebuild = null;
            session.queue(new UpdateRunEnergy(MAX_RUN_ENERGY));
            session.queue(new VarpSmall(OPTION_RUN,0));
            session.queue(new VarpLarge(CHAT_OPTIONS,FIT_CHAT_MODAL));
            session.queue(new VarpLarge(DISPLAY_NAME_TRANSMITTER,HAS_DISPLAY_NAME));
            session.queue(new RunClientScript(SET_DEFAULT_ZOOM_LIMITS,List.of()));
            hud.queue(session);
            syncTutorialTabs(player);
            session.queue(bank.packet(false));
            for (int skill = 0; skill < SKILL_COUNT; skill++) {
                boolean hitpoints = skill == HITPOINTS_SKILL;
                int level = hitpoints ? 10 : 1;
                int xp = hitpoints ? 1154 : 0;
                if (skill == FISHING_SKILL && onboarding != null &&
                    (NewPlayerProfile.lessonOrder(onboarding.stage()) >= NewPlayerProfile.lessonOrder(NewPlayerProfile.VIEW_SKILLS))
                    && (NewPlayerProfile.lessonOrder(onboarding.stage()) >= NewPlayerProfile.lessonOrder(NewPlayerProfile.COOK_SHRIMP)
                        || bank.hasTutorialItem(RAW_SHRIMP))) {
                    xp = SHRIMP_FISHING_XP;
                }
                session.queue(new UpdateStatV2(skill,level,level,xp));
            }
            syncEquipment(player);
            if (onboarding != null) {
                if (onboarding.stage() == NewPlayerProfile.COMBAT_WIELD_DAGGER && bank.equippedWeapon() == BRONZE_DAGGER) {
                    if (!saveStage(player, NewPlayerProfile.COMBAT_GUIDE_RETURN)) sendTutorialProgress(player);
                } else if (onboarding.stage() == NewPlayerProfile.COMBAT_EQUIP_MELEE && tutorialMeleeGearReady(player)) {
                    if (!saveStage(player, NewPlayerProfile.COMBAT_VIEW_OPTIONS)) sendTutorialProgress(player);
                } else if (onboarding.stage() == NewPlayerProfile.COMBAT_EQUIP_RANGED && tutorialRangedGearReady(player)) {
                    if (!saveStage(player, NewPlayerProfile.COMBAT_RANGED_RAT)) sendTutorialProgress(player);
                } else if (onboarding.stage() == NewPlayerProfile.DESIGN) {
                    syncTutorialSettingsTab(player);
                    syncTutorialChatbox(player);
                    openCharacterDesign(player);
                } else if (onboarding.stage() == NewPlayerProfile.EXPERIENCE_SELECTION) {
                    sendTutorialProgress(player);
                    openExperienceSelection(player);
                } else sendTutorialProgress(player);
            }
            session.flush();
            reportPopulation();
            reportPlayer(player, true);
            System.out.printf("KSP_LOCAL_GAME_VIEWPORT_QUEUED interface=%d%n", hud.root());
            System.out.printf("KSP_LOCAL_GAME_BOOTSTRAP_QUEUED index=%d x=%d z=%d%n", index, x, z);
        } catch (RuntimeException | IOException failure) {
            if (rebuild != null) rebuild.release();
            if (registered != null) {
                release(registered);
            } else if (infos != null) {
                protocol.dealloc(infos);
            }
            response.getCtx().close();
            failure.printStackTrace(System.err);
        }
    }

    private void tick() {
        if (++ticks % 5 == 0 && managedWorld) System.out.println("KSP_LOCAL_WORLD_TICKS=" + ticks);
        if (players.isEmpty()) return;
        if (ticks % 2 == 0) {
            for (var player : List.copyOf(players.values())) {
                try {
                    if (player.bank().refreshIfModified()) {
                        player.session().queue(player.bank().packet(false));
                        syncEquipment(player);
                        if (player.widgets().bankGroup != 0) player.session().queue(player.bank().packet(true));
                    }
                } catch (IOException failure) {
                    System.err.println("KSP_ADMIN_CONTAINER_SYNC_FAILED player=" + player.account().key()
                        + " reason=" + failure.getClass().getSimpleName());
                }
            }
        }
        for (var player : List.copyOf(players.values())) {
            try {
                player.session().processIncomingPackets(player);
                if (player.widgets().exit != null) exitGame(player);
                else advanceMovement(player);
            } catch (RuntimeException failure) {
                disconnectFailedPlayer(player,failure);
            }
        }
        if (players.isEmpty()) return;
        npcs.tick(index -> players.values().stream().anyMatch(player -> player.movement().talkTo == index));
        players.values().iterator().next().protocol().update();
        for (var player : List.copyOf(players.values())) {
            try {
                var session = player.session();
                var world = player.infos().getPackets().getRootWorldInfoPackets();
                session.queue(PacketResultKt.getOrThrow(world.getWorldEntityInfo()));
                session.queue(world.getActiveWorld());
                session.queue(PacketResultKt.getOrThrow(world.getPlayerInfo()));
                session.queue(world.getNpcUpdateOrigin());
                session.queue(PacketResultKt.getOrThrow(world.getNpcInfo()));
                if (player.widgets().previewQueued) {
                    player.widgets().previewQueued = false;
                    previewCharacter(player);
                }
                session.flush();
            } catch (RuntimeException failure) {
                disconnectFailedPlayer(player,failure);
            }
        }
    }

    private void disconnectFailedPlayer(Player player, RuntimeException failure) {
        failure.printStackTrace(System.err);
        player.session().requestClose();
        release(player);
    }

    private void consume(Player player, IncomingGameMessage message) {
        if (player.widgets().exit != null) return;
        if ((designing(player) || selectingExperience(player)) && (message instanceof MoveGameClick || message instanceof MoveMinimapClick
            || message instanceof OpNpcV2 || message instanceof OpLocV2 || message instanceof OpLocV3)) return;
        switch (message) {
            case If3Button click -> widgetClick(player, click.getInterfaceId(), click.getComponentId(),
                click.getSub(), click.getOp(), click.getObj());
            case If1Button click -> widgetClick(player, click.getInterfaceId(), click.getComponentId(), -1, 1, -1);
            case CloseModal ignored -> {
                if (selectingExperience(player)) {
                    openExperienceSelection(player);
                    return;
                }
                closeSettings(player);
                closeWorldMap(player);
                closeBank(player);
                closeDialogue(player);
                closeSmithing(player);
                closeTutorialEquipmentStats(player);
            }
            case ResumePauseButton click -> {
                if (click.getInterfaceId() == NPC_DIALOGUE && click.getComponentId() == DIALOGUE_CONTINUE) {
                    continueDialogue(player);
                } else if (click.getInterfaceId() == DIALOGUE_CHOICES
                    && click.getComponentId() == DIALOGUE_OPTIONS_COMPONENT) {
                    chooseDialogue(player, click.getSub());
                }
            }
            case ResumePCountDialog amount -> resumeBankAmount(player, amount.getCount());
            case MessagePublic chat -> publicChat(player,chat);
            case SetChatFilterSettings filters -> player.widgets().publicChatFilter = filters.getPublicChatFilter();
            case WindowStatus window -> updateWindowMode(player, window.getWindowMode());
            case ClientCheat action -> {
                if (!action.getAutocomplete() && player.widgets().settings) {
                    switch (action.getCommand()) {
                        case "ksp-layout 0" -> setGameLayout(player, 0, FIXED_HUD);
                        case "ksp-layout 1" -> setGameLayout(player, 1, CLASSIC_HUD);
                        case "ksp-layout 2" -> setGameLayout(player, 2, MODERN_HUD);
                        default -> { }
                    }
                }
            }
            case MoveGameClick click -> walkTo(player, click.getX(), click.getZ());
            case MoveMinimapClick click -> walkTo(player, click.getX(), click.getZ());
            case IfButtonT click -> tutorialItemUse(player,click);
            case OpLocTV1 click -> tutorialItemOnLocation(player,click.getId(),click.getX(),click.getZ(),
                click.getSelectedInterfaceId(),click.getSelectedComponentId(),click.getSelectedSub(),click.getSelectedObj());
            case OpLocTV2 click -> tutorialItemOnLocation(player,click.getId(),click.getX(),click.getZ(),
                click.getSelectedInterfaceId(),click.getSelectedComponentId(),click.getSelectedSub(),click.getSelectedObj());
            case OpNpcT click -> {
                if (click.getIndex() >= npcs.size() || npcs.spawn(click.getIndex()).id() != 3317
                    || click.getSelectedInterfaceId() != 149 || click.getSelectedComponentId() != 0
                    || click.getSelectedObj() != SMALL_FISHING_NET
                    || !player.bank().hasTutorialItem(click.getSelectedSub(), SMALL_FISHING_NET)) return;
                tutorialFish(player, click.getIndex());
            }
            case OpNpcV2 click -> {
                int index = click.getIndex();
                if (click.getSubop() != 0 || index < 0 || index >= npcs.size()) return;
                int npcId = npcs.spawn(index).id();
                if (npcId == TUTORIAL_RAT && click.getOp() == 2) {
                    attackTutorialRat(player, index);
                    return;
                }
                if (npcId == 3317 && (click.getOp() == 1 || click.getOp() == 3)) {
                    tutorialFish(player,index);
                    return;
                }
                if (npcs.spawn(index).greeting().isEmpty()) return;
                boolean bank = click.getOp() == 3 && npcs.spawn(index).banker();
                if (click.getOp() == 1 || bank) talkTo(player, index, bank);
            }
            case OpLocV2 click -> useLocation(player, click.getId(), click.getX(), click.getZ(), click.getOp(), click.getSubop());
            case OpLocV3 click -> useLocation(player, click.getId(), click.getX(), click.getZ(), click.getOp(), click.getSubop());
            case OpLoc6V1 examine -> examineLocation(player, examine.getId());
            case OpLoc6V2 examine -> examineLocation(player, examine.getId());
            case MapBuildComplete ignored -> {
                syncChatPrompt(player);
                sendDoorChanges(player);
                sendTutorialFire(player,false);
                if (player.onboarding() != null) {
                    syncTutorialSettingsTab(player);
                    if (selectingExperience(player)) openExperienceSelection(player);
                }
                if (designing(player)) previewCharacter(player);
            }
            default -> { }
        }
    }

    private void syncChatPrompt(Player player) {
        // Refresh once the desktop client has built the map and can draw its chat prompt.
        player.session().queue(new VarpLarge(DISPLAY_NAME_TRANSMITTER,HAS_DISPLAY_NAME));
        player.session().queue(new RunClientScript(CHAT_PROMPT_INIT,List.of()));
        if (!player.widgets().chatPromptSynced) {
            player.widgets().chatPromptSynced = true;
            System.out.println("KSP_CHAT_PROMPT_SYNCED after=map-build");
        }
    }

    private void publicChat(Player sender, MessagePublic chat) {
        if (designing(sender)) return;
        if (chat.getType() > 1) {
            sendMessage(sender,"This world supports nearby public chat. Select All or Public to send a message.");
            return;
        }
        String text = chat.getMessage().strip();
        if (text.isEmpty() || text.length() > MAX_CHAT_LENGTH || text.codePoints().anyMatch(Character::isISOControl)) return;

        // A chatbox message also reaches the sender when the client suppresses its own avatar's chat block.
        String escaped = text.replace("<","\u0001").replace(">","<gt>").replace("\u0001","<lt>");
        var message = new MessageGame(chat.getType() == 1 ? 90 : 2,sender.account().displayName(),escaped);
        sender.session().queue(message);
        var origin = sender.movement();
        int recipients = 1;
        for (var viewer : players.values()) {
            var position = viewer.movement();
            int filter = viewer.widgets().publicChatFilter;
            if (viewer == sender || position.plane != origin.plane || filter != 0 && filter != 4
                || filter == 4 && chat.getType() != 1) continue;
            if (Math.max(Math.abs(position.x - origin.x),Math.abs(position.z - origin.z)) <= 15) {
                viewer.session().queue(message);
                recipients++;
            }
        }
        if (!sender.widgets().publicChatConfirmed) {
            sender.widgets().publicChatConfirmed = true;
            System.out.println("KSP_PUBLIC_CHAT_DELIVERED recipients=" + recipients);
        }
    }

    private void updateWindowMode(Player player, int mode) {
        if (mode == 1) changeHud(player, FIXED_HUD);
        if (mode == 2) changeHud(player, player.widgets().resizableHud);
    }

    private void setGameLayout(Player player, int choice, HudLayout hud) {
        if (hud != FIXED_HUD) player.widgets().resizableHud = hud;
        player.session().queue(new RunClientScript(SET_WINDOW_LAYOUT, List.of(choice)));
        changeHud(player, hud);
    }

    private void changeHud(Player player, HudLayout hud) {
        HudLayout previous = player.hud();
        if (previous == hud) return;
        closeTutorialEquipmentStats(player);
        player.widgets().hud = hud;
        player.widgets().tutorialTabMask = -1;
        var session = player.session();
        session.queue(new VarpLarge(LAYOUT_OPTIONS, hud == MODERN_HUD ? MODERN_SIDE_PANELS : 0));
        previous.moveTo(session, hud, designing(player) || player.widgets().settings || player.widgets().smithing || player.widgets().bankGroup != 0, player.widgets().map);
        if (player.onboarding() != null) {
            syncTutorialSettingsTab(player);
            syncTutorialTabs(player);
            syncTutorialChatbox(player);
            if (selectingExperience(player)) openExperienceSelection(player);
        } else if (player.widgets().bankGroup != BANK) {
            session.queue(inventoryItemEvents());
        }
        System.out.printf("KSP_LOCAL_GAME_LAYOUT root=%d%n", hud.root());
    }

    private static final int WORN_EQUIPMENT = 387;
    private static final int VIEW_EQUIPMENT_STATS = 1;
    private static final int EQUIPMENT_STATS = 84;
    private static final int EQUIPMENT_STATS_INVENTORY = 85;
    private static final int WIELD_OPTION = 2;

    private void openTutorialEquipmentStats(Player player) {
        int stage = player.onboarding().stage();
        if (stage == NewPlayerProfile.COMBAT_VIEW_STATS) {
            if (!saveStage(player, NewPlayerProfile.COMBAT_WIELD_DAGGER)) return;
        } else if (stage != NewPlayerProfile.COMBAT_WIELD_DAGGER && stage != NewPlayerProfile.COMBAT_GUIDE_RETURN) {
            return;
        }
        closeBank(player);
        closeSettings(player);
        closeSmithing(player);
        closeDialogue(player);
        if (player.widgets().map == MapMode.MAIN) closeWorldMap(player);
        player.widgets().equipmentStats = true;
        var hud = player.hud();
        var session = player.session();
        session.queue(new IfOpenSub(hud.root(), hud.mainModal(), EQUIPMENT_STATS, 0));
        session.queue(new IfOpenSub(hud.root(), hud.firstTab() + INVENTORY_TAB, EQUIPMENT_STATS_INVENTORY, OVERLAY_INTERFACE));
        session.queue(new RunClientScript(6007, List.of((EQUIPMENT_STATS_INVENTORY << 16) | INVENTORY_ITEMS, 1)));
        session.queue(new IfSetEventsV2(EQUIPMENT_STATS_INVENTORY, INVENTORY_ITEMS, 0, LocalBank.INVENTORY_SIZE - 1, 0, 1 << (WIELD_OPTION - 1)));
        session.queue(player.bank().packet(false));
        session.queue(player.bank().equipmentPacket());
        advanceTutorialEquipment(player);
    }

    private void closeTutorialEquipmentStats(Player player) {
        if (!player.widgets().equipmentStats) return;
        player.widgets().equipmentStats = false;
        var hud = player.hud();
        player.session().queue(new IfCloseSub(hud.root(), hud.mainModal()));
        hud.openInventory(player.session());
    }

    static IfSetEventsV2 inventoryItemEvents() {
        // The native inventory script supplies the item-specific labels. Revision 242
        // carries operation permissions separately from the Use target flags.
        int targets = USE_ON_NPC | USE_ON_LOCATION | USE_ON_WIDGET | ACCEPT_WIDGET_TARGET;
        return new IfSetEventsV2(INVENTORY, INVENTORY_ITEMS, 0, LocalBank.INVENTORY_SIZE - 1,
            targets, INVENTORY_OPERATIONS);
    }

    private static final int BRONZE_SHORTSWORD = 1277;
    private static final int WOODEN_SHIELD = 1171;

    private void syncEquipment(Player player) {
        var gear = player.bank();
        var appearance = player.infos().getPlayerInfo().getAvatar().getExtendedInfo();
        // Ring and ammunition slots are transmitted in the container but do not have appearance slots.
        for (int slot = 0; slot < 12; slot++) {
            if (!LocalBank.equipmentSlot(slot)) continue;
            int id = gear.equippedItem(slot);
            var definition = world.itemDefinition(id);
            appearance.setWornObj(slot, definition == null ? -1 : id,
                definition == null ? -1 : definition.wearPos2,
                definition == null ? -1 : definition.wearPos3);
        }
        player.session().queue(gear.equipmentPacket());
    }

    private boolean tutorialMeleeGearReady(Player player) {
        return player.bank().equippedWeapon() == BRONZE_SHORTSWORD
            && player.bank().equippedItem(LocalBank.SHIELD_SLOT) == WOODEN_SHIELD;
    }

    private boolean tutorialRangedGearReady(Player player) {
        return player.bank().equippedWeapon() == LocalBank.TUTORIAL_SHORTBOW
            && player.bank().equippedItem(LocalBank.AMMO_SLOT) == LocalBank.TUTORIAL_BRONZE_ARROW;
    }

    static boolean isEquipmentOperation(ItemDefinition item, int option) {
        if (item == null || !LocalBank.equipmentSlot(item.wearPos1)
                || item.notedTemplate != -1 || item.placeholderTemplateId != -1
                || option < 1 || option > item.interfaceOptions.length) return false;
        String action = item.interfaceOptions[option - 1];
        return "Wield".equalsIgnoreCase(action) || "Wear".equalsIgnoreCase(action)
            || "Equip".equalsIgnoreCase(action);
    }

    private void equipFromInventory(Player player, int slot, int item, int option) {
        int stage = player.onboarding() == null ? -1 : player.onboarding().stage();
        if (!TutorialTabPolicy.allowed(TutorialTabPolicy.allowedMask(stage), INVENTORY_TAB)) return;
        var gear = player.bank();
        if (!gear.hasTutorialItem(slot, item)) return;
        var definition = world.itemDefinition(item);
        if (!isEquipmentOperation(definition, option)) return;
        if (definition.members && !membersWorld) {
            sendMessage(player, "You can only equip this item on a members world.");
            return;
        }
        try {
            if (!gear.equip(slot, definition, world::itemDefinition)) {
                sendMessage(player, "You could not equip this item. Check that you have inventory space.");
                return;
            }
            player.session().queue(gear.packet(false));
            syncEquipment(player);
            advanceTutorialEquipment(player);
        } catch (IOException failure) {
            sendMessage(player, "Your equipment could not be saved. Try again.");
        }
    }

    private void advanceTutorialEquipment(Player player) {
        if (player.onboarding() == null) return;
        switch (player.onboarding().stage()) {
            case NewPlayerProfile.COMBAT_WIELD_DAGGER -> {
                if (player.bank().equippedWeapon() == BRONZE_DAGGER) saveStage(player, NewPlayerProfile.COMBAT_GUIDE_RETURN);
            }
            case NewPlayerProfile.COMBAT_EQUIP_MELEE -> {
                if (tutorialMeleeGearReady(player)) saveStage(player, NewPlayerProfile.COMBAT_VIEW_OPTIONS);
            }
            case NewPlayerProfile.COMBAT_EQUIP_RANGED -> {
                if (tutorialRangedGearReady(player)) saveStage(player, NewPlayerProfile.COMBAT_RANGED_RAT);
            }
            default -> { }
        }
    }

    private void widgetClick(Player player, int group, int component, int child, int option, int item) {
        if (player.onboarding() != null && group == WORN_EQUIPMENT && component == VIEW_EQUIPMENT_STATS
                && option == 1 && child == -1) {
            openTutorialEquipmentStats(player);
            return;
        }
        boolean inventory = group == INVENTORY && !player.widgets().equipmentStats
            || group == EQUIPMENT_STATS_INVENTORY && player.widgets().equipmentStats;
        if (player.widgets().bankGroup == 0 && inventory && component == INVENTORY_ITEMS) {
            equipFromInventory(player, child, item, option);
            return;
        }
        if (group == SMITHING) {
            if (player.widgets().smithing && component == SMITHING_DAGGER && option == 1 && child == -1
                && player.onboarding().stage() == NewPlayerProfile.SMITH_DAGGER) {
                closeSmithing(player);
                tutorialExchange(player,NewPlayerProfile.COMBAT_GUIDE,new int[]{BRONZE_BAR},BRONZE_DAGGER);
            }
            return;
        }
        if (group == CHARACTER_DESIGN) {
            if (designing(player) && option == 1 && child == -1) editCharacter(player,component);
            return;
        }
        if (designing(player) && group != LOGOUT_INTERFACE && group != WORLD_SWITCHER) return;
        if (player.onboarding() != null && group == player.hud().root() && option == 1 && child == -1) {
            int tab = tutorialTabForStone(player.hud(), component);
            if (tab >= 0 && !TutorialTabPolicy.allowed(
                    TutorialTabPolicy.allowedMask(player.onboarding().stage()), tab)) {
                System.out.printf("KSP_TUTORIAL_LOCKED_TAB stage=%d tab=%d widget=%d%n",
                    player.onboarding().stage(), tab, component);
                return;
            }
        }
        if (group == BANK || group == BANK_SIDE || group == DEPOSIT_BOX) {
            useBankWidget(player, group, component, child, option, item);
            return;
        }
        if (group == NPC_DIALOGUE && component == DIALOGUE_CONTINUE && option == 1) {
            continueDialogue(player);
            return;
        }
        if (group == ORBS_INTERFACE && component == MAP_ORB && child == -1) {
            if (option == 2) toggleWorldMap(player, MapMode.FLOATING);
            if (option == 3) toggleWorldMap(player, MapMode.MAIN);
            return;
        }
        if (option != 1) return;
        if (player.onboarding() != null && group == player.hud().root() && child == -1) {
            tutorialTabClick(player, component);
        }
        Widgets widgets = player.widgets();
        switch (group) {
            case LOGOUT_INTERFACE -> {
                if (widgets.switcher || child != -1) return;
                if (component == 8) widgets.exit = Logout.INSTANCE;
                if (component == 3) openSwitcher(player);
            }
            case WORLD_SWITCHER -> {
                if (!widgets.switcher) return;
                if (component == 25 && child == -1) widgets.exit = Logout.INSTANCE;
                if (component == 5 && child == -1) closeSwitcher(player);
                if (component == WORLD_ROW_CONTAINER && child >= 1 && child <= LAST_WORLD_ROW) {
                    requestWorldTransfer(player, child);
                }
            }
            case ORBS_INTERFACE -> {
                if (component == 28 && child == -1) toggleRun(player);
            }
            case SETTINGS_SIDE -> {
                if (child != -1 || tutorialSettingsHidden(player)) return;
                if (component == 30) toggleRun(player);
                if (component == 32 && !widgets.settings) {
                    closeBank(player);
                    if (widgets.map == MapMode.MAIN) closeWorldMap(player);
                    widgets.settings = true;

                    player.session().queue(new IfOpenSub(player.hud().root(), player.hud().mainModal(), SETTINGS, 0));
                }
            }
            case SETTINGS -> {
                if (component == 4 && child == -1) closeSettings(player);
            }
            case WORLD_MAP -> {
                if ((component == 4 || component == 38) && child == -1) closeWorldMap(player);
            }
            default -> { }
        }
    }

    private static int tutorialTabForStone(HudLayout hud, int component) {
        if (component == hud.settingsStone()) return TutorialTabPolicy.SETTINGS;
        int firstStone = switch (hud.root()) {
            case 548 -> 64;
            case 161 -> 59;
            case 164 -> 52;
            default -> -1;
        };
        if (firstStone >= 0 && component >= firstStone && component < firstStone + 7) {
            return component - firstStone;
        }
        return -1;
    }

    private void tutorialTabClick(Player player, int component) {
        // Revision 242 sends operation 1 from the Skills, Quests and Inventory stones in each HUD.
        int firstStone = switch (player.hud().root()) {
            case 548 -> 64;
            case 161 -> 59;
            case 164 -> 52;
            default -> throw new IllegalStateException("Unsupported tutorial HUD");
        };
        int stage = player.onboarding().stage();
        if (stage == NewPlayerProfile.GUIDE_SETTINGS && component == player.hud().settingsStone()) {
            saveStage(player,NewPlayerProfile.GUIDE_RETURN);
        } else if (stage == NewPlayerProfile.VIEW_INVENTORY && component == firstStone + 3) {
            saveStage(player,NewPlayerProfile.FISH);
        } else if (stage == NewPlayerProfile.VIEW_SKILLS && component == firstStone + 1) {
            saveStage(player,NewPlayerProfile.SURVIVAL_TOOLS);
        } else if (stage == NewPlayerProfile.VIEW_QUESTS && component == firstStone + 2) {
            saveStage(player,NewPlayerProfile.QUEST_GUIDE_RETURN);
        } else if (stage == NewPlayerProfile.COMBAT_VIEW_EQUIPMENT && component == firstStone + TutorialTabPolicy.EQUIPMENT) {
            saveStage(player,NewPlayerProfile.COMBAT_VIEW_STATS);
        } else if (stage == NewPlayerProfile.COMBAT_VIEW_OPTIONS && component == firstStone + TutorialTabPolicy.COMBAT
                && tutorialMeleeGearReady(player)) {
            saveStage(player,NewPlayerProfile.COMBAT_MELEE_RAT);
        }
    }

    private void openSwitcher(Player player) {
        if (!managedWorld) {
            sendMessage(player, "Use the Worlds Dashboard launcher to switch worlds.");
            return;
        }
        player.widgets().switcher = true;
        player.hud().queueLogoutTab(player.session(), WORLD_SWITCHER);
        player.session().queue(new IfSetEventsV2(WORLD_SWITCHER, WORLD_ROW_CONTAINER, 1, LAST_WORLD_ROW, 0, 1));
    }

    private void closeSwitcher(Player player) {
        player.widgets().switcher = false;
        player.widgets().requestGeneration++;
        player.widgets().transferPending = false;
        player.hud().queueLogoutTab(player.session(), LOGOUT_INTERFACE);
    }

    private void closeSettings(Player player) {
        if (!player.widgets().settings) return;
        player.widgets().settings = false;
        player.session().queue(new IfCloseSub(player.hud().root(), player.hud().mainModal()));
    }

    private void toggleWorldMap(Player player, MapMode mode) {
        MapMode current = player.widgets().map;
        closeWorldMap(player);
        if (current == mode) return;
        if (mode == MapMode.MAIN) {
            closeBank(player);
            closeSettings(player);
        }
        player.widgets().map = mode;
        HudLayout hud = player.hud();
        player.session().queue(new IfOpenSub(hud.root(), mode.component(hud), WORLD_MAP,
            mode == MapMode.MAIN ? 0 : OVERLAY_INTERFACE));
        System.out.printf("KSP_LOCAL_WORLD_MAP_OPEN mode=%s%n", mode);
    }

    private void closeWorldMap(Player player) {
        MapMode mode = player.widgets().map;
        if (mode == null) return;
        player.widgets().map = null;
        HudLayout hud = player.hud();
        player.session().queue(new IfCloseSub(hud.root(), mode.component(hud)));
    }

    private void requestWorldTransfer(Player player, int targetId) {
        Widgets widgets = player.widgets();
        if (targetId == worldId || widgets.transferPending) return;
        widgets.transferPending = true;
        int generation = ++widgets.requestGeneration;
        Thread.startVirtualThread(() -> {
            LocalWorldDirectory.World target = null;
            try {
                target = LocalWorldDirectory.find(targetId);
            } catch (IOException failure) {
                System.err.println("KSP_LOCAL_WORLD_DIRECTORY_UNAVAILABLE " + failure.getMessage());
            }
            LocalWorldDirectory.World resolved = target;
            gameThread.execute(() -> finishWorldTransfer(player, generation, resolved));
        });
    }

    private void finishWorldTransfer(Player player, int generation, LocalWorldDirectory.World target) {
        Widgets widgets = player.widgets();
        if (players.get(player.index()) != player || widgets.requestGeneration != generation || widgets.exit != null) return;
        widgets.transferPending = false;
        if (target == null) {
            sendMessage(player, "That world is currently unavailable.");
        } else if (target.members() && !player.account().member()) {
            sendMessage(player, "You need a members account to switch to that world.");
        } else if (!target.members() && !WorldAccess.freeTile(player.movement().x, player.movement().z)) {
            sendMessage(player, "You must move to a free-to-play area before switching to that world.");
        } else if (target.players() >= 1) {
            sendMessage(player, "That world already has an active local session.");
        } else {
            widgets.exit = new LogoutTransfer(HOST, target.id(), target.properties());
        }
    }

    private void exitGame(Player player) {
        var packet = player.widgets().exit;
        player.session().queue(packet);
        player.session().requestClose();
        release(player);
        if (packet instanceof LogoutTransfer transfer) {
            System.out.printf("KSP_LOCAL_WORLD_TRANSFER from=%d to=%d%n", worldId, transfer.getId());
        } else {
            System.out.println("KSP_LOCAL_LOGOUT");
        }
    }

    private void toggleRun(Player player) {
        Movement movement = player.movement();
        setRun(player, !movement.run && movement.energy >= RUN_DRAIN);
    }

    private void setRun(Player player, boolean enabled) {
        player.movement().run = enabled;
        player.session().queue(new VarpSmall(OPTION_RUN, enabled ? 1 : 0));
    }

    private void sendMessage(Player player, String text) {
        player.session().queue(new MessageGame(0, text));
    }

    private void walkTo(Player player, int x, int z) {
        cancelResourceAction(player,"walk");
        closeBank(player);
        closeDialogue(player);
        closeSmithing(player);
        Movement movement = player.movement();
        movement.talkTo = -1;
        if (player.onboarding() != null && !WorldAccess.tutorialTile(x,z)) {
            player.session().queue(new SetMapFlagV2());
            movement.steps.clear();
            movement.onArrival = null;
            sendMessage(player,"Finish the tutorial before leaving the island.");
            return;
        }
        if (!membersWorld && movement.scene.contains(x, z) && !WorldAccess.freeTile(x, z)) {
            movement.steps.clear();
            movement.onArrival = null;
            player.session().queue(new SetMapFlagV2());
            sendMessage(player, "That area is only available on members worlds.");
            return;
        }
        routeTo(player, (tx, tz) -> tx == x && tz == z, null);
    }

    private static final int TUTORIAL_RAT = 3313;
    private static final int TUTORIAL_RAT_MAX_HP = 3;
    private static final int RAT_ATTACK_ANIMATION = 4933;
    private static final int RAT_DEATH_ANIMATION = 4935;
    private static final int SWORD_ATTACK_ANIMATION = 390;
    private static final int TUTORIAL_COMBAT_HEALTH_BAR = 0;
    private static final int HEALTH_BAR_SCALE = 30;
    private static final long GAME_TICK_MILLIS = 600;
    private TutorialRatFight ratFight;

    private static final class TutorialRatFight {
        private final Player player;
        private final int index;
        private final NpcAvatar rat;
        private int ratHp = TUTORIAL_RAT_MAX_HP;

        private TutorialRatFight(Player player, int index, NpcAvatar rat) {
            this.player = player;
            this.index = index;
            this.rat = rat;
        }
    }

    private static boolean reachesTutorialRat(Movement movement, NpcAvatar rat, int x, int z) {
        if (movement.plane != rat.level() || !movement.scene.contains(rat.x(), rat.z())) return false;
        int targetX = Math.clamp(x, rat.x(), rat.x() + 1);
        int targetZ = Math.clamp(z, rat.z(), rat.z() + 1);
        return Math.abs(x - targetX) + Math.abs(z - targetZ) == 1
            && movement.scene.canTalk(movement.plane, x, z, targetX, targetZ);
    }

    private boolean canFightTutorialRat(Player player, boolean ranged) {
        if (player.onboarding() == null) return false;
        int stage = player.onboarding().stage();
        return ranged
            ? stage == NewPlayerProfile.COMBAT_RANGED_RAT && tutorialRangedGearReady(player)
            : stage == NewPlayerProfile.COMBAT_MELEE_RAT && tutorialMeleeGearReady(player);
    }

    private static final int BOW_ATTACK_ANIMATION = 426;

    private static boolean canAttackTutorialRatFrom(Movement movement, NpcAvatar rat, int x, int z, boolean ranged) {
        if (!ranged) return reachesTutorialRat(movement, rat, x, z);
        // The south fence provides a firing line from the instructor's side of the pen.
        return movement.plane == rat.level() && movement.scene.contains(rat.x(), rat.z())
            && z == 9509 && x >= 3102 && x <= 3104 && movement.scene.walkable(movement.plane, x, z);
    }

    private void attackTutorialRat(Player player, int index) {
        boolean ranged = player.onboarding() != null && player.onboarding().stage() == NewPlayerProfile.COMBAT_RANGED_RAT;
        if (!canFightTutorialRat(player, ranged)) {
            if (ranged) sendMessage(player, "Equip your shortbow and bronze arrows first.");
            return;
        }
        if (ratFight != null) {
            if (ratFight.player != player) sendMessage(player, "This rat is occupied. Try again in a moment.");
            return;
        }
        NpcAvatar rat = npcs.avatar(index);
        Movement movement = player.movement();
        if (rat == null || movement.plane != rat.level() || !movement.scene.contains(rat.x(), rat.z())) return;
        cancelResourceAction(player, "combat");
        closeTutorialEquipmentStats(player);
        closeBank(player);
        closeDialogue(player);
        routeTo(player, (x, z) -> canAttackTutorialRatFrom(movement, rat, x, z, ranged), () -> {
            if (ratFight != null || npcs.avatar(index) != rat || !canFightTutorialRat(player, ranged)) return;
            TutorialRatFight fight = new TutorialRatFight(player, index, rat);
            ratFight = fight;
            tutorialRatRound(fight, ranged);
        });
    }

    private void tutorialRatRound(TutorialRatFight fight, boolean ranged) {
        if (ratFight != fight) return;
        Player player = fight.player;
        Movement movement = player.movement();
        if (players.get(player.index()) != player || !canFightTutorialRat(player, ranged)
                || !movement.steps.isEmpty() || !canAttackTutorialRatFrom(movement, fight.rat, movement.x, movement.z, ranged)) {
            cancelTutorialRatFight(player);
            return;
        }
        if (ranged) {
            try {
                if (!player.bank().consumeTutorialArrow()) {
                    sendMessage(player, "You need bronze arrows to continue.");
                    cancelTutorialRatFight(player);
                    return;
                }
                player.session().queue(player.bank().equipmentPacket());
            } catch (IOException failure) {
                sendMessage(player, "Your ammunition could not be saved. Please try again.");
                cancelTutorialRatFight(player);
                return;
            }
        }
        int nextStage = ranged ? NewPlayerProfile.COMBAT_BANK : NewPlayerProfile.COMBAT_RANGED_GUIDE;
        if (fight.ratHp == 1 && !saveStage(player, nextStage)) {
            cancelTutorialRatFight(player);
            return;
        }
        var playerInfo = player.infos().getPlayerInfo().getAvatar().getExtendedInfo();
        var ratInfo = fight.rat.getExtendedInfo();
        playerInfo.setFaceNpc(fight.index, false, 0, 0);
        playerInfo.setSequence(ranged ? BOW_ATTACK_ANIMATION : SWORD_ATTACK_ANIMATION, 0);
        int previousRatHp = fight.ratHp--;
        ratInfo.addHeadBar(player.index(), TUTORIAL_COMBAT_HEALTH_BAR, TUTORIAL_COMBAT_HEALTH_BAR,
            previousRatHp * HEALTH_BAR_SCALE / TUTORIAL_RAT_MAX_HP,
            fight.ratHp * HEALTH_BAR_SCALE / TUTORIAL_RAT_MAX_HP, 0, 20);
        if (fight.ratHp == 0) {
            ratInfo.setSequence(RAT_DEATH_ANIMATION, 0);
            ratInfo.resetFacing();
            playerInfo.resetFacing();
            gameThread.schedule(() -> npcs.despawnTutorialRat(fight.index, fight.rat), 2 * GAME_TICK_MILLIS, TimeUnit.MILLISECONDS);
            gameThread.schedule(() -> {
                npcs.respawnTutorialRat(fight.index);
                if (ratFight == fight) ratFight = null;
            }, 8 * GAME_TICK_MILLIS, TimeUnit.MILLISECONDS);
            return;
        }
        if (!ranged) {
            ratInfo.setFacePlayer(player.index(), false, 0, 0);
            ratInfo.setSequence(RAT_ATTACK_ANIMATION, 0);
            int previousPlayerHp = movement.tutorialHitpoints;
            movement.tutorialHitpoints = Math.max(1, previousPlayerHp - 1);
            playerInfo.addHeadBar(player.index(), TUTORIAL_COMBAT_HEALTH_BAR, TUTORIAL_COMBAT_HEALTH_BAR,
                previousPlayerHp * HEALTH_BAR_SCALE / 10, movement.tutorialHitpoints * HEALTH_BAR_SCALE / 10, 0, 20);
            player.session().queue(new UpdateStatV2(HITPOINTS_SKILL, movement.tutorialHitpoints, 10, 1154));
        }
        gameThread.schedule(() -> tutorialRatRound(fight, ranged), 4 * GAME_TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void cancelTutorialRatFight(Player player) {
        TutorialRatFight fight = ratFight;
        if (fight == null || fight.player != player || fight.ratHp == 0) return;
        ratFight = null;
        fight.rat.getExtendedInfo().resetFacing();
        fight.rat.getExtendedInfo().removeHeadBar(TUTORIAL_COMBAT_HEALTH_BAR);
        if (players.get(player.index()) == player) {
            player.infos().getPlayerInfo().getAvatar().getExtendedInfo().resetFacing();
        }
    }

    private void talkTo(Player player, int index, boolean openBank) {
        cancelResourceAction(player,"talk");
        Movement movement = player.movement();
        NpcAvatar npc = npcs.avatar(index);
        if (movement.plane != npc.level() || !movement.scene.contains(npc.x(), npc.z())) return;
        closeBank(player);
        closeDialogue(player);
        movement.talkTo = index;
        routeTo(player, (x, z) -> movement.scene.canTalk(movement.plane, x, z, npc.x(), npc.z()),
            () -> {
                if (openBank) openBank(player, BANK);
                else openDialogue(player, npcs.spawn(index));
            });
    }

    private void openDialogue(Player player, LumbridgeNpcs.Spawn npc) {
        var session = player.session();
        player.widgets().dialogue = npc;
        player.widgets().dialogueOptions = false;
        syncTutorialChatbox(player);
        session.queue(new IfOpenSub(CHATBOX_INTERFACE, DIALOGUE_CONTAINER, NPC_DIALOGUE, 0));
        session.queue(new IfSetNpcHead(NPC_DIALOGUE, 2, npc.id()));
        session.queue(new IfSetText(NPC_DIALOGUE, 4, npc.name()));
        session.queue(new IfSetText(NPC_DIALOGUE, 6, tutorialDialogue(player,npc)));
        session.queue(new IfSetText(NPC_DIALOGUE, DIALOGUE_CONTINUE, "Click here to continue"));
        session.queue(new RunClientScript(CHATBOX_RELAYOUT,List.of()));
    }

    private void continueDialogue(Player player) {
        var widgets = player.widgets();
        var npc = widgets.dialogue;
        if (npc == null || widgets.dialogueOptions) return;
        closeDialogue(player);
        if (!npc.banker()) {
            continueTutorial(player,npc);
            return;
        }
        openTutorialChoices(player,npc,"Select an Option", "I'd like to access my bank.|No thanks.",2);
    }

    private void openTutorialChoices(Player player, LumbridgeNpcs.Spawn npc,
                                     String heading,String options,int count) {
        var widgets = player.widgets();
        widgets.dialogue = npc;
        widgets.dialogueOptions = true;
        var session = player.session();
        syncTutorialChatbox(player);
        session.queue(new IfOpenSub(CHATBOX_INTERFACE, DIALOGUE_CONTAINER, DIALOGUE_CHOICES, 0));
        session.queue(new RunClientScript(DIALOGUE_OPTIONS_SCRIPT, List.of(heading,options)));
        session.queue(new IfSetEventsV2(DIALOGUE_CHOICES, DIALOGUE_OPTIONS_COMPONENT, 1, count, 1, 0));
        session.queue(new RunClientScript(CHATBOX_RELAYOUT,List.of()));
    }

    private void chooseDialogue(Player player, int choice) {
        var npc = player.widgets().dialogue;
        if (!player.widgets().dialogueOptions || (npc == null && !selectingExperience(player))) return;
        if (selectingExperience(player)) {
            if (!player.onboarding().chooseExperience(choice)) return;
            player.widgets().dialogue = null;
            player.widgets().dialogueOptions = false;
            player.session().queue(new IfCloseSub(player.hud().root(),player.hud().mainModal()));
            saveStage(player,NewPlayerProfile.GUIDE);
            return;
        }
        if (!npc.banker() || choice < 1 || choice > 2) return;
        closeDialogue(player);
        if (choice == 1) openBank(player, BANK);
    }

    private void closeDialogue(Player player) {
        if (player.widgets().dialogue == null) return;
        player.widgets().dialogue = null;
        player.widgets().dialogueOptions = false;
        player.session().queue(new IfCloseSub(CHATBOX_INTERFACE, DIALOGUE_CONTAINER));
        player.session().queue(new RunClientScript(CHATBOX_RELAYOUT,List.of()));
        syncTutorialChatbox(player);
    }

    private boolean selectingExperience(Player player) {
        return player.onboarding() != null
            && player.onboarding().stage() == NewPlayerProfile.EXPERIENCE_SELECTION;
    }

    // Temporary modal implementation: the authentic 2025 full-screen interface ID,
    // its controls and progress pips still require verification from revision 242 cache.
    // This is deliberately separate from the Gielinor Guide/NPC dialogue handler.
    private void openExperienceSelection(Player player) {
        if (!selectingExperience(player)) return;
        var hud = player.hud();
        player.widgets().dialogue = null;
        player.widgets().dialogueOptions = true;
        syncTutorialChatbox(player);
        player.session().queue(new IfOpenSub(hud.root(),hud.mainModal(),DIALOGUE_CHOICES,0));
        player.session().queue(new RunClientScript(DIALOGUE_OPTIONS_SCRIPT,List.of(
            "How familiar are you with Old School RuneScape?",
            "I'm brand new.|I've played before, but not recently.|I'm an experienced player.")));
        player.session().queue(new IfSetEventsV2(DIALOGUE_CHOICES,DIALOGUE_OPTIONS_COMPONENT,1,3,1,0));
    }

    private boolean designing(Player player) {
        return player.onboarding() != null && player.onboarding().stage() == NewPlayerProfile.DESIGN;
    }

    private void openCharacterDesign(Player player) {
        var hud = player.hud();
        player.session().queue(new IfOpenSub(hud.root(),hud.mainModal(),CHARACTER_DESIGN,0));
        int[] controls = {15,16,19,20,23,24,27,28,31,32,35,36,39,40,46,47,50,51,54,55,58,59,62,63,68,69,74};
        for (int component : controls) {
            player.session().queue(new IfSetEventsV2(CHARACTER_DESIGN,component,-1,-1,0,1));
        }
        previewCharacter(player);
    }

    private void previewCharacter(Player player) {
        player.session().queue(new IfSetPlayerModelSelf(CHARACTER_DESIGN,DESIGN_PREVIEW,false));
    }

    private void editCharacter(Player player, int component) {
        if (component == DESIGN_CONFIRM) {
            if (saveStage(player,NewPlayerProfile.EXPERIENCE_SELECTION)) {
                player.session().queue(new IfCloseSub(player.hud().root(),player.hud().mainModal()));
                openExperienceSelection(player);
            }
        } else if (player.onboarding().edit(component,world)) {
            player.onboarding().apply(player.infos().getPlayerInfo().getAvatar().getExtendedInfo());
            player.widgets().previewQueued = true;
        }
    }

    private boolean saveStage(Player player, int stage) {
        try {
            player.onboarding().save(stage,player.movement().x,player.movement().z);
            sendTutorialProgress(player);
            return true;
        } catch (IOException failure) {
            sendMessage(player,"Tutorial progress could not be saved. Try again.");
            return false;
        }
    }

    private boolean tutorialSettingsHidden(Player player) {
        var tutorial = player.onboarding();
        return tutorial != null && (tutorial.stage() == NewPlayerProfile.DESIGN || tutorial.stage() == NewPlayerProfile.EXPERIENCE_SELECTION || tutorial.stage() == NewPlayerProfile.GUIDE);
    }

    /**
     * Keep stone/button backgrounds intact. Only the side-panel subinterfaces
     * are closed until their lessons are unlocked. The separate root icon sprite
     * layers must be mapped from the actual cache before hiding them; guessing
     * their component IDs would remove stone backgrounds in some layouts.
     */
    private void syncTutorialTabs(Player player) {
        int mask = TutorialTabPolicy.allowedMask(
            player.onboarding() == null ? -1 : player.onboarding().stage());
        Widgets widgets = player.widgets();
        int old = widgets.tutorialTabMask;
        if (old == mask) return;
        HudLayout hud = player.hud();
        for (int tab = 0; tab < SIDE_TAB_INTERFACES.length; tab++) {
            boolean allow = TutorialTabPolicy.allowed(mask,tab);
            if (old >= 0 && allow == TutorialTabPolicy.allowed(old,tab)) continue;
            // The root has a separate sprite-only child over each stone button.
            // Hide ONLY the sprite overlay, retaining the native stone and input area.
            int iconComponent = TutorialTabIcons.component(hud.root(), tab);
            if (iconComponent >= 0) {
                player.session().queue(new IfSetHide(hud.root(), iconComponent, !allow));
                System.out.printf("KSP_TUTORIAL_TAB_ICON viewport=%d tab=%d icon=%d visible=%s%n",
                    hud.root(), tab, iconComponent, allow);
            }
            if (allow) {
                if (tab == INVENTORY_TAB) {
                    if (widgets.bankGroup != BANK && !widgets.equipmentStats) hud.openInventory(player.session());
                } else {
                    player.session().queue(new IfOpenSub(hud.root(),hud.firstTab() + tab,
                        SIDE_TAB_INTERFACES[tab],OVERLAY_INTERFACE));
                }
            } else {
                player.session().queue(new IfCloseSub(hud.root(),hud.firstTab() + tab));
            }
        }
        widgets.tutorialTabMask = mask;
        System.out.printf("KSP_TUTORIAL_TABS stage=%d allowedMask=0x%04X viewport=%d%n",
            player.onboarding() == null ? -1 : player.onboarding().stage(),mask,hud.root());
    }

    private void syncTutorialSettingsTab(Player player) {
        var hud = player.hud();
        boolean hidden = tutorialSettingsHidden(player);
        // Root stone remains visible throughout the tutorial. Only its sprite
        // overlay disappears until Gielinor Guide unlocks Settings.
        player.session().queue(new IfSetHide(hud.root(),hud.settingsIcon(),hidden));
    }

    /**
     * The tutorial objective display is separate from the chatbox. Keep chat hidden
     * for onboarding activities, but make it visible while an NPC conversation is
     * open. Restore normal chat after the tutorial is completed.
     *
     * IfSetHide is sent to the active viewport's chatbox component rather than
     * Chatbox (162) itself; the latter is a top-level subinterface, not a component.
     */
    private void syncTutorialChatbox(Player player) {
        boolean hasDialogue = player.widgets().dialogue != null;
        boolean hide = player.onboarding() != null && !hasDialogue;
        HudLayout hud = player.hud();
        player.session().queue(new IfSetHide(hud.root(),hud.chat(),hide));
        System.out.printf("KSP_TUTORIAL_CHATBOX visible=%s stage=%d viewport=%d%n",
            !hide,player.onboarding()==null ? -1 : player.onboarding().stage(),hud.root());
    }

    private void sendTutorialProgress(Player player) {
        int stage = player.onboarding().stage();
        int varp = switch (stage) {
            case NewPlayerProfile.EXPERIENCE_SELECTION -> 0;
            case NewPlayerProfile.GUIDE -> 0;
            case NewPlayerProfile.GUIDE_SETTINGS -> 3;
            case NewPlayerProfile.GUIDE_RETURN -> 7;
            case NewPlayerProfile.SURVIVAL -> 10;
            case NewPlayerProfile.VIEW_INVENTORY -> 20;
            case NewPlayerProfile.FISH -> 30;
            case NewPlayerProfile.VIEW_SKILLS -> 40;
            case NewPlayerProfile.SURVIVAL_TOOLS -> 50;
            case NewPlayerProfile.CHOP_TREE -> 60;
            case NewPlayerProfile.FIRE -> 70;
            case NewPlayerProfile.COOK_SHRIMP -> 80;
            case NewPlayerProfile.CHEF -> 90;
            case NewPlayerProfile.MIX_DOUGH -> 120;
            case NewPlayerProfile.BAKE_BREAD -> 130;
            case NewPlayerProfile.QUEST_GUIDE -> 160;
            case NewPlayerProfile.VIEW_QUESTS -> 170;
            case NewPlayerProfile.QUEST_GUIDE_RETURN -> 180;
            case NewPlayerProfile.MINING_GUIDE -> 200;
            case NewPlayerProfile.MINE_ORES -> 220;
            case NewPlayerProfile.SMELT_BRONZE -> 240;
            case NewPlayerProfile.COLLECT_HAMMER -> 240;
            case NewPlayerProfile.SMITH_DAGGER -> 250;
            // Keep the existing combat lesson marker until revision 242 substep values are verified.
            case NewPlayerProfile.COMBAT_GUIDE, NewPlayerProfile.COMBAT_VIEW_EQUIPMENT,
                NewPlayerProfile.COMBAT_VIEW_STATS, NewPlayerProfile.COMBAT_WIELD_DAGGER,
                NewPlayerProfile.COMBAT_GUIDE_RETURN, NewPlayerProfile.COMBAT_EQUIP_MELEE,
                NewPlayerProfile.COMBAT_VIEW_OPTIONS, NewPlayerProfile.COMBAT_MELEE_RAT,
                NewPlayerProfile.COMBAT_RANGED_GUIDE, NewPlayerProfile.COMBAT_EQUIP_RANGED,
                NewPlayerProfile.COMBAT_RANGED_RAT, NewPlayerProfile.COMBAT_BANK -> 260;
            default -> 0;
        };
        if (varp > 127) player.session().queue(new VarpLarge(TUTORIAL_STAGE_VARP,varp));
        else player.session().queue(new VarpSmall(TUTORIAL_STAGE_VARP,varp));
        syncTutorialSettingsTab(player);
        syncTutorialTabs(player);
        syncTutorialChatbox(player);
        String instruction = switch (stage) {
            case NewPlayerProfile.EXPERIENCE_SELECTION -> "Choose your previous game experience before starting Tutorial Island.";
            case NewPlayerProfile.GUIDE -> "Speak to the Gielinor Guide.";
            case NewPlayerProfile.GUIDE_RETURN -> "Speak to the Gielinor Guide again.";
            case NewPlayerProfile.GUIDE_SETTINGS -> "Open the Settings tab using the wrench icon.";
            case NewPlayerProfile.SURVIVAL -> "Open the door and find the Survival Expert.";
            case NewPlayerProfile.CHOP_TREE -> "Chop down a tree near the Survival Expert.";
            case NewPlayerProfile.FIRE -> "Use your tinderbox on the logs.";
            case NewPlayerProfile.VIEW_INVENTORY -> "Open your Inventory to see the net.";
            case NewPlayerProfile.FISH -> "Use the fishing spot to catch raw shrimp with your net.";
            case NewPlayerProfile.VIEW_SKILLS -> "Open your Skills tab to see your Fishing skill.";
            case NewPlayerProfile.SURVIVAL_TOOLS -> "Speak to the Survival Expert again.";
            case NewPlayerProfile.COOK_SHRIMP -> "Use the raw shrimp on your fire.";
            case NewPlayerProfile.CHEF -> "Visit the Master Chef to learn how to bake bread.";
            case NewPlayerProfile.MIX_DOUGH -> "Use the pot of flour on the bucket of water.";
            case NewPlayerProfile.BAKE_BREAD -> "Use your bread dough on the range.";
            case NewPlayerProfile.QUEST_GUIDE, NewPlayerProfile.QUEST_GUIDE_RETURN -> "Speak to the Quest Guide.";
            case NewPlayerProfile.VIEW_QUESTS -> "Open your Quest List.";
            case NewPlayerProfile.MINING_GUIDE -> "Climb down the ladder and speak to the Mining Instructor.";
            case NewPlayerProfile.MINE_ORES -> "Mine one copper ore and one tin ore.";
            case NewPlayerProfile.SMELT_BRONZE -> "Use the furnace to smelt a bronze bar.";
            case NewPlayerProfile.COLLECT_HAMMER -> "Show the bronze bar to the Mining Instructor.";
            case NewPlayerProfile.SMITH_DAGGER -> "Use the anvil and smith a bronze dagger.";
            case NewPlayerProfile.COMBAT_GUIDE -> "Open the eastern gate and speak to the Combat Instructor.";
            case NewPlayerProfile.COMBAT_VIEW_EQUIPMENT -> "Open the Worn Equipment tab.";
            case NewPlayerProfile.COMBAT_VIEW_STATS -> "Select View equipment stats.";
            case NewPlayerProfile.COMBAT_WIELD_DAGGER -> "Wield the bronze dagger from your inventory.";
            case NewPlayerProfile.COMBAT_GUIDE_RETURN, NewPlayerProfile.COMBAT_RANGED_GUIDE -> "Speak to the Combat Instructor again.";
            case NewPlayerProfile.COMBAT_EQUIP_MELEE -> "Equip the bronze shortsword and wooden shield.";
            case NewPlayerProfile.COMBAT_VIEW_OPTIONS -> "Open the Combat Options tab.";
            case NewPlayerProfile.COMBAT_MELEE_RAT -> "Enter the rat pen and defeat a giant rat with your sword.";
            case NewPlayerProfile.COMBAT_EQUIP_RANGED -> "Equip the shortbow and bronze arrows.";
            case NewPlayerProfile.COMBAT_RANGED_RAT -> "Stand outside the pen and shoot a giant rat through the fence.";
            case NewPlayerProfile.COMBAT_BANK -> "Climb the ladder to continue to the bank.";
            default -> "";
        };
        // The revision-242 Tutorial Controls Guide is driven by tutorial progress varp 281.
        // Do not flood the standard chatbox with every new tutorial objective.
        if (!instruction.isEmpty()) System.out.printf("KSP_TUTORIAL_OBJECTIVE stage=%d instruction=%s%n",stage,instruction);
        int hintNpc = switch (stage) {
            case NewPlayerProfile.GUIDE, NewPlayerProfile.GUIDE_RETURN -> 3308;
            case NewPlayerProfile.SURVIVAL, NewPlayerProfile.SURVIVAL_TOOLS -> 8503;
            case NewPlayerProfile.FISH -> 3317;
            case NewPlayerProfile.CHEF -> 3305;
            case NewPlayerProfile.QUEST_GUIDE, NewPlayerProfile.QUEST_GUIDE_RETURN -> 3312;
            case NewPlayerProfile.MINING_GUIDE, NewPlayerProfile.COLLECT_HAMMER -> 3311;
            case NewPlayerProfile.COMBAT_GUIDE, NewPlayerProfile.COMBAT_GUIDE_RETURN,
                NewPlayerProfile.COMBAT_RANGED_GUIDE -> 3307;
            default -> -1;
        };
        for (int index = 0; index < npcs.size(); index++) {
            if (npcs.spawn(index).id() != hintNpc) continue;
            player.session().queue(new HintArrow(new HintArrow.NpcHintArrow(index)));
            return;
        }
        player.session().queue(new HintArrow(HintArrow.ResetHintArrow.INSTANCE));
    }

    private String tutorialDialogue(Player player, LumbridgeNpcs.Spawn npc) {
        if (player.onboarding() == null) return npc.greeting();
        int stage = player.onboarding().stage();
        if (npc.id() == 3307) {
            return switch (stage) {
                case NewPlayerProfile.COMBAT_GUIDE, NewPlayerProfile.COMBAT_VIEW_EQUIPMENT ->
                    "First, open the Worn Equipment tab. It shows the items you are wearing or wielding.";
                case NewPlayerProfile.COMBAT_VIEW_STATS ->
                    "Choose View equipment stats at the bottom of that tab.";
                case NewPlayerProfile.COMBAT_WIELD_DAGGER ->
                    "Wield the bronze dagger you made, then speak to me again.";
                default -> npc.greeting();
            };
        }
        if (npc.id() == 3308 && stage == NewPlayerProfile.GUIDE_RETURN) {
            return "You opened your settings. Now leave through the door and find the Survival Expert.";
        }
        if (npc.id() == 3312 && (stage == NewPlayerProfile.QUEST_GUIDE || stage == NewPlayerProfile.VIEW_QUESTS)) {
            return "Open your Quest List to see where you can track the quests you discover.";
        }
        if (npc.id() == 3312 && stage == NewPlayerProfile.QUEST_GUIDE_RETURN) {
            return "Your Quest List keeps track of adventures. When you are ready, climb down the ladder to meet the Mining Instructor.";
        }
        if (npc.id() == 3311 && stage == NewPlayerProfile.MINING_GUIDE) {
            return "Use this bronze pickaxe to mine tin and copper. Then smelt both ores together in the furnace.";
        }
        if (npc.id() == 3311 && stage == NewPlayerProfile.COLLECT_HAMMER) {
            return "You made a bronze bar. Take this hammer and use the anvil to smith a bronze dagger.";
        }
        if (npc.id() == 8503 && stage == NewPlayerProfile.SURVIVAL) {
            return "Take this small fishing net. Open your inventory to see it, then try catching shrimp in the pond.";
        }
        if (npc.id() == 8503 && stage == NewPlayerProfile.SURVIVAL_TOOLS) {
            return "You caught a shrimp. Take this axe and tinderbox. Start by chopping a nearby tree.";
        }
        return npc.greeting();
    }

    private void continueTutorial(Player player, LumbridgeNpcs.Spawn npc) {
        if (player.onboarding() == null) return;
        int stage = player.onboarding().stage();
        if (npc.id() == 3308 && stage == NewPlayerProfile.GUIDE) {
            saveStage(player,NewPlayerProfile.GUIDE_SETTINGS);
        } else if (npc.id() == 3308 && stage == NewPlayerProfile.GUIDE_RETURN) {
            saveStage(player,NewPlayerProfile.SURVIVAL);
        } else if (npc.id() == 8503 && stage == NewPlayerProfile.SURVIVAL) {
            tutorialExchange(player,NewPlayerProfile.VIEW_INVENTORY,new int[0],SMALL_FISHING_NET);
        } else if (npc.id() == 8503 && stage == NewPlayerProfile.SURVIVAL_TOOLS) {
            tutorialExchange(player,NewPlayerProfile.CHOP_TREE,new int[0],BRONZE_AXE,TINDERBOX);
        } else if (npc.id() == 3305 && stage == NewPlayerProfile.CHEF) {
            tutorialExchange(player,NewPlayerProfile.MIX_DOUGH,new int[0],FLOUR,WATER);
        } else if (npc.id() == 3312 && stage == NewPlayerProfile.QUEST_GUIDE) {
            saveStage(player,NewPlayerProfile.VIEW_QUESTS);
        } else if (npc.id() == 3312 && stage == NewPlayerProfile.QUEST_GUIDE_RETURN) {
            saveStage(player,NewPlayerProfile.MINING_GUIDE);
        } else if (npc.id() == 3311 && stage == NewPlayerProfile.MINING_GUIDE) {
            tutorialExchange(player,NewPlayerProfile.MINE_ORES,new int[0],BRONZE_PICKAXE);
        } else if (npc.id() == 3311 && stage == NewPlayerProfile.COLLECT_HAMMER && player.bank().hasTutorialItem(BRONZE_BAR)) {
            tutorialExchange(player,NewPlayerProfile.SMITH_DAGGER,new int[0],HAMMER);
        } else if (npc.id() == 3307 && stage == NewPlayerProfile.COMBAT_GUIDE) {
            saveStage(player, NewPlayerProfile.COMBAT_VIEW_EQUIPMENT);
        } else if (npc.id() == 3307 && stage == NewPlayerProfile.COMBAT_GUIDE_RETURN
                && player.bank().equippedWeapon() == BRONZE_DAGGER) {
            tutorialExchange(player, NewPlayerProfile.COMBAT_EQUIP_MELEE, new int[0], BRONZE_SHORTSWORD, WOODEN_SHIELD);
        } else if (npc.id() == 3307 && stage == NewPlayerProfile.COMBAT_RANGED_GUIDE) {
            try {
                if (!player.bank().grantTutorialRangedKit()) {
                    sendMessage(player, "Make room for a shortbow and arrows, then speak to me again.");
                    return;
                }
                player.session().queue(player.bank().packet(false));
                saveStage(player, NewPlayerProfile.COMBAT_EQUIP_RANGED);
            } catch (IOException failure) {
                sendMessage(player, "Your ranged equipment could not be saved. Try again.");
            }
        }
    }

    private void tutorialExchange(Player player,int nextStage,int[] inputs,int... outputs) {
        try {
            if (!player.bank().tutorialExchange(inputs,outputs)) {
                sendMessage(player,"You need the tutorial supplies and enough free inventory space.");
                return;
            }
            player.session().queue(player.bank().packet(false));
            saveStage(player,nextStage);
        } catch (IOException failure) {
            sendMessage(player,"The tutorial items could not be saved.");
        }
    }

    /**
     * Starts a server-authoritative gathering action only after pathing has finished.
     * The animation is transmitted through the normal PLAYER_INFO extended-info mask.
     * The callback executes on the same world game thread and revalidates every prerequisite.
     */
    private void beginResourceAction(Player player, String skill, int animation, long durationTicks,
                                     BooleanSupplier requirements, Runnable reward) {
        if (players.get(player.index()) != player || !requirements.getAsBoolean()) return;
        cancelResourceAction(player, "replaced");
        Movement movement = player.movement();
        final int startX = movement.x, startZ = movement.z, startPlane = movement.plane;
        final long token = ++gatheringGeneration;
        gatheringActions.put(player.index(),token);
        player.infos().getPlayerInfo().getAvatar().getExtendedInfo().setSequence(animation,0);
        System.out.printf("KSP_RESOURCE_START player=%s skill=%s anim=%d ticks=%d x=%d z=%d token=%d%n",
            player.account().key(),skill,animation,durationTicks,startX,startZ,token);
        gameThread.schedule(() -> {
            if (!Long.valueOf(token).equals(gatheringActions.get(player.index()))) return;
            gatheringActions.remove(player.index());
            if (players.get(player.index()) != player) return;
            player.infos().getPlayerInfo().getAvatar().getExtendedInfo().setSequence(-1,0);
            if (movement.x != startX || movement.z != startZ || movement.plane != startPlane
                || !movement.steps.isEmpty() || !requirements.getAsBoolean()) {
                System.out.printf("KSP_RESOURCE_CANCEL player=%s skill=%s reason=invalidated%n",
                    player.account().key(),skill);
                return;
            }
            reward.run();
            System.out.printf("KSP_RESOURCE_FINISH player=%s skill=%s token=%d%n",
                player.account().key(),skill,token);
        }, durationTicks * 600L, TimeUnit.MILLISECONDS);
    }

    private void cancelResourceAction(Player player,String reason) {
        if (gatheringActions.remove(player.index()) == null) return;
        if (players.get(player.index()) == player) {
            player.infos().getPlayerInfo().getAvatar().getExtendedInfo().setSequence(-1,0);
        }
        System.out.printf("KSP_RESOURCE_CANCEL player=%s reason=%s%n",player.account().key(),reason);
    }

    private void tutorialFish(Player player,int index) {
        var profile = player.onboarding();
        if (profile == null || profile.stage() != NewPlayerProfile.FISH
            || !player.bank().hasTutorialItem(SMALL_FISHING_NET)) return;
        var npc = npcs.avatar(index);
        var movement = player.movement();
        if (!movement.scene.contains(npc.x(),npc.z()) || movement.plane != npc.level()) return;
        closeDialogue(player);
        routeTo(player,(x,z) -> Math.abs(x - npc.x()) + Math.abs(z - npc.z()) == 1,
            () -> beginResourceAction(player,"fishing",ANIM_SMALL_NET,TUTORIAL_FISH_TICKS,
                () -> player.onboarding() == profile && profile.stage() == NewPlayerProfile.FISH
                    && player.bank().hasTutorialItem(SMALL_FISHING_NET)
                    && player.movement().plane == npc.level()
                    && player.movement().scene.contains(npc.x(),npc.z())
                    && Math.abs(player.movement().x - npc.x()) + Math.abs(player.movement().z - npc.z()) == 1,
                () -> {
                    int nextStage = profile.fireX() == 0 ? NewPlayerProfile.VIEW_SKILLS : NewPlayerProfile.COOK_SHRIMP;
                    tutorialExchange(player,nextStage,new int[0],RAW_SHRIMP);
                    if (profile.stage() == nextStage) {
                        player.session().queue(new UpdateStatV2(FISHING_SKILL,1,1,SHRIMP_FISHING_XP));
                    }
                }));
    }

    private void tutorialItemUse(Player player,IfButtonT click) {
        if (player.onboarding() == null || click.getSelectedInterfaceId() != 149 || click.getTargetInterfaceId() != 149
            || click.getSelectedComponentId() != 0 || click.getTargetComponentId() != 0) return;
        int first = click.getSelectedObj(), second = click.getTargetObj();
        var bank = player.bank();
        if (!bank.hasTutorialItem(click.getSelectedSub(),first) || !bank.hasTutorialItem(click.getTargetSub(),second)) return;
        int stage = player.onboarding().stage();
        if (stage == NewPlayerProfile.FIRE && pair(first,second,TINDERBOX,LOGS)) {
            beginResourceAction(player,"firemaking",ANIM_FIREMAKING,TUTORIAL_COOK_TICKS,
                () -> player.onboarding() != null && player.onboarding().stage() == NewPlayerProfile.FIRE
                    && bank.hasTutorialItem(TINDERBOX) && bank.hasTutorialItem(LOGS),
                () -> {
                    if (bank.hasTutorialItem(RAW_SHRIMP)) {
                        tutorialExchange(player,NewPlayerProfile.COOK_SHRIMP,new int[]{LOGS});
                    } else {
                        tutorialExchange(player,NewPlayerProfile.FISH,new int[]{LOGS},SMALL_FISHING_NET);
                    }
                    sendTutorialFire(player,false);
                });
        } else if (stage == NewPlayerProfile.MIX_DOUGH && pair(first,second,FLOUR,WATER)) {
            tutorialExchange(player,NewPlayerProfile.BAKE_BREAD,new int[]{FLOUR,WATER},BREAD_DOUGH,EMPTY_POT,EMPTY_BUCKET);
        }
    }

    private static boolean pair(int first,int second,int left,int right) {
        return first == left && second == right || first == right && second == left;
    }

    private void tutorialItemOnLocation(Player player,int id,int x,int z,int group,int component,int slot,int item) {
        if (player.onboarding() == null || group != 149 || component != 0 || !player.bank().hasTutorialItem(slot,item)) return;
        var profile = player.onboarding();
        var movement = player.movement();
        var scene = movement.scene;
        if (profile.stage() == NewPlayerProfile.COOK_SHRIMP && id == TUTORIAL_FIRE && item == RAW_SHRIMP
            && movement.plane == 0 && x == profile.fireX() && z == profile.fireZ()
            && scene.contains(x,z)) {
            routeTo(player,(tx,tz) -> Math.abs(tx - x) + Math.abs(tz - z) <= 1,() -> {
                beginResourceAction(player,"cooking",ANIM_FIRE_COOKING,TUTORIAL_COOK_TICKS,
                    () -> profile.stage() == NewPlayerProfile.COOK_SHRIMP
                        && player.bank().hasTutorialItem(RAW_SHRIMP)
                        && player.movement().plane == 0
                        && player.movement().scene.contains(x,z)
                        && Math.abs(player.movement().x-x)+Math.abs(player.movement().z-z) <= 1,
                    () -> {
                        tutorialExchange(player,NewPlayerProfile.CHEF,new int[]{RAW_SHRIMP},SHRIMP);
                        if (profile.stage() == NewPlayerProfile.CHEF) sendTutorialFire(player,true);
                    });
            });
        } else if (profile.stage() == NewPlayerProfile.BAKE_BREAD && id == TUTORIAL_RANGE && item == BREAD_DOUGH) {
            var location = scene.location(id,movement.plane,x,z);
            if (location != null) bakeBread(player,location);
        } else if (profile.stage() == NewPlayerProfile.SMELT_BRONZE && id == TUTORIAL_FURNACE
            && (item == TIN_ORE || item == COPPER_ORE)) {
            var location = scene.location(id,movement.plane,x,z);
            if (location != null) smeltBronze(player,location);
        } else if (profile.stage() == NewPlayerProfile.SMITH_DAGGER && id == TUTORIAL_ANVIL && item == BRONZE_BAR) {
            var location = scene.location(id,movement.plane,x,z);
            if (location != null) routeTo(player,(tx,tz) -> scene.reaches(location,tx,tz),() -> openSmithing(player));
        }
    }

    private void bakeBread(Player player,LocalWorld.Location range) {
        var scene = player.movement().scene;
        routeTo(player,(x,z) -> scene.reaches(range,x,z),() ->
            beginResourceAction(player,"baking",ANIM_RANGE_COOKING,TUTORIAL_COOK_TICKS,
                () -> player.onboarding() != null && player.onboarding().stage() == NewPlayerProfile.BAKE_BREAD
                    && player.bank().hasTutorialItem(BREAD_DOUGH)
                    && player.movement().scene.reaches(range,player.movement().x,player.movement().z),
                () -> tutorialExchange(player,NewPlayerProfile.QUEST_GUIDE,new int[]{BREAD_DOUGH},BREAD)));
    }

    private void sendTutorialFire(Player player,boolean remove) {
        var profile = player.onboarding();
        var movement = player.movement();
        if (profile == null || profile.fireX() == 0 || movement.plane != 0 ||
            !remove && profile.stage() != NewPlayerProfile.FISH && profile.stage() != NewPlayerProfile.COOK_SHRIMP
            || !movement.scene.contains(profile.fireX(),profile.fireZ())) return;
        int x = profile.fireX() - movement.scene.baseX, z = profile.fireZ() - movement.scene.baseZ;
        player.session().queue(new UpdateZonePartialFollows(x & ~7,z & ~7,0));
        if (remove) player.session().queue(new LocDel(x & 7,z & 7,10,0));
        else player.session().queue(new LocAddChangeV3(TUTORIAL_FIRE,x & 7,z & 7,10,0,(byte) 31));
    }

    private void smeltBronze(Player player,LocalWorld.Location furnace) {
        var scene = player.movement().scene;
        routeTo(player,(x,z) -> scene.reaches(furnace,x,z),
            () -> beginResourceAction(player,"smelting",ANIM_SMELTING,TUTORIAL_SMELT_TICKS,
                () -> player.onboarding() != null && player.onboarding().stage() == NewPlayerProfile.SMELT_BRONZE
                    && player.bank().hasTutorialItem(TIN_ORE) && player.bank().hasTutorialItem(COPPER_ORE)
                    && player.movement().scene.reaches(furnace,player.movement().x,player.movement().z),
                () -> {
                    int nextStage = player.bank().hasTutorialItem(HAMMER)
                        ? NewPlayerProfile.SMITH_DAGGER : NewPlayerProfile.COLLECT_HAMMER;
                    tutorialExchange(player,nextStage,new int[]{TIN_ORE,COPPER_ORE},BRONZE_BAR);
                }));
    }

    private void openSmithing(Player player) {
        if (!player.bank().hasTutorialItem(BRONZE_BAR) || !player.bank().hasTutorialItem(HAMMER)) return;
        closeBank(player);
        closeSettings(player);
        if (player.widgets().map == MapMode.MAIN) closeWorldMap(player);
        var hud = player.hud();
        player.widgets().smithing = true;
        player.session().queue(new VarpSmall(SMITHING_BAR_TYPE,1));
        player.session().queue(new IfOpenSub(hud.root(),hud.mainModal(),SMITHING,0));
        player.session().queue(new IfSetEventsV2(SMITHING,SMITHING_DAGGER,-1,-1,0,1));
    }

    private void closeSmithing(Player player) {
        if (!player.widgets().smithing) return;
        player.widgets().smithing = false;
        var hud = player.hud();
        player.session().queue(new IfCloseSub(hud.root(),hud.mainModal()));
    }

    private boolean tutorialLocation(Player player, LocalWorld.Location location, String action) {
        if (player.onboarding() == null) return false;
        int stage = player.onboarding().stage();
        var scene = player.movement().scene;
        if (location.id() == TUTORIAL_RANGE && "Cook".equals(action) && stage == NewPlayerProfile.BAKE_BREAD) {
            bakeBread(player,location);
            return true;
        }
        if (stage == NewPlayerProfile.MINE_ORES && "Mine".equals(action)
            && (location.id() == COPPER_ROCK || location.id() == TIN_ROCK)) {
            int ore = location.id() == COPPER_ROCK ? COPPER_ORE : TIN_ORE;
            int other = ore == COPPER_ORE ? TIN_ORE : COPPER_ORE;
            routeTo(player,(x,z) -> scene.reaches(location,x,z),() ->
                beginResourceAction(player,"mining",ANIM_BRONZE_PICKAXE,TUTORIAL_MINE_TICKS,
                    () -> player.onboarding() != null
                        && player.onboarding().stage() == NewPlayerProfile.MINE_ORES
                        && player.bank().hasTutorialItem(BRONZE_PICKAXE)
                        && player.movement().scene.location(location.id(),player.movement().plane,location.x(),location.z()) != null
                        && player.movement().scene.reaches(location,player.movement().x,player.movement().z),
                    () -> {
                        int next = player.bank().hasTutorialItem(other) ? NewPlayerProfile.SMELT_BRONZE : NewPlayerProfile.MINE_ORES;
                        tutorialExchange(player,next,new int[0],ore);
                    }));
            return true;
        }
        if (stage == NewPlayerProfile.SMELT_BRONZE && location.id() == TUTORIAL_FURNACE && "Use".equals(action)) {
            smeltBronze(player,location);
            return true;
        }
        if (stage == NewPlayerProfile.SMITH_DAGGER && location.id() == TUTORIAL_ANVIL && "Smith".equals(action)) {
            routeTo(player,(x,z) -> scene.reaches(location,x,z),() -> openSmithing(player));
            return true;
        }
        if (location.id() == 9726 && NewPlayerProfile.lessonOrder(stage) < NewPlayerProfile.lessonOrder(NewPlayerProfile.MINING_GUIDE)) {
            sendMessage(player,"Talk to the Quest Guide before climbing down.");
            return true;
        }
        int requirement = switch (location.id()) {
            case STARTING_DOOR -> NewPlayerProfile.SURVIVAL;
            case SURVIVAL_GATE_LEFT, SURVIVAL_GATE_RIGHT, CHEF_DOOR -> NewPlayerProfile.CHEF;
            case CHEF_EXIT -> NewPlayerProfile.QUEST_GUIDE;
            case 9716 -> NewPlayerProfile.QUEST_GUIDE;
            case 9717,9718 -> NewPlayerProfile.COMBAT_GUIDE;
            default -> -1;
        };
        if (requirement != -1 && "Open".equals(action)) {
            if (NewPlayerProfile.lessonOrder(stage) < NewPlayerProfile.lessonOrder(requirement)) {
                sendMessage(player,"Complete the current tutorial lesson first.");
            }
            else routeTo(player,(x,z) -> scene.reaches(location,x,z),() -> useDoor(player,location,action));
            return true;
        }
        // Object actions are resolved by revision-242 cache definition and click opcode.
        // Do not fall through to the generic unavailable response for a normal tree.
        if (isTutorialNormalTree(location) && "Chop down".equalsIgnoreCase(action)) {
            chopTutorialTree(player, location);
            return true;
        }
        return false;
    }

    private boolean isTutorialNormalTree(LocalWorld.Location location) {
        var def = world.definition(location.id());
        return def != null && "Tree".equalsIgnoreCase(def.getName());
    }

    private void chopTutorialTree(Player player, LocalWorld.Location tree) {
        var profile = player.onboarding();
        if (profile == null || profile.stage() != NewPlayerProfile.CHOP_TREE) {
            sendMessage(player, "Finish the current tutorial lesson first.");
            return;
        }
        if (!player.bank().hasTutorialItem(BRONZE_AXE)) {
            sendMessage(player, "You need the bronze axe from the Survival Expert.");
            return;
        }
        var scene = player.movement().scene;
        routeTo(player, (x,z) -> scene.reaches(tree,x,z), () ->
            beginResourceAction(player,"woodcutting",ANIM_BRONZE_AXE,TUTORIAL_WOODCUT_TICKS,
                () -> player.onboarding() == profile
                    && profile.stage() == NewPlayerProfile.CHOP_TREE
                    && player.bank().hasTutorialItem(BRONZE_AXE)
                    && player.movement().scene.location(tree.id(),player.movement().plane,tree.x(),tree.z()) != null
                    && player.movement().scene.reaches(tree,player.movement().x,player.movement().z),
                () -> {
                    tutorialExchange(player,NewPlayerProfile.FIRE,new int[0],LOGS);
                    if (profile.stage() == NewPlayerProfile.FIRE) {
                        player.session().queue(new UpdateStatV2(WOODCUTTING_SKILL,1,1,NORMAL_TREE_XP));
                    }
                }));
    }

    private void openBank(Player player, int group) {
        try {
            player.bank().refresh();
        } catch (IOException failure) {
            sendMessage(player, "Your bank could not be loaded.");
            return;
        }
        closeDialogue(player);
        closeSettings(player);
        if (player.widgets().map == MapMode.MAIN) closeWorldMap(player);
        player.widgets().bankGroup = group;
        var session = player.session();
        var hud = player.hud();
        session.queue(new IfOpenSub(hud.root(), hud.mainModal(), group, 0));
        if (group == BANK) {
            session.queue(new IfOpenSub(hud.root(), hud.firstTab() + INVENTORY_TAB, BANK_SIDE, OVERLAY_INTERFACE));
            session.queue(new IfSetEventsV2(BANK, BANK_ITEMS, 0, LocalBank.CAPACITY - 1, 0, 0xff));
            session.queue(new IfSetEventsV2(BANK_SIDE, BANK_INVENTORY, 0, LocalBank.INVENTORY_SIZE - 1, 0, 0xfe));
            session.queue(new IfSetEventsV2(BANK, BANK_DEPOSIT_ALL, -1, -1, 0, 1));
        } else {
            session.queue(new IfSetEventsV2(DEPOSIT_BOX, BOX_INVENTORY, 0, LocalBank.INVENTORY_SIZE - 1, 0, 0x3f));
            session.queue(new IfSetEventsV2(DEPOSIT_BOX, BOX_DEPOSIT_ALL, -1, -1, 0, 1));
        }
        syncBank(player);
    }

    private void closeBank(Player player) {
        int group = player.widgets().bankGroup;
        if (group == 0) return;
        player.widgets().bankGroup = 0;
        if (player.widgets().bankAmount != null) {
            player.widgets().bankAmount = null;
            player.session().queue(new RunClientScript(299, List.of(1, 1, 0)));
        }
        var hud = player.hud();
        player.session().queue(new IfCloseSub(hud.root(), hud.mainModal()));
        if (group == BANK) {
            if (TutorialTabPolicy.allowed(TutorialTabPolicy.allowedMask(
                    player.onboarding() == null ? -1 : player.onboarding().stage()), INVENTORY_TAB)) {
                hud.openInventory(player.session());
            } else {
                player.session().queue(new IfCloseSub(hud.root(), hud.firstTab() + INVENTORY_TAB));
            }
        }
    }

    private void syncBank(Player player) {
        player.session().queue(player.bank().packet(false));
        if (player.widgets().bankGroup == BANK) {
            player.session().queue(player.bank().packet(true));
            player.session().queue(new IfSetText(BANK, 5, Integer.toString(player.bank().occupied())));
            player.session().queue(new IfSetText(BANK, 8, Integer.toString(LocalBank.CAPACITY)));
        }
    }

    private void useBankWidget(Player player, int group, int component, int child, int option, int item) {
        var widgets = player.widgets();
        int open = widgets.bankGroup;
        if (open != BANK && open != DEPOSIT_BOX) return;
        if (open == group && child == -1 && option == 1 && setBankQuantity(player, group, component)) return;
        boolean withdraw = open == BANK && group == BANK && component == BANK_ITEMS;
        boolean side = open == BANK && group == BANK_SIDE && component == BANK_INVENTORY;
        boolean box = open == DEPOSIT_BOX && group == DEPOSIT_BOX && component == BOX_INVENTORY;
        boolean depositInventory = open == group && child == -1 && option == 1
            && component == (open == BANK ? BANK_DEPOSIT_ALL : BOX_DEPOSIT_ALL);
        if (depositInventory) {
            moveBankItems(player, new BankAmount(false, -1, -1), Integer.MAX_VALUE);
            return;
        }
        if (child < 0 || !(withdraw || side || box)) return;
        int quantityOption = side ? option - 1 : option;
        var action = new BankAmount(withdraw, child, item);
        if (quantityOption == (box ? 5 : 6)) {
            widgets.bankAmount = action;
            player.session().queue(new RunClientScript(AMOUNT_PROMPT_SCRIPT, List.of("Enter amount:")));
            return;
        }
        int amount = switch (quantityOption) {
            case 1 -> box ? widgets.boxQuantity : widgets.bankQuantity;
            case 2 -> 1;
            case 3 -> 5;
            case 4 -> 10;
            case 5 -> widgets.requestedQuantity;
            case 6 -> box ? Integer.MAX_VALUE : 0;
            case 7 -> box ? 0 : Integer.MAX_VALUE;
            case 8 -> withdraw ? LocalBank.ALL_BUT_ONE : 0;
            default -> 0;
        };
        if (amount != 0) moveBankItems(player, action, amount);
    }

    private boolean setBankQuantity(Player player, int group, int component) {
        int mode;
        if (group == BANK) {
            mode = switch (component) {
                case 29 -> 0;
                case 31 -> 1;
                case 33 -> 2;
                case 37 -> 4;
                default -> -1;
            };
        } else {
            mode = switch (component) {
                case 35 -> 0;
                case 36 -> 1;
                case 37 -> 4;
                case 39 -> 2;
                default -> -1;
            };
        }
        if (mode < 0) return false;
        int quantity = mode == 0 ? 1 : mode == 1 ? 5
            : component == (group == BANK ? 33 : 37) ? 10 : Integer.MAX_VALUE;
        if (group == BANK) {
            player.widgets().bankQuantity = quantity;
            player.session().queue(new VarpLarge(BANK_QUANTITY_VARP, mode << 2));
        } else {
            player.widgets().boxQuantity = quantity;
            player.session().queue(new VarpLarge(DEPOSIT_BOX_MODE_VARP, mode << 28));
        }
        return true;
    }

    private void resumeBankAmount(Player player, int amount) {
        var widgets = player.widgets();
        var pending = widgets.bankAmount;
        widgets.bankAmount = null;
        if (pending == null || amount <= 0 || widgets.bankGroup == 0) return;
        if (widgets.bankGroup == BANK) {
            widgets.requestedQuantity = amount;
            player.session().queue(new VarpLarge(REQUESTED_QUANTITY_VARP, amount << 1));
        }
        moveBankItems(player, pending, amount);
    }

    private void moveBankItems(Player player, BankAmount action, int amount) {
        try {
            player.bank().transfer(action.withdraw(), action.slot(), amount, action.item(), world);
            syncBank(player);
        } catch (IOException failure) {
            sendMessage(player, "The bank transaction could not be saved.");
        }
    }

    private void routeTo(Player player, LocalWorld.TileTarget target, Runnable arrival) {
        cancelTutorialRatFight(player);
        Movement movement = player.movement();
        movement.steps.clear();
        movement.onArrival = arrival;
        if (target.reached(movement.x, movement.z)) {
            finishMovement(player);
            return;
        }
        var route = movement.scene.route(movement.plane, movement.x, movement.z, target);
        boolean exitLocked = blocksStartingDoor(player, route);
        if (route.isEmpty() || exitLocked) {
            movement.onArrival = null;
            movement.talkTo = -1;
            player.session().queue(new SetMapFlagV2());
            sendMessage(player, exitLocked ? "Speak to the Gielinor Guide before leaving the house." : "I can't reach that.");
            return;
        }
        movement.steps.addAll(route);
        var destination = movement.steps.getLast();
        player.session().queue(new SetMapFlagV2(destination.x(), destination.z()));
        System.out.printf("KSP_LOCAL_WALK_ROUTE destination=%d,%d steps=%d%n",
            destination.x(), destination.z(), movement.steps.size());
    }

    private boolean blocksStartingDoor(Player player, List<LocalWorld.Step> route) {
        var tutorial = player.onboarding();
        if (tutorial == null || NewPlayerProfile.lessonOrder(tutorial.stage()) >= NewPlayerProfile.lessonOrder(NewPlayerProfile.SURVIVAL)) {
            return false;
        }
        // The door is shared across players. Stop early-stage players from walking through it if another player opened it.
        int previousX = player.movement().x;
        int previousZ = player.movement().z;
        for (var step : route) {
            if (previousX == STARTING_DOOR_X - 1 && step.x() == STARTING_DOOR_X
                && (previousZ == STARTING_DOOR_Z || step.z() == STARTING_DOOR_Z)) return true;
            previousX = step.x();
            previousZ = step.z();
        }
        return false;
    }

    private void examineLocation(Player player, int id) {
        var object = world.definition(id);
        if (object != null && !"null".equals(object.getName())) {
            sendMessage(player, "You see " + object.getName() + ".");
        }
    }

    private void useLocation(Player player, int id, int objectX, int objectZ, int op, int subop) {
        Movement movement = player.movement();
        if (subop != 0) return;
        LocalWorld.Location location = movement.scene.location(id, movement.plane, objectX, objectZ);
        if (location == null) {
            System.out.printf("KSP_OBJECT_REJECT reason=not_in_scene player=%s id=%d x=%d z=%d plane=%d op=%d subop=%d%n",
                player.account().key(),id,objectX,objectZ,movement.plane,op,subop);
            sendMessage(player,"This object is not present in the current scene.");
            return;
        }
        String action = world.action(location, op);
        var definition = world.definition(id);
        String name = definition == null ? "<undefined>" : definition.getName();
        int tutorialStage = player.onboarding() == null ? -1 : player.onboarding().stage();
        System.out.printf("KSP_OBJECT_CLICK player=%s id=%d name=%s action=%s op=%d subop=%d x=%d z=%d plane=%d stage=%d%n",
            player.account().key(),id,name,action,op,subop,objectX,objectZ,movement.plane,tutorialStage);
        if (action.isEmpty()) {
            System.out.printf("KSP_OBJECT_REJECT reason=missing_menu_action id=%d op=%d%n",id,op);
            sendMessage(player,"This object has no configured action for that menu option.");
            return;
        }
        closeBank(player);
        closeDialogue(player);
        movement.talkTo = -1;
        closeSmithing(player);
        if (tutorialLocation(player,location,action)) return;
        // Trees must be handled separately from tutorial-only actions: an account
        // at a different checkpoint should get a useful requirement message,
        // not the generic unimplemented-object response.
        if ("Chop down".equalsIgnoreCase(action) && isTutorialNormalTree(location)) {
            chopTutorialTree(player,location);
            return;
        }
        if ("Bank".equals(action) || "Use".equals(action) && "Bank chest".equalsIgnoreCase(name)) {
            routeTo(player, (x, z) -> movement.scene.reaches(location, x, z), () -> openBank(player, BANK));
            return;
        }
        if ("Deposit".equals(action) && "Bank Deposit Box".equalsIgnoreCase(name)) {
            routeTo(player, (x, z) -> movement.scene.reaches(location, x, z), () -> openBank(player, DEPOSIT_BOX));
            return;
        }
        var routes = transports.routes(movement.scene, location, action, membersWorld);
        if (!routes.isEmpty()) {
            routeTo(player, (x, z) -> routes.stream().anyMatch(climb -> climb.from().x() == x && climb.from().z() == z),
                () -> climb(player, routes));
            return;
        }
        if ("Open".equals(action) || "Close".equals(action)) {
            routeTo(player, (x, z) -> movement.scene.reaches(location, x, z), () -> useDoor(player, location, action));
            return;
        }
        System.out.printf("KSP_OBJECT_UNIMPLEMENTED player=%s id=%d name=%s action=%s op=%d stage=%d x=%d z=%d%n",
            player.account().key(),id,name,action,op,tutorialStage,objectX,objectZ);
        sendMessage(player, "This " + name + " interaction has not yet been implemented.");
    }

    private void useDoor(Player player, LocalWorld.Location location, String action) {
        LocalWorld.Location changed = world.swapDoor(location, action);
        if (changed == null) {
            sendMessage(player, "This door has no configured open/close behavior.");
            return;
        }
        try {
            for (var viewer : players.values()) {
                var scene = viewer.movement().scene;
                if (!scene.contains(changed.x(),changed.z())) continue;
                viewer.movement().scene = world.build(scene.baseX + 48,scene.baseZ + 48,membersWorld);
                if (viewer.movement().plane == changed.plane()) sendDoor(viewer,changed);
            }
            System.out.printf("KSP_LOCAL_DOOR action=%s id=%d x=%d z=%d%n",
                action, changed.id(), changed.x(), changed.z());
        } catch (IOException failure) {
            world.swapDoor(changed, "Open".equals(action) ? "Close" : "Open");
            sendMessage(player, "That door could not be updated.");
        }
    }

    private void sendDoor(Player player, LocalWorld.Location location) {
        var scene = player.movement().scene;
        int x = location.x() - scene.baseX, z = location.z() - scene.baseZ;
        player.session().queue(new UpdateZonePartialFollows(x & ~7, z & ~7, location.plane()));
        player.session().queue(new LocAddChangeV3(location.id(), x & 7, z & 7, location.shape(),
            location.rotation(), (byte) 31));
    }

    private void sendDoorChanges(Player player) {
        for (var location : world.shiftedDoors(player.movement().scene, player.movement().plane)) sendDoor(player, location);
    }

    private void climb(Player player, List<WorldTransports.Climb> routes) {
        var movement = player.movement();
        var scene = movement.scene;
        for (var route : routes) {
            if (route.from().x() != movement.x || route.from().z() != movement.z) continue;
            var target = route.to();
            try {
                LocalWorld.Scene destination = !scene.nearEdge(target.x(), target.z()) ? scene
                    : world.build(target.x(), target.z(), membersWorld);
                if (!destination.walkable(target.plane(), target.x(), target.z())) continue;
                movement.x = target.x();
                movement.z = target.z();
                movement.plane = target.plane();
                player.infos().getPlayerInfo().getAvatar().getExtendedInfo().setTempMoveSpeed(127);
                player.infos().updateRootCoord(movement.plane, movement.x, movement.z);
                if (destination != scene) {
                    rebuildScene(player, destination);
                } else {
                    sendDoorChanges(player);
                }
                System.out.printf("KSP_LOCAL_CLIMB x=%d z=%d plane=%d%n", movement.x, movement.z, movement.plane);
                return;
            } catch (IOException failure) {
                sendMessage(player, "That destination could not be loaded.");
                return;
            }
        }
        sendMessage(player, "There is no clear landing at that destination.");
    }

    private void rebuildScene(Player player, LocalWorld.Scene next) {
        player.movement().scene = next;
        int centerX = next.baseX + 48, centerZ = next.baseZ + 48;
        player.infos().updateRootBuildAreaCenteredOnPlayer(centerX, centerZ);
        player.session().queue(new RebuildNormalV2(centerX >> 3, centerZ >> 3, 0));
        System.out.printf("KSP_LOCAL_REGION_REBUILD x=%d z=%d%n", centerX, centerZ);
    }

    private void advanceMovement(Player player) {
        Movement movement = player.movement();
        var step = movement.steps.pollFirst();
        if (step == null) {
            if (movement.energy < MAX_RUN_ENERGY) {
                movement.energy = Math.min(MAX_RUN_ENERGY, movement.energy + RUN_REST_REGEN);
                player.session().queue(new UpdateRunEnergy(movement.energy));
            }
            return;
        }
        var next = movement.steps.peekFirst();
        if (movement.run && next != null && movement.energy >= RUN_DRAIN
            && Math.max(Math.abs(next.x() - movement.x), Math.abs(next.z() - movement.z)) == 2) {
            step = movement.steps.removeFirst();
            movement.energy -= RUN_DRAIN;
            player.infos().getPlayerInfo().getAvatar().getExtendedInfo().setTempMoveSpeed(2);
            player.session().queue(new UpdateRunEnergy(movement.energy));
            if (movement.energy < RUN_DRAIN) setRun(player, false);
        }
        movement.x = step.x();
        movement.z = step.z();
        player.infos().updateRootCoord(movement.plane, movement.x, movement.z);
        if (movement.scene.nearEdge(movement.x, movement.z)) {
            try {
                rebuildScene(player, world.build(movement.x, movement.z, membersWorld));
            } catch (IOException failure) {
                movement.steps.clear();
                movement.onArrival = null;
                sendMessage(player, "The next region could not be loaded.");
            }
        }
        if (movement.steps.isEmpty()) finishMovement(player);
    }

    private void finishMovement(Player player) {
        Movement movement = player.movement();
        player.session().queue(new SetMapFlagV2());
        Runnable arrival = movement.onArrival;
        movement.onArrival = null;
        movement.talkTo = -1;
        if (arrival != null) arrival.run();
    }

    private void reportPopulation() {
        if (managedWorld) System.out.println("KSP_LOCAL_WORLD_POPULATION=" + players.size());
    }

    private void reportPlayer(Player player, boolean connected) {
        if (managedWorld) System.out.printf("KSP_GAME_PLAYER=%s=%s%n", player.account().key(),
            connected ? "ONLINE" : "OFFLINE");
    }

    private void release(Player player) {
        if (!players.remove(player.index(),player)) return;
        cancelTutorialRatFight(player);
        gatheringActions.remove(player.index());
        reportPopulation();
        reportPlayer(player, false);
        player.protocol().dealloc(player.infos());
    }

    private void stop(NetworkService<Player> network) throws Exception {
        gameThread.submit(() -> {
            for (var player : List.copyOf(players.values())) {
                player.session().requestClose();
                release(player);
            }
        }).get();
        try {
            network.shutdown(0, 5, TimeUnit.SECONDS).get(6, TimeUnit.SECONDS);
        } finally {
            gameThread.shutdown();
        }
    }

    private static byte[] frameMaster(byte[] master) throws IOException {
        var buffer = Js5WireFraming.prepare(255, 255, master);
        try {
            byte[] framed = new byte[buffer.readableBytes()];
            buffer.readBytes(framed);
            return framed;
        } finally {
            buffer.release();
        }
    }

    private static void generateKeypair(Path directory) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        // A new directory prevents overwriting either half of an existing pair.
        Path parent = directory.toAbsolutePath().getParent();
        if (Files.getFileStore(parent).supportsFileAttributeView("posix")) {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectory(directory);
        }
        writePem(directory.resolve("local-private.pem"), "PRIVATE KEY", keys.getPrivate().getEncoded());
        writePem(directory.resolve("local-public.pem"), "PUBLIC KEY", keys.getPublic().getEncoded());
        System.out.println("Local RSA key pair created in " + directory.toAbsolutePath());
    }

    private static void writePem(Path path, String label, byte[] bytes) throws IOException {
        try {
            String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes);
            Files.writeString(path, "-----BEGIN " + label + "-----\n" + body
                + "\n-----END " + label + "-----\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static RsaKeyPair loadPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path).strip();
        String begin = "-----BEGIN PRIVATE KEY-----";
        String end = "-----END PRIVATE KEY-----";
        if (!pem.startsWith(begin) || !pem.endsWith(end)) {
            throw new IOException("Expected an unencrypted PKCS#8 RSA private key in PEM format");
        }
        byte[] der = Base64.getMimeDecoder().decode(pem.substring(begin.length(), pem.length() - end.length()));
        try {
            var key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            return new RsaKeyPair(key.getPrivateExponent(), key.getModulus());
        } finally {
            Arrays.fill(der, (byte) 0);
        }
    }

    private static HuffmanCodecProvider loadHuffman(VerifiedJs5GroupProvider groups) throws IOException {
        var frame = groups.provide(BINARY_ARCHIVE, HUFFMAN_GROUP);
        if (frame == null) throw new IOException("Missing verified revision-242 Huffman group 10/1");
        try {
            // This group's pinned compressed container fits in a single JS5 block.
            if (frame.readableBytes() > 512) throw new IOException("Unexpected Huffman group frame size");
            frame.skipBytes(3); // Archive and group header.
            byte[] container = new byte[frame.readableBytes()];
            frame.readBytes(container);
            byte[] table = CacheMetadata.unpack(container);
            if (table.length != 256) throw new IOException("Invalid revision-242 Huffman table length");
            var buffer = Unpooled.wrappedBuffer(table);
            try {
                return new DefaultHuffmanCodecProvider(HuffmanCodec.Companion.create(buffer));
            } finally {
                buffer.release();
            }
        } finally {
            frame.release();
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name);
        return value;
    }

    private enum MapMode {
        FLOATING, MAIN;

        int component(HudLayout hud) {
            return this == FLOATING ? hud.floater() : hud.mainModal();
        }
    }

    private record HudLayout(int root, int chat, int orbs, int firstTab, int mainModal, int floater,
                             int settingsStone, int settingsIcon) {
        void openInventory(Session<Player> session) {
            session.queue(new IfOpenSub(root, firstTab + INVENTORY_TAB, INVENTORY, OVERLAY_INTERFACE));
            session.queue(inventoryItemEvents());
        }

        void queueLogoutTab(Session<Player> session, int group) {
            session.queue(new IfOpenSub(root, firstTab + LOGOUT_TAB, group, OVERLAY_INTERFACE));
        }

        void moveTo(Session<Player> session, HudLayout next, boolean settingsOpen, MapMode map) {
            session.queue(new IfOpenTop(next.root()));
            session.queue(new IfMoveSub(root, chat, next.root(), next.chat()));
            session.queue(new IfMoveSub(root, orbs, next.root(), next.orbs()));
            for (int tab = 0; tab < SIDE_TAB_INTERFACES.length; tab++) {
                session.queue(new IfMoveSub(root, firstTab + tab, next.root(), next.firstTab() + tab));
            }
            if (settingsOpen) {
                session.queue(new IfMoveSub(root, mainModal, next.root(), next.mainModal()));
            }
            if (map != null) {
                session.queue(new IfMoveSub(root, map.component(this), next.root(), map.component(next)));
            }
        }

        void queue(Session<Player> session) {
            session.queue(new IfOpenTop(root));
            session.queue(new IfOpenSub(root, chat, CHATBOX_INTERFACE, OVERLAY_INTERFACE));
            session.queue(new IfOpenSub(root, orbs, ORBS_INTERFACE, OVERLAY_INTERFACE));
            for (int tab = 0; tab < SIDE_TAB_INTERFACES.length; tab++) {
                session.queue(new IfOpenSub(root, firstTab + tab, SIDE_TAB_INTERFACES[tab], OVERLAY_INTERFACE));
            }
        }
    }

    private static final class Movement {
        private final ArrayDeque<LocalWorld.Step> steps = new ArrayDeque<>();
        private LocalWorld.Scene scene;
        private int x;
        private int z;

        private Movement(LocalWorld.Scene scene, int x, int z) {
            this.scene = scene;
            this.x = x;
            this.z = z;
        }
        private int plane;
        private int talkTo = -1;
        private Runnable onArrival;
        private boolean run;
        private int energy = MAX_RUN_ENERGY;
        private int tutorialHitpoints = 10;
    }

    private record BankAmount(boolean withdraw, int slot, int item) {}

    private static final class Widgets {
        private HudLayout hud;
        private HudLayout resizableHud = CLASSIC_HUD;
        private boolean switcher;
        private boolean settings;
        private boolean smithing;
        private boolean equipmentStats;
        private boolean previewQueued;
        private boolean chatPromptSynced;
        private boolean publicChatConfirmed;
        private int publicChatFilter;
        private int bankGroup;
        private int bankQuantity = 1;
        private int boxQuantity = 1;
        private int requestedQuantity;
        private BankAmount bankAmount;
        private LumbridgeNpcs.Spawn dialogue;
        private boolean dialogueOptions;
        private MapMode map;
        private int tutorialTabMask = -1;
        private boolean transferPending;
        private int requestGeneration;
        private OutgoingGameMessage exit;

        private Widgets(HudLayout hud) {
            this.hud = hud;
        }
    }

    record Player(int index, LocalAccounts.Account account, Session<Player> session, InfoProtocols protocol,
                  Infos infos, Movement movement, Widgets widgets, LocalBank bank, NewPlayerProfile onboarding) {
        HudLayout hud() { return widgets.hud; }
    }
}
