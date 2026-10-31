package com.thronecraft.net.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thronecraft.net.managers.EconomyTransactionLogger;
import com.thronecraft.net.managers.RestartPlanManager;
import com.thronecraft.net.managers.ShardManager;
import com.thronecraft.net.managers.ShardManager.ShardInfo;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.ZoneId;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public final class DashboardServer implements Listener {
    private static final int DEFAULT_PORT = 8765;
    private static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    private static final long SESSION_LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long LOGIN_CODE_LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_REQUEST_BYTES = 8192;
    private static final int MAX_CONSOLE_COMMAND_LENGTH = 256;
    private static final int MAX_LOG_BYTES = 262144;
    private static final int MAX_LOG_LINES = 200;
    private static final Pattern JSON_STRING = Pattern.compile(
            "\"([^\"\\\\]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final JavaPlugin plugin;
    private final ShardManager shardManager;
    private final RestartPlanManager restartManager;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, LoginCode> loginCodes = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionAttachments = new HashMap<>();
    private HttpServer server;
    private ExecutorService executor;
    private byte[] dashboardPassword;
    private long startedAt;

    public DashboardServer(
            JavaPlugin plugin,
            ShardManager shardManager,
            RestartPlanManager restartManager
    ) {
        this.plugin = plugin;
        this.shardManager = shardManager;
        this.restartManager = restartManager;
    }

    public void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("The dashboard server is already running.");
        }
        int port = configuredPort();
        InetAddress bindAddress = configuredBindAddress();
        dashboardPassword = loadOrCreatePassword();
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "Shard-Dashboard");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        startedAt = System.currentTimeMillis();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPermissionOverrides(player);
        }
        String bindHost = bindAddress instanceof Inet6Address
                ? "[" + bindAddress.getHostAddress() + "]" : bindAddress.getHostAddress();
        plugin.getLogger().info("Admin dashboard listening on http://" + bindHost + ":" + port
                + ". Password file: "
                + plugin.getDataFolder().toPath().resolve("dashboard.password"));
        plugin.getLogger().warning("The dashboard uses plain HTTP. Protect public access with your own "
                + "firewall and HTTPS reverse proxy.");
    }

    private InetAddress configuredBindAddress() throws IOException {
        String configuredValue = plugin.getConfig().getString(
                "dashboard.bind-address", DEFAULT_BIND_ADDRESS);
        if (configuredValue == null || configuredValue.isBlank()) {
            throw new IllegalArgumentException(
                    "dashboard.bind-address must be a literal IPv4 or IPv6 address.");
        }

        InetAddress address = parseAddress(configuredValue.trim());
        if (!address.isAnyLocalAddress() && NetworkInterface.getByInetAddress(address) == null) {
            throw new IllegalArgumentException(
                    "dashboard.bind-address is not assigned to a network interface on this server.");
        }
        return address;
    }

    private InetAddress parseAddress(String value) throws IOException {
        if (value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
            String[] octets = value.split("\\.");
            byte[] address = new byte[4];
            for (int index = 0; index < octets.length; index++) {
                int octet;
                try {
                    octet = Integer.parseInt(octets[index]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid dashboard.bind-address.", exception);
                }
                if (octet > 255) {
                    throw new IllegalArgumentException("Invalid dashboard.bind-address IPv4 octet.");
                }
                address[index] = (byte) octet;
            }
            return InetAddress.getByAddress(address);
        }
        if (value.contains(":") && value.matches("[0-9a-fA-F:]+")) {
            InetAddress address = InetAddress.getByName(value);
            if (address instanceof Inet6Address) {
                return address;
            }
        }
        throw new IllegalArgumentException(
                "dashboard.bind-address must be a literal IPv4 or IPv6 address (not a hostname).");
    }

    private int configuredPort() {
        Object configuredValue = plugin.getConfig().get("dashboard.port");
        if (configuredValue == null) {
            return DEFAULT_PORT;
        }
        if (!(configuredValue instanceof Number number)
                || number.doubleValue() != number.intValue()
                || number.intValue() < 1
                || number.intValue() > 65535) {
            throw new IllegalArgumentException(
                    "dashboard.port must be a whole number between 1 and 65535.");
        }
        return number.intValue();
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            PermissionAttachment attachment = permissionAttachments.remove(player.getUniqueId());
            if (attachment != null) {
                attachment.remove();
            }
        }
        permissionAttachments.clear();
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        sessions.clear();
        loginCodes.clear();
        loginAttempts.clear();
        dashboardPassword = null;
    }

    public String issueLoginCode(Player player) {
        Role role = player.hasPermission("shard.dashboard.admin")
                ? Role.ADMIN : player.hasPermission("shard.dashboard.moderator")
                ? Role.MODERATOR : null;
        if (role == null) {
            throw new IllegalArgumentException("You do not have dashboard staff permission.");
        }
        long now = System.currentTimeMillis();
        loginCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        if (loginCodes.size() >= 128) {
            throw new IllegalStateException("Too many active dashboard link codes. Try again shortly.");
        }
        String code = randomToken().substring(0, 12);
        loginCodes.put(code, new LoginCode(player.getUniqueId(), player.getName(),
                now + LOGIN_CODE_LIFETIME_MILLIS));
        return code;
    }

    private void handle(HttpExchange exchange) {
        try {
            if ("GET".equals(exchange.getRequestMethod())
                    && "/".equals(exchange.getRequestURI().getPath())) {
                serveDashboard(exchange);
                return;
            }
            if (!exchange.getRequestURI().getPath().startsWith("/api/")) {
                sendError(exchange, 404, "Not found.");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && !hasSameOrigin(exchange)) {
                sendError(exchange, 403, "Request origin is not allowed.");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())
                    && !isJsonRequest(exchange.getRequestHeaders())) {
                sendError(exchange, 415, "Send application/json.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && "/api/login".equals(path)) {
                login(exchange);
                return;
            }
            if (!authenticate(exchange)) {
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && "/api/logout".equals(path)) {
                logout(exchange);
                return;
            }

            refreshSessionCookie(exchange);
            routeApi(exchange, path);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 503, exception.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Dashboard request failed.", exception);
            sendError(exchange, 500, "Request failed. Check the server log for details.");
        } finally {
            exchange.close();
        }
    }

    private void serveDashboard(HttpExchange exchange) throws IOException {
        try (InputStream input = DashboardServer.class.getResourceAsStream("/dashboard/index.html")) {
            if (input == null) {
                throw new IOException("Missing dashboard page resource.");
            }
            byte[] content = input.readAllBytes();
            Headers headers = exchange.getResponseHeaders();
            setSecurityHeaders(headers);
            headers.set("Content-Type", "text/html; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
        }
    }

    private void login(HttpExchange exchange) throws Exception {
        String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
        LoginAttempts attempts = loginAttempts.compute(remoteAddress, (ignored, current) -> {
            if (current == null || System.currentTimeMillis() - current.windowStartedAt
                    >= TimeUnit.MINUTES.toMillis(1)) {
                return new LoginAttempts(System.currentTimeMillis(), 0);
            }
            return current;
        });
        synchronized (attempts) {
            if (attempts.count >= 5) {
                sendError(exchange, 429, "Too many login attempts. Wait one minute and try again.");
                return;
            }
            attempts.count++;
        }

        String body = readBody(exchange);
        String providedPassword = optionalJsonString(body, "password");
        Session session;
        if (providedPassword != null) {
            if (!MessageDigest.isEqual(
                    dashboardPassword, providedPassword.getBytes(StandardCharsets.UTF_8))) {
                sendError(exchange, 401, "Incorrect dashboard password.");
                return;
            }
            session = new Session(System.currentTimeMillis(), null, "Server owner", Role.ADMIN, true);
        } else {
            String code = optionalJsonString(body, "linkCode");
            LoginCode loginCode = code == null ? null : loginCodes.remove(code);
            if (loginCode == null || loginCode.expiresAt < System.currentTimeMillis()) {
                sendError(exchange, 401, "That staff link code is invalid or has expired.");
                return;
            }
            Role role = callOnServer(() -> roleFor(Bukkit.getPlayer(loginCode.playerId)));
            if (role == null) {
                sendError(exchange, 401, "That staff member is no longer online or no longer has dashboard permission.");
                return;
            }
            session = new Session(System.currentTimeMillis(), loginCode.playerId,
                    loginCode.playerName, role, false);
        }
        loginAttempts.remove(remoteAddress);
        String sessionId = randomToken();
        synchronized (sessions) {
            pruneExpiredSessions();
            if (sessions.size() >= 32) {
                sendError(exchange, 429, "Too many active dashboard sessions.");
                return;
            }
            sessions.put(sessionId, session);
        }
        setSessionCookie(exchange, sessionId);
        sendJson(exchange, 200, "{\"authenticated\":true,\"username\":"
                + json(session.username) + ",\"role\":" + json(session.role.label) + "}");
    }

    private boolean authenticate(HttpExchange exchange) throws Exception {
        String sessionId = readCookie(exchange.getRequestHeaders(), "ShardDashboardSession");
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null
                || System.currentTimeMillis() - session.lastActivityAt > SESSION_LIFETIME_MILLIS) {
            if (sessionId != null) {
                sessions.remove(sessionId);
            }
            sendError(exchange, 401, "Log in to use the dashboard.");
            return false;
        }
        if (!session.owner) {
            Role role = callOnServer(() -> {
                Player linkedPlayer = Bukkit.getPlayer(session.playerId);
                if (linkedPlayer == null) {
                    return session.role;
                }
                session.username = linkedPlayer.getName();
                return roleFor(linkedPlayer);
            });
            if (role == null) {
                sessions.remove(sessionId);
                sendError(exchange, 401, "Dashboard access was removed. Sign in again when eligible.");
                return false;
            }
            session.role = role;
        }
        session.lastActivityAt = System.currentTimeMillis();
        return true;
    }

    private void logout(HttpExchange exchange) throws IOException {
        String sessionId = readCookie(exchange.getRequestHeaders(), "ShardDashboardSession");
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
        exchange.getResponseHeaders().add("Set-Cookie",
                "ShardDashboardSession=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
        sendJson(exchange, 200, "{\"authenticated\":false}");
    }

    private void refreshSessionCookie(HttpExchange exchange) {
        String sessionId = readCookie(exchange.getRequestHeaders(), "ShardDashboardSession");
        if (sessionId != null) {
            setSessionCookie(exchange, sessionId);
        }
    }

    private void routeApi(HttpExchange exchange, String path) throws Exception {
        if ("GET".equals(exchange.getRequestMethod()) && "/api/session".equals(path)) {
            Session session = sessionFor(exchange);
            sendJson(exchange, 200, "{\"username\":" + json(session.username)
                    + ",\"role\":" + json(session.role.label) + "}");
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/status".equals(path)) {
            sendJson(exchange, 200, callOnServer(this::serverStatusJson));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/players".equals(path)) {
            if (!requireRole(exchange, Role.MODERATOR)) {
                return;
            }
            sendJson(exchange, 200, callOnServer(this::playersJson));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/health".equals(path)) {
            if (!requireRole(exchange, Role.ADMIN)) {
                return;
            }
            sendJson(exchange, 200, callOnServer(this::healthJson));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/moderation".equals(path)) {
            if (!requireRole(exchange, Role.MODERATOR)) {
                return;
            }
            moderate(exchange, readBody(exchange));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/console".equals(path)) {
            if (!requireRole(exchange, Role.ADMIN)) {
                return;
            }
            String log;
            try {
                log = consoleJson();
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read the server log for the dashboard.", exception);
                sendError(exchange, 503, exception.getMessage());
                return;
            }
            sendJson(exchange, 200, log);
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/console/command".equals(path)) {
            if (!requireRole(exchange, Role.ADMIN)) {
                return;
            }
            executeConsoleCommand(exchange, readBody(exchange));
            return;
        }
        if (!requireRole(exchange, Role.ADMIN)) {
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/permissions".equals(path)) {
            sendJson(exchange, 200, callOnServer(this::permissionsJson));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/permissions".equals(path)) {
            updatePermission(exchange, readBody(exchange));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/config".equals(path)) {
            sendJson(exchange, 200, callOnServer(this::configJson));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/config".equals(path)) {
            updateConfig(exchange, readBody(exchange));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/shards".equals(path)) {
            sendJson(exchange, 200, callOnServer(this::shardsJson));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/worlds".equals(path)) {
            sendJson(exchange, 200, callOnServer(this::worldsJson));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/worlds".equals(path)) {
            worldAction(exchange, readBody(exchange));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/player-tools".equals(path)) {
            playerToolAction(exchange, readBody(exchange));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/economy".equals(path)) {
            List<EconomyTransactionLogger.Transaction> transactions =
                    EconomyTransactionLogger.readRecent(plugin, 200);
            sendJson(exchange, 200, transactionsJson(transactions));
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/restart".equals(path)) {
            sendJson(exchange, 200, callOnServer(() ->
                    "{\"restartAt\":" + restartManager.getPlannedAt()
                            + ",\"serverNow\":" + System.currentTimeMillis()
                            + ",\"serverTimeZone\":" + json(ZoneId.systemDefault().getId()) + "}"));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/restart".equals(path)) {
            String body = readBody(exchange);
            String delayMinutesValue = optionalJsonString(body, "delayMinutes");
            long restartAt;
            if (delayMinutesValue != null) {
                long delayMinutes;
                try {
                    delayMinutes = Long.parseLong(delayMinutesValue);
                    if (delayMinutes <= 0) {
                        throw new NumberFormatException("Restart delay must be positive.");
                    }
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Choose a valid positive restart delay in minutes.",
                            exception);
                }
                long delayMillis;
                try {
                    delayMillis = Math.multiplyExact(delayMinutes, 60_000L);
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Restart delay is too large.", exception);
                }
                restartAt = callOnServer(() -> {
                    long plannedAt = Math.addExact(System.currentTimeMillis(), delayMillis);
                    restartManager.schedule(plannedAt);
                    return plannedAt;
                });
            } else {
                String restartTime = jsonString(body, "restartAt");
                try {
                    restartAt = Instant.parse(restartTime).toEpochMilli();
                } catch (java.time.format.DateTimeParseException exception) {
                    throw new IllegalArgumentException("Choose a valid restart date and time.", exception);
                }
                long plannedAt = restartAt;
                callOnServer(() -> {
                    restartManager.schedule(plannedAt);
                    return null;
                });
            }
            plugin.getLogger().info("A planned restart was scheduled for "
                    + Instant.ofEpochMilli(restartAt).atZone(ZoneId.systemDefault())
                    + " in server timezone " + ZoneId.systemDefault() + ".");
            sendJson(exchange, 200, "{\"scheduled\":true}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/restart/cancel".equals(path)) {
            callOnServer(() -> {
                restartManager.cancel();
                return null;
            });
            plugin.getLogger().info("The planned restart was cancelled.");
            sendJson(exchange, 200, "{\"cancelled\":true}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && "/api/shards".equals(path)) {
            shardAction(exchange, readBody(exchange));
            return;
        }
        sendError(exchange, 404, "Not found.");
    }

    private boolean requireRole(HttpExchange exchange, Role requiredRole) throws IOException {
        String sessionId = readCookie(exchange.getRequestHeaders(), "ShardDashboardSession");
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || session.role == null
                || session.role.ordinal() < requiredRole.ordinal()) {
            sendError(exchange, 403, "Your dashboard role does not allow this action.");
            return false;
        }
        return true;
    }

    private void moderate(HttpExchange exchange, String body) throws Exception {
        String action = jsonString(body, "action").toLowerCase(java.util.Locale.ROOT);
        String targetName = jsonString(body, "target");
        String reason = jsonString(body, "reason").trim().replaceAll("\\s+", " ");
        if (!targetName.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Choose a valid Minecraft player name.");
        }
        if (reason.isEmpty() || reason.length() > 160 || reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Enter a reason between 1 and 160 characters.");
        }
        if (!List.of("kick", "mute", "unmute", "warn").contains(action)) {
            throw new IllegalArgumentException("Unsupported moderation action.");
        }

        Session session = sessionFor(exchange);
        callOnServer(() -> {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                throw new IllegalArgumentException("That player is no longer online.");
            }
            if (!session.owner && session.role == Role.MODERATOR
                    && (target.isOp() || target.hasPermission("shard.dashboard.admin"))) {
                throw new IllegalArgumentException("Moderators cannot take action against an admin.");
            }
            if ("unmute".equals(action)) {
                dispatchShardCommand("unmute", target.getName());
            } else {
                dispatchShardCommand(action, target.getName() + " " + reason);
            }
            return null;
        });
        plugin.getLogger().info("Dashboard moderation by " + session.username + ": "
                + action + " " + targetName + " (" + reason + ").");
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private void dispatchShardCommand(String commandName, String arguments) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null || command.getExecutor() == plugin) {
            throw new IllegalStateException("The " + commandName + " shard command is not loaded.");
        }
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandName + " " + arguments)) {
            throw new IllegalStateException("The " + commandName + " command did not complete.");
        }
    }

    private void executeConsoleCommand(HttpExchange exchange, String body) throws Exception {
        String command = jsonString(body, "command").trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty() || command.startsWith("/")
                || command.length() > MAX_CONSOLE_COMMAND_LENGTH
                || command.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Enter one command of up to "
                    + MAX_CONSOLE_COMMAND_LENGTH + " characters.");
        }
        Session session = sessionFor(exchange);
        String commandToRun = command;
        plugin.getLogger().info("Dashboard console command requested by "
                + session.username + ": /" + command);
        boolean dispatched = callOnServer(() ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun));
        if (!dispatched) {
            throw new IllegalArgumentException("The server did not recognize that command.");
        }
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private String consoleJson() throws IOException {
        Path serverDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize()
                .getParent().getParent();
        Path logFile = serverDirectory.resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(logFile)) {
            throw new IOException("The server log file was not found at " + logFile + ".");
        }
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile reader = new RandomAccessFile(logFile.toFile(), "r")) {
            long start = Math.max(0, reader.length() - MAX_LOG_BYTES);
            reader.seek(start);
            if (start > 0) {
                reader.readLine();
            }
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
                if (lines.size() > MAX_LOG_LINES) {
                    lines.remove(0);
                }
            }
        }
        return "{\"lines\":" + stringArray(lines) + ",\"updatedAt\":"
                + System.currentTimeMillis() + "}";
    }

    private void shardAction(HttpExchange exchange, String body) throws Exception {
        String action = jsonString(body, "action");
        String name = jsonString(body, "name");
        callOnServer(() -> {
            switch (action) {
                case "load" -> {
                    shardManager.validateShard(name);
                    shardManager.loadShard(name);
                }
                case "enable" -> shardManager.enableShard(name);
                case "disable" -> shardManager.disableShard(name);
                case "reload" -> {
                    if ("all".equalsIgnoreCase(name)) {
                        shardManager.reloadAllShards();
                    } else {
                        shardManager.reloadShard(name);
                    }
                }
                case "unload" -> shardManager.unloadShard(name);
                default -> throw new IllegalArgumentException("Unsupported shard action.");
            }
            return null;
        });
        plugin.getLogger().info("Dashboard performed shard action '" + action + "' for '" + name + "'.");
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private String serverStatusJson() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<String> playerNames = new ArrayList<>();
        for (Player player : players) {
            if (player != null) {
                playerNames.add(player.getName());
            }
        }
        long loaded = shardManager.getShards().size();
        long enabled = enabledShardCount();
        Runtime runtime = Runtime.getRuntime();
        return "{\"serverVersion\":" + json(Bukkit.getVersion())
                + ",\"onlinePlayers\":" + playerNames.size()
                + ",\"maxPlayers\":" + Bukkit.getMaxPlayers()
                + ",\"players\":" + stringArray(playerNames)
                + ",\"uptimeSeconds\":" + Math.max(0, (System.currentTimeMillis() - startedAt) / 1000)
                + ",\"memoryUsedBytes\":" + (runtime.totalMemory() - runtime.freeMemory())
                + ",\"memoryMaxBytes\":" + runtime.maxMemory()
                + ",\"loadedShards\":" + loaded
                + ",\"enabledShards\":" + enabled + "}";
    }

    private long enabledShardCount() {
        long enabled = 0;
        for (ShardInfo shard : shardManager.getShards()) {
            if (shard != null && shard.enabled()) {
                enabled++;
            }
        }
        return enabled;
    }

    private String healthJson() throws IOException {
        Runtime runtime = Runtime.getRuntime();
        Path serverDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize()
                .getParent().getParent();
        return "{\"javaVersion\":" + json(System.getProperty("java.version"))
                + ",\"operatingSystem\":" + json(System.getProperty("os.name"))
                + ",\"availableProcessors\":" + runtime.availableProcessors()
                + ",\"memoryUsedBytes\":" + (runtime.totalMemory() - runtime.freeMemory())
                + ",\"memoryAllocatedBytes\":" + runtime.totalMemory()
                + ",\"memoryMaxBytes\":" + runtime.maxMemory()
                + ",\"freeDiskBytes\":" + Files.getFileStore(serverDirectory).getUsableSpace()
                + ",\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size()
                + ",\"worldCount\":" + Bukkit.getWorlds().size()
                + ",\"loadedShards\":" + shardManager.getShards().size()
                + ",\"enabledShards\":" + enabledShardCount() + "}";
    }

    private String playersJson() {
        YamlConfiguration moderation = YamlConfiguration.loadConfiguration(
                plugin.getDataFolder().toPath().resolve("moderation.yml").toFile());
        StringBuilder json = new StringBuilder("{\"players\":[");
        int index = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (index++ > 0) {
                json.append(',');
            }
            Role role = roleFor(player);
            json.append("{\"uuid\":").append(json(player.getUniqueId().toString()))
                    .append(",\"name\":").append(json(player.getName()))
                    .append(",\"role\":").append(json(role == null ? "player" : role.label))
                    .append(",\"muted\":").append(moderation.contains(
                            "muted." + player.getUniqueId()))
                    .append(",\"world\":").append(json(player.getWorld().getName()))
                    .append(",\"gameMode\":").append(json(player.getGameMode().name()))
                    .append(",\"health\":").append(player.getHealth())
                    .append(",\"x\":").append(player.getLocation().getBlockX())
                    .append(",\"y\":").append(player.getLocation().getBlockY())
                    .append(",\"z\":").append(player.getLocation().getBlockZ()).append('}');
        }
        return json.append("]}").toString();
    }

    private String permissionsJson() {
        TreeSet<String> nodes = new TreeSet<>();
        for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
            nodes.add(permission.getName());
        }
        StringBuilder json = new StringBuilder("{\"nodes\":")
                .append(stringArray(new ArrayList<>(nodes))).append(",\"players\":[");
        int playerIndex = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (playerIndex++ > 0) {
                json.append(',');
            }
            Map<String, String> overrides = permissionOverrides(player.getUniqueId());
            json.append("{\"uuid\":").append(json(player.getUniqueId().toString()))
                    .append(",\"name\":").append(json(player.getName()))
                    .append(",\"permissions\":[");
            int permissionIndex = 0;
            for (String node : nodes) {
                if (permissionIndex++ > 0) {
                    json.append(',');
                }
                json.append("{\"name\":").append(json(node))
                        .append(",\"effective\":").append(player.hasPermission(node))
                        .append(",\"override\":").append(json(overrides.getOrDefault(node, "inherit")))
                        .append('}');
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    private Map<String, String> permissionOverrides(UUID playerId) {
        Map<String, String> overrides = new HashMap<>();
        for (String entry : plugin.getConfig().getStringList(
                "dashboard.permission-overrides." + playerId)) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0) {
                continue;
            }
            String node = entry.substring(0, separator);
            String value = entry.substring(separator + 1);
            if (("allow".equals(value) || "deny".equals(value))
                    && node.matches("[a-z0-9._-]{1,128}")) {
                overrides.put(node, value);
            }
        }
        return overrides;
    }

    private void updatePermission(HttpExchange exchange, String body) throws Exception {
        String targetId = jsonString(body, "uuid");
        String node = jsonString(body, "permission");
        String mode = jsonString(body, "mode");
        UUID playerId;
        try {
            playerId = UUID.fromString(targetId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Choose a valid online player.");
        }
        if (!node.matches("[a-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("Choose a registered permission node.");
        }
        if (!List.of("allow", "deny", "inherit").contains(mode)) {
            throw new IllegalArgumentException("Choose allow, deny, or inherit.");
        }
        Session session = sessionFor(exchange);
        callOnServer(() -> {
            if (Bukkit.getPluginManager().getPermission(node) == null) {
                throw new IllegalArgumentException("Choose a registered permission node.");
            }
            Player target = Bukkit.getPlayer(playerId);
            if (target == null) {
                throw new IllegalArgumentException("That player is no longer online.");
            }
            String path = "dashboard.permission-overrides." + playerId;
            List<String> overrides = new ArrayList<>(plugin.getConfig().getStringList(path));
            overrides.removeIf(entry -> entry.startsWith(node + "="));
            if (!"inherit".equals(mode)) {
                overrides.add(node + "=" + mode);
            }
            plugin.getConfig().set(path, overrides.isEmpty() ? null : overrides);
            plugin.saveConfig();
            applyPermissionOverrides(target);
            return null;
        });
        plugin.getLogger().info("Dashboard permission update by " + session.username + ": "
                + node + " -> " + mode + " for " + targetId + ".");
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private void applyPermissionOverrides(Player player) {
        PermissionAttachment previous = permissionAttachments.remove(player.getUniqueId());
        if (previous != null) {
            previous.remove();
        }
        Map<String, String> overrides = permissionOverrides(player.getUniqueId());
        if (overrides.isEmpty()) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (Map.Entry<String, String> override : overrides.entrySet()) {
            attachment.setPermission(override.getKey(), "allow".equals(override.getValue()));
        }
        permissionAttachments.put(player.getUniqueId(), attachment);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyPermissionOverrides(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = permissionAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            attachment.remove();
        }
    }

    private String configJson() {
        String bindAddress = plugin.getConfig().getString(
                "dashboard.bind-address", DEFAULT_BIND_ADDRESS);
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return "{\"bindAddress\":" + json(bindAddress == null ? DEFAULT_BIND_ADDRESS : bindAddress)
                + ",\"port\":" + configuredPort()
                + ",\"prefix\":" + json(prefix == null ? "" : prefix) + "}";
    }

    private void updateConfig(HttpExchange exchange, String body) throws Exception {
        String bindAddress = jsonString(body, "bindAddress").trim();
        String portValue = jsonString(body, "port").trim();
        String prefix = jsonString(body, "prefix");
        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole-number dashboard port.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Dashboard port must be between 1 and 65535.");
        }
        if (prefix.length() > 128 || prefix.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Message prefix must be at most 128 characters.");
        }
        Session session = sessionFor(exchange);
        callOnServer(() -> {
            InetAddress address = parseAddress(bindAddress);
            if (!address.isAnyLocalAddress() && NetworkInterface.getByInetAddress(address) == null) {
                throw new IllegalArgumentException(
                        "Bind address must be assigned to a network interface on this server.");
            }
            plugin.getConfig().set("dashboard.bind-address", bindAddress);
            plugin.getConfig().set("dashboard.port", port);
            plugin.getConfig().set("messages.prefix", prefix);
            plugin.saveConfig();
            return null;
        });
        plugin.getLogger().info("Dashboard safe configuration updated by " + session.username + ".");
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private Role roleFor(Player player) {
        if (player == null) {
            return null;
        }
        if (player.hasPermission("shard.dashboard.admin")) {
            return Role.ADMIN;
        }
        if (player.hasPermission("shard.dashboard.moderator")) {
            return Role.MODERATOR;
        }
        return null;
    }

    private Session sessionFor(HttpExchange exchange) {
        String sessionId = readCookie(exchange.getRequestHeaders(), "ShardDashboardSession");
        return sessionId == null ? null : sessions.get(sessionId);
    }

    private String shardsJson() throws IOException {
        var loadedShards = shardManager.getShards();
        StringBuilder json = new StringBuilder("{\"loaded\":[");
        for (int index = 0; index < loadedShards.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            ShardInfo shard = loadedShards.get(index);
            json.append("{\"name\":").append(json(shard.name()))
                    .append(",\"fileName\":").append(json(shard.fileName()))
                    .append(",\"enabled\":").append(shard.enabled()).append('}');
        }
        json.append("],\"available\":[");
        var files = shardManager.getShardFolder().listFiles((directory, fileName) ->
                fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        if (files == null) {
            throw new IOException("Could not list the shard folder.");
        }
        for (int index = 0; index < files.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(json(files[index].getName()));
        }
        return json.append("]}").toString();
    }

    private String worldsJson() {
        StringBuilder json = new StringBuilder("{\"worlds\":[");
        List<World> worlds = Bukkit.getWorlds();
        for (int index = 0; index < worlds.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            World world = worlds.get(index);
            json.append("{\"name\":").append(json(world.getName()))
                    .append(",\"environment\":").append(json(world.getEnvironment().name()))
                    .append(",\"difficulty\":").append(json(world.getDifficulty().name()))
                    .append(",\"time\":").append(world.getTime())
                    .append(",\"storm\":").append(world.hasStorm())
                    .append(",\"thundering\":").append(world.isThundering())
                    .append(",\"players\":").append(world.getPlayers().size())
                    .append(",\"borderSize\":").append(world.getWorldBorder().getSize())
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private void worldAction(HttpExchange exchange, String body) throws Exception {
        String action = jsonString(body, "action");
        String worldName = jsonString(body, "world");
        String value = jsonString(body, "value");
        callOnServer(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new IllegalArgumentException("That world is not loaded.");
            }
            switch (action) {
                case "time" -> {
                    long time = switch (value) {
                        case "day" -> 1000L;
                        case "noon" -> 6000L;
                        case "night" -> 13000L;
                        case "midnight" -> 18000L;
                        default -> throw new IllegalArgumentException("Unsupported time preset.");
                    };
                    world.setTime(time);
                }
                case "weather" -> {
                    switch (value) {
                        case "clear" -> {
                            world.setStorm(false);
                            world.setThundering(false);
                        }
                        case "rain" -> {
                            world.setStorm(true);
                            world.setThundering(false);
                        }
                        case "thunder" -> {
                            world.setStorm(true);
                            world.setThundering(true);
                        }
                        default -> throw new IllegalArgumentException("Unsupported weather setting.");
                    }
                    world.setWeatherDuration(12_000);
                    world.setThunderDuration(12_000);
                }
                case "difficulty" -> {
                    try {
                        world.setDifficulty(Difficulty.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("Unsupported world difficulty.", exception);
                    }
                }
                case "save" -> world.save();
                default -> throw new IllegalArgumentException("Unsupported world action.");
            }
            return null;
        });
        Session session = sessionFor(exchange);
        plugin.getLogger().info("Dashboard world action " + action + " requested by "
                + session.username + " for " + worldName
                + (value.isBlank() ? "." : " (" + value + ")."));
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private void playerToolAction(HttpExchange exchange, String body) throws Exception {
        String action = jsonString(body, "action");
        UUID targetId = UUID.fromString(jsonString(body, "target"));
        String destinationValue = action.equals("teleport-player")
                ? jsonString(body, "destination") : null;
        UUID destinationId = destinationValue == null ? null : UUID.fromString(destinationValue);
        callOnServer(() -> {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                throw new IllegalArgumentException("That player is no longer online.");
            }
            switch (action) {
                case "teleport-player" -> {
                    Player destination = Bukkit.getPlayer(destinationId);
                    if (destination == null) {
                        throw new IllegalArgumentException("The destination player is no longer online.");
                    }
                    if (!target.teleport(destination.getLocation())) {
                        throw new IllegalStateException("The teleport was cancelled by the server.");
                    }
                }
                case "spawn" -> {
                    if (!target.teleport(target.getWorld().getSpawnLocation())) {
                        throw new IllegalStateException("The teleport was cancelled by the server.");
                    }
                }
                case "heal" -> {
                    var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealth == null) {
                        throw new IllegalStateException("Could not determine the player's maximum health.");
                    }
                    target.setHealth(maxHealth.getValue());
                }
                case "feed" -> {
                    target.setFoodLevel(20);
                    target.setSaturation(20);
                }
                case "clear-effects" -> new ArrayList<>(target.getActivePotionEffects())
                        .forEach(effect -> target.removePotionEffect(effect.getType()));
                case "gamemode" -> {
                    try {
                        target.setGameMode(GameMode.valueOf(
                                jsonString(body, "value").toUpperCase(java.util.Locale.ROOT)));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("Unsupported game mode.", exception);
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported player tool action.");
            }
            Session session = sessionFor(exchange);
            plugin.getLogger().info("Dashboard player action " + action + " requested by "
                    + session.username + " for " + target.getName() + ".");
            return null;
        });
        sendJson(exchange, 200, "{\"completed\":true}");
    }

    private String transactionsJson(List<EconomyTransactionLogger.Transaction> transactions) {
        StringBuilder json = new StringBuilder("{\"transactions\":[");
        for (int index = 0; index < transactions.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            EconomyTransactionLogger.Transaction transaction = transactions.get(index);
            json.append("{\"timestamp\":").append(json(transaction.timestamp()))
                    .append(",\"actor\":").append(json(transaction.actor()))
                    .append(",\"action\":").append(json(transaction.action()))
                    .append(",\"target\":").append(json(transaction.target()))
                    .append(",\"amount\":").append(json(transaction.amount()))
                    .append(",\"balanceBefore\":").append(json(transaction.balanceBefore()))
                    .append(",\"balanceAfter\":").append(json(transaction.balanceAfter())).append('}');
        }
        return json.append("]}").toString();
    }

    private <T> T callOnServer(java.util.concurrent.Callable<T> action) throws Exception {
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, action).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Dashboard action failed.", cause);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("The Minecraft server did not respond in time.", exception);
        }
    }

    private byte[] loadOrCreatePassword() throws IOException {
        Path passwordFile = plugin.getDataFolder().toPath().resolve("dashboard.password");
        Files.createDirectories(passwordFile.getParent());
        if (!Files.exists(passwordFile)) {
            String generated = randomToken();
            try {
                Files.writeString(passwordFile, generated, StandardCharsets.UTF_8);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                generated = Files.readString(passwordFile, StandardCharsets.UTF_8).trim();
            }
            setOwnerOnlyPermissions(passwordFile);
            if (generated.length() < 32) {
                throw new IOException("Generated dashboard password is too short.");
            }
            plugin.getLogger().info("Created dashboard password file: " + passwordFile);
            plugin.getLogger().info("Read that file locally to sign in. Delete it while the server is stopped to reset the password.");
            return generated.getBytes(StandardCharsets.UTF_8);
        }
        String existing = Files.readString(passwordFile, StandardCharsets.UTF_8).trim();
        if (existing.length() < 32) {
            throw new IOException("Dashboard password file must contain at least 32 characters: "
                    + passwordFile);
        }
        return existing.getBytes(StandardCharsets.UTF_8);
    }

    private void pruneExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry ->
                now - entry.getValue().lastActivityAt > SESSION_LIFETIME_MILLIS);
    }

    private void setOwnerOnlyPermissions(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            plugin.getLogger().warning("Could not set POSIX permissions on the dashboard password file. "
                    + "Restrict access to the Shard plugin data folder using your operating system.");
        }
    }

    private boolean hasSameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (origin == null || host == null) {
            return false;
        }
        try {
            URI originUri = URI.create(origin);
            return originUri.getHost() != null
                    && host.equalsIgnoreCase(originUri.getRawAuthority())
                    && (originUri.getScheme().equalsIgnoreCase("http")
                    || originUri.getScheme().equalsIgnoreCase("https"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isJsonRequest(Headers headers) {
        String contentType = headers.getFirst("Content-Type");
        return contentType != null && contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private String jsonString(String body, String key) {
        String value = optionalJsonString(body, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing string field: " + key);
        }
        return value;
    }

    private String optionalJsonString(String body, String key) {
        Matcher matcher = JSON_STRING.matcher(body);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) {
                return unescapeJson(matcher.group(2));
            }
        }
        return null;
    }

    private String unescapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= value.length()) {
                throw new IllegalArgumentException("Invalid JSON escape.");
            }
            char escaped = value.charAt(index);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) {
                        throw new IllegalArgumentException("Invalid JSON Unicode escape.");
                    }
                    try {
                        result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Invalid JSON Unicode escape.", exception);
                    }
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape.");
            }
        }
        return result.toString();
    }

    private String readCookie(Headers headers, String cookieName) {
        String cookies = headers.getFirst("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String cookie : cookies.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].equals(cookieName)) {
                return parts[1];
            }
        }
        return null;
    }

    private void setSessionCookie(HttpExchange exchange, String sessionId) {
        String secure = "https".equalsIgnoreCase(
                exchange.getRequestHeaders().getFirst("X-Forwarded-Proto")) ? " Secure;" : "";
        exchange.getResponseHeaders().set("Set-Cookie",
                "ShardDashboardSession=" + sessionId + "; HttpOnly; SameSite=Strict;"
                        + secure + " Path=/; Max-Age=1800");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String stringArray(List<String> strings) {
        return strings.stream().map(DashboardServer::json)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", body);
    }

    private void sendError(HttpExchange exchange, int status, String message) {
        try {
            sendJson(exchange, status, "{\"error\":" + json(
                    message == null ? "Request failed." : message) + "}");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.FINE, "Could not write dashboard error response.", exception);
        }
    }

    private void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        setSecurityHeaders(headers);
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
    }

    private void setSecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy",
                "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    }

    private static final class Session {
        private volatile long lastActivityAt;
        private final UUID playerId;
        private volatile String username;
        private volatile Role role;
        private final boolean owner;

        private Session(long lastActivityAt, UUID playerId, String username, Role role, boolean owner) {
            this.lastActivityAt = lastActivityAt;
            this.playerId = playerId;
            this.username = username;
            this.role = role;
            this.owner = owner;
        }
    }

    private enum Role {
        MODERATOR("Moderator"),
        ADMIN("Admin");

        private final String label;

        Role(String label) {
            this.label = label;
        }
    }

    private record LoginCode(UUID playerId, String playerName, long expiresAt) {
    }

    private static final class LoginAttempts {
        private final long windowStartedAt;
        private volatile int count;

        private LoginAttempts(long windowStartedAt, int count) {
            this.windowStartedAt = windowStartedAt;
            this.count = count;
        }
    }
}
