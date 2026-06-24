package com.akinokaede.mctunnel.client;

import com.akinokaede.mctunnel.transport.grpc.GrpcClientTunnelProtocol;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeClientTunnelProtocol;
import com.akinokaede.mctunnel.transport.tls.TlsClientTunnelProtocol;
import com.akinokaede.mctunnel.transport.websocket.WebSocketClientTunnelProtocol;

public final class MinecraftTunnelClient {
	private static boolean initialized;

	private MinecraftTunnelClient() {
	}

	public static synchronized void init() {
		if (initialized) {
			return;
		}

		ClientTunnelProtocols.register(new TlsClientTunnelProtocol());
		ClientTunnelProtocols.register(new GrpcClientTunnelProtocol());
		ClientTunnelProtocols.register(new WebSocketClientTunnelProtocol());
		ClientTunnelProtocols.register(new HttpUpgradeClientTunnelProtocol());
		initialized = true;
	}
}
