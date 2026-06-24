package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.TrustedProxyHeaders;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelRequestLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

final class GrpcServerTunnel extends ChannelDuplexHandler {
	private final Consumer<TunnelConnectionMetadata> metadataConsumer;
	private final Queue<PendingWrite> pendingWrites = new ArrayDeque<>();
	private ByteBuf inboundBuffer;
	private Http2FrameStream stream;
	private boolean streamActive;

	GrpcServerTunnel(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof Http2HeadersFrame headersFrame) {
			handleHeaders(ctx, headersFrame);
			return;
		}

		if (msg instanceof Http2DataFrame dataFrame) {
			if (!isTunnelStream(dataFrame)) {
				ReferenceCountUtil.release(dataFrame);
				return;
			}
			appendInbound(ctx, dataFrame.content());
			ReferenceCountUtil.release(dataFrame);
			return;
		}

		if (msg instanceof Http2ResetFrame resetFrame) {
			if (isTunnelStream(resetFrame)) {
				throw new IllegalStateException("gRPC stream was reset: " + resetFrame.errorCode());
			}
			ReferenceCountUtil.release(resetFrame);
			return;
		}

		if (msg instanceof Http2Frame) {
			ReferenceCountUtil.release(msg);
			return;
		}

		ctx.fireChannelRead(msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
		if (!(msg instanceof ByteBuf byteBuf)) {
			ctx.write(msg, promise);
			return;
		}

		if (!streamActive) {
			pendingWrites.add(new PendingWrite(msg, promise));
			return;
		}

		writeGrpcData(ctx, byteBuf, promise);
	}

	@Override
	public void flush(ChannelHandlerContext ctx) {
		if (streamActive && stream != null) {
			ctx.flush();
		}
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) {
		if (inboundBuffer != null) {
			inboundBuffer.release();
			inboundBuffer = null;
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		while (!pendingWrites.isEmpty()) {
			pendingWrites.remove().promise().setFailure(cause);
		}
		cause.printStackTrace();
		ctx.close();
	}

	private void handleHeaders(ChannelHandlerContext ctx, Http2HeadersFrame headersFrame) {
		try {
			if (headersFrame.headers().method() == null) {
				return;
			}

			if (stream != null) {
				writeGrpcRejection(ctx, headersFrame, "Only one gRPC tunnel stream is supported");
				return;
			}

			if (!isConfiguredEndpoint(headersFrame.headers())) {
				writeNotFound(ctx, headersFrame);
				return;
			}

			if (!isTunnelRequest(headersFrame.headers())) {
				writeGrpcRejection(ctx, headersFrame, "Unsupported gRPC tunnel request");
				return;
			}

			stream = headersFrame.stream();
			streamActive = true;
			Map<String, String> metadata = extractMetadata(headersFrame.headers());
			java.net.SocketAddress proxiedRemoteAddress = TrustedProxyHeaders.resolve(
					ctx.channel().remoteAddress(),
					name -> {
						CharSequence value = headersFrame.headers().get(name);
						return value == null ? null : value.toString();
					});
			if (proxiedRemoteAddress != null) {
				metadata.put("proxied.remote_address", proxiedRemoteAddress.toString());
			}
			TunnelRequestLog.accepted(
					GrpcTunnelProtocol.ID,
					headerValue(headersFrame.headers().path()),
					headerValue(headersFrame.headers().authority()),
					ctx.channel().remoteAddress(),
					proxiedRemoteAddress,
					headerValue(headersFrame.headers().get(HttpHeaderNames.USER_AGENT)));
			metadataConsumer.accept(new TunnelConnectionMetadata(
					GrpcTunnelProtocol.ID,
					metadata,
					headersFrame,
					proxiedRemoteAddress));

			Http2Headers responseHeaders = new DefaultHttp2Headers()
					.status("200")
					.set("content-type", "application/grpc");
			ctx.writeAndFlush(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(stream));
			MinecraftTunnel.debug("gRPC server stream is active");

			while (!pendingWrites.isEmpty()) {
				PendingWrite pending = pendingWrites.remove();
				writeGrpcData(ctx, (ByteBuf) pending.msg(), pending.promise());
			}
			ctx.flush();
		} finally {
			ReferenceCountUtil.release(headersFrame);
		}
	}

	private boolean isTunnelRequest(Http2Headers headers) {
		CharSequence method = headers.method();
		CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
		return "POST".contentEquals(method)
				&& contentType != null
				&& contentType.toString().toLowerCase(Locale.ROOT).startsWith("application/grpc");
	}

	private boolean isConfiguredEndpoint(Http2Headers headers) {
		CharSequence path = headers.path();
		return path != null && GrpcPaths.matchesConfiguredEndpoint(path.toString(), TunnelConfig.SERVER_ENDPOINT);
	}

	private void writeNotFound(ChannelHandlerContext ctx, Http2HeadersFrame request) {
		Http2Headers headers = new DefaultHttp2Headers()
				.status("404");
		ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(request.stream()));
	}

	private void writeGrpcRejection(ChannelHandlerContext ctx, Http2HeadersFrame request, String message) {
		Http2Headers headers = new DefaultHttp2Headers()
				.status("200")
				.set("content-type", "application/grpc")
				.set("grpc-status", "12")
				.set("grpc-message", message);
		ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(request.stream()));
	}

	private void writeGrpcData(ChannelHandlerContext ctx, ByteBuf byteBuf, ChannelPromise promise) {
		try {
			ByteBuf encoded = GrpcMessageCodec.encode(ctx.alloc(), byteBuf);
			ctx.write(new DefaultHttp2DataFrame(encoded, false).stream(stream), promise);
		} finally {
			byteBuf.release();
		}
	}

	private void appendInbound(ChannelHandlerContext ctx, ByteBuf content) {
		if (inboundBuffer == null) {
			inboundBuffer = ctx.alloc().buffer(content.readableBytes());
		}
		inboundBuffer.writeBytes(content, content.readerIndex(), content.readableBytes());

		while (true) {
			ByteBuf decoded = GrpcMessageCodec.tryDecode(ctx.alloc(), inboundBuffer);
			if (decoded == null) {
				return;
			}
			ctx.fireChannelRead(decoded);
		}
	}

	private boolean isTunnelStream(Http2StreamFrame frame) {
		return stream != null && frame.stream() == stream;
	}

	private Map<String, String> extractMetadata(Http2Headers headers) {
		Map<String, String> attributes = new LinkedHashMap<>();
		CharSequence path = headers.path();
		if (path != null) {
			attributes.put("uri", path.toString());
		}
		for (Map.Entry<CharSequence, CharSequence> header : headers) {
			attributes.put("header." + header.getKey().toString().toLowerCase(Locale.ROOT), header.getValue().toString());
		}
		return attributes;
	}

	private static String headerValue(CharSequence value) {
		return value == null ? null : value.toString();
	}

	private record PendingWrite(Object msg, ChannelPromise promise) {
	}
}
