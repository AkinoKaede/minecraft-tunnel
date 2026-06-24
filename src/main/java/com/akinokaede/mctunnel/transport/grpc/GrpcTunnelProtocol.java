package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.transport.TunnelProtocol;

public final class GrpcTunnelProtocol implements TunnelProtocol {
	public static final String ID = "grpc";

	@Override
	public String id() {
		return ID;
	}
}
