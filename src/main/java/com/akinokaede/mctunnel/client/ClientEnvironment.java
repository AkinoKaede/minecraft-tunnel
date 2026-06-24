package com.akinokaede.mctunnel.client;

public final class ClientEnvironment {
	private ClientEnvironment() {
	}

	public static boolean isClient() {
		try {
			Class.forName("net.minecraft.client.Minecraft", false, ClientEnvironment.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}
}
