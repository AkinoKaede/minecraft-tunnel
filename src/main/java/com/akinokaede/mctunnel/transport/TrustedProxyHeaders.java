package com.akinokaede.mctunnel.transport;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.google.common.net.InetAddresses;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class TrustedProxyHeaders {
	private static final List<CidrRange> TRUSTED_PROXIES = parseTrustedProxies(TunnelConfig.TRUSTED_PROXIES);

	private TrustedProxyHeaders() {
	}

	public static SocketAddress resolve(SocketAddress remoteAddress, Function<String, String> headerGetter) {
		if (!(remoteAddress instanceof InetSocketAddress remoteInet) || TRUSTED_PROXIES.isEmpty()) {
			return null;
		}

		InetAddress proxyAddress = remoteInet.getAddress();
		if (proxyAddress == null || !isTrusted(proxyAddress)) {
			return null;
		}

		InetAddress forwardedAddress = firstForwardedFor(headerGetter.apply("x-forwarded-for"));
		if (forwardedAddress == null) {
			forwardedAddress = parseHeaderAddress(headerGetter.apply("x-real-ip"));
		}
		if (forwardedAddress == null) {
			return null;
		}

		return new InetSocketAddress(forwardedAddress, remoteInet.getPort());
	}

	private static boolean isTrusted(InetAddress address) {
		for (CidrRange range : TRUSTED_PROXIES) {
			if (range.matches(address)) {
				return true;
			}
		}
		return false;
	}

	private static InetAddress firstForwardedFor(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (String part : value.split(",")) {
			InetAddress address = parseHeaderAddress(part);
			if (address != null) {
				return address;
			}
		}
		return null;
	}

	private static InetAddress parseHeaderAddress(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		String normalized = value.trim();
		int zoneIndex = normalized.indexOf('%');
		if (zoneIndex >= 0) {
			normalized = normalized.substring(0, zoneIndex);
		}
		if (normalized.startsWith("[") && normalized.contains("]")) {
			normalized = normalized.substring(1, normalized.indexOf(']'));
		} else {
			int colon = normalized.indexOf(':');
			int lastColon = normalized.lastIndexOf(':');
			if (colon >= 0 && colon == lastColon) {
				normalized = normalized.substring(0, colon);
			}
		}

		try {
			return InetAddresses.forString(normalized);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static List<CidrRange> parseTrustedProxies(String value) {
		List<CidrRange> ranges = new ArrayList<>();
		if (value == null || value.isBlank()) {
			return ranges;
		}

		for (String token : value.split(",")) {
			String normalized = token.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			try {
				ranges.add(CidrRange.parse(normalized));
			} catch (IllegalArgumentException e) {
				MinecraftTunnel.info("Ignoring invalid trusted proxy range: " + normalized);
			}
		}
		return List.copyOf(ranges);
	}

	private record CidrRange(InetAddress networkAddress, int prefixLength) {
		private static CidrRange parse(String value) {
			String addressPart = value;
			int prefixLength = -1;
			int slash = value.indexOf('/');
			if (slash >= 0) {
				addressPart = value.substring(0, slash);
				prefixLength = Integer.parseInt(value.substring(slash + 1));
			}

			InetAddress address;
			try {
				address = InetAddresses.forString(addressPart);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid IP address", e);
			}

			int bitLength = address.getAddress().length * 8;
			if (prefixLength < 0) {
				prefixLength = bitLength;
			}
			if (prefixLength < 0 || prefixLength > bitLength) {
				throw new IllegalArgumentException("Invalid CIDR prefix length");
			}
			return new CidrRange(address, prefixLength);
		}

		private boolean matches(InetAddress address) {
			byte[] networkBytes = networkAddress.getAddress();
			byte[] addressBytes = address.getAddress();
			if (networkBytes.length != addressBytes.length) {
				return false;
			}

			BigInteger network = new BigInteger(1, networkBytes);
			BigInteger candidate = new BigInteger(1, addressBytes);
			int bitLength = networkBytes.length * 8;
			BigInteger mask = BigInteger.ONE.shiftLeft(bitLength).subtract(BigInteger.ONE)
					.xor(BigInteger.ONE.shiftLeft(bitLength - prefixLength).subtract(BigInteger.ONE));
			return network.and(mask).equals(candidate.and(mask));
		}
	}
}
