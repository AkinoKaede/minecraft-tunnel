package com.akinokaede.mctunnel.transport;

import java.net.URI;
import java.util.Objects;

public final class ClientTunnelEndpoint {
	private final String protocolId;
	private final URI uri;
	private final String tlsSni;
	private final String httpHost;

	public ClientTunnelEndpoint(String protocolId, URI uri, String tlsSni, String httpHost) {
		this.protocolId = Objects.requireNonNull(protocolId, "protocolId");
		this.uri = Objects.requireNonNull(uri, "uri");
		this.tlsSni = tlsSni;
		this.httpHost = Objects.requireNonNull(httpHost, "httpHost");
	}

	public String protocolId() {
		return protocolId;
	}

	public URI uri() {
		return uri;
	}

	public String host() {
		return uri.getHost();
	}

	public int port() {
		return uri.getPort();
	}

	public String tlsSni() {
		return tlsSni;
	}

	public String httpHost() {
		return httpHost;
	}

	public boolean usesTls() {
		return "tls".equalsIgnoreCase(uri.getScheme())
				|| "wss".equalsIgnoreCase(uri.getScheme())
				|| "httpupgrades".equalsIgnoreCase(uri.getScheme())
				|| "grpc".equalsIgnoreCase(uri.getScheme());
	}

	public boolean semanticallyEquals(ClientTunnelEndpoint other) {
		return other != null
				&& Objects.equals(protocolId, other.protocolId)
				&& Objects.equals(uri, other.uri)
				&& Objects.equals(tlsSni, other.tlsSni)
				&& Objects.equals(httpHost, other.httpHost);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(uri.toString())
				.append("\nProtocol: ").append(protocolId)
				.append("\nHTTP Host: ").append(httpHost);
		if (tlsSni != null) {
			builder.append("\nTLS SNI: ").append(tlsSni);
		}
		return builder.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ClientTunnelEndpoint other)) {
			return false;
		}
		return semanticallyEquals(other);
	}

	@Override
	public int hashCode() {
		return Objects.hash(protocolId, uri, tlsSni, httpHost);
	}
}
