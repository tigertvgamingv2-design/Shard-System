package com.thronecraft.net.managers;

import com.thronecraft.net.shards.ShardModule;
import com.thronecraft.net.shards.ShardMessages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.jar.JarFile;

public class ShardManager {
    private final JavaPlugin plugin;
    private final List<LoadedShard> loadedShards = new ArrayList<>();

    public ShardManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int loadShards() throws IOException {
        File[] jars = getShardFolder().listFiles((directory, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            throw new IOException("Could not list the shard folder.");
        }

        List<File> sortedJars = new ArrayList<>();
        for (File jar : jars) {
            if (jar != null) {
                sortedJars.add(jar);
            }
        }
        sortedJars.sort((left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName()));
        int loadedCount = 0;
        for (File jar : sortedJars) {
            try {
                loadShard(jar);
                loadedCount++;
            } catch (Exception | LinkageError exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not load shard from " + jar.getName() + ".", exception);
            }
        }
        return loadedCount;
    }

    public File getShardFolder() throws IOException {
        File shardFolder = new File(plugin.getDataFolder(), "shards");
        if (!shardFolder.exists() && !shardFolder.mkdirs()) {
            throw new IOException("Could not create shard folder: " + shardFolder);
        }
        if (!shardFolder.isDirectory()) {
            throw new IOException("Shard path is not a directory: " + shardFolder);
        }
        return shardFolder;
    }

    public List<ShardInfo> getShards() {
        return loadedShards.stream()
                .map(shard -> new ShardInfo(shard.name(), shard.fileName(), shard.enabled()))
                .toList();
    }

    public Optional<ShardInfo> getShard(String name) {
        return findShard(name).map(shard ->
                new ShardInfo(shard.name(), shard.fileName(), shard.enabled()));
    }

    public void loadShard(String fileName) throws Exception {
        File folder = getShardFolder();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                || !new File(fileName).getName().equals(fileName)) {
            throw new IOException("Use the name of a .jar file directly inside the shards folder.");
        }

        File jar = new File(folder, fileName);
        if (!jar.isFile()) {
            throw new IOException("Shard JAR not found: " + fileName);
        }
        loadShard(jar);
    }

    public void validateShard(String fileName) throws Exception {
        File folder = getShardFolder();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                || !new File(fileName).getName().equals(fileName)) {
            throw new IOException("Use the name of a .jar file directly inside the shards folder.");
        }
        File jar = new File(folder, fileName);
        if (!jar.isFile()) {
            throw new IOException("Shard JAR not found: " + fileName);
        }

