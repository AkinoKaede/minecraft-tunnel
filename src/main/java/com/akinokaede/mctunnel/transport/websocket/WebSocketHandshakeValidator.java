package com.akinokaede.mctunnel.transport.websocket;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Base64;
import java.util.Locale;

public final class WebSocketHandshakeValidator {
	private static final int NONCE_LENGTH = 16;

	private WebSocketHandshakeValidator() {
	}

	public static boolean hasWebSocketKey(HttpRequest request) {
		return request.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_KEY);
	}

	public static boolean isUpgradeRequest(HttpRequest request) {
		HttpHeaders headers = request.headers();
		return HttpMethod.GET.equals(request.method())
				&& HttpVersion.HTTP_1_1.equals(request.protocolVersion())
				&& containsToken(headers, HttpHeaderNames.CONNECTION, "upgrade")
				&& containsToken(headers, HttpHeaderNames.UPGRADE, "websocket");
	}

	public static boolean isValidOpeningHandshake(HttpRequest request) {
		return isUpgradeRequest(request)
				&& hasRequiredHost(request)
				&& isVersion13(request)
				&& hasValidKey(request);
	}

	public static boolean isVersion13(HttpRequest request) {
		return "13".equals(request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION));
	}

	private static boolean hasRequiredHost(HttpRequest request) {
		String host = request.headers().get(HttpHeaderNames.HOST);
		return host != null && !host.isBlank();
	}

	private static boolean hasValidKey(HttpRequest request) {
		String value = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_KEY);
		if (value == null || value.isBlank()) {
			return false;
		}

		try {
			return Base64.getDecoder().decode(value.trim()).length == NONCE_LENGTH;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static boolean containsToken(HttpHeaders headers, CharSequence name, String expected) {
		for (String value : headers.getAll(name)) {
			for (String token : value.split(",")) {
				if (expected.equals(token.trim().toLowerCase(Locale.ROOT))) {
					return true;
				}
			}
		}
		return false;
	}
}
