package com.akinokaede.mctunnel.forge;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.client.ClientEnvironment;
import com.akinokaede.mctunnel.client.MinecraftTunnelClient;
import net.minecraftforge.fml.common.Mod;

@Mod(MinecraftTunnel.MOD_ID)
public final class MinecraftTunnelForgeMod {
	public MinecraftTunnelForgeMod() {
		MinecraftTunnel.init();
		if (ClientEnvironment.isClient()) {
			MinecraftTunnelClient.init();
		}
	}
}
