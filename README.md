# Minecraft Tunnel

Minecraft Tunnel adds tunnel transports for Minecraft Java Edition
connections. It can carry the normal Minecraft protocol over WebSocket, HTTP
Upgrade, gRPC over HTTP/2, or TLS-wrapped vanilla TCP.

The mod is designed for servers that need to place Minecraft traffic behind
reverse proxies, TLS endpoints, or HTTP-aware infrastructure while keeping the
client connection flow simple.

## Features

- WebSocket tunnel support with `ws://` and `wss://` addresses.
- HTTP Upgrade tunnel support with `httpupgrade://` and `httpupgrades://`
  addresses.
- gRPC bidirectional stream support with `grpc://` and `grpc+h2c://`
  addresses.
- TLS-wrapped vanilla TCP support with `tls://` addresses.
- Optional server-side restriction to tunnel-only traffic.
- Optional trusted proxy support for `X-Forwarded-For` and `X-Real-IP`.

## Compatibility

| Loader | Side | Artifact |
| --- | --- | --- |
| Fabric | Client and server | `minecraft-tunnel-0.1.0+26.1.2+fabric.jar` |
| Forge | Client and server | `minecraft-tunnel-0.1.0+26.1.2+forge.jar` |
| NeoForge | Client and server | `minecraft-tunnel-0.1.0+26.1.2+neoforge.jar` |
| Ignite | Server | `minecraft-tunnel-0.1.0+26.1.2+ignite.jar` |

Minecraft version range: `>=26.1.2 <26.2`.

Ignite support is server-only. Ignite is a Mixin loader for Spigot/Paper, and
the project is positioned as a Mixin loader for Paper/Velocity/Spigot/Hytale.
The Ignite artifact lets server owners install Minecraft Tunnel's server-side
mixins without running a Fabric, Forge, or NeoForge server. Players still need
a Fabric, Forge, or NeoForge client jar when they connect with explicit tunnel
addresses or DNS URI tunnel discovery.

## Installation

Install the matching jar for your loader.

For Fabric, Forge, or NeoForge clients, place the loader-specific client jar in
the client's `mods` directory. The same loader-specific jar can also be used on
Fabric, Forge, or NeoForge servers.

For Ignite-backed servers, install Ignite first, then place the Ignite jar in
the server's Ignite mods directory.

Vanilla addresses such as `example.com:25565` keep using the normal Minecraft
TCP connection path.

Vanilla-style hostnames without an explicit port can also opt into a tunnel
through a DNS `URI` record at `_minecraft._tcp.<hostname>`.

## Client Addresses

Minecraft Tunnel activates when the server address starts with one of its
supported URI schemes.

### WebSocket

```text
ws://example.com:25565/mc
wss://example.com/mc
```

`ws://` uses cleartext WebSocket. `wss://` uses TLS and WebSocket.

### HTTP Upgrade

```text
httpupgrade://example.com:25565/mc
httpupgrades://example.com/mc
```

`httpupgrade://` performs an HTTP/1.1 Upgrade handshake and then carries raw
Minecraft protocol bytes on the upgraded connection. `httpupgrades://` adds TLS
before the HTTP Upgrade handshake.

### gRPC

```text
grpc://example.com/MinecraftTunnel
grpc+h2c://example.com:25565/MinecraftTunnel
```

`grpc://` uses TLS with HTTP/2. `grpc+h2c://` uses HTTP/2 cleartext
prior-knowledge.

The URI path is the gRPC service name. The Minecraft byte stream is carried by
the bidirectional streaming method:

```text
/${serviceName}/Tun
```

For example, `grpc://example.com/MinecraftTunnel` sends gRPC requests to:

```text
/MinecraftTunnel/Tun
```

If no service name is provided, `MinecraftTunnel` is used.

### TLS-Wrapped Vanilla TCP

```text
tls://example.com:25565
```

`tls://` performs a TLS handshake and then sends normal Minecraft protocol
bytes inside the TLS stream. It does not add HTTP, WebSocket, or gRPC framing.

Use this form when the remote endpoint accepts TLS and forwards the decrypted
stream to a vanilla Minecraft listener.

## Host, SNI, and Routing

Tunnel addresses can separate the resolved host from the HTTP `Host` header
and TLS SNI value.

