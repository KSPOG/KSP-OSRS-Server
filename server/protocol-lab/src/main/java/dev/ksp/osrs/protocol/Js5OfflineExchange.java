package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, no-network JS5 request/response harness for the revision-242
 * RSProt request opcodes. This is NOT a socket, login or full JS5 service.
 *
 * RSProt osrs-242: each request is 1-byte opcode + 3-byte payload;
 * request opcodes: 0 prefetch, 1 urgent, 2/3 priority, 4 XOR change.
 * XOR byte transform and master-index 255/255 are not enabled here.
 */
public final class Js5OfflineExchange {
    public static final int MAX_MESSAGES = 8192;
    private Js5OfflineExchange() {}

    public enum Type { PREFETCH, URGENT, PRIORITY_HIGH, PRIORITY_LOW, XOR_CHANGE }
    public record Request(Type type, int archive, int group, int xorKey) {}
    public record Result(int requests, int responses, int denied,
                         int controlMessages, long responseBytes,
                         List<String> examples) {
        public Result { examples = List.copyOf(examples); }
    }

    public static List<Request> parse(byte[] packets) throws IOException {
        if (packets == null || packets.length == 0 || packets.length % 4 != 0
                || packets.length / 4 > MAX_MESSAGES)
            throw new IOException("JS5 packet stream must contain 1..8192 complete four-byte packets");
        List<Request> result = new ArrayList<>(packets.length / 4);
        for (int i = 0; i < packets.length; i += 4) {
            int opcode = packets[i] & 0xff;
            int archive = packets[i + 1] & 0xff;
            int group = (packets[i + 2] & 0xff) << 8 | packets[i + 3] & 0xff;
            Type type = switch (opcode) {
                case 0 -> Type.PREFETCH;
                case 1 -> Type.URGENT;
                case 2 -> Type.PRIORITY_HIGH;
                case 3 -> Type.PRIORITY_LOW;
                case 4 -> Type.XOR_CHANGE;
                default -> throw new IOException("Unsupported JS5 opcode " + opcode);
            };
            // RSProt XOR_CHANGE uses g1 key then skips 2 reserved bytes.
            if (type == Type.XOR_CHANGE && group != 0)
                throw new IOException("Nonzero XOR control reserved bytes");
            result.add(new Request(type, archive, group,
                type == Type.XOR_CHANGE ? archive : 0));
        }
        return List.copyOf(result);
    }

    public static Result replay(Path cache, byte[] packets) throws IOException {
        List<Request> parsed = parse(packets);
        int responses = 0, denied = 0, controls = 0;
        long bytes = 0;
        List<String> examples = new ArrayList<>();
        try (var provider = new VerifiedJs5GroupProvider(cache)) {
            for (Request request : parsed) {
                if (request.type() == Type.XOR_CHANGE) {
                    controls++;
                    if (request.xorKey() != 0)
                        throw new IOException("XOR handling is not implemented in offline harness");
                    continue;
                }
                if (request.type() == Type.PRIORITY_HIGH || request.type() == Type.PRIORITY_LOW) {
                    controls++;
                    continue;
                }
                if (request.archive() == 255 && request.group() == 255) {
                    denied++;
                    sample(examples, "MASTER_INDEX_NOT_VERIFIED");
                    continue;
                }
                ByteBuf response = provider.provide(request.archive(), request.group());
                if (response == null) {
                    denied++;
                    sample(examples, "NOT_AVAILABLE:" + request.archive() + "/" + request.group());
                    continue;
                }
                try {
                    if (response.readableBytes() < 8
                        || response.getUnsignedByte(0) != request.archive()
                        || response.getUnsignedShort(1) != request.group())
                        throw new IOException("Invalid JS5 provider response header");
                    for (int position = 512; position < response.readableBytes(); position += 512)
                        if (response.getUnsignedByte(position) != 255)
                            throw new IOException("Invalid JS5 continuation marker");
                    responses++;
                    bytes += response.readableBytes();
                } finally {
                    response.release();
                }
            }
        }
        return new Result(parsed.size(), responses, denied, controls, bytes, examples);
    }

    private static void sample(List<String> examples, String note) {
        if (examples.size() < 12) examples.add(note);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Js5OfflineExchange <cache-folder>");
            System.exit(2);
        }
        // Contains requests for archive 24 group 0; index 24; a deliberately
        // unavailable special master index; and normal priority/control frames.
        byte[] packets = {1, 24, 0, 0, 0, (byte)255, 0, 24, 1, (byte)255,
            0, (byte)255, 2, 0, 0, 0, 4, 0, 0, 0};
        Result report = replay(Path.of(args[0]), packets);
        System.out.println("Offline JS5 exchange: requests=" + report.requests()
            + " responses=" + report.responses() + " denied=" + report.denied()
            + " control=" + report.controlMessages() + " bytes=" + report.responseBytes());
        for (String note : report.examples()) System.out.println("NOTE " + note);
        System.out.println("Master 255/255 and client acceptance: UNVERIFIED");
        System.out.println("Network listeners: DISABLED");
        if (report.responses() != 2 || report.denied() != 1 || report.controlMessages() != 2)
            System.exit(3);
    }
}
