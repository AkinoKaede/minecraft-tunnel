package com.akinokaede.mctunnel.mixin.ignite;

import com.akinokaede.mctunnel.server.TunnelConnectionAccess;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import java.net.SocketAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Connection.class)
public class IgniteConnectionMixin implements TunnelConnectionAccess {
	@Shadow
	public SocketAddress address;

	@Unique
	private TunnelConnectionMetadata mctunnel$metadata;

	@Override
	public TunnelConnectionMetadata mctunnel$getTunnelMetadata() {
		return mctunnel$metadata;
	}

	@Override
	public void mctunnel$setTunnelMetadata(TunnelConnectionMetadata metadata) {
		this.mctunnel$metadata = metadata;
		if (metadata != null && metadata.proxiedRemoteAddress() != null) {
			this.address = metadata.proxiedRemoteAddress();
		}
	}
}
