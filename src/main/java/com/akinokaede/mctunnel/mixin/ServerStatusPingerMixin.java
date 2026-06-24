package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.client.TunnelConnectionAccess;
import com.akinokaede.mctunnel.client.TunnelServerAddress;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
	@Inject(method = "pingServer", require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
	private void mctunnel$beforeConnect(CallbackInfo ci,
			@Local(ordinal = 0, argsOnly = false) ServerAddress serverAddress) {
		TunnelConnectionAccess.CONNECT_TO_SERVER_ARG.push(TunnelServerAddress.from(serverAddress));
	}
}