        try (JarFile jarFile = new JarFile(jar)) {
            ShardDescriptor descriptor = readDescriptor(jarFile);
            if (findShard(descriptor.name()).isPresent()) {
                throw new IOException("A shard named '" + descriptor.name() + "' is already loaded.");
            }

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{jar.toURI().toURL()}, plugin.getClass().getClassLoader())) {
                Class<?> mainClass = Class.forName(descriptor.mainClass(), false, classLoader);
                if (!ShardModule.class.isAssignableFrom(mainClass)) {
                    throw new IOException("Main class must implement " + ShardModule.class.getName() + ".");
                }
                mainClass.getConstructor();
            }
        }
    }

    public void enableShard(String name) throws Exception {
        LoadedShard shard = requireShard(name);
        if (shard.enabled()) {
            throw new IllegalStateException("Shard '" + shard.name() + "' is already enabled.");
        }
        try {
            shard.module().onEnable(plugin);
            shard.setEnabled(true);
        } catch (Exception | LinkageError exception) {
            try {
                shard.module().onDisable();
            } catch (Exception | LinkageError disableException) {
                exception.addSuppressed(disableException);
            }
            throw exception;
        }
    }

    public void disableShard(String name) throws Exception {
        LoadedShard shard = requireShard(name);
        if (!shard.enabled()) {
            throw new IllegalStateException("Shard '" + shard.name() + "' is already disabled.");
        }
        shard.module().onDisable();
        shard.setEnabled(false);
    }

    public void reloadShard(String name) throws Exception {
        LoadedShard shard = requireShard(name);
        String fileName = shard.fileName();
        unloadShard(name);
        loadShard(fileName);
        ShardMessages.broadcast("&aShard &f" + shard.name() + " &awas reloaded.");
    }

    public int reloadAllShards() throws IOException {
        disableShards();
        int loadedCount = loadShards();
        for (ShardInfo shard : getShards()) {
            ShardMessages.broadcast("&aShard &f" + shard.name() + " &awas reloaded.");
        }
        return loadedCount;
    }

    public void unloadShard(String name) throws Exception {
        LoadedShard shard = requireShard(name);
        Exception failure = null;
        if (shard.enabled()) {
            try {
                shard.module().onDisable();
            } catch (Exception exception) {
                failure = exception;
            } catch (LinkageError error) {
                failure = new IllegalStateException("Shard disable failed.", error);
            }
        }
        closeShard(shard);
        loadedShards.remove(shard);
        if (failure != null) {
            throw failure;
        }
    }

    public void disableShards() {
        for (int index = loadedShards.size() - 1; index >= 0; index--) {
            LoadedShard shard = loadedShards.get(index);
            if (shard.enabled()) {
                try {
                    shard.module().onDisable();
                } catch (Exception | LinkageError exception) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not disable shard '" + shard.name() + "'.", exception);
                }
            }
            closeShard(shard);
        }
        loadedShards.clear();
    }

    private void loadShard(File jar) throws Exception {
        URLClassLoader classLoader = null;
        ShardModule module = null;
        try (JarFile jarFile = new JarFile(jar)) {
            ShardDescriptor descriptor = readDescriptor(jarFile);
            if (findShard(descriptor.name()).isPresent()) {
                throw new IOException("A shard named '" + descriptor.name() + "' is already loaded.");
            }

            classLoader = new URLClassLoader(
                    new URL[]{jar.toURI().toURL()},
                    plugin.getClass().getClassLoader());
            Class<?> mainClass = Class.forName(descriptor.mainClass(), true, classLoader);
            if (!ShardModule.class.isAssignableFrom(mainClass)) {
                throw new IOException("Main class must implement " + ShardModule.class.getName() + ".");
            }

            Constructor<?> constructor = mainClass.getConstructor();
            module = (ShardModule) constructor.newInstance();
            if (module == null) {
                throw new IllegalStateException("Shard constructor returned null.");
            }
            module.onEnable(plugin);
            loadedShards.add(new LoadedShard(descriptor.name(), jar.getName(), module, classLoader));
            plugin.getLogger().info("Loaded shard '" + descriptor.name() + "' from " + jar.getName() + ".");
        } catch (Exception | LinkageError exception) {
            if (module != null) {
                try {
                    module.onDisable();
                } catch (Exception | LinkageError disableException) {
                    exception.addSuppressed(disableException);
                }
            }
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            throw exception;
        }
    }

    private ShardDescriptor readDescriptor(JarFile jarFile) throws IOException {
        var descriptorEntry = jarFile.getJarEntry("shard.yml");
        if (descriptorEntry == null) {
            throw new IOException("Missing shard.yml.");
        }

        YamlConfiguration descriptor;
        try (InputStreamReader reader = new InputStreamReader(
                jarFile.getInputStream(descriptorEntry), StandardCharsets.UTF_8)) {
            descriptor = YamlConfiguration.loadConfiguration(reader);
        }

        String name = descriptor.getString("name");
        String mainClassName = descriptor.getString("main");
        if (name == null || name.isBlank() || mainClassName == null || mainClassName.isBlank()) {
            throw new IOException("shard.yml must define non-empty 'name' and 'main' values.");
        }
        return new ShardDescriptor(name, mainClassName);
    }

    private Optional<LoadedShard> findShard(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return loadedShards.stream()
                .filter(shard -> shard.name().equalsIgnoreCase(name))
                .findFirst();
    }

    private LoadedShard requireShard(String name) {
        return findShard(name).orElseThrow(() ->
                new IllegalArgumentException("Shard not found: " + name));
    }

    private void closeShard(LoadedShard shard) {
        try {
            shard.classLoader().close();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not close the class loader for shard '" + shard.name() + "'.", exception);
        }
    }

    public record ShardInfo(String name, String fileName, boolean enabled) {
    }

    private record ShardDescriptor(String name, String mainClass) {
    }

    private static final class LoadedShard {
        private final String name;
        private final String fileName;
        private final ShardModule module;
        private final URLClassLoader classLoader;
        private boolean enabled = true;

        private LoadedShard(String name, String fileName, ShardModule module, URLClassLoader classLoader) {
            this.name = name;
            this.fileName = fileName;
            this.module = module;
            this.classLoader = classLoader;
        }

        private String name() {
            return name;
        }

        private String fileName() {
            return fileName;
        }

        private ShardModule module() {
            return module;
        }

        private URLClassLoader classLoader() {
            return classLoader;
        }

        private boolean enabled() {
            return enabled;
        }

        private void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
