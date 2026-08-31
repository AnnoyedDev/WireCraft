package com.annoyeddev.client;

import com.annoyeddev.VpnMinecraft;
import com.annoyeddev.config.VpnConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.nio.file.Path;

public final class WireproxyConsentPrompt {
	private static final String WIREPROXY_URL = "https://github.com/windtf/wireproxy";

	private WireproxyConsentPrompt() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			VpnConfig config = VpnMinecraft.getConfig();
			if (screen instanceof TitleScreen && !config.wireproxyConsentAsked) {
				client.setScreenAndShow(buildScreen(client, screen, config));
			}
		});
	}

	private static Screen buildScreen(Minecraft client, Screen parent, VpnConfig config) {
		MutableComponent message = Component.translatable("wirecraft.consent.message")
				.append(Component.literal("\n\n"))
				.append(Component.literal(WIREPROXY_URL)
						.withStyle(style -> style
								.withClickEvent(new ClickEvent.OpenUrl(URI.create(WIREPROXY_URL)))
								.withUnderlined(true)
								.withColor(ChatFormatting.AQUA)));

		return new ConfirmScreen(
				accepted -> {
					config.autoDownloadWireproxy = accepted;
					config.wireproxyConsentAsked = true;
					VpnMinecraft.saveConfig();

					if (accepted) {
						Path binDir = FabricLoader.getInstance().getGameDir().resolve(VpnMinecraft.MOD_ID).resolve("bin");
						client.setScreenAndShow(new WireproxyDownloadScreen(parent, binDir));
					} else {
						client.setScreenAndShow(parent);
					}
				},
				Component.translatable("wirecraft.config.title"),
				message,
				Component.translatable("wirecraft.consent.accept"),
				Component.translatable("wirecraft.consent.decline"));
	}
}
