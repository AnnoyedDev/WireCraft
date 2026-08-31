package com.annoyeddev.client;

import com.annoyeddev.VpnMinecraft;
import com.annoyeddev.backend.VpnBackendManager;
import com.annoyeddev.backend.VpnConnectionState;
import com.annoyeddev.config.ServerBinding;
import com.annoyeddev.config.VpnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ProxyRouting {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft/routing");
	private static final long AUTO_CONNECT_TIMEOUT_SECONDS = 20;

	private ProxyRouting() {
	}

	public static void beforeConnect(InetSocketAddress address) {
		VpnConfig config = VpnMinecraft.getConfig();
		if (!config.enabled) {
			return;
		}

		String host = address.getHostString();
		int port = address.getPort();
		Optional<ServerBinding> bindingOpt = config.findBindingForAddress(host, port);
		if (bindingOpt.isEmpty()) {
			return;
		}

		ServerBinding binding = bindingOpt.get();
		if (!binding.autoConnect || binding.profileName.isBlank()) {
			return;
		}

		VpnBackendManager manager = VpnMinecraft.getBackendManager();
		if (manager.getState() == VpnConnectionState.CONNECTED) {
			return;
		}

		LOGGER.info("Auto-connecting WireGuard tunnel '{}' for server {}:{}", binding.profileName, host, port);
		try {
			manager.connectByName(binding.profileName)
					.get(AUTO_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			LOGGER.error("Auto-connect failed before joining {}:{}, continuing without a tunnel", host, port, e);
		}
	}

	public static Optional<InetSocketAddress> currentSocksProxy() {
		VpnConfig config = VpnMinecraft.getConfig();
		if (!config.enabled) {
			return Optional.empty();
		}
		return VpnMinecraft.getBackendManager().getLocalProxyAddress();
	}
}
