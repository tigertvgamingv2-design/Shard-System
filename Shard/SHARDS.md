# Shards

## License setup

Shard verifies its license with your Licencing-Manager server before loading
shards or enabling the `/shard` command. The check runs asynchronously; Shard
disables itself if the license is invalid, the server cannot be reached, or the
response is invalid.

Set `license.key` in `plugins/Shard/config.yml` to the key for this server.
Optionally customize `messages.prefix` in that file. The license API URL and
product slug (`shard`) are built into the plugin. The license manager
normalizes product slugs to lowercase when products are created. Shard
generates a stable fingerprint from the server hostname for activation.

Restart the server after changing the key. Use a license key issued for the
`Shard` product.

Shard can load feature modules from JAR files in:

```text
plugins/Shard/shards/
```

Put each shard JAR directly in that folder. Shards are loaded in filename order
when Shard enables and are disabled in reverse order when Shard disables.

Set `messages.prefix` in `plugins/Shard/config.yml` to change the prefix used
by shard messages. The default is `&8[&bShard&8] &7`. Message text and the
prefix support Bukkit `&` color/format codes and hex colors written as
`&#RRGGBB`. Built-in shard messages use this automatically; custom modules can
use `ShardMessages.send(sender, message)` and `ShardMessages.broadcast(message)`
to apply the same formatting.

Each JAR must contain a `shard.yml` file at its root:

```yaml
name: ExampleShard
main: com.example.shard.ExampleShard
```

The public class named by `main` must have a public no-argument constructor
and implement `com.thronecraft.net.shards.ShardModule`. Compile the shard
against the Shard plugin JAR and the Spigot API; do not bundle either
dependency in the shard JAR.

### Make a custom shard

Start with the example in `examples/customer-shard-template/`. Copy its
`shard.yml` and Java source into a project of your own, then change the shard
name, main-class path, and feature code. A shard can listen to Bukkit events,
schedule work, and use the normal Bukkit API from its `onEnable` method.

The provided example welcomes players when they join. It also shows how to
send a message using the configurable prefix and color formatting. For custom
shards, unregister listeners and cancel scheduled tasks in `onDisable`.

Build with a JDK compatible with the Java version used by your server. Put the
Shard plugin JAR and a matching Spigot API JAR on the compile classpath. For
example, from the project directory on Windows:

```powershell
$classpath = "Shard-1.0.0-SNAPSHOT.jar;spigot-api.jar"
javac -encoding UTF-8 -cp $classpath -d build src\main\java\com\example\customergreeting\CustomerGreetingShard.java
jar --create --file CustomerGreeting.jar -C build . -C . shard.yml
```

Replace the JAR names and source path with the files on your machine. The
finished JAR must have `shard.yml` and the compiled main class at its root.
Do not include the Shard plugin or Spigot API JAR inside the custom shard.
Copy `CustomerGreeting.jar` directly into `plugins/Shard/shards/`, then run:

```text
/shard validate CustomerGreeting.jar
/shard load CustomerGreeting.jar
```

Use the shard `name` from `shard.yml` to disable or unload it:

```text
/shard disable CustomerGreeting
/shard unload CustomerGreeting
```

After replacing a shard JAR, unload it before overwriting the file, then
validate and load it again. `/shard reload` reloads the shards currently
present in the folder. Successful reloads broadcast a chat message naming each
reloaded shard.

## Web admin dashboard

Shard starts a login-protected admin dashboard on `127.0.0.1:8765` by
default. Set `dashboard.bind-address` in `plugins/Shard/config.yml` to an IPv4
or IPv6 address assigned to the server to listen on that interface. Use
`0.0.0.0` to listen on all IPv4 interfaces, including public interfaces.
Hostnames are not accepted. Change `dashboard.port` to any unused port from
`1` to `65535` if needed. Restart the plugin/server after changing either
setting. The dashboard shows live player locations and game modes, loaded
shards, planned restarts, economy activity, searchable recent server logs, and
runtime health details including Java heap and available disk space.

Dashboard staff roles use the server's permission manager:

| Permission | Access |
| --- | --- |
| `shard.dashboard.moderator` | View online players and kick, mute/unmute, or warn them |
| `shard.dashboard.admin` | Full dashboard access, including shard management, economy, restart controls, permission editing, safe settings, and the server console |

