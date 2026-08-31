package com.annoyeddev.backend.wireguard;

import java.security.SecureRandom;
import java.util.Base64;

public final class WireGuardKeyUtil {
	private WireGuardKeyUtil() {
	}

	public record KeyPairResult(String privateKeyBase64, String publicKeyBase64) {
	}

	public static KeyPairResult generateKeyPair() {
		byte[] raw = new byte[32];
		new SecureRandom().nextBytes(raw);
		byte[] privateScalar = X25519.clampScalar(raw);
		byte[] publicPoint = X25519.scalarBaseMult(privateScalar);
		return new KeyPairResult(encode(privateScalar), encode(publicPoint));
	}

	public static String derivePublicKey(String privateKeyBase64) {
		byte[] privateScalar = decode(privateKeyBase64);
		if (privateScalar.length != 32) {
			throw new IllegalArgumentException("Private key must decode to exactly 32 bytes");
		}
		byte[] publicPoint = X25519.scalarBaseMult(X25519.clampScalar(privateScalar));
		return encode(publicPoint);
	}

	public static String generatePresharedKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return encode(key);
	}

	private static String encode(byte[] raw) {
		return Base64.getEncoder().encodeToString(raw);
	}

	private static byte[] decode(String base64) {
		return Base64.getDecoder().decode(base64.trim());
	}
}
