# Cloudflared Tunnel Connect

A client-side Minecraft 1.7.10 Forge mod that connects to servers published through a
[Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/),
straight from the vanilla multiplayer server list.

Cloudflare Tunnel can expose a TCP service such as a Minecraft server without opening a
port on the router. On the client side, `cloudflared access tcp` has to be running and
Minecraft has to connect to that local listener. This mod automates that whole flow.

## Features

- Per-server Cloudflare settings in the multiplayer server list:
  - Enable/disable the tunnel for each server
  - Tunnel hostname (for example `mc.example.com`)
  - Local port (`empty` = pick a free port automatically)
- Joining is transparent:
  - The **Join Server** button and **double-click** both start the tunnel first and then
    connect to `127.0.0.1:<port>`.
- Automatic `cloudflared` download and validation:
  - Downloaded from the official GitHub releases into `config/cftunnel/bin/`
  - File format, size and `cloudflared --version` are verified before use
- Global settings screen (top-right **CF Settings** button):
  - `cloudflared` path override
  - Release version
  - Ready timeout
  - Extra command line arguments
- Useful failure diagnostics:
  - If the tunnel cannot be established, the tail of the `cloudflared` log is shown
  - HTTP 502 / `bad handshake` is reported with a hint instead of a bare
    "Connection reset"
- Tunnels are stopped automatically when you disconnect or close the game.

## Requirements

- Minecraft 1.7.10 with Forge `10.13.4.1614` (client only; do not install on the server)
- A domain managed by Cloudflare and a **named tunnel**. Cloudflare quick tunnels
  (`trycloudflare.com`) only support HTTP, so they cannot be used for Minecraft.
- The server side must run `cloudflared tunnel run` with a public hostname pointing to
  the Minecraft TCP port.

## Installation

1. Put `cftunnel-<version>.jar` into your `mods/` folder.
2. Start the game. The mod does not need to be installed on the server.

## Server side

Create a named tunnel and route a hostname to the server's TCP port, for example in the
tunnel configuration:

```yaml
tunnel: <tunnel-id>
credentials-file: /path/to/<tunnel-id>.json

ingress:
  - hostname: mc.example.com
    service: tcp://localhost:25565
  - service: http_status:404
```

Then run:

```bash
cloudflared tunnel run <tunnel-name>
```

You can check the route with `https://<hostname>`; while the tunnel is down Cloudflare
returns HTTP 502.

## Client usage

1. Open **Multiplayer**.
2. Select the server you want to join.
3. Press **Cloudflare...** (bottom-right) and set:
   - **Cloudflare Tunnel: ON**
   - **Hostname**: your tunnel hostname, for example `mc.example.com`
   - **Local port**: leave empty for automatic selection (or set a fixed port)
4. Join with **Join Server** or by double-clicking the server.

The current state for the selected server is shown at the top-left of the screen
(`Cloudflare: mc.example.com (auto)` / `Cloudflare: off`).

### Cloudflare Access

If the hostname is protected by Cloudflare Access, press **Login** to authenticate in
your browser. If the hostname is not protected, login is not required and the mod
reports that instead of an error.

## Files

All files are stored under the game directory:

| Path | Purpose |
| --- | --- |
| `config/cftunnel/config.json` | Global settings |
| `config/cftunnel/servers.json` | Per-server tunnel settings |
| `config/cftunnel/bin/cloudflared.exe` | Downloaded `cloudflared` |
| `logs/cftunnel/*.log` | `cloudflared` output per connection |

Server settings are keyed by the address stored in `servers.dat`. If you change a
server's address, configure the tunnel again.

## Building

ForgeGradle 1.2 requires Java 8 for the build:

```bash
export JAVA_HOME=/path/to/jdk8   # Windows: set JAVA_HOME=...
./gradlew build
```

The distributable jar is written to `build/libs/cftunnel-<version>.jar`
(the `-dev` variant is for development only).

`build.gradle` points ForgeGradle's Minecraft download at `launcher.mojang.com`
because the old `s3.amazonaws.com` URLs it uses by default no longer exist.

## Troubleshooting

- **HTTP 502 / `bad handshake`** — `cloudflared tunnel run` is not running on the
  server, or the public hostname is not configured for that tunnel.
- **Connection reset right after joining** — the tunnel listener started but the edge
  rejected the connection. Check **Cloudflare...** settings and the tunnel log.
- **Local port is already in use** — choose another local port, or leave the field
  empty so the mod picks a free one.
- **Login rejected** — login is only needed when the hostname is behind Cloudflare
  Access. See the message shown by the mod.
