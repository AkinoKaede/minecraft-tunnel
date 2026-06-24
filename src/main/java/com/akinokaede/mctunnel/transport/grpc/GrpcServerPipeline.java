package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.util.function.Consumer;

public final class GrpcServerPipeline {
	private static final String CODEC = "McTunnelGrpcHttp2";
	private static final String BRIDGE = "McTunnelGrpcBridge";

	private GrpcServerPipeline() {
	}

	public static void install(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		if (pipeline.get(CODEC) == null && pipeline.get(BRIDGE) == null) {
			pipeline.addAfter("timeout", CODEC, Http2FrameCodecBuilder.forServer().build());
			pipeline.addAfter(CODEC, BRIDGE, new GrpcServerTunnel(metadataConsumer));
		}
	}
}
