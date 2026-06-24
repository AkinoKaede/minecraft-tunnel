package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.TrustedProxyHeaders;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class WebSocketServerHandshake extends ChannelInboundHandlerAdapter {
	private final Consumer<TunnelConnectionMetadata> metadataConsumer;

	public WebSocketServerHandshake(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof HttpRequest request) {
			handleRequest(ctx, request);
			return;
		}

		if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
			return;
		}

		MinecraftTunnel.debug("Unexpected object during WebSocket handshake: " + msg.getClass().getName());
	}

	private void handleRequest(ChannelHandlerContext ctx, HttpRequest request) {
		if (!WebSocketHandshakeValidator.isValidOpeningHandshake(request) || !isConfiguredEndpoint(request.uri())) {
			writeDefaultHttpResponse(ctx);
			return;
		}

		String websocketUrl = "ws://" + request.headers().get(HttpHeaderNames.HOST) + request.uri();
		MinecraftTunnel.debug("Upgrading tunnel connection to " + websocketUrl);
		Map<String, String> metadata = extractMetadata(request);
		java.net.SocketAddress proxiedRemoteAddress = TrustedProxyHeaders.resolve(
				ctx.channel().remoteAddress(),
				name -> request.headers().get(name));
		if (proxiedRemoteAddress != null) {
			metadata.put("proxied.remote_address", proxiedRemoteAddress.toString());
		}
		metadataConsumer.accept(new TunnelConnectionMetadata(
				WebSocketTunnelProtocol.ID,
				metadata,
				request,
				proxiedRemoteAddress));

		ctx.pipeline().replace(this, "McTunnelWebSocketServer", new ServerBridge());

		WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
				websocketUrl,
				null,
				true,
				TunnelConfig.MAX_FRAME_PAYLOAD_LENGTH);
		WebSocketServerHandshaker handshaker = factory.newHandshaker(request);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
			return;
		}

		handshaker.handshake(ctx.channel(), request)
				.addListener(future -> MinecraftTunnel.debug("WebSocket server handshake completed: " + future.isSuccess()));
	}

	private boolean isConfiguredEndpoint(String requestUri) {
		return TunnelConfig.SERVER_ENDPOINT == null || TunnelConfig.SERVER_ENDPOINT.equals(requestUri);
	}

	private Map<String, String> extractMetadata(HttpRequest request) {
		Map<String, String> attributes = new LinkedHashMap<>();
		attributes.put("uri", request.uri());
		for (Map.Entry<String, String> header : request.headers()) {
			attributes.put("header." + header.getKey().toLowerCase(Locale.ROOT), header.getValue());
		}
		return attributes;
	}

	private void writeDefaultHttpResponse(ChannelHandlerContext ctx) {
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,
				HttpResponseStatus.OK,
				Unpooled.copiedBuffer("Minecraft Tunnel", CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private static final class ServerBridge extends WebSocketBinaryBridge {
		private ServerBridge() {
			super("C->S", "S->C");
		}

		@Override
		protected void writeFrame(ChannelHandlerContext ctx, io.netty.handler.codec.http.websocketx.WebSocketFrame frame,
				io.netty.channel.ChannelPromise promise) {
			ctx.write(frame, promise);
		}
	}
}
