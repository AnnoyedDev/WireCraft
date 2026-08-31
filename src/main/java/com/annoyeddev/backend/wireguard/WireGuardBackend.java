package com.annoyeddev.backend.wireguard;

import com.annoyeddev.backend.VpnConnectionState;
import com.annoyeddev.config.WireGuardProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class WireGuardBackend {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft/wireguard");
	private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	private final Path binDir;
	private final Path configDir;
	private final WireGuardProfile profile;
	private final String wireproxyPathOverride;
	private final boolean autoDownload;

	private final AtomicReference<VpnConnectionState> state = new AtomicReference<>(VpnConnectionState.DISCONNECTED);
	private final Deque<String> recentLogLines = new ArrayDeque<>();
	private volatile String statusMessage = "";
	private volatile Process process;
	private volatile int socksPort = -1;

	public WireGuardBackend(Path dataDir, WireGuardProfile profile, String wireproxyPathOverride, boolean autoDownload) {
		this.binDir = dataDir.resolve("bin");
		this.configDir = dataDir.resolve("wireproxy-config");
		this.profile = profile;
		this.wireproxyPathOverride = wireproxyPathOverride;
		this.autoDownload = autoDownload;
	}

	public CompletableFuture<Void> start() {
		state.set(VpnConnectionState.CONNECTING);
		return CompletableFuture.runAsync(this::doStart, EXECUTOR);
	}

	private void doStart() {
		try {
			Path binary = WireproxyBinaryLocator.resolve(binDir, wireproxyPathOverride, autoDownload);
			socksPort = findFreePort();
			Path configFile = WireproxyConfigWriter.write(configDir, profile, socksPort);

			ProcessBuilder pb = new ProcessBuilder(binary.toString(), "-c", configFile.toString());
			pb.redirectErrorStream(true);
			process = pb.start();
			pumpOutput(process);

			awaitPortOpen(process, "127.0.0.1", socksPort, 8000);

			if (process.isAlive()) {
				state.set(VpnConnectionState.CONNECTED);
				statusMessage = "Connected via wireproxy on 127.0.0.1:" + socksPort;
				LOGGER.info(statusMessage);
			} else {
				fail("wireproxy exited before the tunnel came up (exit " + process.exitValue() + "): " + lastLogLines());
			}
		} catch (Exception e) {
			LOGGER.error("Failed to start WireGuard tunnel", e);
			fail(e.getMessage() != null ? e.getMessage() : e.toString());
		}
	}

	private void fail(String message) {
		statusMessage = message;
		state.set(VpnConnectionState.ERROR);
		stopProcessOnly();
	}

	public void stop() {
		state.set(VpnConnectionState.DISCONNECTING);
		stopProcessOnly();
		state.set(VpnConnectionState.DISCONNECTED);
	}

	private void stopProcessOnly() {
		Process p = process;
		if (p != null && p.isAlive()) {
			p.destroy();
			try {
				if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
					p.destroyForcibly();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				p.destroyForcibly();
			}
		}
	}

	public VpnConnectionState getState() {
		return state.get();
	}

	public Optional<InetSocketAddress> getLocalProxyAddress() {
		if (state.get() == VpnConnectionState.CONNECTED && socksPort > 0) {
			return Optional.of(new InetSocketAddress("127.0.0.1", socksPort));
		}
		return Optional.empty();
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	private void pumpOutput(Process process) {
		Thread thread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					LOGGER.info("[wireproxy] {}", line);
					synchronized (recentLogLines) {
						recentLogLines.addLast(line);
						while (recentLogLines.size() > 20) {
							recentLogLines.removeFirst();
						}
					}
				}
			} catch (IOException ignored) {
			}
		}, "wireproxy-output-pump");
		thread.setDaemon(true);
		thread.start();
	}

	private String lastLogLines() {
		synchronized (recentLogLines) {
			return String.join(" | ", recentLogLines);
		}
	}

	private void awaitPortOpen(Process process, String host, int port, long timeoutMillis) throws IOException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		IOException last = null;
		while (System.currentTimeMillis() < deadline) {
			if (!process.isAlive()) {
				throw new IOException("wireproxy exited before the tunnel came up (exit " + process.exitValue()
						+ "): " + lastLogLines());
			}
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(host, port), 250);
				return;
			} catch (IOException e) {
				last = e;
				try {
					Thread.sleep(150);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while waiting for wireproxy", ie);
				}
			}
		}
		throw new IOException("Timed out waiting for wireproxy's SOCKS5 port to open", last);
	}

	private static int findFreePort() throws IOException {
		try (var socket = new java.net.ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
