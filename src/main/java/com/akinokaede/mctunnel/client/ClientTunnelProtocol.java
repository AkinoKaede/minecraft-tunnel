package com.akinokaede.mctunnel.client;

import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import io.netty.channel.ChannelPipeline;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public interface ClientTunnelProtocol {
	String id();

	Optional<ServerAddress> parseClientAddress(String rawAddress);

	void installClient(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint);
}
