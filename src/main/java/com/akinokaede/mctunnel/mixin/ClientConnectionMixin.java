package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.client.TunnelConnectionAccess;
import com.akinokaede.mctunnel.client.TunnelServerAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(Connection.class)
public abstract class ClientConnectionMixin implements TunnelConnectionAccess {
	@Unique
	private TunnelServerAddress mctunnel$address;

	@Inject(method = "connectToServer", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
	private static void mctunnel$beforeConnectToServer(CallbackInfoReturnable<Connection> cir,
			@Local(ordinal = 0, argsOnly = false) Connection connection) {
		((TunnelConnectionAccess) connection).mctunnel$setAddress(TunnelConnectionAccess.CONNECT_TO_SERVER_ARG.pop());
	}

	@Override
	public TunnelServerAddress mctunnel$getAddress() {
		return mctunnel$address;
	}

	@Override
	public void mctunnel$setAddress(TunnelServerAddress address) {
		this.mctunnel$address = address;
	}
}
