package com.annoyeddev.backend.wireguard;

import java.math.BigInteger;

final class X25519 {
	private static final BigInteger P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
	private static final BigInteger A24 = BigInteger.valueOf(121665);
	private static final BigInteger BASE_U = BigInteger.valueOf(9);

	private X25519() {
	}

	static byte[] scalarBaseMult(byte[] clampedScalarLE) {
		return scalarMult(clampedScalarLE, encodeU(BASE_U));
	}

	static byte[] scalarMult(byte[] clampedScalarLE, byte[] uLE) {
		BigInteger k = decodeLittleEndian(clampedScalarLE);
		byte[] maskedU = uLE.clone();
		maskedU[31] &= (byte) 0x7F;
		BigInteger u = decodeLittleEndian(maskedU).mod(P);

		BigInteger x1 = u;
		BigInteger x2 = BigInteger.ONE;
		BigInteger z2 = BigInteger.ZERO;
		BigInteger x3 = u;
		BigInteger z3 = BigInteger.ONE;
		int swap = 0;

		for (int t = 254; t >= 0; t--) {
			int kt = k.testBit(t) ? 1 : 0;
			swap ^= kt;
			BigInteger[] xs = cswap(swap, x2, x3);
			x2 = xs[0];
			x3 = xs[1];
			BigInteger[] zs = cswap(swap, z2, z3);
			z2 = zs[0];
			z3 = zs[1];
			swap = kt;

			BigInteger A = x2.add(z2).mod(P);
			BigInteger AA = A.multiply(A).mod(P);
			BigInteger B = x2.subtract(z2).mod(P);
			BigInteger BB = B.multiply(B).mod(P);
			BigInteger E = AA.subtract(BB).mod(P);
			BigInteger C = x3.add(z3).mod(P);
			BigInteger D = x3.subtract(z3).mod(P);
			BigInteger DA = D.multiply(A).mod(P);
			BigInteger CB = C.multiply(B).mod(P);

			BigInteger sum = DA.add(CB).mod(P);
			x3 = sum.multiply(sum).mod(P);
			BigInteger diff = DA.subtract(CB).mod(P);
			z3 = x1.multiply(diff.multiply(diff).mod(P)).mod(P);
			x2 = AA.multiply(BB).mod(P);
			z2 = E.multiply(AA.add(A24.multiply(E)).mod(P)).mod(P);
		}

		BigInteger[] xs = cswap(swap, x2, x3);
		x2 = xs[0];
		BigInteger[] zs = cswap(swap, z2, z3);
		z2 = zs[0];

		BigInteger result = x2.multiply(z2.modPow(P.subtract(BigInteger.TWO), P)).mod(P);
		return encodeU(result);
	}

	static byte[] clampScalar(byte[] rawLE32) {
		byte[] k = rawLE32.clone();
		k[0] &= (byte) 248;
		k[31] &= (byte) 127;
		k[31] |= (byte) 64;
		return k;
	}

	private static BigInteger[] cswap(int swap, BigInteger a, BigInteger b) {
		if (swap == 0) {
			return new BigInteger[] {a, b};
		}
		return new BigInteger[] {b, a};
	}

	private static BigInteger decodeLittleEndian(byte[] le) {
		byte[] be = new byte[le.length];
		for (int i = 0; i < le.length; i++) {
			be[i] = le[le.length - 1 - i];
		}
		return new BigInteger(1, be);
	}

	private static byte[] encodeU(BigInteger u) {
		byte[] be = u.mod(P).toByteArray();
		byte[] le = new byte[32];
		int len = Math.min(be.length, 32);
		for (int i = 0; i < len; i++) {
			byte srcByte = be[be.length - 1 - i];
			le[i] = srcByte;
		}
		return le;
	}
}
