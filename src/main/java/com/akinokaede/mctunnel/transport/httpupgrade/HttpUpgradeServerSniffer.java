package com.akinokaede.mctunnel.transport.httpupgrade;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.transport.HttpTunnelResponses;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocols;
import com.akinokaede.mctunnel.transport.websocket.WebSocketServerHandshake;
import com.akinokaede.mctunnel.transport.websocket.WebSocketHandshakeValidator;
import com.akinokaede.mctunnel.transport.websocket.WebSocketTunnelProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class HttpUpgradeServerSniffer {
	private static final String CODEC = "McTunnelHttpServer";
	private static final String ROUTER = "McTunnelHttpUpgradeRouter";
	private static final byte[] HTTP_GET_PREFIX = "GET ".getBytes(StandardCharsets.US_ASCII);

	private HttpUpgradeServerSniffer() {
	}

	public static void install(ChannelPipeline pipeline, ChannelHandler oldHandler, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		if (pipeline.get(CODEC) == null && pipeline.get(ROUTER) == null) {
			pipeline.replace(oldHandler, CODEC, new HttpServerCodec());
			pipeline.addAfter(CODEC, "McTunnelHttpAggregator", new HttpObjectAggregator(8192 * 4));
			pipeline.addAfter("McTunnelHttpAggregator", ROUTER, new Router(metadataConsumer));
		}
	}

	public static boolean isCandidate(ByteBuf in) {
		return prefixMatches(in, HTTP_GET_PREFIX);
	}

	public static boolean isIncompleteCandidate(ByteBuf in) {
		return isIncompletePrefix(in, HTTP_GET_PREFIX) || (isCandidate(in) && findHeaderEnd(in) < 0);
	}

	public static boolean hasCompleteCandidate(ByteBuf in) {
		int headerEnd = findHeaderEnd(in);
		return isCandidate(in) && headerEnd >= 0 && isHttpGetRequestLine(in, headerEnd);
	}

	private static boolean prefixMatches(ByteBuf in, byte[] prefix) {
		int readableBytes = in.readableBytes();
		if (readableBytes == 0) {
			return false;
		}

		in.markReaderIndex();
		int bytesToCompare = Math.min(readableBytes, prefix.length);
		boolean matches = true;
		for (int i = 0; i < bytesToCompare; i++) {
			byte actual = in.readByte();
			if (toUpperAscii(actual) != prefix[i]) {
				matches = false;
				break;
			}
		}
		in.resetReaderIndex();
		return matches;
	}

	private static boolean isIncompletePrefix(ByteBuf in, byte[] prefix) {
		return in.readableBytes() < prefix.length && prefixMatches(in, prefix);
	}

	private static int findHeaderEnd(ByteBuf in) {
		int readerIndex = in.readerIndex();
		int writerIndex = in.writerIndex();
		for (int i = readerIndex; i <= writerIndex - 4; i++) {
			if (in.getByte(i) == '\r'
					&& in.getByte(i + 1) == '\n'
					&& in.getByte(i + 2) == '\r'
					&& in.getByte(i + 3) == '\n') {
				return i;
			}
		}
		return -1;
	}

	private static boolean isHttpGetRequestLine(ByteBuf in, int headerEnd) {
		int readerIndex = in.readerIndex();
		int lineEnd = -1;
		for (int i = readerIndex; i < headerEnd - 1; i++) {
			if (in.getByte(i) == '\r' && in.getByte(i + 1) == '\n') {
				lineEnd = i;
				break;
			}
		}
		if (lineEnd < 0) {
			return false;
		}

		String requestLine = in.toString(readerIndex, lineEnd - readerIndex, StandardCharsets.US_ASCII);
		return requestLine.regionMatches(true, 0, "GET ", 0, HTTP_GET_PREFIX.length)
				&& (requestLine.endsWith(" HTTP/1.1") || requestLine.endsWith(" HTTP/1.0"));
	}

	private static byte toUpperAscii(byte value) {
		if (value >= 'a' && value <= 'z') {
			return (byte) (value - ('a' - 'A'));
		}
		return value;
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
			HttpTunnelResponses.writeDefaultAndClose(ctx);
		}
	}
}
