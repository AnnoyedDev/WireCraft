package com.annoyeddev.client;

import com.annoyeddev.VpnMinecraft;
import com.annoyeddev.config.ServerBinding;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.net.InetSocketAddress;
import java.util.Optional;

public class VpnMinecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VpnHud.register();
		WireproxyConsentPrompt.register();
		MultiplayerScreenWidgets.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> PublicIpTracker.tickIfEnabled());

		if (VpnMinecraft.getConfig().connectOnStartup) {
			VpnMinecraft.getConfig().primaryConfiguredProfile()
					.ifPresent(profile -> VpnMinecraft.getBackendManager().connect(profile));
		}

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			InetSocketAddress address = handler.getConnection().getRemoteAddress() instanceof InetSocketAddress inet
					? inet
					: null;
			if (address == null) {
				return;
			}
			Optional<ServerBinding> binding = VpnMinecraft.getConfig().findBindingForAddress(address.getHostString(), address.getPort());
			if (binding.isPresent() && binding.get().autoDisconnectOnLeave) {
				VpnMinecraft.getBackendManager().disconnect();
			}
		});
	}
}
