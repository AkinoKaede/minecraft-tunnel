package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.transport.TunnelProtocol;

public final class WebSocketTunnelProtocol implements TunnelProtocol {
	public static final String ID = "websocket";

	@Override
	public String id() {
		return ID;
	}
}
