package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.client.ClientTunnelProtocol;
import com.akinokaede.mctunnel.client.TunnelServerAddress;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import io.netty.channel.ChannelPipeline;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class WebSocketClientTunnelProtocol implements ClientTunnelProtocol {
	@Override
	public String id() {
		return WebSocketTunnelProtocol.ID;
	}

	@Override
	public Optional<ServerAddress> parseClientAddress(String rawAddress) {
		try {
			URI uri = new URI(rawAddress);
			String scheme = uri.getScheme();
			String host = uri.getHost();

			if (scheme == null || host == null) {
				return Optional.empty();
			}
			if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
				return Optional.empty();
			}

			IDN.toASCII(host);
			int port = uri.getPort();
			if (port < 0 || port > 65535) {
				port = "wss".equalsIgnoreCase(scheme) ? 443 : 80;
			}

			String path = uri.getPath();
			if (path == null || path.isEmpty()) {
				path = "/";
			}

			String tlsSni = null;
			String httpHost = host;
			String[] userInfo = splitUserInfo(uri.getUserInfo());
			if ("wss".equalsIgnoreCase(scheme)) {
				tlsSni = host;
				if (userInfo.length > 0) {
					tlsSni = userInfo[0].isEmpty() ? host : userInfo[0];
					httpHost = userInfo.length > 1 && !userInfo[1].isEmpty() ? userInfo[1] : tlsSni;
				}
			} else if (userInfo.length > 0 && !userInfo[0].isBlank()) {
				httpHost = userInfo[0];
			}

			URI normalizedUri = new URI(scheme, null, host, port, path, uri.getQuery(), uri.getFragment());
			ServerAddress address = new ServerAddress(host, port);
			TunnelServerAddress.from(address).mctunnel$setEndpoint(
					new ClientTunnelEndpoint(id(), normalizedUri, tlsSni, httpHost));
			return Optional.of(address);
		} catch (IllegalArgumentException | URISyntaxException ignored) {
			return Optional.empty();
		}
	}

	@Override
	public void installClient(ChannelPipeline pipeline, ClientTunnelEndpoint endpoint) {
		WebSocketClientTunnel.install(pipeline, endpoint);
	}

	private static String[] splitUserInfo(String userInfo) {
		return userInfo == null ? new String[0] : userInfo.split(":", -1);
	}
}
