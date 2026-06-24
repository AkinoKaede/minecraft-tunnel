package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.server.TunnelConnectionAccess;
import com.akinokaede.mctunnel.transport.TunnelProtocols;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public class ServerConnectionListenerMixin {
	@Inject(method = "initChannel", at = @At("RETURN"), require = 1)
	private void mctunnel$initChannel(CallbackInfo ci,
			@Local(ordinal = 0, argsOnly = true) Channel channel,
			@Local(ordinal = 0, argsOnly = false) Connection connection) {
		TunnelProtocols.installServer(
				channel.pipeline(),
				metadata -> ((TunnelConnectionAccess) connection).mctunnel$setTunnelMetadata(metadata));
	}
}
