package com.akinokaede.mctunnel.transport.httpupgrade;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocols;
import com.akinokaede.mctunnel.transport.grpc.GrpcTunnelProtocol;
import com.akinokaede.mctunnel.transport.websocket.WebSocketServerHandshake;
import com.akinokaede.mctunnel.transport.websocket.WebSocketHandshakeValidator;
import com.akinokaede.mctunnel.transport.websocket.WebSocketTunnelProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public final class HttpUpgradeServerSniffer extends ByteToMessageDecoder {
	private static final String SNIFFER = "McTunnelHttpUpgradeSniffer";
	private static final String CODEC = "McTunnelHttpServer";
	private static final String ROUTER = "McTunnelHttpUpgradeRouter";

	private final Consumer<TunnelConnectionMetadata> metadataConsumer;

	private HttpUpgradeServerSniffer(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
	}

	public static void install(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		if (pipeline.get(SNIFFER) == null && pipeline.get(CODEC) == null && pipeline.get(ROUTER) == null) {
			pipeline.addAfter("timeout", SNIFFER, new HttpUpgradeServerSniffer(metadataConsumer));
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		if (in.readableBytes() < 3) {
			return;
		}

		in.markReaderIndex();
		byte[] bytes = new byte[3];
		in.readBytes(bytes);
		in.resetReaderIndex();

		String methodPrefix = new String(bytes, StandardCharsets.US_ASCII);
		if ("PRI".equalsIgnoreCase(methodPrefix) && TunnelProtocols.isRegistered(GrpcTunnelProtocol.ID)) {
			MinecraftTunnel.debug("Incoming HTTP/2 tunnel candidate");
			ctx.pipeline().remove(this);
			return;
		}

		if ("GET".equalsIgnoreCase(methodPrefix)) {
			MinecraftTunnel.debug("Incoming HTTP Upgrade tunnel candidate");
			ctx.pipeline().replace(this, CODEC, new HttpServerCodec());
			ctx.pipeline().addAfter(CODEC, "McTunnelHttpAggregator", new HttpObjectAggregator(8192 * 4));
			ctx.pipeline().addAfter("McTunnelHttpAggregator", ROUTER, new Router(metadataConsumer));
			return;
		}

		if (TunnelConfig.DISABLE_VANILLA_TCP) {
			MinecraftTunnel.debug("Rejecting standard Minecraft TCP connection");
			ctx.close();
			return;
		}

		MinecraftTunnel.debug("Incoming vanilla TCP connection");
		ctx.pipeline().remove(this);
	}

	private static final class Router extends io.netty.channel.ChannelInboundHandlerAdapter {
		private final Consumer<TunnelConnectionMetadata> metadataConsumer;

		private Router(Consumer<TunnelConnectionMetadata> metadataConsumer) {
			this.metadataConsumer = metadataConsumer;
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (!(msg instanceof HttpRequest request)) {
				ctx.fireChannelRead(msg);
				return;
			}

			boolean websocketRequest = WebSocketHandshakeValidator.isValidOpeningHandshake(request);
			if (websocketRequest && TunnelProtocols.isRegistered(WebSocketTunnelProtocol.ID)) {
				ctx.pipeline().replace(this, "McTunnelWebSocketHandshake", new WebSocketServerHandshake(metadataConsumer));
				ctx.fireChannelRead(request);
				return;
			}

			boolean rawHttpUpgradeRequest = HttpUpgradeServerHandshake.isUpgradeRequest(request)
					&& !WebSocketHandshakeValidator.hasWebSocketKey(request);
			if (rawHttpUpgradeRequest && TunnelProtocols.isRegistered(HttpUpgradeTunnelProtocol.ID)) {
				ctx.pipeline().replace(this, "McTunnelHttpUpgradeHandshake", new HttpUpgradeServerHandshake(metadataConsumer));
				ctx.fireChannelRead(request);
				return;
			}

			writeDefaultHttpResponse(ctx);
		}

		private static void writeDefaultHttpResponse(ChannelHandlerContext ctx) {
			DefaultFullHttpResponse response = new DefaultFullHttpResponse(
					HttpVersion.HTTP_1_1,
					HttpResponseStatus.OK,
					Unpooled.copiedBuffer("Minecraft Tunnel", CharsetUtil.UTF_8));
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
			response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		}
	}
}
