package com.annoyeddev.backend.wireguard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.annoyeddev.util.TarGzExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class WireproxyBinaryLocator {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft/wireproxy");
	private static final String RELEASES_API = "https://api.github.com/repos/windtf/wireproxy/releases/latest";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public interface ProgressListener {
		void onProgress(long bytesDownloaded, long totalBytes);
	}

	private static final ProgressListener NO_OP_PROGRESS = (downloaded, total) -> {
	};

	private WireproxyBinaryLocator() {
	}

	public static Path resolve(Path binDir, String configuredPath, boolean autoDownload) throws IOException {
		if (configuredPath != null && !configuredPath.isBlank()) {
			Path p = Path.of(configuredPath);
			if (Files.isExecutable(p)) {
				return p;
			}
			throw new IOException("Configured wireproxy path is not an executable file: " + p);
		}

		Path cached = binDir.resolve(binaryName());
		if (Files.isExecutable(cached)) {
			return cached;
		}

		if (!autoDownload) {
			throw new IOException(
					"No wireproxy binary found. Either enable auto-download in the config screen, "
							+ "or download one yourself from https://github.com/windtf/wireproxy/releases "
							+ "and set its path, expected at: " + cached);
		}

		return downloadFresh(binDir, NO_OP_PROGRESS);
	}

	public static Path downloadFresh(Path binDir, ProgressListener listener) throws IOException {
		Files.createDirectories(binDir);

		JsonObject release = fetchJson(RELEASES_API).getAsJsonObject();
		JsonArray assets = release.getAsJsonArray("assets");

		String assetName = assetName();
		String downloadUrl = null;
		String checksumsUrl = null;
		for (JsonElement el : assets) {
			JsonObject asset = el.getAsJsonObject();
			String name = asset.get("name").getAsString();
			if (name.equals(assetName)) {
				downloadUrl = asset.get("browser_download_url").getAsString();
			}
			if (name.equals("checksums.txt")) {
				checksumsUrl = asset.get("browser_download_url").getAsString();
			}
		}
		if (downloadUrl == null) {
			throw new IOException("No wireproxy release asset matches this platform: " + assetName);
		}

		LOGGER.info("Downloading wireproxy from {}", downloadUrl);
		Path tarGz = Files.createTempFile("wireproxy", ".tar.gz");
		try {
			downloadTo(downloadUrl, tarGz, listener);

			Optional<String> expectedSha256 = checksumsUrl != null
					? findChecksum(checksumsUrl, assetName)
					: Optional.empty();
			if (expectedSha256.isPresent()) {
				verifySha256(tarGz, expectedSha256.get());
			} else {
				LOGGER.warn("Could not verify wireproxy checksum (checksums.txt entry not found); proceeding anyway");
			}

			Path target = binDir.resolve(binaryName());
			Path extractedTemp = Files.createTempFile(binDir, "wireproxy-extract", ".tmp");
			TarGzExtractor.extractFirstRegularFile(tarGz, extractedTemp);
			Files.move(extractedTemp, target, StandardCopyOption.REPLACE_EXISTING);
			if (!isWindows()) {
				target.toFile().setExecutable(true, true);
			}
			return target;
		} finally {
			Files.deleteIfExists(tarGz);
		}
	}

	private static void downloadTo(String url, Path dest, ProgressListener listener) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofMinutes(2))
					.GET()
					.build();
			HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				throw new IOException("Download failed, HTTP " + response.statusCode() + " for " + url);
			}
			long total = response.headers().firstValueAsLong("content-length").orElse(-1);
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(dest)) {
				byte[] buf = new byte[8192];
				long downloaded = 0;
				int n;
				while ((n = in.read(buf)) >= 0) {
					out.write(buf, 0, n);
					downloaded += n;
					listener.onProgress(downloaded, total);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Download interrupted", e);
		}
	}

	private static JsonElement fetchJson(String url) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(15))
					.header("Accept", "application/vnd.github+json")
					.GET()
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IOException("GitHub API request failed, HTTP " + response.statusCode());
			}
			return JsonParser.parseString(response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Request interrupted", e);
		}
	}

	private static Optional<String> findChecksum(String checksumsUrl, String assetName) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(checksumsUrl)).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return Optional.empty();
			}
			for (String line : response.body().split("\n")) {
				String trimmed = line.trim();
				if (trimmed.endsWith(assetName)) {
					String[] parts = trimmed.split("\\s+");
					if (parts.length >= 2) {
						return Optional.of(parts[0].toLowerCase(Locale.ROOT));
					}
				}
			}
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	private static void verifySha256(Path file, String expectedHex) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var in = Files.newInputStream(file)) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) >= 0) {
					digest.update(buf, 0, n);
				}
			}
			String actualHex = HexFormat.of().formatHex(digest.digest());
			if (!actualHex.equalsIgnoreCase(expectedHex)) {
				throw new IOException("wireproxy checksum mismatch: expected " + expectedHex + " got " + actualHex);
			}
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static String assetName() {
		String os = osToken();
		String arch = archToken();
		return "wireproxy_" + os + "_" + arch + ".tar.gz";
	}

	private static String binaryName() {
		return isWindows() ? "wireproxy.exe" : "wireproxy";
	}

	private static boolean isWindows() {
		return osName().contains("win");
	}

	private static String osName() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	}

	private static String osToken() {
		String name = osName();
		if (name.contains("win")) return "windows";
		if (name.contains("mac") || name.contains("darwin")) return "darwin";
		return "linux";
	}

	private static final Set<String> KNOWN_ARM64 = Set.of("aarch64", "arm64");

	private static String archToken() {
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (KNOWN_ARM64.contains(arch)) return "arm64";
		if (arch.equals("amd64") || arch.equals("x86_64")) return "amd64";
		if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return "386";
		if (arch.startsWith("arm")) return "arm";
		return arch;
	}
}
