package com.annoyeddev.config;

import java.util.ArrayList;
import java.util.List;

public class WireGuardProfile {
	public String name = "New Profile";

	public String privateKey = "";
	public String address = "10.0.0.2/32";
	public List<String> dns = new ArrayList<>();
	public int mtu = 1420;

	public String peerPublicKey = "";
	public String presharedKey = "";
	public String endpointHost = "";
	public int endpointPort = 51820;
	public List<String> allowedIps = new ArrayList<>(List.of("0.0.0.0/0"));
	public int persistentKeepalive = 25;
}
