package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import com.akinokaede.mctunnel.transport.TunnelUserAgent;
import com.akinokaede.mctunnel.config.TunnelConfig;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import java.util.Collections;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

final class WebSocketClientTunnel extends WebSocketBinaryBridge {
	private final WebSocketClientHandshaker handshaker;
	private ChannelPromise handshakeFuture;

	private WebSocketClientTunnel(ClientTunnelEndpoint endpoint) {
		super("S->C", "C->S");
		DefaultHttpHeaders headers = new DefaultHttpHeaders();
		headers.set("Host", endpoint.httpHost());
		headers.set("User-Agent", TunnelUserAgent.value());
		this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
				endpoint.uri(),
				WebSocketVersion.V13,
				null,
				true,
				headers,
				TunnelConfig.MAX_FRAME_PAYLOAD_LENGTH);
	}

	static void install(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		MinecraftTunnel.info("Connecting through tunnel:\n" + endpoint);

		if (endpoint.usesTls()) {
			pipeline.addAfter("timeout", "McTunnelWebSocketSsl", sslHandler(endpoint));
		}

		String base = endpoint.usesTls() ? "McTunnelWebSocketSsl" : "timeout";
		pipeline.addAfter(base, "McTunnelWebSocketHttpClient", new HttpClientCodec());
		pipeline.addAfter("McTunnelWebSocketHttpClient", "McTunnelWebSocketAggregator", new HttpObjectAggregator(8192 * 4));
		pipeline.addAfter("McTunnelWebSocketAggregator", "McTunnelWebSocketCompression", WebSocketClientCompressionHandler.INSTANCE);
		pipeline.addAfter("McTunnelWebSocketCompression", "McTunnelWebSocketClient", new WebSocketClientTunnel(endpoint));
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
		handshakeFuture = ctx.newPromise();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		handshaker.handshake(ctx.channel());
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel channel = ctx.channel();
		if (!handshaker.isHandshakeComplete()) {
			try {
				handshaker.finishHandshake(channel, (FullHttpResponse) msg);
				MinecraftTunnel.debug("WebSocket client handshake completed");
				handshakeFuture.setSuccess();
			} catch (WebSocketHandshakeException e) {
				MinecraftTunnel.debug("WebSocket client handshake failed");
				handshakeFuture.setFailure(e);
			}
			return;
		}

		if (msg instanceof FullHttpResponse response) {
			throw new IllegalStateException("Unexpected HTTP response from WebSocket tunnel: "
					+ response.status() + " " + response.content().toString(CharsetUtil.UTF_8));
		}

		super.channelRead(ctx, msg);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (handshakeFuture != null && !handshakeFuture.isDone()) {
			handshakeFuture.setFailure(cause);
		}
		cause.printStackTrace();
		ctx.close();
	}

	@Override
	protected void writeFrame(ChannelHandlerContext ctx, WebSocketFrame frame, ChannelPromise promise) {
		if (handshakeFuture != null && handshakeFuture.isSuccess()) {
			ctx.write(frame, promise);
			return;
		}

		ChannelPromise queuedPromise = promise;
		handshakeFuture.addListener(future -> {
			if (future.isSuccess()) {
				ctx.write(frame, queuedPromise);
			} else {
				queuedPromise.setFailure(future.cause());
			}
		});
	}

	private static SslHandler sslHandler(ClientTunnelEndpoint endpoint) {
		try {
			SslContext sslContext = SslContextBuilder.forClient().build();
			String peerHost = endpoint.tlsSni() == null ? endpoint.host() : endpoint.tlsSni();
			SSLEngine engine = sslContext.newEngine(ByteBufAllocator.DEFAULT, peerHost, endpoint.port());

			SSLParameters parameters = engine.getSSLParameters();
			parameters.setServerNames(Collections.singletonList(new SNIHostName(peerHost)));
			parameters.setEndpointIdentificationAlgorithm("HTTPS");
			engine.setSSLParameters(parameters);

			return new SslHandler(engine);
		} catch (SSLException e) {
			throw new IllegalStateException("Unable to create TLS handler for WebSocket tunnel", e);
		}
	}
}
