# Shard

**Shard is a modular Minecraft server plugin for building a server from
independent, loadable features.** Manage modules in-game, add your own Java
shards, and optionally administer your server through a web dashboard.

> **License required:** Shard verifies a valid license when it starts. Set the
> license key issued for the Shard product in `plugins/Shard/config.yml`. The
> plugin disables itself if verification fails or the licensing service cannot
> be reached.

## What it includes

- **A modular loader** for shard JARs, with validation and commands to load,
  enable, disable, reload, and unload modules without replacing the main plugin.
- **Built-in server features** for join and quit messages, first joins,
  coordinates, world time, death messages, sleep notices, and protection
  toggles.
- **Example feature packs** for homes, warps, spawn and teleport requests,
  creative-world activities, and 50 standalone Essentials-style commands.
- **A browser dashboard** for monitoring players and server health, managing
  shards, and handling supported moderation and administration tasks.
- **Custom shard support** using a small Java module interface and a
  `shard.yml` descriptor.

See the [shard guide](Shard/SHARDS.md) for the complete feature list, dashboard
setup, permissions, commands, and custom-shard examples.

## Requirements

- A Minecraft server compatible with the Bukkit/Spigot API version used by
  this build.
- A valid Shard license key and network access to the license verification
  service at startup.
- For building from source: JDK 25 and Maven.

## Install

1. Download the Shard plugin JAR from a release or build it from source.
2. Copy `Shard-1.0.0-SNAPSHOT.jar` into your server's `plugins/` directory.
3. Start the server once to create `plugins/Shard/config.yml`.
4. Set `license.key` in that file to your Shard license key.
5. Restart the server. Shard creates `plugins/Shard/shards/` for module JARs.

The default configuration also sets the message prefix and dashboard address
and port. The dashboard listens on `127.0.0.1:8765` by default; review the
[dashboard security guidance](Shard/SHARDS.md#web-admin-dashboard) before
making it reachable from other machines.

## Build from source

From the `Shard/` project directory, run:

```shell
mvn package
```

The plugin JAR is written to `Shard/target/Shard-1.0.0-SNAPSHOT.jar`.

To build the 50 Essentials-style modules on Windows, run
`.\build-essentials-shards.ps1` from `Shard/`. The resulting JARs are written to
`Shard/target/essentials-shards/`. Copy the modules you want into
`plugins/Shard/shards/` and run `/shard reload`.

## Manage shards

Put each shard JAR directly in `plugins/Shard/shards/`. Shard loads modules in
filename order at startup. Use the `name` from a shard's `shard.yml` for
management commands:

| Command | Description |
| --- | --- |
| `/shard list` | List loaded shards and their states |
| `/shard info <shard>` | Show a shard's file and state |
| `/shard validate <file.jar>` | Check a JAR before loading it |
| `/shard load <file.jar>` | Load a JAR from the shards folder |
| `/shard enable <shard>` / `/shard disable <shard>` | Change a loaded shard's state |
| `/shard reload [shard\|all]` | Reload one shard or all shards |
| `/shard unload <shard>` | Disable and unload a shard |

These management commands require `shard.admin`, which defaults to server
operators. `/shards` is an alias for `/shard`.

## Create a custom shard

Start with [`Shard/examples/customer-shard-template/`](Shard/examples/customer-shard-template/).
Each shard JAR contains a root-level `shard.yml` naming its public Java class.
That class must implement `ShardModule` and provide a public no-argument
constructor. Compile against the Shard plugin and Bukkit/Spigot API; do not
bundle either dependency inside the shard JAR.

Validate and load a module with:

```text
/shard validate CustomerGreeting.jar
/shard load CustomerGreeting.jar
```

See [SHARDS.md](Shard/SHARDS.md#make-a-custom-shard) for a complete build
example and lifecycle guidance.

## Dashboard

Staff can sign in using a one-time code from `/shard dashboard link`.
Dashboard access is permission-based:

- `shard.dashboard.moderator` grants player monitoring and supported moderation
  actions.
- `shard.dashboard.admin` grants full dashboard administration and inherits
  moderator access.

The owner password is stored locally in
`plugins/Shard/dashboard.password`. Keep it private. The dashboard uses plain
HTTP; for remote access, use a VPN, SSH tunnel, or HTTPS reverse proxy rather
than exposing the dashboard port directly. Read the
[full dashboard guidance](Shard/SHARDS.md#web-admin-dashboard) before changing
the bind address.

## Repository layout

- `Shard/src/` - plugin source code and dashboard assets
- `Shard/examples/` - sample shard modules and feature packs
- `Shard/SHARDS.md` - detailed user and developer documentation
- `Shard/build-essentials-shards.ps1` - Windows build script for the Essentials-style pack

## License

Shard requires a valid product license to run. This repository does not include
a license key; obtain one from the Shard publisher and keep it out of source
control.
