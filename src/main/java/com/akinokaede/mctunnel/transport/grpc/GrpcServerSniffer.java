package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocols;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeTunnelProtocol;
import com.akinokaede.mctunnel.transport.websocket.WebSocketTunnelProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public final class GrpcServerSniffer extends ByteToMessageDecoder {
	private static final String SNIFFER = "McTunnelGrpcSniffer";
	private static final String CODEC = "McTunnelGrpcHttp2";
	private static final String BRIDGE = "McTunnelGrpcBridge";
	private static final byte[] HTTP2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

	private final Consumer<TunnelConnectionMetadata> metadataConsumer;

	private GrpcServerSniffer(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
	}

	public static void install(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		if (pipeline.get(SNIFFER) == null && pipeline.get(CODEC) == null && pipeline.get(BRIDGE) == null) {
			pipeline.addAfter("timeout", SNIFFER, new GrpcServerSniffer(metadataConsumer));
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		if (in.readableBytes() < 3) {
			return;
		}

		if (!matchesHttp2Preface(in)) {
			if (TunnelConfig.DISABLE_VANILLA_TCP && !shouldPassToHttp1TunnelSniffer(in)) {
				MinecraftTunnel.debug("Rejecting standard Minecraft TCP connection");
				ctx.close();
				return;
			}

			ctx.pipeline().remove(this);
			return;
		}

		if (in.readableBytes() < HTTP2_PREFACE.length) {
			return;
		}

		MinecraftTunnel.debug("Incoming gRPC HTTP/2 tunnel candidate");
		ctx.pipeline().replace(this, CODEC, Http2FrameCodecBuilder.forServer().build());
		ctx.pipeline().addAfter(CODEC, BRIDGE, new GrpcServerTunnel(metadataConsumer));
	}

	private static boolean matchesHttp2Preface(ByteBuf in) {
		int readable = Math.min(in.readableBytes(), HTTP2_PREFACE.length);
		for (int i = 0; i < readable; i++) {
			if (in.getByte(in.readerIndex() + i) != HTTP2_PREFACE[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean looksLikeHttp1(ByteBuf in) {
		if (in.readableBytes() < 3) {
			return false;
		}
		String prefix = in.toString(in.readerIndex(), 3, StandardCharsets.US_ASCII);
		return "GET".equalsIgnoreCase(prefix);
	}

	private static boolean shouldPassToHttp1TunnelSniffer(ByteBuf in) {
		return looksLikeHttp1(in)
				&& (TunnelProtocols.isRegistered(WebSocketTunnelProtocol.ID)
						|| TunnelProtocols.isRegistered(HttpUpgradeTunnelProtocol.ID));
	}
}
