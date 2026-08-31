package com.annoyeddev.client;

import com.annoyeddev.VpnMinecraft;
import com.annoyeddev.backend.VpnConnectionState;

public final class VpnToggle {
	private VpnToggle() {
	}

	public static boolean isActive() {
		VpnConnectionState state = VpnMinecraft.getBackendManager().getState();
		return state == VpnConnectionState.CONNECTED || state == VpnConnectionState.CONNECTING;
	}

	public static void toggle() {
		var manager = VpnMinecraft.getBackendManager();
		if (isActive()) {
			manager.disconnect();
			return;
		}

		var config = VpnMinecraft.getConfig();
		if (config.wireGuardProfiles.isEmpty()) {
			return;
		}
		String profileName = config.wireGuardProfiles.get(0).name;
		manager.connectByName(profileName);
	}
}
