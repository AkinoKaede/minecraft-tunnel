package com.akinokaede.mctunnel.transport.tls;

import com.akinokaede.mctunnel.client.ClientTunnelProtocol;
import com.akinokaede.mctunnel.client.TunnelServerAddress;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import io.netty.channel.ChannelPipeline;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class TlsClientTunnelProtocol implements ClientTunnelProtocol {
	public static final String ID = "tls";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Optional<ServerAddress> parseClientAddress(String rawAddress) {
		try {
			URI uri = new URI(rawAddress);
			String scheme = uri.getScheme();
			String host = uri.getHost();

			if (scheme == null || host == null || !"tls".equalsIgnoreCase(scheme)) {
				return Optional.empty();
			}

			IDN.toASCII(host);
			int port = uri.getPort();
			if (port < 0 || port > 65535) {
				port = 25565;
			}

			String tlsSni = host;
			String userInfo = uri.getUserInfo();
			if (userInfo != null && !userInfo.isBlank()) {
				tlsSni = userInfo;
			}

			URI normalizedUri = new URI(scheme, null, host, port, null, null, null);
			ServerAddress address = new ServerAddress(host, port);
			TunnelServerAddress.from(address).mctunnel$setEndpoint(
					new ClientTunnelEndpoint(id(), normalizedUri, tlsSni, host));
			return Optional.of(address);
		} catch (IllegalArgumentException | URISyntaxException ignored) {
			return Optional.empty();
		}
	}

	@Override
	public void installClient(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		TlsClientTunnel.install(pipeline, endpoint);
	}
}
