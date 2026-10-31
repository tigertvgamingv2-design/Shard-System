package com.thronecraft.net.managers;

import com.thronecraft.net.shards.ShardMessages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public final class RestartPlanManager {
    private static final List<Long> ANNOUNCEMENTS_SECONDS =
            List.of(3600L, 1800L, 600L, 300L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);
    private final JavaPlugin plugin;
    private final File planFile;
    private BukkitTask ticker;
    private long plannedAt;
    private int announcementIndex;

    public RestartPlanManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.planFile = new File(plugin.getDataFolder(), "restart-plan.yml");
    }

    public void start() {
        YamlConfiguration plan = YamlConfiguration.loadConfiguration(planFile);
        long savedTime = plan.getLong("restart-at", 0);
        if (savedTime <= 0) {
            return;
        }
        if (savedTime <= System.currentTimeMillis()) {
            clearPlan();
            plugin.getLogger().warning("Discarded an expired saved restart plan.");
            return;
        }
        plannedAt = savedTime;
        announcementIndex = firstAnnouncementIndex(savedTime - System.currentTimeMillis());
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void schedule(long restartAt) throws IOException {
        long now = System.currentTimeMillis();
        long delay = restartAt - now;
        if (delay <= 0) {
            throw new IllegalArgumentException("Choose a restart time in the future.");
        }
        YamlConfiguration plan = new YamlConfiguration();
        plan.set("restart-at", restartAt);
        plan.save(planFile);
        cancelTicker();
        plannedAt = restartAt;
        announcementIndex = firstAnnouncementIndex(delay);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void cancel() throws IOException {
        new YamlConfiguration().save(planFile);
        cancelTicker();
        plannedAt = 0;
    }

    public long getPlannedAt() {
        return plannedAt;
    }

    public void stop() {
        cancelTicker();
    }

    private void tick() {
        if (plannedAt <= 0) {
            cancelTicker();
            return;
        }
        long remaining = plannedAt - System.currentTimeMillis();
        if (remaining <= 0) {
            plannedAt = 0;
            cancelTicker();
            clearPlan();
            plugin.getLogger().warning("Scheduled restart time reached; requesting graceful server shutdown.");
            Bukkit.shutdown();
            return;
        }

        long secondsRemaining = (remaining + 999) / 1000;
        while (announcementIndex < ANNOUNCEMENTS_SECONDS.size()
                && secondsRemaining <= ANNOUNCEMENTS_SECONDS.get(announcementIndex)) {
            long threshold = ANNOUNCEMENTS_SECONDS.get(announcementIndex++);
            ShardMessages.broadcast("&eA planned server restart will begin in "
                    + formatDuration(threshold) + ".");
        }
    }

    private int firstAnnouncementIndex(long delayMillis) {
        long seconds = (delayMillis + 999) / 1000;
        int index = 0;
        while (index < ANNOUNCEMENTS_SECONDS.size()
                && ANNOUNCEMENTS_SECONDS.get(index) > seconds) {
            index++;
        }
        return index;
    }

    private String formatDuration(long seconds) {
        if (seconds >= 60) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    private void cancelTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void clearPlan() {
        try {
            new YamlConfiguration().save(planFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not clear the saved restart plan.", exception);
        }
    }
}
