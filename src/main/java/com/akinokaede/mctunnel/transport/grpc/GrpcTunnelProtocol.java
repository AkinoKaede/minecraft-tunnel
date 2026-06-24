package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocol;
import io.netty.channel.ChannelPipeline;
import java.util.function.Consumer;

public final class GrpcTunnelProtocol implements TunnelProtocol {
	public static final String ID = "grpc";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void installServer(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		GrpcServerPipeline.install(pipeline, metadataConsumer);
	}
}
