package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.client.TunnelConnectionAccess;
import com.akinokaede.mctunnel.client.TunnelServerAddress;
import com.akinokaede.mctunnel.client.ClientTunnelProtocols;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(targets = "net.minecraft.network.Connection$1")
public class ConnectionChannelInitializerMixin {
	@Unique
	private Connection mctunnel$connection;

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void mctunnel$init(CallbackInfo ci, @Local(ordinal = 0, argsOnly = true) Connection connection) {
		this.mctunnel$connection = connection;
	}

	@Inject(method = "initChannel", at = @At("RETURN"), require = 1)
	private void mctunnel$initChannel(Channel channel, CallbackInfo ci) {
		TunnelServerAddress address = ((TunnelConnectionAccess) mctunnel$connection).mctunnel$getAddress();
		if (address != null && !address.mctunnel$isVanilla()) {
			ClientTunnelProtocols.installClient(channel.pipeline(), address.mctunnel$getEndpoint());
		}
	}
}
