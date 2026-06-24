# Minecraft Tunnel

[English](README.md) | 中文（中国）

Minecraft Tunnel 为 Minecraft Java 版连接提供额外的隧道传输方式。它可以通过 WebSocket、HTTP Upgrade、基于 HTTP/2 的 gRPC，或套在 TLS 中的原版 TCP 承载标准 Minecraft 协议。

如果你想让 Minecraft 服务器接入反向代理、TLS 入口或其他支持 HTTP 的网络环境，同时保持玩家侧连接方式尽量简单，可以使用这个模组。

## 功能

- 支持使用 `ws://` 和 `wss://` 地址的 WebSocket 隧道。
- 支持使用 `httpupgrade://` 和 `httpupgrades://` 地址的 HTTP Upgrade 隧道。
- 支持使用 `grpc://` 和 `grpc+h2c://` 地址的 gRPC 双向流。
- 支持 `tls://` 地址的 TLS 封装原版 TCP。
- 支持在服务端选择协议，包括显式启用原版 TCP。
- 支持配置可信代理，用于处理 `X-Forwarded-For` 和 `X-Real-IP`。

## 兼容性

| 加载器 | 运行位置 | 文件 |
| --- | --- | --- |
| Fabric | 客户端和服务端 | `minecraft-tunnel-0.2.1+26.1.2+fabric.jar` |
| Forge | 客户端和服务端 | `minecraft-tunnel-0.2.1+26.1.2+forge.jar` |
| NeoForge | 客户端和服务端 | `minecraft-tunnel-0.2.1+26.1.2+neoforge.jar` |
| Ignite | 服务端 | `minecraft-tunnel-0.2.1+26.1.2+ignite.jar` |

Minecraft 版本范围：`>=26.1.2 <26.2`。

Ignite 支持仅限服务端。Ignite 是面向 Spigot/Paper 的 Mixin 加载器，也面向 Paper、Velocity、Spigot 和 Hytale 等服务端生态。Ignite 版可以在不切换到 Fabric、Forge 或 NeoForge 服务端的情况下安装 Minecraft Tunnel 的服务端 Mixin。玩家通过显式隧道地址或 DNS URI 发现功能连接时，仍然需要安装 Fabric、Forge 或 NeoForge 客户端 jar。

## 安装

安装与你使用的加载器匹配的 jar。

对于 Fabric、Forge 或 NeoForge 客户端，把对应的 jar 放入客户端的 `mods` 目录。同一个加载器对应的 jar 也可以用于 Fabric、Forge 或 NeoForge 服务端。

对于使用 Ignite 的服务端，请先安装 Ignite，然后把 Ignite jar 放入服务端的 Ignite mods 目录。

像 `example.com:25565` 这样的原版地址会继续使用正常的 Minecraft TCP 连接路径。

没有显式端口的普通主机名也可以通过 `_minecraft._tcp.<hostname>` 上的 DNS `URI` 记录发现并使用隧道。

## 客户端地址

当服务器地址以受支持的 URI scheme 开头时，Minecraft Tunnel 会自动启用。

对于大多数支持 HTTP 的部署，建议优先使用 HTTP Upgrade。它使用许多支持 WebSocket 的 CDN 和反向代理已支持的 HTTP/1.1 Upgrade 路径，并在升级后直接承载原始 Minecraft 字节，相比 WebSocket 可减少帧封装开销。

### WebSocket

```text
ws://example.com:25565/MinecraftTunnel
wss://example.com/MinecraftTunnel
```

`ws://` 使用明文 WebSocket。`wss://` 使用 TLS 和 WebSocket。

### HTTP Upgrade

```text
httpupgrade://example.com:25565/MinecraftTunnel
httpupgrades://example.com/MinecraftTunnel
```

`httpupgrade://` 会执行 HTTP/1.1 Upgrade 握手，然后在升级后的连接上承载原始 Minecraft 协议字节。`httpupgrades://` 会先建立 TLS，再执行 HTTP Upgrade 握手。

### gRPC

```text
grpc://example.com/MinecraftTunnel
grpc+h2c://example.com:25565/MinecraftTunnel
```

`grpc://` 使用带 TLS 的 HTTP/2。`grpc+h2c://` 使用明文 HTTP/2 prior-knowledge。

URI 路径是 gRPC 服务名。Minecraft 字节流由双向流方法承载：

```text
/${serviceName}/Tun
```

例如，`grpc://example.com/MinecraftTunnel` 会把 gRPC 请求发送到：

```text
/MinecraftTunnel/Tun
```

如果没有提供服务名，则使用 `MinecraftTunnel`。

### TLS 封装原版 TCP

```text
tls://example.com:25565
```

`tls://` 会执行 TLS 握手，然后在 TLS 流中发送标准 Minecraft 协议字节。它不会添加 HTTP、WebSocket 或 gRPC 帧。

