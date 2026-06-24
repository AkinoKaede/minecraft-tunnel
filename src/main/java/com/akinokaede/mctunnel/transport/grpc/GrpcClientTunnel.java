package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

final class GrpcClientTunnel extends ChannelDuplexHandler {
	private static final String SSL = "McTunnelGrpcSsl";
	private static final String CODEC = "McTunnelGrpcHttp2";
	private static final String CLIENT = "McTunnelGrpcClient";

	private final ClientTunnelEndpoint endpoint;
	private final Queue<PendingWrite> pendingWrites = new ArrayDeque<>();
	private ByteBuf inboundBuffer;
	private Http2StreamChannel streamChannel;
	private boolean streamActive;

	private GrpcClientTunnel(ClientTunnelEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	static void install(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		MinecraftTunnel.info("Connecting through tunnel:\n" + endpoint);

		if (endpoint.usesTls()) {
			pipeline.addAfter("timeout", SSL, sslHandler(endpoint));
		}

		String base = endpoint.usesTls() ? SSL : "timeout";
		pipeline.addAfter(base, CODEC, Http2FrameCodecBuilder.forClient().build());
		pipeline.addAfter(CODEC, "McTunnelGrpcMultiplex", new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
		pipeline.addAfter("McTunnelGrpcMultiplex", CLIENT, new GrpcClientTunnel(endpoint));
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		MinecraftTunnel.info("Starting gRPC tunnel over HTTP/2");
		new Http2StreamChannelBootstrap(ctx.channel())
				.handler(new ChannelInitializer<Http2StreamChannel>() {
					@Override
					protected void initChannel(Http2StreamChannel channel) {
						channel.pipeline().addLast(new StreamHandler(ctx));
					}
				})
				.open()
				.addListener(future -> {
					if (!future.isSuccess()) {
						failPending(future.cause());
						ctx.close();
						return;
					}

					streamChannel = (Http2StreamChannel) future.getNow();
					streamChannel.writeAndFlush(new DefaultHttp2HeadersFrame(requestHeaders(), false));
				});
		super.channelActive(ctx);
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
		if (streamActive && streamChannel != null) {
			streamChannel.flush();
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
		failPending(cause);
		cause.printStackTrace();
		ctx.close();
	}

	private void writeGrpcData(ChannelHandlerContext ctx, ByteBuf byteBuf, ChannelPromise promise) {
		try {
			if (MinecraftTunnel.debugEnabled()) {
				MinecraftTunnel.debug("gRPC C->S (" + byteBuf.readableBytes() + " bytes)");
			}
			ByteBuf encoded = GrpcMessageCodec.encode(ctx.alloc(), byteBuf);
			streamChannel.write(new DefaultHttp2DataFrame(encoded, false), streamChannel.newPromise())
					.addListener(future -> {
						if (future.isSuccess()) {
							promise.setSuccess();
						} else {
							promise.setFailure(future.cause());
						}
					});
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
			if (MinecraftTunnel.debugEnabled()) {
				MinecraftTunnel.debug("gRPC S->C (" + decoded.readableBytes() + " bytes)");
			}
			ctx.fireChannelRead(decoded);
		}
	}

	private void activateStream(ChannelHandlerContext ctx) {
		if (streamActive) {
			return;
		}
		streamActive = true;
		MinecraftTunnel.info("gRPC client stream established; flushing queued Minecraft data");
		while (!pendingWrites.isEmpty()) {
			PendingWrite pending = pendingWrites.remove();
			writeGrpcData(ctx, (ByteBuf) pending.msg(), pending.promise());
		}
		streamChannel.flush();
	}

	private Http2Headers requestHeaders() {
		Http2Headers headers = new DefaultHttp2Headers()
				.method("POST")
				.scheme(endpoint.usesTls() ? "https" : "http")
				.authority(endpoint.httpHost())
				.path(GrpcPaths.methodPath(endpoint.uri()));
		headers.set("content-type", "application/grpc");
		headers.set("te", "trailers");
		headers.set("grpc-encoding", "identity");
		headers.set("user-agent", "minecraft-tunnel");
		return headers;
	}

	private void failPending(Throwable cause) {
		while (!pendingWrites.isEmpty()) {
			pendingWrites.remove().promise().setFailure(cause);
		}
	}

	private static SslHandler sslHandler(ClientTunnelEndpoint endpoint) {
		try {
			SslContext sslContext = SslContextBuilder.forClient()
					.applicationProtocolConfig(new ApplicationProtocolConfig(
							ApplicationProtocolConfig.Protocol.ALPN,
							ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
							ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
							ApplicationProtocolNames.HTTP_2))
					.build();
			String peerHost = endpoint.tlsSni() == null ? endpoint.host() : endpoint.tlsSni();
			SSLEngine engine = sslContext.newEngine(ByteBufAllocator.DEFAULT, peerHost, endpoint.port());

			SSLParameters parameters = engine.getSSLParameters();
			parameters.setServerNames(Collections.singletonList(new SNIHostName(peerHost)));
			parameters.setEndpointIdentificationAlgorithm("HTTPS");
			engine.setSSLParameters(parameters);

			return new SslHandler(engine);
		} catch (SSLException e) {
			throw new IllegalStateException("Unable to create TLS handler for gRPC tunnel", e);
		}
	}

	private record PendingWrite(Object msg, ChannelPromise promise) {
	}

	private final class StreamHandler extends ChannelInboundHandlerAdapter {
		private final ChannelHandlerContext parentCtx;

		private StreamHandler(ChannelHandlerContext parentCtx) {
			this.parentCtx = parentCtx;
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof Http2HeadersFrame headersFrame) {
				try {
					CharSequence status = headersFrame.headers().status();
					if (status == null) {
						return;
					}
					if (!"200".contentEquals(status)) {
						throw new IllegalStateException("gRPC tunnel failed: HTTP/2 status "
								+ headersFrame.headers().status());
					}
					activateStream(parentCtx);
				} finally {
					ReferenceCountUtil.release(msg);
				}
				return;
			}

			if (msg instanceof Http2DataFrame dataFrame) {
				appendInbound(parentCtx, dataFrame.content());
				ReferenceCountUtil.release(dataFrame);
				return;
			}

			if (msg instanceof Http2ResetFrame resetFrame) {
				throw new IllegalStateException("gRPC stream was reset: " + resetFrame.errorCode());
			}

			ReferenceCountUtil.release(msg);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			GrpcClientTunnel.this.exceptionCaught(parentCtx, cause);
		}
	}
}