```text
tls://sni.example@203.0.113.10:25565
ws://host.example@203.0.113.10/mc
wss://sni.example:host.example@203.0.113.10/mc
httpupgrade://host.example@203.0.113.10/mc
httpupgrades://sni.example:host.example@203.0.113.10/mc
grpc+h2c://host.example@203.0.113.10/MinecraftTunnel
grpc://sni.example:host.example@203.0.113.10/MinecraftTunnel
```

When one value is provided before `@`, it is used as the HTTP host or TLS SNI
where applicable. When two values are provided as `sni:host@address`, the first
value is used as TLS SNI and the second value is used as the HTTP host.

## DNS URI Records

Instead of asking players to type a tunnel URI, publish a DNS `URI` record for
the Minecraft service name:

```text
_minecraft._tcp.example.com. 300 IN URI 10 1 "wss://edge.example.com/mc"
```

Players can then connect to `example.com`. If the record target is a supported
tunnel URI, Minecraft Tunnel uses it as though the player entered it directly.
Explicit tunnel URIs still take precedence, and addresses with an explicit port
such as `example.com:25565` keep the vanilla connection path.

The URI record target supports the same schemes and Host/SNI user-info syntax
documented above. When multiple URI records exist, the client tries lower
priority values first, then higher weight values.

## Server Behavior

The Minecraft server port can accept normal Minecraft TCP and enabled tunnel
protocols on the same listener.

WebSocket requests are accepted as standard WebSocket binary streams. HTTP
Upgrade requests are accepted as raw byte streams after `101 Switching
Protocols`. gRPC requests use HTTP/2 `POST` with `application/grpc` on a
long-lived bidirectional stream.

For `grpc://`, terminate TLS and ALPN in front of the Minecraft server and
forward HTTP/2 cleartext to the Minecraft port. For direct cleartext HTTP/2,
use `grpc+h2c://`.

Set `mctunnel.disableVanillaTCP=true` if the server should reject normal
Minecraft TCP and accept only enabled tunnel protocols.

## Configuration

Configuration is provided with Java system properties.

| Property | Side | Default | Description |
| --- | --- | --- | --- |
| `mctunnel.protocol` | Server | `all` | Comma-separated list of enabled server tunnel protocols. Supported values: `all`, `websocket`, `ws`, `httpupgrade`, `grpc`. |
| `mctunnel.endpoint` | Server | any path | If set, only this exact tunnel path is accepted. For gRPC, either `/Service` or `/Service/Tun` matches the same method. |
| `mctunnel.disableVanillaTCP` | Server | `false` | Close standard Minecraft TCP connections and accept only enabled tunnel protocols. |
| `mctunnel.trustedProxies` | Server | empty | Comma-separated trusted proxy IP/CIDR ranges allowed to supply `X-Forwarded-For` or `X-Real-IP`. Example: `127.0.0.1/32,10.0.0.0/8,::1/128`. |
| `mctunnel.maxFramePayloadLength` | Both | `65536` | Maximum WebSocket frame payload length. |
| `mctunnel.debug` | Both | `false` | Enable debug logs. |
| `mctunnel.dumpBytes` | Both | `false` | Dump tunnel bytes when debug logging is enabled. |

Example:

```bash
java -Dmctunnel.protocol=websocket,grpc \
  -Dmctunnel.endpoint=/mc \
  -Dmctunnel.trustedProxies=127.0.0.1/32,10.0.0.0/8 \
  -jar server.jar
```

## Build From Source

Build each loader artifact from its loader directory:

```bash
cd fabric
./gradlew build
```

```bash
cd forge
./gradlew build
```

```bash
cd neoforge
./gradlew build
```

```bash
cd ignite
./gradlew build
```

The main jars are written to:

```text
fabric/build/libs/minecraft-tunnel-0.1.0+26.1.2+fabric.jar
forge/build/libs/minecraft-tunnel-0.1.0+26.1.2+forge.jar
neoforge/build/libs/minecraft-tunnel-0.1.0+26.1.2+neoforge.jar
ignite/build/libs/minecraft-tunnel-0.1.0+26.1.2+ignite.jar
```

## License

Minecraft Tunnel is licensed under the Apache License, Version 2.0. See
[LICENSE](LICENSE) for details.

SPDX-License-Identifier: Apache-2.0