当远端入口接受 TLS，并把解密后的流量转发到普通 Minecraft 监听器时，可以使用这种形式。

## Host、SNI 与路由

隧道地址可以把实际解析的目标主机、HTTP `Host` 头和 TLS SNI 分开设置。

```text
tls://sni.example@203.0.113.10:25565
ws://host.example@203.0.113.10/MinecraftTunnel
wss://sni.example:host.example@203.0.113.10/MinecraftTunnel
httpupgrade://host.example@203.0.113.10/MinecraftTunnel
httpupgrades://sni.example:host.example@203.0.113.10/MinecraftTunnel
grpc+h2c://host.example@203.0.113.10/MinecraftTunnel
grpc://sni.example:host.example@203.0.113.10/MinecraftTunnel
```

当 `@` 前只提供一个值时，它会在适用场景下作为 HTTP host 或 TLS SNI 使用。当以 `sni:host@address` 形式提供两个值时，第一个值作为 TLS SNI，第二个值作为 HTTP host。

## DNS URI 记录

如果不想让玩家手动输入隧道 URI，可以为 Minecraft 服务名发布 DNS `URI` 记录：

```text
_minecraft._tcp.example.com. 300 IN URI 10 1 "httpupgrades://edge.example.com/MinecraftTunnel"
```

随后玩家可以直接连接 `example.com`。如果记录目标是受支持的隧道 URI，Minecraft Tunnel 会像玩家直接输入该 URI 一样使用它。显式隧道 URI 仍然优先；像 `example.com:25565` 这样带显式端口的地址会继续走原版连接路径。

URI 记录目标支持上文列出的相同 scheme，以及 Host/SNI user-info 语法。当存在多条 URI 记录时，客户端会先尝试优先级较低的值，再尝试权重较高的值。

## 服务端行为

Minecraft 服务端端口会接受 `mctunnel.protocol` 中列出的协议。默认启用普通 Minecraft TCP、WebSocket、HTTP Upgrade 和 gRPC。

WebSocket 请求会作为标准 WebSocket 二进制流处理。HTTP Upgrade 请求会在 `101 Switching Protocols` 后作为原始字节流处理。gRPC 请求使用带 `application/grpc` 的 HTTP/2 `POST`，并运行在长生命周期的双向流上。

对于 `grpc://`，请由 Minecraft 服务端前的代理处理 TLS 和 ALPN，并将解密后的 HTTP/2 明文流量转发到 Minecraft 端口。如果需要直接使用明文 HTTP/2，请使用 `grpc+h2c://`。

## 配置

配置通过 Java 系统属性提供。

| 属性 | 端 | 默认值 | 描述 |
| --- | --- | --- | --- |
| `mctunnel.protocol` | 服务端 | `websocket,httpupgrade,grpc,vanilla` | 逗号分隔的已启用服务端协议列表。支持的值：`websocket`、`ws`、`httpupgrade`、`vanilla`、`grpc`。 |
| `mctunnel.endpoint` | 服务端 | 任意路径 | 如果设置，则只接受完全匹配的隧道路径。对于 gRPC，`/Service` 或 `/Service/Tun` 会匹配同一个方法。 |
| `mctunnel.trustedProxies` | 服务端 | 空 | 逗号分隔的可信代理 IP/CIDR 范围，允许提供 `X-Forwarded-For` 或 `X-Real-IP`。示例：`127.0.0.1/32,10.0.0.0/8,::1/128`。 |
| `mctunnel.userAgent` | 客户端 | 自动生成 | 覆盖基于 HTTP 的客户端隧道发送的 `User-Agent`。默认格式为 `MinecraftTunnel/<模组版本> Minecraft/<游戏版本> <加载器>/<加载器版本> Netty/<Netty 版本>`。 |
| `mctunnel.maxFramePayloadLength` | 两端 | `65536` | 最大 WebSocket 帧负载长度。 |
| `mctunnel.debug` | 两端 | `false` | 启用调试日志。 |

示例：

```bash
java -Dmctunnel.protocol=websocket,httpupgrade,grpc,vanilla \
  -Dmctunnel.endpoint=/MinecraftTunnel \
  -Dmctunnel.trustedProxies=127.0.0.1/32,10.0.0.0/8 \
  -jar server.jar
```

## 从源码构建

从各加载器目录构建对应 jar：

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

主 jar 会输出到：

```text
fabric/build/libs/minecraft-tunnel-0.2.1+26.1.2+fabric.jar
forge/build/libs/minecraft-tunnel-0.2.1+26.1.2+forge.jar
neoforge/build/libs/minecraft-tunnel-0.2.1+26.1.2+neoforge.jar
ignite/build/libs/minecraft-tunnel-0.2.1+26.1.2+ignite.jar
```

## 许可证

Minecraft Tunnel 使用 Apache License, Version 2.0 授权。详情请参阅 [LICENSE](LICENSE)。

SPDX-License-Identifier: Apache-2.0
