package com.akinokaede.mctunnel.transport;

import com.akinokaede.mctunnel.config.TunnelConfig;
import io.netty.util.Version;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import net.minecraft.SharedConstants;

public final class TunnelUserAgent {
	private static final String VERSION_RESOURCE = "/mctunnel-version.properties";
	private static final String UNKNOWN_VERSION = "unknown";
	private static final String VALUE = create();

	private TunnelUserAgent() {
	}

	public static String value() {
		return VALUE;
	}

	private static String create() {
		if (validHeaderValue(TunnelConfig.USER_AGENT)) {
			return TunnelConfig.USER_AGENT;
		}

			Properties versions = loadVersions();
			StringBuilder userAgent = new StringBuilder()
					.append("MinecraftTunnel/")
					.append(version(versions, "mod_version"))
					.append(" Minecraft/")
					.append(minecraftVersion());
		String loaderVersion = loaderVersion();
		if (loaderVersion != null && !loaderVersion.isBlank()) {
			userAgent.append(" ").append(loaderVersion);
		}
		String nettyVersion = nettyVersion();
		if (nettyVersion != null && !nettyVersion.isBlank()) {
			userAgent.append(" Netty/").append(nettyVersion);
		}
		return userAgent.toString();
	}

	private static Properties loadVersions() {
		Properties versions = new Properties();
		try (InputStream input = TunnelUserAgent.class.getResourceAsStream(VERSION_RESOURCE)) {
			if (input != null) {
				versions.load(input);
			}
		} catch (IOException ignored) {
		}
		return versions;
	}

	private static String version(Properties versions, String key) {
		String value = versions.getProperty(key);
		if (value == null || value.isBlank() || value.startsWith("${")) {
			return UNKNOWN_VERSION;
		}
		return value;
	}

	private static String minecraftVersion() {
		try {
			SharedConstants.tryDetectVersion();
			String version = SharedConstants.getCurrentVersion().name();
			if (version != null && !version.isBlank()) {
				return version;
			}
		} catch (RuntimeException ignored) {
		}
		return UNKNOWN_VERSION;
	}

	private static String loaderVersion() {
		String version = fabricLoaderVersion();
		if (version != null) {
			return version;
		}
		version = neoForgeVersion();
		if (version != null) {
			return version;
		}
		return forgeVersion();
	}

	private static String fabricLoaderVersion() {
		try {
			Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false,
					TunnelUserAgent.class.getClassLoader());
			Object loader = loaderClass.getMethod("getInstance").invoke(null);
			Object container = optionalValue(loaderClass.getMethod("getModContainer", String.class)
					.invoke(loader, "fabricloader"));
			if (container == null) {
				return null;
			}
			Class<?> containerClass = Class.forName("net.fabricmc.loader.api.ModContainer", false,
					TunnelUserAgent.class.getClassLoader());
			Class<?> metadataClass = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata", false,
					TunnelUserAgent.class.getClassLoader());
			Object metadata = containerClass.getMethod("getMetadata").invoke(container);
			Object version = metadataClass.getMethod("getVersion").invoke(metadata);
			return loaderValue("Fabric", version == null ? null : version.toString());
		} catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException
				 | InvocationTargetException | LinkageError | RuntimeException ignored) {
			return null;
		}
	}

	private static String neoForgeVersion() {
		try {
			Class<?> versionClass = Class.forName("net.neoforged.neoforge.common.NeoForgeVersion", false,
					TunnelUserAgent.class.getClassLoader());
			Object version = versionClass.getMethod("getVersion").invoke(null);
			return loaderValue("NeoForge", version == null ? null : version.toString());
		} catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException
				 | InvocationTargetException | LinkageError | RuntimeException ignored) {
			return null;
		}
	}

	private static String forgeVersion() {
		try {
			Class<?> versionClass = Class.forName("net.minecraftforge.versions.forge.ForgeVersion", false,
					TunnelUserAgent.class.getClassLoader());
			Object version = versionClass.getMethod("getVersion").invoke(null);
			return loaderValue("Forge", version == null ? null : version.toString());
		} catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException
				 | InvocationTargetException | LinkageError | RuntimeException ignored) {
			return null;
		}
	}

	private static Object optionalValue(Object value) {
		if (value instanceof Optional<?> optional) {
			return optional.orElse(null);
		}
		return value;
	}

	private static String loaderValue(String name, String version) {
		if (version == null || version.isBlank()) {
			return null;
		}
		String value = name + "/" + version;
		return validHeaderValue(value) ? value : null;
	}

	private static boolean validHeaderValue(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < 0x20 || c == 0x7F) {
				return false;
			}
		}
		return true;
	}

	private static String nettyVersion() {
		try {
			Map<String, Version> versions = Version.identify(TunnelUserAgent.class.getClassLoader());
			Version common = versions.get("netty-common");
			if (common != null) {
				return common.artifactVersion();
			}
			for (Version version : versions.values()) {
				return version.artifactVersion();
			}
		} catch (RuntimeException ignored) {
		}
		return null;
	}
}
