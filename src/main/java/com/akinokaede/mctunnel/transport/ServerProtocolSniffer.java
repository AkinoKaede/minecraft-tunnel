package com.akinokaede.mctunnel.transport;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.config.TunnelConfig;
import com.akinokaede.mctunnel.transport.grpc.GrpcServerSniffer;
import com.akinokaede.mctunnel.transport.grpc.GrpcTunnelProtocol;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeServerSniffer;
import com.akinokaede.mctunnel.transport.httpupgrade.HttpUpgradeTunnelProtocol;
import com.akinokaede.mctunnel.transport.websocket.WebSocketTunnelProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.function.Consumer;

public final class ServerProtocolSniffer extends ByteToMessageDecoder {
	private static final String SNIFFER = "McTunnelServerProtocolSniffer";

	private final Consumer<TunnelConnectionMetadata> metadataConsumer;

	private ServerProtocolSniffer(Consumer<TunnelConnectionMetadata> metadataConsumer) {
		this.metadataConsumer = metadataConsumer;
	}

	public static void install(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		if (pipeline.get(SNIFFER) == null) {
			pipeline.addAfter("timeout", SNIFFER, new ServerProtocolSniffer(metadataConsumer));
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		if (httpEnabled() && HttpUpgradeServerSniffer.isCandidate(in)) {
			if (HttpUpgradeServerSniffer.isIncompleteCandidate(in)) {
				return;
			}
			if (HttpUpgradeServerSniffer.hasCompleteCandidate(in)) {
				MinecraftTunnel.debug("Incoming HTTP tunnel candidate");
				HttpUpgradeServerSniffer.install(ctx.pipeline(), this, metadataConsumer);
				return;
			}
		}

		if (grpcEnabled() && GrpcServerSniffer.matchesHttp2Preface(in)) {
			if (!GrpcServerSniffer.hasCompleteHttp2Preface(in)) {
				return;
			}
			MinecraftTunnel.debug("Incoming gRPC tunnel candidate");
			GrpcServerSniffer.install(ctx.pipeline(), this, metadataConsumer);
			return;
		}

		if (HttpUpgradeServerSniffer.isIncompleteCandidate(in)
				|| (grpcEnabled() && GrpcServerSniffer.isIncompleteHttp2Preface(in))) {
			return;
		}

		if (TunnelConfig.vanillaEnabled()) {
			MinecraftTunnel.debug("Incoming vanilla TCP connection");
			ctx.pipeline().remove(this);
		} else {
			MinecraftTunnel.debug("Rejecting connection because vanilla TCP is not enabled");
			ctx.close();
		}
	}

	private static boolean httpEnabled() {
		return TunnelProtocols.isRegistered(WebSocketTunnelProtocol.ID)
				|| TunnelProtocols.isRegistered(HttpUpgradeTunnelProtocol.ID);
	}

	private static boolean grpcEnabled() {
		return TunnelProtocols.isRegistered(GrpcTunnelProtocol.ID);
	}
}
