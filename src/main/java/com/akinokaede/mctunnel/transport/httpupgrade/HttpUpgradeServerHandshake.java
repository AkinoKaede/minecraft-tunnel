package com.akinokaede.mctunnel.transport.httpupgrade;

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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class HttpUpgradeServerHandshake extends ChannelInboundHandlerAdapter {
	private final Consumer<TunnelConnectionMetadata> metadataConsumer;

	HttpUpgradeServerHandshake(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof HttpRequest request) {
			handleRequest(ctx, request);
			return;
		}

		if (msg != LastHttpContent.EMPTY_LAST_CONTENT) {
			MinecraftTunnel.debug("Unexpected object during HTTPUpgrade handshake: " + msg.getClass().getName());
		}
	}

	private void handleRequest(ChannelHandlerContext ctx, HttpRequest request) {
		if (!isUpgradeRequest(request) || !isConfiguredEndpoint(request.uri())) {
			writeDefaultHttpResponse(ctx);
			return;
		}

		MinecraftTunnel.debug("Upgrading tunnel connection to raw HTTPUpgrade stream: " + request.uri());
		Map<String, String> metadata = extractMetadata(request);
		java.net.SocketAddress proxiedRemoteAddress = TrustedProxyHeaders.resolve(
				ctx.channel().remoteAddress(),
				name -> request.headers().get(name));
		if (proxiedRemoteAddress != null) {
			metadata.put("proxied.remote_address", proxiedRemoteAddress.toString());
		}
		metadataConsumer.accept(new TunnelConnectionMetadata(
				HttpUpgradeTunnelProtocol.ID,
				metadata,
				request,
				proxiedRemoteAddress));

		DefaultFullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,
				HttpResponseStatus.SWITCHING_PROTOCOLS,
				Unpooled.EMPTY_BUFFER);
		response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
		response.headers().set(HttpHeaderNames.UPGRADE, "websocket");

		ctx.writeAndFlush(response).addListener(future -> {
			if (!future.isSuccess()) {
				ctx.close();
				return;
			}

			removeIfPresent(ctx, "McTunnelHttpUpgradeHandshake");
			removeIfPresent(ctx, "McTunnelHttpAggregator");
			removeIfPresent(ctx, "McTunnelHttpServer");
			MinecraftTunnel.debug("HTTPUpgrade raw stream is active");
		});
	}

	private boolean isConfiguredEndpoint(String requestUri) {
		return TunnelConfig.SERVER_ENDPOINT == null || TunnelConfig.SERVER_ENDPOINT.equals(requestUri);
	}

	static boolean isUpgradeRequest(HttpRequest request) {
		HttpHeaders headers = request.headers();
		String connection = headers.get(HttpHeaderNames.CONNECTION, "");
		String upgrade = headers.get(HttpHeaderNames.UPGRADE, "");
		return connection.toLowerCase(Locale.ROOT).contains("upgrade")
				&& "websocket".equalsIgnoreCase(upgrade);
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

	private static void removeIfPresent(ChannelHandlerContext ctx, String name) {
		if (ctx.pipeline().get(name) != null) {
			ctx.pipeline().remove(name);
		}
	}
}
