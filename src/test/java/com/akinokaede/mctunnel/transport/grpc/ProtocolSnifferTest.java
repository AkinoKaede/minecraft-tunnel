package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import com.akinokaede.mctunnel.transport.TunnelProtocols;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeTunnelProtocol;
import com.akinokaede.mctunnel.transport.websocket.WebSocketTunnelProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Stream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolSnifferTest {
	private static final Consumer<TunnelConnectionMetadata> IGNORE_METADATA = metadata -> {
	};
	private static final String HTTP2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";

	@AfterEach
	void clearProtocolProperty() {
		System.clearProperty("mctunnel.protocol");
		System.clearProperty("mctunnel.protocols");
	}

	@Test
	void grpcPrefaceMatcherWaitsForCompletePrefaceAndRestoresReaderIndex() {
		ByteBuf partialPreface = copiedBuffer("skipPRI * HTTP/2.0\r\n\r\nSM");
		partialPreface.readerIndex(4);
		int readerIndex = partialPreface.readerIndex();

		assertTrue(GrpcServerSniffer.matchesHttp2Preface(partialPreface));
		assertEquals(readerIndex, partialPreface.readerIndex());
		assertEquals(partialPreface.readableBytes(), partialPreface.readableBytes());
		assertTrue(!GrpcServerSniffer.hasCompleteHttp2Preface(partialPreface));
		assertEquals(readerIndex, partialPreface.readerIndex());

		ByteBuf fullPreface = copiedBuffer("skip" + HTTP2_PREFACE);
		fullPreface.readerIndex(4);
		assertTrue(GrpcServerSniffer.hasCompleteHttp2Preface(fullPreface));
		assertEquals(4, fullPreface.readerIndex());
	}

	@Test
	void serverSnifferInstallsHttp2OnlyAfterCompletePreface() {
		TunnelProtocols.register(new GrpcTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("PRI * HTTP/2.0\r\n\r\nSM"));
		assertNull(channel.pipeline().get(Http2FrameCodec.class));

		channel.writeInbound(copiedBuffer("\r\n\r\n"));
		assertNotNull(channel.pipeline().get(Http2FrameCodec.class));
		channel.finishAndReleaseAll();
	}

	@Test
	void serverSnifferRoutesPriPrefixToGrpc() {
		enableAllProtocols();
		TunnelProtocols.register(new GrpcTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("PRI"));
		assertNull(channel.pipeline().get(Http2FrameCodec.class));
		assertNotNull(channel.pipeline().get("McTunnelServerProtocolSniffer"));
		channel.finishAndReleaseAll();
	}

	@Test
	void grpcRequestWithoutPathReturnsHttp2NotFound() {
		EmbeddedChannel channel = new EmbeddedChannel(new GrpcServerTunnel(IGNORE_METADATA));
		Http2HeadersFrame request = new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
				.method("POST")
				.set("content-type", "application/grpc")).stream(new TestStream(1));

		channel.writeInbound(request);

		Http2HeadersFrame response = channel.readOutbound();
		assertEquals("404", response.headers().status().toString());
		assertTrue(response.isEndStream());
		channel.finishAndReleaseAll();
	}

	@Test
	void tunnelProtocolsInstallOneServerProtocolSniffer() {
		TunnelProtocols.register(new GrpcTunnelProtocol());
		TunnelProtocols.register(new WebSocketTunnelProtocol());
		TunnelProtocols.register(new HttpUpgradeTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();

		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		assertNotNull(channel.pipeline().get("McTunnelServerProtocolSniffer"));
		channel.finishAndReleaseAll();
	}

	@Test
	void httpSnifferWaitsForCompleteHttpGetRequestBeforeInstallingCodec() {
		enableAllProtocols();
		TunnelProtocols.register(new WebSocketTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("GET /MinecraftTunnel HTTP/1.1\r\nHost: example.test\r\n"));
		assertNull(channel.pipeline().get(HttpServerCodec.class));
		assertNotNull(channel.pipeline().get("McTunnelServerProtocolSniffer"));

		channel.finishAndReleaseAll();
	}

	@Test
	void httpSnifferRoutesCompleteHttpGetRequest() {
		enableAllProtocols();
		TunnelProtocols.register(new WebSocketTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("GET /MinecraftTunnel HTTP/1.1\r\nHost: example.test\r\n\r\n"));
		ByteBuf response = channel.readOutbound();
		assertTrue(response.toString(StandardCharsets.US_ASCII).startsWith("HTTP/1.1 200 OK"));
		response.release();
		channel.finishAndReleaseAll();
	}

	@Test
	void httpUpgradeWrongPathReturnsNotFound() {
		enableAllProtocols();
		TunnelProtocols.register(new HttpUpgradeTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("""
				GET /wrong HTTP/1.1\r
				Host: example.test\r
				Connection: Upgrade\r
				Upgrade: websocket\r
				\r
				"""));
		assertStatusLine(channel, "HTTP/1.1 404 Not Found");
		channel.finishAndReleaseAll();
	}

	@Test
	void websocketWrongPathReturnsNotFound() {
		enableAllProtocols();
		TunnelProtocols.register(new WebSocketTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("""
				GET /wrong HTTP/1.1\r
				Host: example.test\r
				Connection: Upgrade\r
				Upgrade: websocket\r
				Sec-WebSocket-Version: 13\r
				Sec-WebSocket-Key: %s\r
				\r
				""".formatted(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.US_ASCII)))));
		assertStatusLine(channel, "HTTP/1.1 404 Not Found");
		channel.finishAndReleaseAll();
	}

	@Test
	void httpSnifferDoesNotTreatInvalidGetPrefixAsHttp() {
		enableAllProtocols();
		TunnelProtocols.register(new GrpcTunnelProtocol());
		TunnelProtocols.register(new WebSocketTunnelProtocol());
		EmbeddedChannel channel = channelWithTimeout();
		TunnelProtocols.installServer(channel.pipeline(), IGNORE_METADATA);

		channel.writeInbound(copiedBuffer("GET minecraft bytes\r\n\r\n"));
		assertNull(channel.pipeline().get(HttpServerCodec.class));
		assertNull(channel.pipeline().get("McTunnelServerProtocolSniffer"));
		channel.finishAndReleaseAll();
	}

	private static EmbeddedChannel channelWithTimeout() {
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast("timeout", new ChannelInboundHandlerAdapter());
		return channel;
	}

	private static ByteBuf copiedBuffer(String value) {
		return Unpooled.copiedBuffer(value, StandardCharsets.US_ASCII);
	}

	private static void assertStatusLine(EmbeddedChannel channel, String expected) {
		ByteBuf response = channel.readOutbound();
		assertTrue(response.toString(StandardCharsets.US_ASCII).startsWith(expected));
		response.release();
	}

	private static void enableAllProtocols() {
		System.setProperty("mctunnel.protocol", "websocket,httpupgrade,grpc,vanilla");
	}

	private record TestStream(int id) implements Http2FrameStream {
		@Override
		public Http2Stream.State state() {
			return Http2Stream.State.OPEN;
		}
	}
}
