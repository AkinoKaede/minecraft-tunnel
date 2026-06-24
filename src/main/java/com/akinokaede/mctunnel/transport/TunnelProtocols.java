package com.akinokaede.mctunnel.transport;

import io.netty.channel.ChannelPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TunnelProtocols {
	private static final List<TunnelProtocol> PROTOCOLS = new ArrayList<>();

	private TunnelProtocols() {
	}

	public static void register(TunnelProtocol protocol) {
		for (TunnelProtocol registered : PROTOCOLS) {
			if (registered.id().equals(protocol.id())) {
				return;
			}
		}
		PROTOCOLS.add(protocol);
	}

	public static void registerIfEnabled(TunnelProtocol protocol, Predicate<String> enabled) {
		if (enabled.test(protocol.id())) {
			register(protocol);
		}
	}

	public static boolean isRegistered(String protocolId) {
		for (TunnelProtocol protocol : PROTOCOLS) {
			if (protocol.id().equals(protocolId)) {
				return true;
			}
		}
		return false;
	}

	public static int protocolCount() {
		return PROTOCOLS.size();
	}

	public static void installServer(ChannelPipeline pipeline, Consumer<TunnelConnectionMetadata> metadataConsumer) {
		for (TunnelProtocol protocol : PROTOCOLS) {
			protocol.installServer(pipeline, metadataConsumer);
		}
	}
}
