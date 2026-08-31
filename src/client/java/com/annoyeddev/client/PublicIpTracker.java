package com.annoyeddev.client;

import com.annoyeddev.VpnMinecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class PublicIpTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft/publicip");
	private static final URI LOOKUP_URI = URI.create("https://api.ipify.org");
	private static final long REFRESH_INTERVAL_MILLIS = 20_000;
	private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	private static final AtomicReference<String> cachedIp = new AtomicReference<>();
	private static final AtomicReference<Boolean> viaTunnel = new AtomicReference<>(false);
	private static final AtomicBoolean fetching = new AtomicBoolean(false);
	private static volatile long lastFetchStarted = 0;

	private PublicIpTracker() {
	}

	public static void tickIfEnabled() {
		if (!VpnMinecraft.getConfig().showPublicIp) {
			return;
		}
		refreshIfStale();
	}

	public static void refreshIfStale() {
		long now = System.currentTimeMillis();
		if (now - lastFetchStarted < REFRESH_INTERVAL_MILLIS) {
			return;
		}
		refreshNow();
	}

	public static void refreshNow() {
		if (!fetching.compareAndSet(false, true)) {
			return;
		}
		lastFetchStarted = System.currentTimeMillis();
		EXECUTOR.submit(PublicIpTracker::fetch);
	}

	private static void fetch() {
		try {
			Optional<InetSocketAddress> socksProxy = VpnMinecraft.getBackendManager().getLocalProxyAddress();
			HttpURLConnection connection = (HttpURLConnection) (socksProxy.isPresent()
					? LOOKUP_URI.toURL().openConnection(new Proxy(Proxy.Type.SOCKS, socksProxy.get()))
					: LOOKUP_URI.toURL().openConnection(Proxy.NO_PROXY));
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestMethod("GET");

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				String ip = reader.readLine();
				if (ip != null && !ip.isBlank()) {
					cachedIp.set(ip.trim());
					viaTunnel.set(socksProxy.isPresent());
				}
			}
		} catch (IOException e) {
			LOGGER.debug("Public IP lookup failed", e);
		} finally {
			fetching.set(false);
		}
	}

	public static Optional<String> getCachedIp() {
		return Optional.ofNullable(cachedIp.get());
	}

	public static boolean isCurrentIpViaTunnel() {
		return Boolean.TRUE.equals(viaTunnel.get());
	}

	public static boolean isFetching() {
		return fetching.get();
	}
}
