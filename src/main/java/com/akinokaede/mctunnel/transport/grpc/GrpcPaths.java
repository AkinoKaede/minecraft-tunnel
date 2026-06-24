package com.akinokaede.mctunnel.transport.grpc;

import java.net.URI;

final class GrpcPaths {
	private static final String DEFAULT_SERVICE_NAME = "MinecraftTunnel";
	private static final String TUN_METHOD = "Tun";

	private GrpcPaths() {
	}

	static String methodPath(URI uri) {
		String serviceName = uri.getRawPath();
		if (serviceName == null || serviceName.isEmpty() || "/".equals(serviceName)) {
			serviceName = DEFAULT_SERVICE_NAME;
		}
		return methodPath(serviceName);
	}

	static boolean matchesConfiguredEndpoint(String requestPath, String configuredEndpoint) {
		if (configuredEndpoint == null || configuredEndpoint.isBlank()) {
			return true;
		}
		return requestPath.equals(configuredEndpoint) || requestPath.equals(methodPath(configuredEndpoint));
	}

	private static String methodPath(String serviceName) {
		String normalized = stripSlashes(serviceName);
		if (normalized.isEmpty()) {
			normalized = DEFAULT_SERVICE_NAME;
		}
		if (normalized.endsWith("/" + TUN_METHOD)) {
			return "/" + normalized;
		}
		return "/" + normalized + "/" + TUN_METHOD;
	}

	private static String stripSlashes(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) == '/') {
			start++;
		}
		while (end > start && value.charAt(end - 1) == '/') {
			end--;
		}
		return value.substring(start, end);
	}
}
