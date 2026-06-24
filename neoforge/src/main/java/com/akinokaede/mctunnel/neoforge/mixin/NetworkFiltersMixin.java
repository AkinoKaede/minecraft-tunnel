package com.akinokaede.mctunnel.neoforge.mixin;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.filters.GenericPacketSplitter;
import net.neoforged.neoforge.network.filters.NetworkFilters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkFilters.class, remap = false)
public final class NetworkFiltersMixin {
	@Inject(method = "injectIfNecessary", at = @At("HEAD"), require = 1)
	private static void mctunnel$removeDuplicateFilters(Connection manager, CallbackInfo ci) {
		ChannelPipeline pipeline = manager.channel().pipeline();
		removeIfPresent(pipeline, "neoforge:vanilla_filter");
		removeIfPresent(pipeline, GenericPacketSplitter.CHANNEL_HANDLER_NAME);
	}

	private static void removeIfPresent(ChannelPipeline pipeline, String name) {
		if (pipeline.get(name) != null) {
			pipeline.remove(name);
		}
	}
}
