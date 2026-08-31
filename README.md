![logo](/logomd.png)
# WireCraft

A Fabric client mod (Minecraft 26.2) that tunnels **only the Minecraft
connection** through WireGuard.

## Why WireGuard

WireGuard runs through [wireproxy](https://github.com/windtf/wireproxy),
a fully userspace WireGuard client that exposes itself as a local SOCKS5
proxy. That means:

- **No admin/root rights.** It never touches OS routing tables and never
  creates a TUN device.
- **No system-wide side effects.** Everything else on your machine keeps
  using your normal network.
- **Only Minecraft's socket goes through it.** The mod hooks the Netty
  connection Minecraft itself opens to the server and routes just that
  socket through wireproxy's SOCKS5 proxy.

This mod only supports WireGuard.

## Requirements

- Minecraft 26.2, Fabric Loader >= 0.19.3, Fabric API 0.158.0+26.2
- Java 25
- [Cloth Config](https://modrinth.com/mod/cloth-config) and
  [ModMenu](https://modrinth.com/mod/modmenu) (for the config screen)
- Nothing else required by default - the mod can auto-download the small
  `wireproxy` helper binary.

## Building

```
./gradlew build
```

The mod jar ends up in `build/libs/`. Needs a Java 25 JDK; if `java` isn't
on your PATH, point Gradle at one explicitly, e.g.:

```
JAVA_HOME=/path/to/a/java25/jdk ./gradlew build
```

This has been built and compiles cleanly against the real Minecraft 26.2 /
Fabric API 0.158.0+26.2 toolchain. See "The Netty connection mixin" below
for the one part that was individually disassembled and verified rather than
just compiled.

## Setting up a tunnel

1. Launch the game once with the mod installed, it generates a fresh
   Curve25519 keypair for you automatically.
2. Open **Mods → WireCraft → Config** (via ModMenu). In the "WireGuard
   Profiles" category, "Your public key" shows your current public key,
   ready to copy (click the field, Ctrl+A, Ctrl+C) and give to whoever runs
   the WireGuard peer/server.
3. Fill in:
   - Tunnel address (the CIDR your peer assigned you, e.g. `10.0.0.2/32`)
   - Peer public key, endpoint host/port, allowed IPs
   - Preshared key if your peer uses one
4. Enable auto-download of `wireproxy` (or point "wireproxy binary path" at
   one you downloaded yourself from
   https://github.com/windtf/wireproxy/releases and verified).
5. In the multiplayer server list screen, use the **VPN: ON/OFF** button
   (top-right) to toggle the tunnel. Hover **"See current public IP"** next
   to it to check what address the connection is actually exiting through.

Need to start over? The "Reset this profile" toggle in the config screen
clears every field and generates a brand new keypai, turn it on, then
click Done to apply.

### Importing an existing config

If someone handed you a ready-made WireGuard config, paste its path into
the "Import" field at the top of the "WireGuard Profiles" category (paths
are relative to the game directory unless absolute), then click Done. A
toast notification confirms whether the import succeeded and, if not, why.

Parses a standard wg-quick `.conf` (`[Interface]`/`[Peer]`) and replaces
your primary WireGuard profile (the one everything - the multiplayer-screen
button, auto-connect, the config screen - actually connects with).

### Auto-connect per server

In the config screen's "Server Bindings" category, set a server address
(host, or host:port) and the WireGuard profile to use. The mod then starts
the tunnel automatically right before connecting to that server, and tears
it down when you disconnect (both toggleable per binding).

### Multiple profiles / bindings

The config screen edits the first ("primary") WireGuard profile and server
binding for convenience. The underlying data model supports full lists of
both - to add more, edit `config/wirecraft.json` directly, e.g.:

```json
{
  "wireGuardProfiles": [
    { "name": "home", "privateKey": "...", "...": "..." },
    { "name": "work", "privateKey": "...", "...": "..." }
  ],
  "serverBindings": [
    { "serverAddress": "survival.example.com", "profileName": "home", "autoConnect": true },
    { "serverAddress": "creative.example.com:25566", "profileName": "work", "autoConnect": true }
  ]
}
```

## wireproxy binary

By default the mod does **not** download anything automatically, you opt
in via the "Auto-download wireproxy" toggle or via the one-time on-screen
alert. When enabled, on first connect it fetches the right release asset
for your OS/CPU from `github.com/windtf/wireproxy`'s GitHub releases, verifies
it against that release's own `checksums.txt` (SHA-256), and caches it under
`<game dir>/wirecraft/bin/`. If you'd rather not have the mod fetch
executables on its own, leave that off and point "wireproxy binary path" at
a copy you downloaded and checked yourself.

## License and MODPACK

WireCraft is licensed under the [GNU Lesser General Public License v3.0](LICENSE)
(LGPL-3.0-or-later).

You're welcome to include this mod, unmodified, in any modpack - public or
private, free or monetized - without asking for separate permission first.
Just credit "WireCraft" and link back to this repository.
