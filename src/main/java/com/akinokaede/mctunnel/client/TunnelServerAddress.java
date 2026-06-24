package com.akinokaede.mctunnel.client;

import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public interface TunnelServerAddress {
	void mctunnel$setEndpoint(ClientTunnelEndpoint endpoint);

	ClientTunnelEndpoint mctunnel$getEndpoint();

	String mctunnel$getRawHost();

	default boolean mctunnel$isVanilla() {
		return mctunnel$getEndpoint() == null;
	}

	static TunnelServerAddress from(ServerAddress serverAddress) {
		return (TunnelServerAddress) (Object) serverAddress;
	}
}
