package com.akinokaede.mctunnel.api;

import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;

public interface TunnelServerConnection {
	TunnelConnectionMetadata mctunnel$getTunnelMetadata();
}
