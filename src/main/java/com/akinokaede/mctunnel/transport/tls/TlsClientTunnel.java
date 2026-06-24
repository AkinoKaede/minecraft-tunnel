package com.akinokaede.mctunnel.transport.tls;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.transport.ClientTls;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import io.netty.channel.ChannelPipeline;

final class TlsClientTunnel {
	private TlsClientTunnel() {
	}

	static void install(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		MinecraftTunnel.info("Connecting through tunnel:\n" + endpoint);
		pipeline.addAfter("timeout", "McTunnelTls", ClientTls.sslHandler(endpoint));
	}
}
