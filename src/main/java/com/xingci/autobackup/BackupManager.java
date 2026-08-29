package com.xingci.autobackup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class BackupManager {

    private static final String BACKUP_FOLDER_NAME =
            "backups";

    private static final String SERVER_BACKUP_FOLDER_NAME =
            "server-backups";

    private static final int WORLD_BACKUP_LIMIT =
            20;

    private static final int SERVER_BACKUP_LIMIT =
            5;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HHmmss"
            );

    public static BackupResult createBackupWithCleanup(
            MinecraftServer server) throws IOException {

        return createBackupWithCleanup(
                server,
                null
        );
    }

    public static BackupResult createBackupWithCleanup(
            MinecraftServer server,
            String customName) throws IOException {

        Path backupDir =
                getBackupDirectory(server);

        Files.createDirectories(backupDir);

        BackupResult deleted =
                deleteOldestIfLimitReached(
                        backupDir,
                        WORLD_BACKUP_LIMIT
                );

        String backupName =
                customName == null
                        ? generateBackupName(
                                getWorldName(server),
                                getBackupCountForToday(server) + 1
                        )
                        : customName;

        String createdName =
                createBackup(
                        server,
                        backupName
                );

        Path backupPath =
                backupDir.resolve(createdName);

        long backupSize =
                getBackupSize(backupPath);

        return new BackupResult(
                createdName,
                backupSize,
                deleted.deletedName(),
                deleted.deletedSize(),
                getDirectorySize(backupDir)
        );
    }

    public static BackupResult createServerBackupWithCleanup(
            MinecraftServer server) throws IOException {

        return createServerBackupWithCleanup(
                server,
                null
        );
    }

    public static BackupResult createServerBackupWithCleanup(
            MinecraftServer server,
            String customName) throws IOException {

        Path backupDir =
                getServerBackupDirectory(server);

        Files.createDirectories(backupDir);

        BackupResult deleted =
                deleteOldestIfLimitReached(
                        backupDir,
                        SERVER_BACKUP_LIMIT
                );

        String backupName =
                customName == null
                        ? "autobackup-server-"
                                + LocalDateTime.now().format(DATE_FORMATTER)
                        : customName;

        String createdName =
                createServerBackup(
                        server,
                        backupName
                );

        Path backupPath =
                backupDir.resolve(createdName);

        long backupSize =
                getBackupSize(backupPath);

        return new BackupResult(
                createdName,
                backupSize,
                deleted.deletedName(),
                deleted.deletedSize(),
                getDirectorySize(backupDir)
        );
    }

    public static String createBackup(
            MinecraftServer server) throws IOException {

        return createBackupWithCleanup(server).backupName();
    }

    public static String createBackup(
            MinecraftServer server,
            String customName) throws IOException {

        Path worldPath =
                getWorldPath(server);

        Path backupDir =
                getBackupDirectory(server);

        Files.createDirectories(backupDir);

        String backupName =
                withFormatSuffix(
                        sanitizeName(customName),
                        BackupConfig.get().getBackupFormat()
                );

        Path backupPath =
                validateBackupPath(
                        backupDir,
                        backupName,
                        "备份"
                );

        if (Files.exists(backupPath)) {
            throw new IOException(
                    "备份已存在: " + backupName
            );
        }

        try {
            copyPath(
                    worldPath,
                    backupPath,
                    BackupConfig.get().getBackupFormat(),
                    null
            );
            return backupName;

        } catch (IOException e) {
            deleteIfExists(backupPath);
            throw e;
        }
    }

    public static String createServerBackup(
            MinecraftServer server) throws IOException {

        return createServerBackupWithCleanup(server).backupName();
    }

    public static String createServerBackup(
            MinecraftServer server,
            String customName) throws IOException {

        Path serverPath =
                getServerPath();

        Path backupDir =
                getServerBackupDirectory(server);

        Files.createDirectories(backupDir);

        String backupName =
                withFormatSuffix(
                        sanitizeName(customName),
                        BackupConfig.get().getBackupFormat()
                );

        Path backupPath =
                validateBackupPath(
                        backupDir,
                        backupName,
                        "服务器备份"
                );

        if (Files.exists(backupPath)) {
            throw new IOException(
                    "服务器备份已存在: " + backupName
            );
        }

        try {
            copyPath(
                    serverPath,
                    backupPath,
                    BackupConfig.get().getBackupFormat(),
                    getServerBackupDirectory(server)
            );
            return backupName;

        } catch (IOException e) {
            deleteIfExists(backupPath);
            throw e;
        }
    }

    public static List<String> listBackups(
            MinecraftServer server) throws IOException {

        return listBackupEntries(
                getBackupDirectory(server)
        ).stream()
                .map(BackupEntry::name)
                .toList();
    }

    public static List<String> listServerBackups(
            MinecraftServer server) throws IOException {

        return listBackupEntries(
                getServerBackupDirectory(server)
        ).stream()
                .map(BackupEntry::name)
                .toList();
    }

    public static List<BackupEntry> listBackupEntries(
            MinecraftServer server) throws IOException {

        return listBackupEntries(
                getBackupDirectory(server)
        );
    }

    public static List<BackupEntry> listServerBackupEntries(
            MinecraftServer server) throws IOException {

        return listBackupEntries(
                getServerBackupDirectory(server)
        );
    }

    public static void deleteBackup(
            MinecraftServer server,
            String backupName) throws IOException {

        deleteNamedBackup(
                getBackupDirectory(server),
                backupName,
                "备份"
        );
    }

    public static void deleteServerBackup(
            MinecraftServer server,
            String backupName) throws IOException {

        deleteNamedBackup(
                getServerBackupDirectory(server),
                backupName,
                "服务器备份"
        );
    }

    public static BackupResult deleteAllBackupsKeepLatest(
            MinecraftServer server) throws IOException {

        return deleteAllKeepLatest(
                getBackupDirectory(server)
        );
    }

    public static BackupResult deleteAllServerBackupsKeepLatest(
            MinecraftServer server) throws IOException {

        return deleteAllKeepLatest(
                getServerBackupDirectory(server)
        );
    }

    public static void restoreBackup(
            MinecraftServer server,
            String backupName) throws IOException {

        restorePath(
                resolveExistingBackup(
                        getBackupDirectory(server),
                        backupName,
                        "备份"
                ),
                getWorldPath(server)
        );
    }

    public static void restoreServerBackup(
            MinecraftServer server,
            String backupName) throws IOException {

        restorePath(
                resolveExistingBackup(
                        getServerBackupDirectory(server),
                        backupName,
                        "服务器备份"
                ),
                getServerPath()
        );
    }

    public static Path getBackupDirectory(
            MinecraftServer server) {

        Path worldPath =
                getWorldPath(server);

        Path savesDir =
                worldPath.getParent();

        if (savesDir == null) {
            throw new IllegalStateException(
                    "无法获取 saves 目录"
            );
        }

        return savesDir
                .resolve(BACKUP_FOLDER_NAME)
                .resolve(sanitizeName(getWorldName(server)))
                .normalize();
    }

    public static Path getServerBackupDirectory(
            MinecraftServer server) {

        return getServerPath()
                .resolve(SERVER_BACKUP_FOLDER_NAME)
                .normalize();
    }

    public static long getBackupSize(
            Path backupPath) throws IOException {

        if (!Files.exists(backupPath)) {
            return 0L;
        }

        if (Files.isRegularFile(backupPath)) {
            return Files.size(backupPath);
        }

        return getDirectorySize(backupPath);
    }

    public static long getTotalBackupFolderSize(
            MinecraftServer server) throws IOException {

        return getDirectorySize(
                getBackupDirectory(server)
        );
    }

    public static long getTotalServerBackupFolderSize(
            MinecraftServer server) throws IOException {

        return getDirectorySize(
                getServerBackupDirectory(server)
        );
    }

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
                Locale.ROOT,
                "%.2f %s",
                size,
                units[unitIndex]
        );
    }

    private static BackupResult deleteOldestIfLimitReached(
            Path backupDir,
            int limit) throws IOException {

        List<Path> backups =
                listBackupPaths(backupDir);

        if (backups.size() < limit) {
            return new BackupResult(
                    "",
                    0L,
                    "",
                    0L,
                    getDirectorySize(backupDir)
            );
        }

        Path oldest =
                backups.getFirst();

        long deletedSize =
                getBackupSize(oldest);

        String deletedName =
                oldest.getFileName().toString();

        deleteIfExists(oldest);

        return new BackupResult(
                "",
                0L,
                deletedName,
                deletedSize,
                getDirectorySize(backupDir)
        );
    }

    private static BackupResult deleteAllKeepLatest(
            Path backupDir) throws IOException {

        List<Path> backups =
                listBackupPaths(backupDir);

        if (backups.size() <= 1) {
            return new BackupResult(
                    "",
                    0L,
                    "",
                    0L,
                    getDirectorySize(backupDir)
            );
        }

        Path latest =
                backups.getLast();

        long deletedSize = 0L;
        int deletedCount = 0;

        for (Path backup : backups) {
            if (backup.equals(latest)) {
                continue;
            }

            deletedSize += getBackupSize(backup);
            deleteIfExists(backup);
            deletedCount++;
        }

        return new BackupResult(
                "",
                0L,
                "共删除 " + deletedCount + " 个备份，已保留 "
                        + latest.getFileName(),
                deletedSize,
                getDirectorySize(backupDir)
        );
    }

    private static List<BackupEntry> listBackupEntries(
            Path backupDir) throws IOException {

        List<BackupEntry> entries =
                new ArrayList<>();

        for (Path backup : listBackupPaths(backupDir)) {
            entries.add(
                    new BackupEntry(
                            backup.getFileName().toString(),
                            getBackupSize(backup)
                    )
            );
        }

        return entries;
    }

    private static List<Path> listBackupPaths(
            Path backupDir) throws IOException {

        List<Path> backups =
                new ArrayList<>();

        if (!Files.exists(backupDir)) {
            return backups;
        }

        try (Stream<Path> stream =
                     Files.list(backupDir)) {

            stream
                    .filter(path ->
                            Files.isDirectory(path)
                                    || path.toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path);
                        } catch (IOException e) {
                            return null;
                        }
                    }, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(backups::add);
        }

        return backups;
    }

    private static void deleteNamedBackup(
            Path backupDir,
            String backupName,
            String typeName) throws IOException {

        deleteIfExists(
                resolveExistingBackup(
                        backupDir,
                        backupName,
                        typeName
                )
        );
    }

    private static Path resolveExistingBackup(
            Path backupDir,
            String backupName,
            String typeName) throws IOException {

        String sanitizedName =
                sanitizeNameAllowZip(backupName);

        Path backupPath =
                validateBackupPath(
                        backupDir,
                        sanitizedName,
                        typeName
                );

        if (!Files.exists(backupPath)) {
            throw new IOException(
                    typeName + "不存在: " + sanitizedName
            );
        }

        if (!Files.isDirectory(backupPath)
                && !backupPath.toString().endsWith(".zip")) {
            throw new IOException(
                    typeName + "格式无效: " + sanitizedName
            );
        }

        return backupPath;
    }

    private static Path validateBackupPath(
            Path backupDir,
            String backupName,
            String typeName) throws IOException {

        if (backupName == null
                || backupName.isBlank()) {
            throw new IOException(
                    typeName + "名称不能为空"
            );
        }

        Path normalizedBackupDir =
                backupDir.normalize();

        Path backupPath =
                normalizedBackupDir
                        .resolve(backupName)
                        .normalize();

        if (!backupPath.getParent()
                .equals(normalizedBackupDir)) {
            throw new IOException(
                    "无效的" + typeName + "名称"
            );
        }

        return backupPath;
    }

    private static void restorePath(
            Path backupPath,
            Path targetPath) throws IOException {

        Path tempRestore =
                targetPath.resolveSibling(
                        targetPath.getFileName()
                                + "-autobackup-restore-tmp"
                );

        deleteIfExists(tempRestore);

        if (Files.isDirectory(backupPath)) {
            copyDirectory(
                    backupPath,
                    tempRestore,
                    null
            );
        } else {
            unzip(
                    backupPath,
                    tempRestore
            );
        }

        deleteIfExists(targetPath);
        Files.move(
                tempRestore,
                targetPath,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static void copyPath(
            Path source,
            Path target,
            BackupFormat format,
            Path skippedDirectory) throws IOException {

        if (format == BackupFormat.ZIP) {
            zipDirectory(
                    source,
                    target,
                    skippedDirectory
            );
        } else {
            copyDirectory(
                    source,
                    target,
                    skippedDirectory
            );
        }
    }

    private static void copyDirectory(
            Path source,
            Path target,
            Path skippedDirectory) throws IOException {

        Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory,
                            BasicFileAttributes attributes)
                            throws IOException {

                        if (shouldSkip(directory, skippedDirectory)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        Files.createDirectories(
                                target.resolve(
                                        source.relativize(directory)
                                )
                        );

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes)
                            throws IOException {

                        if (isLockFile(file.getFileName().toString())) {
                            return FileVisitResult.CONTINUE;
                        }

                        Files.copy(
                                file,
                                target.resolve(source.relativize(file)),
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

    private static void zipDirectory(
            Path source,
            Path zipPath,
            Path skippedDirectory) throws IOException {

        try (OutputStream outputStream =
                     Files.newOutputStream(zipPath);
             ZipOutputStream zipOutputStream =
                     new ZipOutputStream(outputStream)) {

            Files.walkFileTree(
                    source,
                    new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path directory,
                                BasicFileAttributes attributes)
                                throws IOException {

                            if (shouldSkip(directory, skippedDirectory)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file,
                                BasicFileAttributes attributes)
                                throws IOException {

                            if (isLockFile(file.getFileName().toString())) {
                                return FileVisitResult.CONTINUE;
                            }

                            ZipEntry entry =
                                    new ZipEntry(
                                            source.relativize(file)
                                                    .toString()
                                                    .replace('\\', '/')
                                    );

                            zipOutputStream.putNextEntry(entry);
                            Files.copy(file, zipOutputStream);
                            zipOutputStream.closeEntry();

                            return FileVisitResult.CONTINUE;
                        }
                    }
            );
        }
    }

    private static void unzip(
            Path zipPath,
            Path target) throws IOException {

        Files.createDirectories(target);

        try (InputStream inputStream =
                     Files.newInputStream(zipPath);
             ZipInputStream zipInputStream =
                     new ZipInputStream(inputStream)) {

            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path outputPath =
                        target.resolve(entry.getName())
                                .normalize();

                if (!outputPath.startsWith(target)) {
                    throw new IOException(
                            "zip 文件包含不安全路径: " + entry.getName()
                    );
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(
                            zipInputStream,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                zipInputStream.closeEntry();
            }
        }
    }

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

    private static void deleteIfExists(
            Path path) throws IOException {

        if (!Files.exists(path)) {
            return;
        }

        if (Files.isRegularFile(path)) {
            Files.delete(path);
            return;
        }

        Files.walkFileTree(
                path,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes)
                            throws IOException {

                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path directory,
                            IOException exception)
                            throws IOException {

                        if (exception != null) {
                            throw exception;
                        }

                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

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

    private static Path getServerPath() {
        return Path.of("")
                .toAbsolutePath()
                .normalize();
    }

    private static String getWorldName(
            MinecraftServer server) {

        Path worldPath =
                getWorldPath(server);

        if (worldPath.getFileName() == null) {
            return "world";
        }

        return worldPath
                .getFileName()
                .toString();
    }

    private static String generateBackupName(
            String worldName,
            int count) {

        return sanitizeName(worldName)
                + "_"
                + LocalDateTime.now().format(DATE_FORMATTER)
                + "_"
                + count;
    }

    private static int getBackupCountForToday(
            MinecraftServer server) {

        try {
            String prefix =
                    sanitizeName(getWorldName(server))
                            + "_"
                            + LocalDateTime.now()
                                    .format(DATE_FORMATTER)
                                    .substring(0, 10);

            return (int) listBackups(server)
                    .stream()
                    .filter(name -> name.startsWith(prefix))
                    .count();

        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean shouldSkip(
            Path directory,
            Path skippedDirectory) {

        return skippedDirectory != null
                && directory.normalize().startsWith(
                        skippedDirectory.normalize()
                );
    }

    private static String withFormatSuffix(
            String backupName,
            BackupFormat format) {

        if (backupName == null
                || backupName.isBlank()) {
            return "";
        }

        if (format == BackupFormat.ZIP
                && !backupName.endsWith(".zip")) {
            return backupName + ".zip";
        }

        return backupName;
    }

    private static String sanitizeNameAllowZip(
            String name) {

        String sanitized =
                sanitizeName(name);

        if (name != null
                && name.trim().endsWith(".zip")
                && !sanitized.endsWith(".zip")) {
            return sanitized + ".zip";
        }

        return sanitized;
    }

    private static String sanitizeName(
            String name) {

        if (name == null) {
            return "";
        }

        return name
                .trim()
                .replaceAll(
                        "[^a-zA-Z0-9_\\-\\.\\u4e00-\\u9fa5]",
                        "_"
                );
    }

    private static boolean isLockFile(
            String fileName) {

        return fileName.equals("session.lock")
                || fileName.endsWith(".lock");
    }
}
