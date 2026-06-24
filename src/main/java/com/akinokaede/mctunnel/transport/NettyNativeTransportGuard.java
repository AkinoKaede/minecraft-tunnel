package com.akinokaede.mctunnel.transport;

import java.util.Locale;

public final class NettyNativeTransportGuard {
	private static final String NO_NATIVE_PROPERTY = "io.netty.transport.noNative";

	private NettyNativeTransportGuard() {
	}

	public static void disableNativeTransportProbeOnNonLinux() {
		if (System.getProperty(NO_NATIVE_PROPERTY) != null || isLinux()) {
			return;
		}

		System.setProperty(NO_NATIVE_PROPERTY, "true");
		System.out.println("[MinecraftTunnel I] Disabled Netty native transport probing on non-Linux Forge runtime");
	}

	private static boolean isLinux() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
	}
}