Admins inherit the Moderator permission. Admin defaults to operators; Moderator
defaults to nobody. Grant these nodes to staff with your permissions plugin.
For LuckPerms, for example, use
`/lp user <name> permission set shard.dashboard.moderator true` or
`/lp user <name> permission set shard.dashboard.admin true`.
Admins can also set persistent allow/deny overrides for registered permission
nodes on online players from the dashboard. These Shard-managed overrides are
saved in `plugins/Shard/config.yml` and applied again when the player joins.
Moderation actions require the matching `kick`, `mute`, `unmute`, and `warn`
shards to be loaded. Moderators cannot take action against dashboard admins.

Staff sign in by running `/shard dashboard link` in-game and entering the
one-time code on the dashboard login page. Codes expire after five minutes.
The code verifies the linked Minecraft account and its current dashboard
permissions; a dashboard session expires after 30 minutes of inactivity. The
owner password remains available for full access and recovery. Keep it private.

Admins can view the last 200 lines of `logs/latest.log` and run server commands
as the console. Console commands have full server privileges and are recorded
in the plugin log. Treat dashboard admin access like server operator access.
The Configuration page can update only the dashboard bind address, dashboard
port, and Shard message prefix. Bind address and port changes require a server
or plugin restart; the editor does not expose or modify the license key.
The complete economy transaction history is kept in
`plugins/Shard/economy-transactions.csv`.

The owner dashboard password is generated in
`plugins/Shard/dashboard.password`. Read it locally on the server; do not
publish it or put it in chat. Delete that file while the server is stopped to
generate a new password on next startup. Staff should use their one-time
in-game link code instead. Sign in at
`http://<server-ip>:<dashboard.port>` when the selected bind address is
reachable. For loopback-only access, use an SSH tunnel, VPN, or authenticated HTTPS
reverse proxy to reach the service remotely. For an SSH tunnel, run
`ssh -L <dashboard.port>:127.0.0.1:<dashboard.port> user@server` and open
`http://127.0.0.1:<dashboard.port>` locally.

The dashboard serves plain HTTP; the server operator is responsible for
firewall rules and protecting network traffic. For internet access, we strongly
recommend an HTTPS reverse proxy or VPN rather than exposing the dashboard
port directly. If using a reverse proxy, terminate HTTPS there, preserve the
browser's `Host` header, and set `X-Forwarded-Proto: https`.

Restart schedules use the Minecraft server machine's timezone and survive a
normal plugin/server restart. At the scheduled time, Shard announces a
countdown and requests a graceful server shutdown. Configure your server host,
container, or process supervisor to start the server again; Bukkit plugins
cannot restart the stopped JVM by themselves. Expired schedules are discarded
on startup rather than triggering an unexpected immediate shutdown.

The economy audit records transactions made by the included `/pay`, `/eco`,
`/sellhand`, and `/sellall` shards. Custom economy modules must call
`EconomyTransactionLogger.recordOrReport(...)` after successfully saving a
balance change if they should also be included.

```java
package com.example.shard;

import com.thronecraft.net.shards.ShardModule;
import org.bukkit.plugin.java.JavaPlugin;

public class ExampleShard implements ShardModule {
    @Override
    public void onEnable(JavaPlugin plugin) {
        plugin.getLogger().info("ExampleShard enabled");
    }

    @Override
    public void onDisable() {
    }
}
```

Each shard has its own class loader and can use classes from its JAR and the
server/plugin class path. A shard cannot depend on classes private to another
shard.

The ten example shards also expose individual commands. Commands are declared
by the main Shard plugin and become active when their matching shard is
loaded. Running a command without arguments executes or previews that shard's
feature. Configuration subcommands require `shard.admin` (operators by
default):

| Command | What it controls |
| --- | --- |
| `/welcome` | Join welcome message |
| `/joinbroadcast` | Join announcement |
| `/quitbroadcast` | Quit announcement |
| `/firstjoin` | First-visit greeting |
| `/joincoordinates` | Join location message |
| `/worldtime` | Join world-time message |
| `/deathprefix` | Player death message format |
| `/sleepnotice` | Bed/sleep announcement |
| `/nocreepergrief` | Creeper block damage protection |
| `/nophantoms` | Phantom spawn protection |

For message shards, use `/command set <message>` to change a format,
`/command reset` to restore its default, `/command preview` to preview it, and
`/command status` to check its state. `/command enable` and `/command disable`
toggle that shard's behavior; `/command reload` reloads its settings file.
The two protection shards use `status`, `enable`, `disable`, and `reload`.

