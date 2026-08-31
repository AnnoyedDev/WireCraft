package com.annoyeddev;

import com.annoyeddev.backend.VpnBackendManager;
import com.annoyeddev.backend.wireguard.WireGuardKeyUtil;
import com.annoyeddev.config.VpnConfig;
import com.annoyeddev.config.WireGuardProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class VpnMinecraft implements ModInitializer {
	public static final String MOD_ID = "wirecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static VpnConfig config;
	private static VpnBackendManager backendManager;
	private static Path dataDir;
	private static Path configFile;

	@Override
	public void onInitialize() {
		dataDir = FabricLoader.getInstance().getGameDir().resolve(MOD_ID);
		configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");

		config = VpnConfig.load(configFile);
		if (config.wireGuardProfiles.isEmpty()) {
			WireGuardProfile fresh = new WireGuardProfile();
			WireGuardKeyUtil.KeyPairResult keyPair = WireGuardKeyUtil.generateKeyPair();
			fresh.privateKey = keyPair.privateKeyBase64();
			config.wireGuardProfiles.add(fresh);
			config.save(configFile);
			LOGGER.info("Generated a fresh WireGuard keypair for the default profile. Public key: {}", keyPair.publicKeyBase64());
		}
		backendManager = new VpnBackendManager(dataDir, config);

		LOGGER.info("WireCraft initialized ({} WireGuard profile(s) configured)", config.wireGuardProfiles.size());
	}

	public static VpnConfig getConfig() {
		return config;
	}

	public static void saveConfig() {
		config.save(configFile);
	}

	public static VpnBackendManager getBackendManager() {
		return backendManager;
	}
}
