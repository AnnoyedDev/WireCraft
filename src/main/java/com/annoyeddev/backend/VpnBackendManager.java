package com.annoyeddev.backend;

import com.annoyeddev.backend.wireguard.WireGuardBackend;
import com.annoyeddev.config.VpnConfig;
import com.annoyeddev.config.WireGuardProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class VpnBackendManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft");

	private final Path dataDir;
	private final VpnConfig config;
	private final AtomicReference<WireGuardBackend> active = new AtomicReference<>();
	private volatile String activeProfileName = "";

	public VpnBackendManager(Path dataDir, VpnConfig config) {
		this.dataDir = dataDir;
		this.config = config;
	}

	public synchronized CompletableFuture<Void> connect(WireGuardProfile profile) {
		disconnectInternal();
		WireGuardBackend backend = new WireGuardBackend(dataDir, profile, config.wireproxyPath, config.autoDownloadWireproxy);
		active.set(backend);
		activeProfileName = profile.name;
		return backend.start();
	}

	public CompletableFuture<Void> connectByName(String profileName) {
		return config.findWireGuardProfile(profileName)
				.map(this::connect)
				.orElseGet(() -> {
					String message = "No WireGuard profile named '" + profileName + "'";
					LOGGER.error(message);
					return CompletableFuture.failedFuture(new IllegalArgumentException(message));
				});
	}

	public synchronized void disconnect() {
		disconnectInternal();
	}

	private void disconnectInternal() {
		WireGuardBackend backend = active.getAndSet(null);
		if (backend != null) {
			backend.stop();
		}
		activeProfileName = "";
	}

	public VpnConnectionState getState() {
		WireGuardBackend backend = active.get();
		return backend != null ? backend.getState() : VpnConnectionState.DISCONNECTED;
	}

	public String getActiveProfileName() {
		return activeProfileName;
	}

	public Optional<InetSocketAddress> getLocalProxyAddress() {
		WireGuardBackend backend = active.get();
		return backend != null ? backend.getLocalProxyAddress() : Optional.empty();
	}

	public String getStatusMessage() {
		WireGuardBackend backend = active.get();
		return backend != null ? backend.getStatusMessage() : "";
	}
}
