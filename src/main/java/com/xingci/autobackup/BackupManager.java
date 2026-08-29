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

    // =========================================================
    // 目录
    // =========================================================

    /**
     * 世界备份：
     *
     * saves/backups/世界名/
     */
    private static final String BACKUP_FOLDER_NAME = "backups";

    /**
     * 整个服务器备份：
     *
     * 服务器根目录/server-backups/
     */
    private static final String SERVER_BACKUP_FOLDER_NAME =
            "server-backups";


    // =========================================================
    // 日期格式
    // =========================================================

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HHmmss"
            );


    // =========================================================
    // 世界备份
    // =========================================================

    /**
     * 自动创建世界备份。
     */
    public static String createBackup(
            MinecraftServer server) throws IOException {

        String worldName =
                getWorldName(server);

        int backupCount =
                getBackupCountForToday(
                        server,
                        worldName
                ) + 1;

        String backupName =
                generateBackupName(
                        worldName,
                        backupCount
                );

        return createBackup(
                server,
                backupName
        );
    }


    /**
     * 创建指定名称的世界备份。
     */
    public static String createBackup(
            MinecraftServer server,
            String customName) throws IOException {

        Path worldPath =
                getWorldPath(server);

        Path backupDir =
                getBackupDirectory(server);

        Files.createDirectories(
                backupDir
        );

        String sanitizedName =
                sanitizeName(customName);

        if (sanitizedName.isEmpty()) {

            throw new IOException(
                    "备份名称不能为空"
            );
        }

        Path backupPath =
                backupDir
                        .resolve(sanitizedName)
                        .normalize();

        if (!backupPath.getParent()
                .equals(backupDir.normalize())) {

            throw new IOException(
                    "无效的备份名称"
            );
        }

        if (Files.exists(backupPath)) {

            throw new IOException(
                    "备份已存在: "
                            + sanitizedName
            );
        }

        AutoBackup.LOGGER.info(
                "开始创建世界备份: {}",
                sanitizedName
        );

        try {

            copyDirectory(
                    worldPath,
                    backupPath
            );

        } catch (IOException e) {

            try {

                if (Files.exists(backupPath)) {
                    deleteDirectory(backupPath);
                }

            } catch (IOException cleanupException) {

                AutoBackup.LOGGER.warn(
                        "删除不完整世界备份失败: {}",
                        cleanupException.getMessage()
                );
            }

            throw e;
        }

        AutoBackup.LOGGER.info(
                "世界备份已创建: {}",
                sanitizedName
        );

        return sanitizedName;
    }


    /**
     * 获取世界备份目录。
     *
     * 例如：
     *
     * saves/
     * └── backups/
     *     └── world/
     */
    public static Path getBackupDirectory(
            MinecraftServer server) {

        String worldName =
                getWorldName(server);

        Path levelDataPath =
                server.getWorldPath(
                        LevelResource.LEVEL_DATA_FILE
                );

        Path worldPath =
                levelDataPath.getParent();

        if (worldPath == null) {
            throw new IllegalStateException(
                    "无法获取世界目录"
            );
        }

        Path savesDir =
                worldPath.getParent();

        if (savesDir == null) {
            throw new IllegalStateException(
                    "无法获取 saves 目录"
            );
        }

        return savesDir
                .resolve(BACKUP_FOLDER_NAME)
                .resolve(sanitizeName(worldName))
                .normalize();
    }


    /**
     * 获取当前世界目录。
     */
    private static Path getWorldPath(
            MinecraftServer server) {

        Path levelDataPath =
                server.getWorldPath(
                        LevelResource.LEVEL_DATA_FILE
                );

        Path worldPath =
                levelDataPath.getParent();

        if (worldPath == null) {
            throw new IllegalStateException(
                    "无法获取世界目录"
            );
        }

        return worldPath;
    }


    /**
     * 获取世界名称。
     */
    private static String getWorldName(
            MinecraftServer server) {

        Path levelDataPath =
                server.getWorldPath(
                        LevelResource.LEVEL_DATA_FILE
                );

        Path worldPath =
                levelDataPath.getParent();

        if (worldPath == null
                || worldPath.getFileName() == null) {

            return "world";
        }

        return worldPath
                .getFileName()
                .toString();
    }


    /**
     * 自动世界备份名称：
     *
     * world_2026-08-28-171500_1
     */
    private static String generateBackupName(
            String worldName,
            int count) {

        String date =
                LocalDateTime.now()
                        .format(DATE_FORMATTER);

        return worldName
                + "_"
                + date
                + "_"
                + count;
    }


    /**
     * 获取今天的备份数量。
     */
    private static int getBackupCountForToday(
            MinecraftServer server,
            String worldName) {

        Path backupDir =
                getBackupDirectory(server);

        if (!Files.exists(backupDir)) {
            return 0;
        }

        int count = 0;

        try (Stream<Path> stream =
                     Files.list(backupDir)) {

            String prefix =
                    worldName
                            + "_"
                            + LocalDateTime.now()
                            .format(DATE_FORMATTER)
                            .substring(0, 10);

            count = (int) stream
                    .filter(Files::isDirectory)
                    .map(path ->
                            path.getFileName()
                                    .toString())
                    .filter(name ->
                            name.startsWith(prefix))
                    .count();

        } catch (IOException e) {

            AutoBackup.LOGGER.warn(
                    "无法统计备份数量: {}",
                    e.getMessage()
            );
        }

        return count;
    }


    /**
     * 列出世界备份。
     *
     * /backup list
     */
    public static List<String> listBackups(
            MinecraftServer server) throws IOException {

        Path backupDir =
                getBackupDirectory(server);

        List<String> backups =
                new ArrayList<>();

        if (!Files.exists(backupDir)) {
            return backups;
        }

        try (Stream<Path> stream =
                     Files.list(backupDir)) {

            stream
                    .filter(Files::isDirectory)
                    .map(path ->
                            path.getFileName()
                                    .toString())
                    .sorted()
                    .forEach(
                            backups::add
                    );
        }

        return backups;
    }


    /**
     * 删除世界备份。
     *
     * /backup del <name>
     */
    public static void deleteBackup(
            MinecraftServer server,
            String backupName) throws IOException {

        Path backupDir =
                getBackupDirectory(server);

        String sanitizedName =
                sanitizeName(backupName);

        Path backupPath =
                backupDir
                        .resolve(sanitizedName)
                        .normalize();

        if (!backupPath.getParent()
                .equals(backupDir.normalize())) {

            throw new IOException(
                    "无效的备份名称"
            );
        }

        if (!Files.exists(backupPath)) {

            throw new IOException(
                    "备份不存在: "
                            + backupName
            );
        }

        if (!Files.isDirectory(backupPath)) {

            throw new IOException(
                    "备份不是目录: "
                            + backupName
            );
        }

        deleteDirectory(
                backupPath
        );

        AutoBackup.LOGGER.info(
                "世界备份已删除: {}",
                sanitizedName
        );
    }


    // =========================================================
    // 服务器备份
    // =========================================================

    /**
     * 自动创建整个服务器备份。
     *
     * 名称：
     *
     * autobackup-server-2026-08-28-180000
     */
    public static String createServerBackup(
            MinecraftServer server) throws IOException {

        String backupName =
                "autobackup-server-"
                        + LocalDateTime.now()
                        .format(DATE_FORMATTER);

        return createServerBackup(
                server,
                backupName
        );
    }


    /**
     * 创建指定名称的整个服务器备份。
     */
    public static String createServerBackup(
            MinecraftServer server,
            String customName) throws IOException {

        /*
         * Minecraft 服务端根目录。
         */
        Path serverPath =
                Path.of("")
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isDirectory(serverPath)) {

            throw new IOException(
                    "无法找到服务器目录: "
                            + serverPath
            );
        }


        String backupName =
                sanitizeName(customName);

        if (backupName.isEmpty()) {

            throw new IOException(
                    "服务器备份名称不能为空"
            );
        }


        /*
         * 服务器备份目录：
         *
         * 服务器根目录/server-backups/
         */
        Path serverBackupDir =
                getServerBackupDirectory(
                        server
                );

        Files.createDirectories(
                serverBackupDir
        );


        Path backupPath =
                serverBackupDir
                        .resolve(backupName)
                        .normalize();


        if (!backupPath.getParent()
                .equals(serverBackupDir.normalize())) {

            throw new IOException(
                    "无效的服务器备份名称"
            );
        }


        if (Files.exists(backupPath)) {

            throw new IOException(
                    "服务器备份已存在: "
                            + backupName
            );
        }


        AutoBackup.LOGGER.info(
                "开始创建服务器备份: {}",
                backupName
        );


        try {

            copyServerDirectory(
                    serverPath,
                    backupPath
            );

        } catch (IOException e) {

            try {

                if (Files.exists(backupPath)) {
                    deleteDirectory(backupPath);
                }

            } catch (IOException cleanupException) {

                AutoBackup.LOGGER.warn(
                        "删除不完整服务器备份失败: {}",
                        cleanupException.getMessage()
                );
            }

            throw e;
        }


        AutoBackup.LOGGER.info(
                "服务器备份已创建: {}",
                backupName
        );

        return backupName;
    }


    /**
     * 获取服务器备份目录。
     *
     * 这里明确放在：
     *
     * 服务器根目录/server-backups/
     */
    public static Path getServerBackupDirectory(
            MinecraftServer server) {

        Path serverPath =
                Path.of("")
                        .toAbsolutePath()
                        .normalize();

        return serverPath
                .resolve(
                        SERVER_BACKUP_FOLDER_NAME
                )
                .normalize();
    }


    /**
     * 列出服务器备份。
     *
     * /backup server list
     */
    public static List<String> listServerBackups(
            MinecraftServer server) throws IOException {

        Path serverBackupDir =
                getServerBackupDirectory(server);

        List<String> backups =
                new ArrayList<>();

        if (!Files.exists(serverBackupDir)) {
            return backups;
        }

        try (Stream<Path> stream =
                     Files.list(serverBackupDir)) {

            stream
                    .filter(Files::isDirectory)
                    .map(path ->
                            path.getFileName()
                                    .toString())
                    .sorted()
                    .forEach(
                            backups::add
                    );
        }

        return backups;
    }


    /**
     * 删除服务器备份。
     *
     * /backup server del <name>
     */
    public static void deleteServerBackup(
            MinecraftServer server,
            String backupName) throws IOException {

        Path serverBackupDir =
                getServerBackupDirectory(server);

        String sanitizedName =
                sanitizeName(backupName);

        Path backupPath =
                serverBackupDir
                        .resolve(sanitizedName)
                        .normalize();

        if (!backupPath.getParent()
                .equals(serverBackupDir.normalize())) {

            throw new IOException(
                    "无效的服务器备份名称"
            );
        }

        if (!Files.exists(backupPath)) {

            throw new IOException(
                    "服务器备份不存在: "
                            + backupName
            );
        }

        if (!Files.isDirectory(backupPath)) {

            throw new IOException(
                    "服务器备份不是目录: "
                            + backupName
            );
        }

        deleteDirectory(
                backupPath
        );

        AutoBackup.LOGGER.info(
                "服务器备份已删除: {}",
                sanitizedName
        );
    }


    // =========================================================
    // 获取备份大小
    // =========================================================

    /**
     * 获取一个备份目录的大小。
     *
     * 单位：字节
     */
    public static long getBackupSize(
            Path backupPath) throws IOException {

        if (!Files.exists(backupPath)) {
            return 0L;
        }

        if (Files.isRegularFile(backupPath)) {
            return Files.size(backupPath);
        }

        try (Stream<Path> stream =
                     Files.walk(backupPath)) {

            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {

                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }

                    })
                    .sum();
        }
    }


    /**
     * 获取整个世界备份文件夹大小。
     */
    public static long getTotalBackupFolderSize(
            MinecraftServer server) throws IOException {

        return getDirectorySize(
                getBackupDirectory(server)
        );
    }


    /**
     * 获取整个服务器备份文件夹大小。
     */
    public static long getTotalServerBackupFolderSize(
            MinecraftServer server) throws IOException {

        return getDirectorySize(
                getServerBackupDirectory(server)
        );
    }


    /**
     * 获取目录大小。
     */
    private static long getDirectorySize(
            Path directory) throws IOException {

        if (!Files.exists(directory)) {
            return 0L;
        }

        if (Files.isRegularFile(directory)) {
            return Files.size(directory);
        }

        try (Stream<Path> stream =
                     Files.walk(directory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {

                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }

                    })
                    .sum();
        }
    }


    /**
     * 格式化文件大小。
     *
     * 例如：
     *
     * 1024 -> 1.00 KB
     * 1048576 -> 1.00 MB
     * 1073741824 -> 1.00 GB
     */
    public static String formatSize(
            long bytes) {

        if (bytes < 1024) {

            return bytes + " B";
        }

        double size =
                bytes;

        String[] units = {
                "B",
                "KB",
                "MB",
                "GB",
                "TB"
        };

        int unitIndex = 0;

        while (size >= 1024
                && unitIndex < units.length - 1) {

            size /= 1024;
            unitIndex++;
        }

        return String.format(
                "%.2f %s",
                size,
                units[unitIndex]
        );
    }


    // =========================================================
    // 文件复制
    // =========================================================

    /**
     * 复制世界目录。
     */
    private static void copyDirectory(
            Path source,
            Path target) throws IOException {

        Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir,
                            BasicFileAttributes attrs)
                            throws IOException {

                        Path targetDir =
                                target.resolve(
                                        source.relativize(
                                                dir
                                        )
                                );

                        Files.createDirectories(
                                targetDir
                        );

                        return FileVisitResult.CONTINUE;
                    }


                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs)
                            throws IOException {

                        String fileName =
                                file.getFileName()
                                        .toString();

                        if (isLockFile(fileName)) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path targetFile =
                                target.resolve(
                                        source.relativize(
                                                file
                                        )
                                );

                        Files.copy(
                                file,
                                targetFile,
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        return FileVisitResult.CONTINUE;
                    }


                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file,
                            IOException exc)
                            throws IOException {

                        throw new IOException(
                                "复制文件失败: "
                                        + file
                                        + " - "
                                        + exc.getMessage(),
                                exc
                        );
                    }
                }
        );
    }


    /**
     * 复制整个服务器。
     *
     * 特别注意：
     *
     * server-backups/
     * 不能再次复制进去。
     *
     * 否则会出现：
     *
     * server-backups/
     * └── backup1/
     *     └── server-backups/
     *         └── backup1/
     *             ...
     */
    private static void copyServerDirectory(
            Path source,
            Path target) throws IOException {

        Path serverBackupDir =
                source
                        .resolve(
                                SERVER_BACKUP_FOLDER_NAME
                        )
                        .normalize();

        Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir,
                            BasicFileAttributes attrs)
                            throws IOException {

                        Path normalizedDir =
                                dir.normalize();

                        /*
                         * 不复制 server-backups。
                         */
                        if (normalizedDir.equals(
                                serverBackupDir)) {

                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        Path targetDir =
                                target.resolve(
                                        source.relativize(
                                                dir
                                        )
                                );

                        Files.createDirectories(
                                targetDir
                        );

                        return FileVisitResult.CONTINUE;
                    }


                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs)
                            throws IOException {

                        String fileName =
                                file.getFileName()
                                        .toString();

                        /*
                         * 跳过锁文件。
                         */
                        if (isLockFile(fileName)) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path targetFile =
                                target.resolve(
                                        source.relativize(
                                                file
                                        )
                                );

                        Files.copy(
                                file,
                                targetFile,
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        return FileVisitResult.CONTINUE;
                    }


                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file,
                            IOException exc)
                            throws IOException {

                        throw new IOException(
                                "复制服务器文件失败: "
                                        + file
                                        + " - "
                                        + exc.getMessage(),
                                exc
                        );
                    }
                }
        );
    }


    // =========================================================
    // 删除目录
    // =========================================================

    private static void deleteDirectory(
            Path directory) throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs)
                            throws IOException {

                        Files.delete(file);

                        return FileVisitResult.CONTINUE;
                    }


                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir,
                            IOException exc)
                            throws IOException {

                        if (exc != null) {
                            throw exc;
                        }

                        Files.delete(dir);

                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }


    // =========================================================
    // 文件名
    // =========================================================

    private static String sanitizeName(
            String name) {

        if (name == null) {
            return "";
        }

        return name
                .trim()
                .replaceAll(
                        "[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]",
                        "_"
                );
    }


    private static boolean isLockFile(
            String fileName) {

        return fileName.equals("session.lock")
                || fileName.endsWith(".lock");
    }
}