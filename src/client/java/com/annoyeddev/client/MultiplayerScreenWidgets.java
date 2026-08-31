package com.annoyeddev.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

public final class MultiplayerScreenWidgets {
	private record ActiveWidgets(Button vpnButton, Button ipButton) {
	}

	private static final Map<Screen, ActiveWidgets> ACTIVE = new WeakHashMap<>();
	private static volatile Screen currentScreen;

	private MultiplayerScreenWidgets() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof JoinMultiplayerScreen)) {
				return;
			}

			ActiveWidgets stale = ACTIVE.remove(screen);
			if (stale != null) {
				Screens.getWidgets(screen).remove(stale.vpnButton());
				Screens.getWidgets(screen).remove(stale.ipButton());
			}

			PublicIpTracker.refreshIfStale();

			Button vpnButton = Button.builder(vpnLabel(), b -> VpnToggle.toggle())
					.pos(screen.width - 90, 6)
					.size(84, 20)
					.tooltip(Tooltip.create(Component.translatable("wirecraft.multiplayer.vpnTooltip")))
					.build();
			Screens.getWidgets(screen).add(vpnButton);

			Button ipButton = Button.builder(Component.translatable("wirecraft.multiplayer.seeIp"), b -> PublicIpTracker.refreshNow())
					.pos(screen.width - 150, 30)
					.size(144, 20)
					.tooltip(Tooltip.create(ipTooltip()))
					.build();
			Screens.getWidgets(screen).add(ipButton);

			ACTIVE.put(screen, new ActiveWidgets(vpnButton, ipButton));
			currentScreen = screen;

			ScreenEvents.remove(screen).register(closed -> {
				if (currentScreen == closed) {
					currentScreen = null;
				}
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Screen screen = currentScreen;
			if (screen == null) {
				return;
			}
			ActiveWidgets widgets = ACTIVE.get(screen);
			if (widgets == null) {
				return;
			}
			widgets.vpnButton().setMessage(vpnLabel());
			widgets.ipButton().setTooltip(Tooltip.create(ipTooltip()));
		});
	}

	private static Component vpnLabel() {
		return Component.translatable(VpnToggle.isActive() ? "wirecraft.multiplayer.vpnOn" : "wirecraft.multiplayer.vpnOff");
	}

	private static Component ipTooltip() {
		return PublicIpTracker.getCachedIp()
				.map(ip -> Component.translatable(
						PublicIpTracker.isCurrentIpViaTunnel() ? "wirecraft.multiplayer.tunnelIp" : "wirecraft.multiplayer.publicIp", ip))
				.orElse(Component.translatable(PublicIpTracker.isFetching() ? "wirecraft.multiplayer.lookingUp" : "wirecraft.multiplayer.unknown"));
	}
}
