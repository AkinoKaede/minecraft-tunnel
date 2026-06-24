package com.akinokaede.mctunnel.neoforge;

import com.akinokaede.mctunnel.MinecraftTunnel;
import com.akinokaede.mctunnel.client.ClientEnvironment;
import com.akinokaede.mctunnel.client.MinecraftTunnelClient;
import net.neoforged.fml.common.Mod;

@Mod(MinecraftTunnel.MOD_ID)
public final class MinecraftTunnelNeoForgeMod {
	public MinecraftTunnelNeoForgeMod() {
		MinecraftTunnel.init();
		if (ClientEnvironment.isClient()) {
			MinecraftTunnelClient.init();
		}
	}
}
