package com.akinokaede.mctunnel.mixin.ignite;

import com.akinokaede.mctunnel.server.TunnelConnectionAccess;
import com.akinokaede.mctunnel.transport.TunnelConnectionMetadata;
import io.netty.channel.Channel;
import java.net.SocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class IgniteConnectionMixin implements TunnelConnectionAccess {
	@Shadow
	public SocketAddress address;

	@Shadow
	private Channel channel;

	@Shadow
	private DisconnectionDetails disconnectionDetails;

	@Shadow
	public boolean isConnected() {
		throw new AssertionError();
	}

	@Unique
	private TunnelConnectionMetadata mctunnel$metadata;

	@Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"), cancellable = true)
	private void mctunnel$disconnectFromEventLoop(DisconnectionDetails details, CallbackInfo ci) {
		if (channel != null && isConnected() && channel.eventLoop().inEventLoop()) {
			this.disconnectionDetails = details;
			channel.close();
			ci.cancel();
		}
	}

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
