package com.annoyeddev.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public final class TarGzExtractor {
	private TarGzExtractor() {
	}

	public static void extractFirstRegularFile(Path tarGz, Path destination) throws IOException {
		try (InputStream fis = Files.newInputStream(tarGz);
			 GZIPInputStream gis = new GZIPInputStream(fis)) {
			byte[] header = new byte[512];
			while (true) {
				int read = readFully(gis, header);
				if (read < 512 || isZeroBlock(header)) {
					break;
				}

				long size = parseOctal(header, 124, 12);
				byte typeFlag = header[156];

				long blocks = (size + 511) / 512;
				long paddedSize = blocks * 512;

				if (typeFlag == '0' || typeFlag == 0) {
					try (var out = Files.newOutputStream(destination)) {
						long remaining = size;
						byte[] buf = new byte[8192];
						while (remaining > 0) {
							int toRead = (int) Math.min(buf.length, remaining);
							int n = gis.read(buf, 0, toRead);
							if (n < 0) {
								throw new IOException("Unexpected end of tar stream");
							}
							out.write(buf, 0, n);
							remaining -= n;
						}
						skip(gis, paddedSize - size);
					}
					return;
				} else {
					skip(gis, paddedSize);
				}
			}
			throw new IOException("No regular file found in " + tarGz);
		}
	}

	private static int readFully(InputStream in, byte[] buf) throws IOException {
		int total = 0;
		while (total < buf.length) {
			int n = in.read(buf, total, buf.length - total);
			if (n < 0) {
				return total;
			}
			total += n;
		}
		return total;
	}

	private static void skip(InputStream in, long n) throws IOException {
		while (n > 0) {
			long skipped = in.skip(n);
			if (skipped <= 0) {
				if (in.read() < 0) {
					return;
				}
				skipped = 1;
			}
			n -= skipped;
		}
	}

	private static boolean isZeroBlock(byte[] header) {
		for (byte b : header) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}

	private static long parseOctal(byte[] header, int offset, int length) {
		String s = new String(header, offset, length).trim();
		if (s.isEmpty()) {
			return 0;
		}
		return Long.parseLong(s.trim(), 8);
	}
}
