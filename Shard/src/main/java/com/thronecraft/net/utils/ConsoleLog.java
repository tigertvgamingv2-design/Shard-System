package com.thronecraft.net.utils;

import org.bukkit.Bukkit;

/** Colored, consistently formatted plugin output for the server console. */
public final class ConsoleLog {
    private static final String PREFIX = "§8[§bShard§8] ";

    private ConsoleLog() {
    }

    public static void info(String message) {
        send("§7" + message);
    }

    public static void success(String message) {
        send("§a✔ " + message);
    }

    public static void warn(String message) {
        send("§e⚠ " + message);
    }

    public static void error(String message) {
        send("§c✖ " + message);
    }

    /** Wraps a value so it stands out inside an info message. */
    public static String highlight(Object value) {
        String text = String.valueOf(value).replace('§', '?').replaceAll("[\\r\\n\\u001B]", " ");
        return "§f" + text + "§7";
    }

    private static void send(String message) {
        Bukkit.getConsoleSender().sendMessage(PREFIX + message);
    }
}
