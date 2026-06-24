package com.akinokaede.mctunnel.transport;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import java.util.Collections;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

public final class ClientTls {
	private ClientTls() {
	}

	public static SslHandler sslHandler(ClientTunnelEndpoint endpoint) {
		try {
			SslContext sslContext = SslContextBuilder.forClient().build();
			String peerHost = endpoint.tlsSni() == null ? endpoint.host() : endpoint.tlsSni();
			SSLEngine engine = sslContext.newEngine(ByteBufAllocator.DEFAULT, peerHost, endpoint.port());

			SSLParameters parameters = engine.getSSLParameters();
			parameters.setServerNames(Collections.singletonList(new SNIHostName(peerHost)));
			parameters.setEndpointIdentificationAlgorithm("HTTPS");
			engine.setSSLParameters(parameters);

			return new SslHandler(engine);
		} catch (SSLException e) {
			throw new IllegalStateException("Unable to create TLS handler for tunnel", e);
		}
	}
}
