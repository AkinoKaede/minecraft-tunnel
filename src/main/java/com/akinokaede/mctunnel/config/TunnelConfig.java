package com.akinokaede.mctunnel.config;

public final class TunnelConfig {
	public static final boolean DISABLE_VANILLA_TCP =
			Boolean.parseBoolean(System.getProperty("mctunnel.disableVanillaTCP", "false"));

	public static final String SERVER_PROTOCOLS =
			System.getProperty("mctunnel.protocol", System.getProperty("mctunnel.protocols", "all"));

	public static final String SERVER_ENDPOINT =
			blankToNull(System.getProperty("mctunnel.endpoint"));

	public static final String TRUSTED_PROXIES =
			System.getProperty("mctunnel.trustedProxies", "");

	public static final String USER_AGENT =
			blankToNull(System.getProperty("mctunnel.userAgent"));

	public static final int MAX_FRAME_PAYLOAD_LENGTH =
			parseInt(System.getProperty("mctunnel.maxFramePayloadLength"), 65536);

	private TunnelConfig() {
	}

	public static boolean serverProtocolEnabled(String protocolId) {
		if (SERVER_PROTOCOLS == null || SERVER_PROTOCOLS.isBlank()) {
			return true;
		}

		for (String value : SERVER_PROTOCOLS.split(",")) {
			String normalized = normalizeProtocol(value);
			if ("all".equals(normalized) || protocolId.equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	private static String normalizeProtocol(String value) {
		String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
		if ("ws".equals(normalized)) {
			return "websocket";
		}
		return normalized;
	}

	private static int parseInt(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}

		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
