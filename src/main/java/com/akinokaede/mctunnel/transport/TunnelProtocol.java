package com.akinokaede.mctunnel.transport;

import io.netty.channel.ChannelPipeline;
import java.util.function.Consumer;

public interface TunnelProtocol {
	String id();

	void installServer(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer);
}
