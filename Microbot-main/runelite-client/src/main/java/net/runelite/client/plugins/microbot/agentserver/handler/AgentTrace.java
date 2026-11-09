package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class AgentTrace {

	static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static volatile Path file;

	private AgentTrace() {}

	public static void setFile(Path traceFile) {
		file = traceFile;
	}

	public static void delete(Path traceFile) {
		synchronized (AgentTrace.class) {
			try {
				Files.deleteIfExists(traceFile);
				Files.deleteIfExists(rotated(traceFile));
			} catch (IOException e) {
				log.debug("Could not delete agent trace: {}", e.getMessage());
			}
		}
	}

	static List<String> fieldNames(String body) {
		try {
			JsonElement parsed = GSON.fromJson(body, JsonElement.class);
			if (parsed != null && parsed.isJsonObject()) {
				return new ArrayList<>(parsed.getAsJsonObject().keySet());
			}
		} catch (JsonParseException ignored) {
		}
		return List.of();
	}

	private static Path rotated(Path traceFile) {
		return traceFile.resolveSibling(traceFile.getFileName() + ".1");
	}

	private static void createOwnerOnly(Path file) throws IOException {
		if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		} else {
			Files.createFile(file);
		}
	}

	static boolean isEnabled() {
		return file != null;
	}

	static void record(String method, String uri, int status, long durationMs, String body) {
		Path target = file;
		if (target == null) {
			return;
		}
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("at", Instant.now().toString());
		line.put("method", method);
		line.put("uri", uri);
		line.put("status", status);
		line.put("ms", durationMs);
		if (body != null && !body.isBlank()) {
			line.put("fields", fieldNames(body));
		}
		byte[] bytes = (GSON.toJson(line) + "\n").getBytes(StandardCharsets.UTF_8);
		synchronized (AgentTrace.class) {
			try {
				Files.createDirectories(target.getParent());
				if (Files.exists(target) && Files.size(target) + bytes.length > MAX_BYTES) {
					Files.move(target, rotated(target), StandardCopyOption.REPLACE_EXISTING);
				}
				if (!Files.exists(target)) {
					createOwnerOnly(target);
				}
				Files.write(target, bytes, StandardOpenOption.APPEND);
			} catch (IOException e) {
				log.debug("Could not write agent trace: {}", e.getMessage());
			}
		}
	}
}
