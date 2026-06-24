package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.client.TunnelConnectionAccess;
import com.akinokaede.mctunnel.client.TunnelServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class ConnectScreenThreadMixin {
	@Unique
	private ServerAddress mctunnel$serverAddress;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void mctunnel$init(CallbackInfo ci,
			@Local(ordinal = 0, argsOnly = true) ServerAddress serverAddress) {
		this.mctunnel$serverAddress = serverAddress;
	}

	@Inject(method = "run", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
	private void mctunnel$beforeConnect(CallbackInfo ci,
			@Local(ordinal = 0, argsOnly = false) Connection connection) {
		((TunnelConnectionAccess) connection).mctunnel$setAddress(TunnelServerAddress.from(mctunnel$serverAddress));
	}
}
