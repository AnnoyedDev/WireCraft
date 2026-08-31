package com.annoyeddev.client.gui;

import com.annoyeddev.VpnMinecraft;
import com.annoyeddev.backend.wireguard.WireGuardKeyUtil;
import com.annoyeddev.config.ConfigImporter;
import com.annoyeddev.config.ServerBinding;
import com.annoyeddev.config.VpnConfig;
import com.annoyeddev.config.WireGuardProfile;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class VpnConfigScreen {
	private static final Logger LOGGER = LoggerFactory.getLogger("wirecraft/config-screen");
	private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();

	private VpnConfigScreen() {
	}

	public static Screen create(Screen parent) {
		VpnConfig config = VpnMinecraft.getConfig();
		WireGuardProfile profile = primaryWireGuardProfile(config);
		ServerBinding binding = primaryBinding(config);

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("wirecraft.config.title"))
				.setSavingRunnable(VpnMinecraft::saveConfig);

		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("wirecraft.config.category.general"));
		general.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.enabled"), config.enabled)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("wirecraft.config.enabled.tooltip"))
				.setSaveConsumer(v -> config.enabled = v)
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.connectOnStartup"), config.connectOnStartup)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("wirecraft.config.connectOnStartup.tooltip"))
				.setSaveConsumer(v -> config.connectOnStartup = v)
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.autoDownloadWireproxy"), config.autoDownloadWireproxy)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("wirecraft.config.autoDownloadWireproxy.tooltip"))
				.setSaveConsumer(v -> config.autoDownloadWireproxy = v)
				.build());
		general.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireproxyPath"), config.wireproxyPath)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.wireproxyPath = v)
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.showPublicIp"), config.showPublicIp)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("wirecraft.config.showPublicIp.tooltip"))
				.setSaveConsumer(v -> config.showPublicIp = v)
				.build());

		ConfigCategory wg = builder.getOrCreateCategory(Component.translatable("wirecraft.config.category.profiles"));
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.importPath"), "")
				.setDefaultValue("")
				.setSaveConsumer(VpnConfigScreen::importIfPathGiven)
				.build());
		wg.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.wireguard.reset"), false)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("wirecraft.config.wireguard.reset.tooltip"))
				.setSaveConsumer(VpnConfigScreen::resetIfRequested)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.yourPublicKey"), currentPublicKeyDisplay(profile))
				.setSaveConsumer(v -> {
				})
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.profileName"), profile.name)
				.setSaveConsumer(v -> profile.name = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.privateKey"), profile.privateKey)
				.setSaveConsumer(v -> profile.privateKey = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.address"), profile.address)
				.setSaveConsumer(v -> profile.address = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.dns"), String.join(",", profile.dns))
				.setSaveConsumer(v -> profile.dns = splitCsv(v))
				.build());
		wg.addEntry(eb.startIntField(Component.translatable("wirecraft.config.wireguard.mtu"), profile.mtu)
				.setDefaultValue(1420)
				.setMin(576)
				.setMax(9000)
				.setSaveConsumer(v -> profile.mtu = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.peerPublicKey"), profile.peerPublicKey)
				.setSaveConsumer(v -> profile.peerPublicKey = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.presharedKey"), profile.presharedKey)
				.setSaveConsumer(v -> profile.presharedKey = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.endpointHost"), profile.endpointHost)
				.setSaveConsumer(v -> profile.endpointHost = v)
				.build());
		wg.addEntry(eb.startIntField(Component.translatable("wirecraft.config.wireguard.endpointPort"), profile.endpointPort)
				.setDefaultValue(51820)
				.setMin(1)
				.setMax(65535)
				.setSaveConsumer(v -> profile.endpointPort = v)
				.build());
		wg.addEntry(eb.startStrField(Component.translatable("wirecraft.config.wireguard.allowedIps"), String.join(",", profile.allowedIps))
				.setSaveConsumer(v -> profile.allowedIps = splitCsv(v))
				.build());
		wg.addEntry(eb.startIntField(Component.translatable("wirecraft.config.wireguard.keepalive"), profile.persistentKeepalive)
				.setDefaultValue(25)
				.setMin(0)
				.setMax(3600)
				.setSaveConsumer(v -> profile.persistentKeepalive = v)
				.build());

		ConfigCategory bindings = builder.getOrCreateCategory(Component.translatable("wirecraft.config.category.bindings"));
		bindings.addEntry(eb.startTextDescription(Component.translatable("wirecraft.config.bindings.hint"))
				.build());
		bindings.addEntry(eb.startStrField(Component.translatable("wirecraft.config.bindings.serverAddress"), binding.serverAddress)
				.setSaveConsumer(v -> binding.serverAddress = v)
				.build());
		bindings.addEntry(eb.startStrField(Component.translatable("wirecraft.config.bindings.profileName"), binding.profileName)
				.setSaveConsumer(v -> binding.profileName = v)
				.build());
		bindings.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.bindings.autoConnect"), binding.autoConnect)
				.setDefaultValue(true)
				.setSaveConsumer(v -> binding.autoConnect = v)
				.build());
		bindings.addEntry(eb.startBooleanToggle(Component.translatable("wirecraft.config.bindings.autoDisconnect"), binding.autoDisconnectOnLeave)
				.setDefaultValue(true)
				.setSaveConsumer(v -> binding.autoDisconnectOnLeave = v)
				.build());

		ConfigCategory status = builder.getOrCreateCategory(Component.translatable("wirecraft.config.category.status"));
		status.addEntry(eb.startTextDescription(Component.translatable("wirecraft.config.status.state",
						VpnMinecraft.getBackendManager().getState()))
				.build());
		status.addEntry(eb.startTextDescription(Component.translatable("wirecraft.config.status.activeProfile",
						VpnMinecraft.getBackendManager().getActiveProfileName()))
				.build());
		status.addEntry(eb.startTextDescription(Component.translatable("wirecraft.config.status.statusMessage",
						VpnMinecraft.getBackendManager().getStatusMessage()))
				.build());
		status.addEntry(eb.startTextDescription(Component.translatable("wirecraft.config.status.hint"))
				.build());

		return builder.build();
	}

	private static WireGuardProfile primaryWireGuardProfile(VpnConfig config) {
		if (config.wireGuardProfiles.isEmpty()) {
			config.wireGuardProfiles.add(new WireGuardProfile());
		}
		return config.wireGuardProfiles.get(0);
	}

	private static ServerBinding primaryBinding(VpnConfig config) {
		if (config.serverBindings.isEmpty()) {
			config.serverBindings.add(new ServerBinding());
		}
		return config.serverBindings.get(0);
	}

	private static String currentPublicKeyDisplay(WireGuardProfile profile) {
		if (profile.privateKey.isBlank()) {
			return "";
		}
		try {
			return WireGuardKeyUtil.derivePublicKey(profile.privateKey);
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	private static void importIfPathGiven(String rawPath) {
		if (rawPath == null || rawPath.isBlank()) {
			return;
		}
		Path path = Path.of(rawPath.trim());
		if (!path.isAbsolute()) {
			path = FabricLoader.getInstance().getGameDir().resolve(path);
		}
		try {
			WireGuardProfile imported = ConfigImporter.importWireGuard(path);
			VpnConfig config = VpnMinecraft.getConfig();
			if (config.wireGuardProfiles.isEmpty()) {
				config.wireGuardProfiles.add(imported);
			} else {
				config.wireGuardProfiles.set(0, imported);
			}
			showToast(Component.translatable("wirecraft.config.wireguard.importSuccessTitle"),
					Component.translatable("wirecraft.config.wireguard.importSuccessMessage", imported.name));
		} catch (IOException e) {
			LOGGER.error("Failed to import WireGuard config from {}", path, e);
			showToast(Component.translatable("wirecraft.config.wireguard.importFailureTitle"),
					Component.literal(e.getMessage() != null ? e.getMessage() : e.toString()));
		}
	}

	private static void resetIfRequested(boolean reset) {
		if (!reset) {
			return;
		}
		VpnConfig config = VpnMinecraft.getConfig();
		WireGuardProfile fresh = new WireGuardProfile();
		WireGuardKeyUtil.KeyPairResult keyPair = WireGuardKeyUtil.generateKeyPair();
		fresh.privateKey = keyPair.privateKeyBase64();
		if (config.wireGuardProfiles.isEmpty()) {
			config.wireGuardProfiles.add(fresh);
		} else {
			config.wireGuardProfiles.set(0, fresh);
		}
		showToast(Component.translatable("wirecraft.config.wireguard.resetToastTitle"),
				Component.translatable("wirecraft.config.wireguard.resetToastMessage"));
	}

	private static void showToast(Component title, Component message) {
		SystemToast.add(Minecraft.getInstance().gui.toastManager(), TOAST_ID, title, message);
	}

	private static java.util.List<String> splitCsv(String v) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String part : v.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				out.add(trimmed);
			}
		}
		return out;
	}
}
