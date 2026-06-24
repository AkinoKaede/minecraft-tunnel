package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocol;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeServerSniffer;
import io.netty.channel.ChannelPipeline;
import java.util.function.Consumer;

public final class WebSocketTunnelProtocol implements TunnelProtocol {
	public static final String ID = "websocket";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void installServer(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		HttpUpgradeServerSniffer.install(pipeline, metadataConsumer);
	}
}
