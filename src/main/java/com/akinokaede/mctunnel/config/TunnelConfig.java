package com.akinokaede.mctunnel.config;

public final class TunnelConfig {
	public static final String VANILLA_PROTOCOL_ID = "vanilla";

	public static final String SERVER_PROTOCOLS =
			System.getProperty("mctunnel.protocol", System.getProperty("mctunnel.protocols", "websocket,httpupgrade,vanilla"));

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

	public static boolean vanillaEnabled() {
		return serverProtocolEnabled(VANILLA_PROTOCOL_ID);
	}

	public static boolean serverProtocolEnabled(String protocolId) {
		if (SERVER_PROTOCOLS == null) {
			return false;
		}

		boolean grpcEnabled = containsProtocol("grpc");
		if (grpcEnabled) {
			return "grpc".equals(protocolId);
		}

		if ("grpc".equals(protocolId)) {
			return false;
		}

		return containsProtocol(protocolId);
	}

	private static boolean containsProtocol(String protocolId) {
		for (String value : SERVER_PROTOCOLS.split(",")) {
			String normalized = normalizeProtocol(value);
			if (protocolId.equals(normalized)) {
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
