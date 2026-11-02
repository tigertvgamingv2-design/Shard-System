package com.thronecraft.net;

import org.bukkit.plugin.java.JavaPlugin;
import com.thronecraft.net.licensing.LicenseVerifier;
import com.thronecraft.net.commands.ShardCommand;
import com.thronecraft.net.listeners.PlayerListener;
import com.thronecraft.net.managers.RestartPlanManager;
import com.thronecraft.net.managers.ShardManager;
import com.thronecraft.net.shards.ShardMessages;
import com.thronecraft.net.utils.ConsoleLog;
import com.thronecraft.net.web.DashboardServer;

import java.io.IOException;
import java.util.logging.Level;

public class Shard extends JavaPlugin {
    private static final String LICENSE_API_URL = "https://lic.cloudrix.uk/api/verify";
    private static final String PRODUCT_SLUG = "shard";

    private ShardManager shardManager;
    private RestartPlanManager restartPlanManager;
    private DashboardServer dashboardServer;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        ShardMessages.initialize(this);

        String licenseKey = getConfig().getString("license.key", "YOUR_LICENSE_KEY").trim();
        if (licenseKey.isEmpty() || licenseKey.startsWith("YOUR_")) {
            getLogger().severe("Set license.key in config.yml before enabling this plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConsoleLog.info("Verifying license...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            LicenseVerifier.VerificationResult result;
            try {
                result = LicenseVerifier.verify(LICENSE_API_URL, PRODUCT_SLUG, licenseKey);
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Could not verify the Shard license.", exception);
                getServer().getScheduler().runTask(this, () -> disableForLicenseFailure());
                return;
            }

            getServer().getScheduler().runTask(this, () -> {
                if (!result.valid()) {
                    getLogger().severe("License verification failed: " + result.reason());
                    disableForLicenseFailure();
                    return;
                }
                String reason = result.reason();
                ConsoleLog.success("License verification succeeded"
                        + (reason == null || reason.isBlank() ? "." : ": " + reason));
                enableLicensedFeatures();
            });
        });
    }

    private void enableLicensedFeatures() {
        shardManager = new ShardManager(this);
        try {
            shardManager.loadShards();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not prepare the shards folder.", exception);
        }

        restartPlanManager = new RestartPlanManager(this);
        restartPlanManager.start();
        dashboardServer = new DashboardServer(this, shardManager, restartPlanManager);
        DashboardServer activeDashboard = dashboardServer;
        try {
            dashboardServer.start();
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE,
                    "The admin dashboard could not start. Shard commands remain available.", exception);
            dashboardServer = null;
            activeDashboard = null;
        }
        ShardCommand shardCommand = new ShardCommand(this, shardManager, activeDashboard);
        getCommand("shard").setExecutor(shardCommand);
        getCommand("shard").setTabCompleter(shardCommand);
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        
        ConsoleLog.success(getDescription().getName() + " has been enabled!");
    }

    private void disableForLicenseFailure() {
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        if (dashboardServer != null) {
            dashboardServer.stop();
            dashboardServer = null;
        }
        if (restartPlanManager != null) {
            restartPlanManager.stop();
            restartPlanManager = null;
        }
        if (shardManager != null) {
            shardManager.disableShards();
            shardManager = null;
        }
        ShardMessages.clear(this);
        ConsoleLog.info(getDescription().getName() + " has been disabled!");
    }
    
}
