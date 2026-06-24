package com.akinokaede.mctunnel.server;

import com.akinokaede.mctunnel.api.TunnelServerConnection;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;

public interface TunnelConnectionAccess extends TunnelServerConnection {
	void mctunnel$setTunnelMetadata(TunnelConnectionMetadata metadata);
}
