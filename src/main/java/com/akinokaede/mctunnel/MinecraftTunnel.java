package com.akinokaede.mctunnel;

import com.akinokaede.mctunnel.transport.TunnelProtocols;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.grpc.GrpcTunnelProtocol;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeTunnelProtocol;
import com.akinokaede.mctunnel.transport.websocket.WebSocketTunnelProtocol;

public final class MinecraftTunnel {
	public static final String MOD_ID = "minecraft_tunnel";

	private static final boolean DEBUG =
			Boolean.parseBoolean(System.getProperty("mctunnel.debug", "false"));

	private static boolean initialized;

	private MinecraftTunnel() {
	}

	public static synchronized void init() {
		if (initialized) {
			return;
		}

		TunnelProtocols.registerIfEnabled(new GrpcTunnelProtocol(), TunnelConfig::serverProtocolEnabled);
		TunnelProtocols.registerIfEnabled(new WebSocketTunnelProtocol(), TunnelConfig::serverProtocolEnabled);
		TunnelProtocols.registerIfEnabled(new HttpUpgradeTunnelProtocol(), TunnelConfig::serverProtocolEnabled);
		initialized = true;
		info("Initialized with " + TunnelProtocols.protocolCount() + " server tunnel protocol(s); vanilla TCP "
				+ (TunnelConfig.vanillaEnabled() ? "enabled" : "disabled"));
	}

	public static boolean debugEnabled() {
		return DEBUG;
	}

	public static void debug(String message) {
		if (DEBUG) {
			System.out.println("[MinecraftTunnel D] " + message);
		}
	}

	public static void info(String message) {
		System.out.println("[MinecraftTunnel I] " + message);
	}
}
