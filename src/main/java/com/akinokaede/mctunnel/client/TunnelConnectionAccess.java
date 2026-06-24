package com.akinokaede.mctunnel.client;

import com.akinokaede.mctunnel.ArgHolder;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;

public interface TunnelConnectionAccess extends com.akinokaede.mctunnel.server.TunnelConnectionAccess {
	ArgHolder<TunnelServerAddress> CONNECT_TO_SERVER_ARG = ArgHolder.nullable();

	TunnelServerAddress mctunnel$getAddress();

	void mctunnel$setAddress(TunnelServerAddress address);

	@Override
	void mctunnel$setTunnelMetadata(TunnelConnectionMetadata metadata);
}
