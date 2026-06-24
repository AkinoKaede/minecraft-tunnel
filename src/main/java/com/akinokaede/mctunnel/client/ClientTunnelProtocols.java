package com.akinokaede.mctunnel.client;

import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import io.netty.channel.ChannelPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ClientTunnelProtocols {
	private static final List<ClientTunnelProtocol> PROTOCOLS = new ArrayList<>();

	private ClientTunnelProtocols() {
	}

	public static void register(ClientTunnelProtocol protocol) {
		for (ClientTunnelProtocol registered : PROTOCOLS) {
			if (registered.id().equals(protocol.id())) {
				return;
			}
		}
		PROTOCOLS.add(protocol);
	}

	public static Optional<ServerAddress> parseClientAddress(String rawAddress) {
		Optional<ServerAddress> explicit = parseExplicitClientAddress(rawAddress);
		if (explicit.isPresent()) {
			return explicit;
		}
		return DnsUriTunnelResolver.resolve(rawAddress);
	}

	public static Optional<ServerAddress> parseExplicitClientAddress(String rawAddress) {
		for (ClientTunnelProtocol protocol : PROTOCOLS) {
			Optional<ServerAddress> parsed = protocol.parseClientAddress(rawAddress);
			if (parsed.isPresent()) {
				return parsed;
			}
		}
		return Optional.empty();
	}

	public static void installClient(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		for (ClientTunnelProtocol protocol : PROTOCOLS) {
			if (protocol.id().equals(endpoint.protocolId())) {
				protocol.installClient(pipeline, endpoint);
				return;
			}
		}
	}
}
