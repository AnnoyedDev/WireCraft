package com.annoyeddev.backend.wireguard;

import com.annoyeddev.config.WireGuardProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class WireproxyConfigWriter {
	private WireproxyConfigWriter() {
	}

	public static Path write(Path dir, WireGuardProfile profile, int socksPort) throws IOException {
		Files.createDirectories(dir);
		Path file = dir.resolve("wireproxy-" + sanitize(profile.name) + ".conf");

		StringBuilder sb = new StringBuilder();
		sb.append("[Interface]\n");
		sb.append("Address = ").append(profile.address).append('\n');
		sb.append("PrivateKey = ").append(profile.privateKey).append('\n');
		if (!profile.dns.isEmpty()) {
			sb.append("DNS = ").append(String.join(",", profile.dns)).append('\n');
		}
		if (profile.mtu > 0) {
			sb.append("MTU = ").append(profile.mtu).append('\n');
		}
		sb.append('\n');

		sb.append("[Peer]\n");
		sb.append("PublicKey = ").append(profile.peerPublicKey).append('\n');
		if (profile.presharedKey != null && !profile.presharedKey.isBlank()) {
			sb.append("PresharedKey = ").append(profile.presharedKey).append('\n');
		}
		sb.append("Endpoint = ").append(profile.endpointHost).append(':').append(profile.endpointPort).append('\n');
		sb.append("AllowedIPs = ").append(joinAllowedIps(profile)).append('\n');
		if (profile.persistentKeepalive > 0) {
			sb.append("PersistentKeepalive = ").append(profile.persistentKeepalive).append('\n');
		}
		sb.append('\n');

		sb.append("[Socks5]\n");
		sb.append("BindAddress = 127.0.0.1:").append(socksPort).append('\n');

		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		return file;
	}

	private static String joinAllowedIps(WireGuardProfile profile) {
		StringJoiner joiner = new StringJoiner(",");
		profile.allowedIps.forEach(joiner::add);
		return joiner.toString();
	}

	private static String sanitize(String name) {
		return name.replaceAll("[^a-zA-Z0-9_-]", "_");
	}
}
