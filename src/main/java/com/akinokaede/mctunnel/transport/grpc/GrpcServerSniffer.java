package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class GrpcServerSniffer {
	private static final String CODEC = "McTunnelGrpcHttp2";
	private static final String BRIDGE = "McTunnelGrpcBridge";
	private static final byte[] HTTP2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

	private GrpcServerSniffer() {
	}

	public static void install(ChannelPipeline pipeline, ChannelHandler oldHandler, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		if (pipeline.get(CODEC) == null && pipeline.get(BRIDGE) == null) {
			pipeline.replace(oldHandler, CODEC, Http2FrameCodecBuilder.forServer().build());
			pipeline.addAfter(CODEC, BRIDGE, new GrpcServerTunnel(metadataConsumer));
		}
	}

	public static boolean isIncompleteHttp2Preface(ByteBuf in) {
		return in.readableBytes() < HTTP2_PREFACE.length && matchesHttp2Preface(in);
	}

	public static boolean hasCompleteHttp2Preface(ByteBuf in) {
		return in.readableBytes() >= HTTP2_PREFACE.length && matchesHttp2Preface(in);
	}

	public static boolean matchesHttp2Preface(ByteBuf in) {
		int readableBytes = in.readableBytes();
		if (readableBytes == 0) {
			return false;
		}

		in.markReaderIndex();
		int bytesToCompare = Math.min(readableBytes, HTTP2_PREFACE.length);
		boolean matches = true;
		for (int i = 0; i < bytesToCompare; i++) {
			if (in.readByte() != HTTP2_PREFACE[i]) {
				matches = false;
				break;
			}
		}
		in.resetReaderIndex();
		return matches;
	}
}
