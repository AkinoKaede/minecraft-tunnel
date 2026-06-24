# Repository Guidelines

## Project Structure & Module Organization

This repository builds Minecraft Tunnel for multiple loaders from shared Java sources. Core code lives in `src/main/java/com/akinokaede/mctunnel`, with shared resources in `src/main/resources`. Loader projects are split into `fabric/`, `forge/`, `neoforge/`, and `ignite/`; each has its own `build.gradle`, Gradle wrapper, metadata resources, and generated build output under `<loader>/build/`. Common version and dependency properties live in `common.properties`, then merge with each loader's `gradle.properties`.

## Build, Test, and Development Commands

Use the Gradle wrapper inside the loader you are working on:

```bash
cd fabric && ./gradlew build
cd forge && ./gradlew build
cd neoforge && ./gradlew build
cd ignite && ./gradlew build
```

These commands compile the shared sources with loader-specific packaging and write jars to `<loader>/build/libs/`. CI runs the same matrix on JDK 25 and adds `-Parchive_commit_suffix=<sha>` for non-release builds. For local client/server run tasks, use the relevant loader Gradle tasks when available, such as Forge or NeoForge run configurations.

## Coding Style & Naming Conventions

Source is Java 25 with UTF-8 encoding. Follow the existing style: tabs for indentation, braces on the same line, `PascalCase` classes, `camelCase` methods and fields, and lowercase package names under `com.akinokaede.mctunnel`. Keep transport-specific code in `transport/<protocol>/`, client-only code in `client/`, server integration in `server/`, and mixins in `mixin/`. Avoid loader-specific references in shared code unless the source set explicitly excludes them.

## Testing Guidelines

There is currently no dedicated `src/test` tree. Treat `./gradlew build` for each affected loader as the minimum verification step because packaging rules differ by loader. When adding tests, prefer standard Gradle test source sets and name test classes after the subject, for example `TunnelConfigTest`. For networking changes, include manual validation notes for the affected schemes (`ws://`, `wss://`, `httpupgrade://`, `grpc://`, `grpc+h2c://`, or `tls://`).

## Commit & Pull Request Guidelines

Recent commits use short, imperative subject lines such as `Add GitHub Actions build workflow` and `Update workflow actions to Node 24`. Keep subjects concise and describe the behavior or maintenance change. Pull requests should include a summary, affected loader(s), commands run, and any manual client/server tunnel checks. Link related issues when applicable and include screenshots only for user-visible Minecraft UI changes.

## Security & Configuration Tips

Configuration is driven by Java system properties documented in `README.md`, including `mctunnel.protocol`, `mctunnel.endpoint`, `mctunnel.disableVanillaTCP`, and `mctunnel.trustedProxies`. Be careful with trusted proxy handling and address parsing; changes here can affect client IP attribution, routing, and whether vanilla TCP remains accepted.
