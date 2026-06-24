package com.akinokaede.mctunnel.transport.httpupgrade;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.transport.ClientTls;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import com.akinokaede.mctunnel.transport.TunnelUserAgent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;

final class HttpUpgradeClientTunnel extends io.netty.channel.ChannelDuplexHandler {
	private final ClientTunnelEndpoint endpoint;
	private final Queue<PendingWrite> pendingWrites = new ArrayDeque<>();
	private boolean upgraded;

	private HttpUpgradeClientTunnel(ClientTunnelEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	static void install(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		MinecraftTunnel.info("Connecting through tunnel:\n" + endpoint);

		if (endpoint.usesTls()) {
			pipeline.addAfter("timeout", "McTunnelHttpUpgradeSsl", ClientTls.sslHandler(endpoint));
		}

		String base = endpoint.usesTls() ? "McTunnelHttpUpgradeSsl" : "timeout";
		pipeline.addAfter(base, "McTunnelHttpUpgradeClientCodec", new HttpClientCodec());
		pipeline.addAfter("McTunnelHttpUpgradeClientCodec", "McTunnelHttpUpgradeClient", new HttpUpgradeClientTunnel(endpoint));
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		DefaultFullHttpRequest request = new DefaultFullHttpRequest(
				HttpVersion.HTTP_1_1,
				HttpMethod.GET,
				rawPathAndQuery(endpoint));
		request.headers().set(HttpHeaderNames.HOST, endpoint.httpHost());
		request.headers().set(HttpHeaderNames.USER_AGENT, TunnelUserAgent.value());
		request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
		request.headers().set(HttpHeaderNames.UPGRADE, "websocket");
		MinecraftTunnel.info("Starting HTTPUpgrade handshake");
		ctx.writeAndFlush(request);
		super.channelActive(ctx);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (upgraded) {
			ctx.write(msg, promise);
			return;
		}

		if (!(msg instanceof ByteBuf)) {
			ctx.write(msg, promise);
			return;
		}

		pendingWrites.add(new PendingWrite(msg, promise));
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (upgraded) {
			ctx.fireChannelRead(msg);
			return;
		}

		if (msg instanceof HttpContent content) {
			ReferenceCountUtil.release(content);
			return;
		}

		if (!(msg instanceof HttpResponse response)) {
			throw new IllegalStateException("Unexpected response during HTTPUpgrade handshake: "
					+ msg.getClass().getName());
		}

		if (response.status().code() != HttpResponseStatus.SWITCHING_PROTOCOLS.code()
				|| !"websocket".equalsIgnoreCase(response.headers().get(HttpHeaderNames.UPGRADE, ""))) {
			throw new IllegalStateException("HTTPUpgrade handshake failed: " + response.status());
		}

		upgraded = true;
		MinecraftTunnel.info("HTTPUpgrade client handshake completed; flushing queued Minecraft data");
		removeIfPresent(ctx, "McTunnelHttpUpgradeClientCodec");

		while (!pendingWrites.isEmpty()) {
			PendingWrite pending = pendingWrites.remove();
			MinecraftTunnel.debug("Flushing queued HTTPUpgrade write");
			ctx.write(pending.msg(), pending.promise());
		}
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		while (!pendingWrites.isEmpty()) {
			pendingWrites.remove().promise().setFailure(cause);
		}
		cause.printStackTrace();
		ctx.close();
	}

	private static void removeIfPresent(ChannelHandlerContext ctx, String name) {
		if (ctx.pipeline().get(name) != null) {
			ctx.pipeline().remove(name);
		}
	}

	private static String rawPathAndQuery(ClientTunnelEndpoint endpoint) {
		String path = endpoint.uri().getRawPath();
		if (path == null || path.isEmpty()) {
			path = "/";
		}

		String query = endpoint.uri().getRawQuery();
		return query == null || query.isEmpty() ? path : path + "?" + query;
	}

	private record PendingWrite(Object msg, ChannelPromise promise) {
	}
}
