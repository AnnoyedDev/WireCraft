package com.annoyeddev.client;

import com.annoyeddev.VpnMinecraft;
import com.annoyeddev.backend.VpnConnectionState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class VpnHud {
	private VpnHud() {
	}

	public static void register() {
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(VpnMinecraft.MOD_ID, "status"),
				(context, tickCounter) -> {
					if (!VpnMinecraft.getConfig().enabled) {
						return;
					}

					VpnConnectionState state = VpnMinecraft.getBackendManager().getState();
					String message = switch (state) {
						case CONNECTING -> Component.translatable("wirecraft.hud.connecting").getString();
						case CONNECTED -> Component.translatable("wirecraft.hud.connected",
								VpnMinecraft.getBackendManager().getActiveProfileName()).getString();
						case ERROR -> Component.translatable("wirecraft.hud.error").getString();
						case DISCONNECTED, DISCONNECTING -> Component.translatable("wirecraft.hud.disconnected").getString();
					};

					int color = switch (state) {
						case CONNECTED -> 0xFF55FF55;
						case CONNECTING -> 0xFFFFFF55;
						case ERROR -> 0xFFFF5555;
						case DISCONNECTED, DISCONNECTING -> 0xFFAAAAAA;
					};

					Minecraft client = Minecraft.getInstance();
					context.text(client.font, message, 6, 6, color, true);
				});

		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(VpnMinecraft.MOD_ID, "public_ip"),
				(context, tickCounter) -> {
					if (!VpnMinecraft.getConfig().showPublicIp) {
						return;
					}

					String text = PublicIpTracker.getCachedIp()
							.map(ip -> (PublicIpTracker.isCurrentIpViaTunnel() ? "Tunnel IP: " : "Public IP: ") + ip)
							.orElse(PublicIpTracker.isFetching() ? "Public IP: looking up..." : "Public IP: unknown");
					int color = PublicIpTracker.isCurrentIpViaTunnel() ? 0xFF55FF55 : 0xFFAAAAAA;

					Minecraft client = Minecraft.getInstance();
					int screenWidth = client.getWindow().getGuiScaledWidth();
					int screenHeight = client.getWindow().getGuiScaledHeight();
					int textWidth = client.font.width(text);
					int x = screenWidth - textWidth - 6;
					int y = screenHeight - client.font.lineHeight - 6;
					context.text(client.font, text, x, y, color, true);
				});
	}
}