Settings are saved separately in `plugins/Shard/shard-settings/`. Messages
support Bukkit `&` color/format codes and hex colors written as `&#RRGGBB`.
Available placeholders are `{player}`, `{x}`, `{y}`, `{z}`, `{world}`,
`{time}`, and `{death_message}`; each message uses the placeholders relevant
to that shard.

When a shard is disabled or unloaded individually, its `onDisable()` method
must unregister any listeners and cancel any tasks that shard created.

## Commands

All management commands require the `shard.admin` permission, which defaults
to server operators. `/shards` is an alias for `/shard`.

| Command | Description |
| --- | --- |
| `/shard help` | Show available commands |
| `/shard list` | List loaded shards and their states |
| `/shard info <shard>` | Show a shard's JAR filename and state |
| `/shard status` | Show loaded, enabled, and disabled counts |
| `/shard folder` | Show the folder where shard JARs belong |
| `/shard validate <file.jar>` | Check the descriptor and main class without loading it |
| `/shard load <file.jar>` | Load a JAR from the shards folder |
| `/shard unload <shard>` | Disable and unload a shard (the JAR remains on disk) |
| `/shard enable <shard>` | Enable a loaded, disabled shard |
| `/shard disable <shard>` | Disable a loaded shard without unloading it |
| `/shard reload [shard\|all]` | Reload one shard, all shards, or all by default |
| `/shard reloadplugin` | Disable and re-enable the Shard plugin |

Use the JAR filename for `load` and `validate`, and the `name` from
`shard.yml` for commands that operate on an already loaded shard.
Prefer `/shard reload` for routine changes; `/shard reloadplugin` re-runs this
plugin's Bukkit enable/disable lifecycle and is not a replacement for a
server-wide reload.

## Example shards

Ten example shard projects are in `examples/shards/`. Their built JARs are in
`target/example-shards/`. Copy the JARs you want into
`plugins/Shard/shards/`, then run `/shard reload` or restart the server.

| Shard | What it does |
| --- | --- |
| `Welcome.jar` | Privately welcomes a player when they join |
| `JoinBroadcast.jar` | Sets a short join announcement |
| `QuitBroadcast.jar` | Sets a short quit announcement |
| `FirstJoin.jar` | Welcomes players on their first visit |
| `JoinCoordinates.jar` | Shows a player their coordinates when joining |
| `WorldTime.jar` | Shows the player's world time when joining |
| `DeathPrefix.jar` | Adds a prefix to death messages |
| `NoCreeperGrief.jar` | Prevents creeper explosions from damaging blocks |
| `NoPhantoms.jar` | Prevents phantoms from spawning |
| `SleepNotice.jar` | Announces when a player enters a bed to sleep |

## Advanced example shards

Four advanced examples are in `examples/advanced-shards/`. Their built JARs
are in `target/advanced-shards/`. Copy the JARs you want into
`plugins/Shard/shards/`, then run `/shard reload` or restart the server.

| Shard | Commands | Features |
| --- | --- | --- |
| `Homes.jar` | `/sethome [name] [public|private] [icon]`, `/home [player] <name>`, `/homes [player]`, `/delhome <name>` | Persistent homes with private-by-default visibility, public-home browsing, icon menus, and 5/10/25/unlimited permission tiers |
| `Warps.jar` | `/warp <name>`, `/warps [category]`, `/setwarp <name> [category] [icon]`, `/delwarp <name>` | Persistent categorized warps with icon menus and pagination; setting and deleting requires `shard.warps.admin` |

`/homes` and `/warps` open clickable, paginated menus for players. Homes are
private unless explicitly published with `/sethome <name> public`; public
homes can be browsed with `/homes <player>` or visited with
`/home <player> <name>`. Home limits default to five, with
`shard.homes.limit.10`, `shard.homes.limit.25`, and `shard.homes.unlimited`
providing higher tiers. Warps can be grouped with a category and displayed
using an item material, for example `/setwarp market shops EMERALD` and
`/warps shops`.
| `TeleportRequests.jar` | `/tpa <player>`, `/tpahere <player>`, `/tpaccept [player]`, `/tpdeny [player]`, `/tpcancel` | Requests expire after 60 seconds; one pending request per target |
| `SpawnBack.jar` | `/spawn`, `/setspawn`, `/back` | Persistent server spawn and per-player previous/death location; setting spawn requires `shard.spawn.admin` |

