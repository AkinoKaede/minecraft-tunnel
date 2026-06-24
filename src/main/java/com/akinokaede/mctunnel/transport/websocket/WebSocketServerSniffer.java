package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

final class WebSocketServerSniffer extends ByteToMessageDecoder {
	private final Consumer<TunnelConnectionMetadata> metadataConsumer;

	WebSocketServerSniffer(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
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
		if ("GET".equalsIgnoreCase(methodPrefix)) {
			MinecraftTunnel.debug("Incoming WebSocket tunnel candidate");
			ctx.pipeline().replace(this, "McTunnelWebSocketHttpServer", new HttpServerCodec());
			ctx.pipeline().addAfter("McTunnelWebSocketHttpServer", "McTunnelWebSocketHandshake",
					new WebSocketServerHandshake(metadataConsumer));
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
}
