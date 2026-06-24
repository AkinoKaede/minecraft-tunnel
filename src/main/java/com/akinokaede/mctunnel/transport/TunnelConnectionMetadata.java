package com.akinokaede.mctunnel.transport;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class TunnelConnectionMetadata {
	private final String protocolId;
	private final Map<String, String> attributes;
	private final Object nativeRequest;
	private final SocketAddress proxiedRemoteAddress;

	public TunnelConnectionMetadata(String protocolId, Map<String, String> attributes, Object nativeRequest) {
		this(protocolId, attributes, nativeRequest, null);
	}

	public TunnelConnectionMetadata(String protocolId, Map<String, String> attributes, Object nativeRequest,
			SocketAddress proxiedRemoteAddress) {
		this.protocolId = Objects.requireNonNull(protocolId, "protocolId");
		this.attributes = Collections.unmodifiableMap(Map.copyOf(attributes));
		this.nativeRequest = nativeRequest;
		this.proxiedRemoteAddress = proxiedRemoteAddress;
	}

	public String protocolId() {
		return protocolId;
	}

	public Map<String, String> attributes() {
		return attributes;
	}

	public Object nativeRequest() {
		return nativeRequest;
	}

	public SocketAddress proxiedRemoteAddress() {
		return proxiedRemoteAddress;
	}
}