Advanced shard data is saved in `plugins/Shard/advanced/`. Teleports use the
standard Bukkit teleport API. The advanced JARs depend only on
`ShardModule`, Bukkit/Paper APIs, and their bundled support class; they do not
depend on `ShardSettings`.

## Creative example shards

Creative gameplay experiments are in `examples/creative-shards/`. Their JARs
are built to `target/creative-shards/`. Copy the JARs you want into
`plugins/Shard/shards/`, then run `/shard reload` or restart the server.

| Shard | Command | What it does |
| --- | --- | --- |
| `BiomeAtlas.jar` | `/atlas` or `/atlas clear` | Records each newly visited biome and builds a personal discovery atlas |
| `MoonJournal.jar` | `/moonjournal` | Right-click a spyglass under a clear night sky to record each moon phase per world |
| `TrailEcho.jar` | `/trail start`, `/trail stop`, `/trail show`, `/trail clear` | Records a short in-memory walking route and replays it as temporary particles |
| `LivingWorld.jar` | `/worldpulse`, `/worldpulse top` | Shared biome exploration advances world ranks and rewards everyone online in that world at each milestone |

Atlas, moon-journal, and world-pulse data are stored in `plugins/Shard/creative/`.
Trail Echo keeps at most 32 points per active player, sampled after each
eight-block walk; trails are intentionally temporary and clear when the player
leaves or the shard is unloaded.

World Pulse only counts the first discovery of each biome by each player.
Community progress counts each distinct biome once per world, so revisiting a
biome does not farm progress. Milestones are at 3, 7, 12, 18, 26, 36, and 48
known biomes; each grants a small XP reward to players currently in that world.

## Essentials-style shards

The standalone Essentials-style pack contains 50 independent shards inspired
by the selected EssentialsX feature areas. It does not require EssentialsX or
Vault. With JDK 25 and Maven available, build it from the `Shard` project
directory with:

```powershell
.\build-essentials-shards.ps1
```

The script builds one JAR per shard into `target/essentials-shards/`. Copy the
JARs you want into `plugins/Shard/shards/`, then run `/shard reload`.
If Maven or Java are not on `PATH`, pass their installation paths with
`-MavenExecutable` and `-JavaHome`.

| Area | Shards and commands |
| --- | --- |
| Economy | `balance`, `pay`, `eco`, `baltop`, `worth`, `sellhand`, `sellall`, `paytoggle` |
| Items | `kit`, `createkit`, `delkit`, `kits`, `give`, `item`, `more`, `hat`, `skull` |
| Moderation | `kick`, `ban`, `unban`, `mute`, `unmute`, `warn`, `warnings`, `invsee` |
| Utility | `heal`, `feed`, `repair`, `fly`, `speed`, `time`, `weather`, `workbench` |
| Chat | `msg`, `reply`, `ignore`, `unignore`, `mail`, `mailread`, `broadcast`, `nick` |
| Protection | `vanish`, `god`, `commandblock`, `antibuild`, `jail`, `unjail`, `tempban`, `tempmute`, `sudo`, `antispam` |

Chat features include private-message replies and ignore lists (`/ignore list`),
paginated offline mail (`/mailread [page|clear]`), operator-only broadcasts,
and saved nicknames. Nickname colors and changing another player's nickname
require `shard.essentials.nick.color` and `shard.essentials.nick.others`,
respectively.

These features intentionally do not add another teleport/home/warp system; use
the existing advanced teleport shards for those. Each Essentials-style command
uses its `shard.essentials.<command>` permission; player chat commands are
available by default, while administrative commands default to operators.
Economy balances are Shard's own persisted balances and are not shared with a
Vault economy provider. Avoid installing another plugin that registers the
same commands unless you have configured command conflicts appropriately.

The advanced teleport-request shard provides `/tpa`, `/tpahere`, `/tpaccept`,
`/tpdeny` (also `/tpadeny`), and `/tpcancel` (also `/tpacancel`). Requests
expire after one minute, have a short anti-spam cooldown, and a player can have
one outgoing request at a time. Shard message formatting gives usage, error,
warning, and success responses distinct colors; explicit `&` and hex formatting
in a message is preserved.
