package com.akinokaede.mctunnel.transport;

import com.akinokaede.mctunnel.MinecraftTunnel;
import java.net.SocketAddress;

public final class TunnelRequestLog {
	private static final int MAX_VALUE_LENGTH = 256;

	private TunnelRequestLog() {
	}

	public static void accepted(
			String protocol,
			String uri,
			String authority,
			SocketAddress remoteAddress,
			SocketAddress proxiedRemoteAddress,
			String userAgent) {
		StringBuilder message = new StringBuilder()
				.append("Accepted ")
				.append(protocol)
				.append(" tunnel")
				.append(" uri=")
				.append(quoted(uri))
				.append(" authority=")
				.append(quoted(authority))
				.append(" remote=")
				.append(quoted(address(remoteAddress)))
				.append(" userAgent=")
				.append(quoted(userAgent));
		if (proxiedRemoteAddress != null) {
			message.append(" proxiedRemote=").append(quoted(address(proxiedRemoteAddress)));
		}
		MinecraftTunnel.info(message.toString());
	}

	private static String address(SocketAddress address) {
		return address == null ? null : address.toString();
	}

	private static String quoted(String value) {
		return "\"" + sanitize(value) + "\"";
	}

	private static String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		StringBuilder sanitized = new StringBuilder(Math.min(value.length(), MAX_VALUE_LENGTH));
		for (int i = 0; i < value.length() && sanitized.length() < MAX_VALUE_LENGTH; i++) {
			char c = value.charAt(i);
			if (c < 0x20 || c == 0x7F) {
				sanitized.append('?');
			} else {
				sanitized.append(c);
			}
		}
		if (value.length() > sanitized.length()) {
			sanitized.append("...");
		}
		return sanitized.toString();
	}
}
