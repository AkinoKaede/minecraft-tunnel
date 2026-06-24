package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.client.TunnelServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
	@Shadow
	@Final
	static Logger LOGGER;

	@Inject(method = "connect", at = @At("HEAD"), require = 1)
	private void mctunnel$connect(CallbackInfo ci,
			@Local(ordinal = 0, argsOnly = true) ServerAddress serverAddress) {
		TunnelServerAddress tunnelAddress = TunnelServerAddress.from(serverAddress);
		if (!tunnelAddress.mctunnel$isVanilla()) {
			LOGGER.info("Connecting through Minecraft Tunnel: {}", tunnelAddress.mctunnel$getEndpoint());
		}
	}
}
