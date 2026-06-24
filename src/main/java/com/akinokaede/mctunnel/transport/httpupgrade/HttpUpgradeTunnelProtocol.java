package com.akinokaede.mctunnel.transport.httpupgrade;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocol;
import io.netty.channel.ChannelPipeline;
import java.util.function.Consumer;

public final class HttpUpgradeTunnelProtocol implements TunnelProtocol {
	public static final String ID = "httpupgrade";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void installServer(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		HttpUpgradeServerSniffer.install(pipeline, metadataConsumer);
	}
}
