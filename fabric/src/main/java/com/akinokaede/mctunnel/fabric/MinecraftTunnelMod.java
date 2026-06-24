package com.akinokaede.mctunnel.fabric;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.client.MinecraftTunnelClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public final class MinecraftTunnelMod implements ModInitializer, ClientModInitializer {
	@Override
	public void onInitialize() {
		MinecraftTunnel.init();
	}

	@Override
	public void onInitializeClient() {
		MinecraftTunnel.init();
		MinecraftTunnelClient.init();
	}
}
