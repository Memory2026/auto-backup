package com.xingci.autobackup;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class BackupManager {

    private static final String BACKUP_FOLDER_NAME = "backups";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    /**
     * Create a backup of the current world with auto-generated name
     */
    public static String createBackup(MinecraftServer server) throws IOException {
        String worldName = getWorldName(server);
        int backupCount = getBackupCountForToday(server, worldName) + 1;
        String backupName = generateBackupName(worldName, backupCount);
        return createBackup(server, backupName);
    }

    /**
     * Create a backup with custom name
     */
    public static String createBackup(MinecraftServer server, String customName) throws IOException {
        String worldName = getWorldName(server);
        Path worldPath = getWorldPath(server);

        // Create backup directory
        Path backupDir = getBackupDirectory(server);
        Files.createDirectories(backupDir);

        // Create backup path
        String sanitizedName = sanitizeName(customName);
        Path backupPath = backupDir.resolve(sanitizedName);

        // Check if backup already exists
        if (Files.exists(backupPath)) {
            throw new IOException("备份已存在: " + customName);
        }

        // Copy world to backup
        copyDirectory(worldPath, backupPath);

        AutoBackup.LOGGER.info("备份已创建: " + sanitizedName);
        return sanitizedName;
    }

    /**
     * List all backups for the current world
     */
    public static List<String> listBackups(MinecraftServer server) throws IOException {
        Path backupDir = getBackupDirectory(server);
        List<String> backups = new ArrayList<>();

        if (!Files.exists(backupDir)) {
            return backups;
        }

        try (Stream<Path> stream = Files.list(backupDir)) {
            stream.filter(Files::isDirectory)
                  .map(p -> p.getFileName().toString())
                  .sorted()
                  .forEach(backups::add);
        }

        return backups;
    }

    /**
     * Get the backup directory path for the current world
     * Backup is stored at: {world_parent_dir}/backups/{world_name}/
     * This is OUTSIDE the world directory to avoid recursive copying
     */
    public static Path getBackupDirectory(MinecraftServer server) {
        String worldName = getWorldName(server);
        // LevelResource.LEVEL_DATA_FILE points to level.dat in the world directory
        // e.g., /run/saves/新的世界/level.dat
        Path levelDataPath = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
        // worldPath = /run/saves/新的世界
        Path worldPath = levelDataPath.getParent();
        // savesDir = /run/saves
        Path savesDir = worldPath.getParent();
        return savesDir.resolve(BACKUP_FOLDER_NAME).resolve(sanitizeName(worldName));
    }

    /**
     * Get the current world path (the directory containing level.dat)
     */
    private static Path getWorldPath(MinecraftServer server) {
        Path levelDataPath = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
        return levelDataPath.getParent();
    }

    /**
     * Get the current world name from the path
     */
    private static String getWorldName(MinecraftServer server) {
        Path levelDataPath = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
        Path worldPath = levelDataPath.getParent();
        return worldPath.getFileName().toString();
    }

    /**
     * Generate backup name with format: year-month-day-time-count
     */
    private static String generateBackupName(String worldName, int count) {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DATE_FORMATTER);
        return worldName + "_" + dateStr + "_" + count;
    }

    /**
     * Get backup count for today (based on naming pattern)
     */
    private static int getBackupCountForToday(MinecraftServer server, String worldName) {
        Path backupDir = getBackupDirectory(server);
        int count = 0;

        if (!Files.exists(backupDir)) {
            return 0;
        }

        try (Stream<Path> stream = Files.list(backupDir)) {
            LocalDateTime today = LocalDateTime.now();
            String todayPrefix = worldName + "_" + today.format(DATE_FORMATTER).substring(0, 10);

            count = (int) stream.filter(Files::isDirectory)
                  .map(p -> p.getFileName().toString())
                  .filter(name -> name.startsWith(todayPrefix))
                  .count();
        } catch (IOException e) {
            AutoBackup.LOGGER.warn("无法统计备份数量: " + e.getMessage());
        }

        return count;
    }

    /**
     * Sanitize file name to prevent path traversal
     */
    private static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
    }

    /**
     * Recursively copy a directory
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                // Skip lock files and other temporary files that may cause conflicts
                if (fileName.equals("session.lock") || fileName.endsWith(".lock")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                AutoBackup.LOGGER.warn("复制文件失败: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
