package com.thronecraft.net.managers;

import com.thronecraft.net.shards.ShardMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EconomyTransactionLogger {
    private static final String HEADER =
            "timestamp,actor,action,target,amount,balance_before,balance_after";

    private EconomyTransactionLogger() {
    }

    public static synchronized void record(
            JavaPlugin plugin,
            String actor,
            String action,
            String target,
            double amount,
            double balanceBefore,
            double balanceAfter
    ) throws IOException {
        Path file = plugin.getDataFolder().toPath().resolve("economy-transactions.csv");
        boolean needsHeader = !Files.exists(file) || Files.size(file) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (needsHeader) {
                writer.write(HEADER);
                writer.newLine();
            }
            writer.write(String.join(",",
                    csv(Instant.now().toString()),
                    csv(actor),
                    csv(action),
                    csv(target),
                    csv(format(amount)),
                    csv(format(balanceBefore)),
                    csv(format(balanceAfter))));
            writer.newLine();
        }
    }

    public static void recordOrReport(
            JavaPlugin plugin,
            CommandSender sender,
            String actor,
            String action,
            String target,
            double amount,
            double balanceBefore,
            double balanceAfter
    ) {
        try {
            record(plugin, actor, action, target, amount, balanceBefore, balanceAfter);
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "An economy change completed but could not be written to economy-transactions.csv.",
                    exception);
            ShardMessages.send(sender, ChatColor.RED
                    + "The economy change completed, but its audit record could not be saved.");
        }
    }

    public static synchronized List<Transaction> readRecent(JavaPlugin plugin, int limit) throws IOException {
        if (limit < 1) {
            throw new IllegalArgumentException("The transaction limit must be positive.");
        }
        Path file = plugin.getDataFolder().toPath().resolve("economy-transactions.csv");
        if (!Files.exists(file)) {
            return List.of();
        }

        List<Transaction> transactions = new ArrayList<>(limit);
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long position = reader.length() - 1;
            ByteArrayOutputStream reversedLine = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            boolean reachedStart = false;
            while (position >= 0 && transactions.size() < limit && !reachedStart) {
                long chunkStart = Math.max(0, position - chunk.length + 1);
                int chunkLength = (int) (position - chunkStart + 1);
                reader.seek(chunkStart);
                reader.readFully(chunk, 0, chunkLength);
                for (int index = chunkLength - 1; index >= 0; index--) {
                    if (chunk[index] == '\n') {
                        if (reversedLine.size() > 0) {
                            addTransaction(file, reversedLine, transactions);
                        }
                        reversedLine.reset();
                        if (transactions.size() == limit) {
                            break;
                        }
                    } else {
                        reversedLine.write(chunk[index]);
                    }
                }
                reachedStart = chunkStart == 0;
                position = chunkStart - 1;
            }
            if (transactions.size() < limit && reversedLine.size() > 0) {
                addTransaction(file, reversedLine, transactions);
            }
        }
        return List.copyOf(transactions);
    }

    private static void addTransaction(
            Path file,
            ByteArrayOutputStream reversedLine,
            List<Transaction> transactions
    ) throws IOException {
        byte[] bytes = reversedLine.toByteArray();
        for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
            byte temporary = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = temporary;
        }
        String line = new String(bytes, StandardCharsets.UTF_8);
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        if (HEADER.equals(line)) {
            return;
        }
        List<String> fields = parseCsv(line);
        if (fields.size() != 7) {
            throw new IOException("Malformed transaction row in " + file);
        }
        transactions.add(new Transaction(
                fields.get(0), fields.get(1), fields.get(2), fields.get(3),
                fields.get(4), fields.get(5), fields.get(6)));
    }

    private static String format(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static List<String> parseCsv(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (current == '"' && field.isEmpty()) {
                quoted = true;
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IOException("Unterminated quoted value in economy transaction log.");
        }
        fields.add(field.toString());
        return fields;
    }

    public record Transaction(
            String timestamp,
            String actor,
            String action,
            String target,
            String amount,
            String balanceBefore,
            String balanceAfter
    ) {
    }
}
