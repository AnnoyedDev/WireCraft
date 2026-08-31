package com.annoyeddev.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VpnConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean enabled = true;

	public boolean autoDownloadWireproxy = false;
	public String wireproxyPath = "";
	public boolean wireproxyConsentAsked = false;

	public boolean showPublicIp = false;

	public boolean connectOnStartup = false;

	public List<WireGuardProfile> wireGuardProfiles = new ArrayList<>();
	public List<ServerBinding> serverBindings = new ArrayList<>();

	public Optional<WireGuardProfile> findWireGuardProfile(String name) {
		return wireGuardProfiles.stream().filter(p -> p.name.equals(name)).findFirst();
	}

	public Optional<WireGuardProfile> primaryConfiguredProfile() {
		if (wireGuardProfiles.isEmpty()) {
			return Optional.empty();
		}
		WireGuardProfile profile = wireGuardProfiles.get(0);
		if (profile.privateKey.isBlank() || profile.peerPublicKey.isBlank() || profile.endpointHost.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(profile);
	}

	public Optional<ServerBinding> findBindingForAddress(String host, int port) {
		String full = host + ":" + port;
		for (ServerBinding b : serverBindings) {
			if (b.serverAddress.equalsIgnoreCase(full)) {
				return Optional.of(b);
			}
		}
		for (ServerBinding b : serverBindings) {
			if (b.serverAddress.equalsIgnoreCase(host)) {
				return Optional.of(b);
			}
		}
		return Optional.empty();
	}

	public static VpnConfig load(Path path) {
		if (!Files.exists(path)) {
			VpnConfig fresh = new VpnConfig();
			fresh.save(path);
			return fresh;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			VpnConfig loaded = GSON.fromJson(reader, VpnConfig.class);
			return loaded != null ? loaded : new VpnConfig();
		} catch (IOException e) {
			LOGGER.error("Failed to read {}, falling back to defaults", path, e);
			return new VpnConfig();
		}
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write {}", path, e);
		}
	}
}
