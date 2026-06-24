package com.akinokaede.mctunnel.transport.httpupgrade;

import com.akinokaede.mctunnel.transport.TunnelProtocol;

public final class HttpUpgradeTunnelProtocol implements TunnelProtocol {
	public static final String ID = "httpupgrade";

	@Override
	public String id() {
		return ID;
	}
}
