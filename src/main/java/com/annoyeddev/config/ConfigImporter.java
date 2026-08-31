package com.annoyeddev.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigImporter {
	private ConfigImporter() {
	}

	public static WireGuardProfile importWireGuard(Path file) throws IOException {
		if (!Files.isReadable(file)) {
			throw new IOException("Cannot read " + file + sandboxHint(file));
		}

		WireGuardProfile profile = new WireGuardProfile();
		profile.dns.clear();
		profile.allowedIps.clear();
		profile.name = stripExtension(file.getFileName().toString());

		String section = "";
		boolean peerSeen = false;
		for (String rawLine : Files.readAllLines(file)) {
			String line = stripComment(rawLine).trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("[")) {
				section = line.toLowerCase(Locale.ROOT);
				if (section.contains("peer")) {
					if (peerSeen) {
						break;
					}
					peerSeen = true;
				}
				continue;
			}

			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).trim();

			if (section.contains("interface")) {
				switch (key) {
					case "address" -> profile.address = value;
					case "privatekey" -> profile.privateKey = value;
					case "dns" -> profile.dns.addAll(splitCsv(value));
					case "mtu" -> profile.mtu = parseIntOr(value, profile.mtu);
					default -> {
					}
				}
			} else if (section.contains("peer")) {
				switch (key) {
					case "publickey" -> profile.peerPublicKey = value;
					case "presharedkey" -> profile.presharedKey = value;
					case "allowedips" -> profile.allowedIps.addAll(splitCsv(value));
					case "persistentkeepalive" -> profile.persistentKeepalive = parseIntOr(value, profile.persistentKeepalive);
					case "endpoint" -> {
						int lastColon = value.lastIndexOf(':');
						if (lastColon > 0) {
							profile.endpointHost = value.substring(0, lastColon).replace("[", "").replace("]", "");
							profile.endpointPort = parseIntOr(value.substring(lastColon + 1), profile.endpointPort);
						} else {
							profile.endpointHost = value;
						}
					}
					default -> {
					}
				}
			}
		}

		if (profile.allowedIps.isEmpty()) {
			profile.allowedIps.add("0.0.0.0/0");
		}
		if (profile.privateKey.isBlank() || profile.peerPublicKey.isBlank() || profile.endpointHost.isBlank()) {
			throw new IOException("Missing PrivateKey, peer PublicKey, or Endpoint - not a usable WireGuard config");
		}
		return profile;
	}

	private static String sandboxHint(Path file) {
		if (!Files.exists(Path.of("/.flatpak-info"))) {
			return "";
		}
		Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		if (file.toAbsolutePath().normalize().startsWith(gameDir)) {
			return "";
		}
		return " - the launcher is running in a Flatpak sandbox, which only exposes a few specific host "
				+ "locations (which one varies by system/config - being under your home directory does not "
				+ "guarantee it's visible in the sandbox). The one path guaranteed to work without changing any "
				+ "permissions is the game's own folder, since the game already reads/writes there: copy the "
				+ "file into " + gameDir + " and import it from there (or grant broader access with Flatseal / "
				+ "`flatpak override --user --filesystem=host org.prismlauncher.PrismLauncher`).";
	}

	private static String stripComment(String line) {
		int hash = line.indexOf('#');
		int semi = line.indexOf(';');
		int cut = hash < 0 ? semi : (semi < 0 ? hash : Math.min(hash, semi));
		return cut < 0 ? line : line.substring(0, cut);
	}

	private static List<String> splitCsv(String value) {
		List<String> out = new ArrayList<>();
		for (String part : value.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				out.add(trimmed);
			}
		}
		return out;
	}

	private static int parseIntOr(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}
}
