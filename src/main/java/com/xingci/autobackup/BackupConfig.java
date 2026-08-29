package com.xingci.autobackup;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class BackupConfig {

    private static final Path CONFIG_PATH =
            Path.of("config", "auto-backup.properties")
                    .toAbsolutePath()
                    .normalize();

    private static final BackupConfig INSTANCE =
            new BackupConfig();

    private int worldIntervalMinutes = 5;
    private int serverIntervalMinutes = 60;
    private BackupFormat backupFormat = BackupFormat.FOLDER;

    public static BackupConfig get() {
        return INSTANCE;
    }

    public static void load() {
        INSTANCE.read();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            Properties properties =
                    new Properties();

            properties.setProperty(
                    "worldIntervalMinutes",
                    Integer.toString(worldIntervalMinutes)
            );
            properties.setProperty(
                    "serverIntervalMinutes",
                    Integer.toString(serverIntervalMinutes)
            );
            properties.setProperty(
                    "backupFormat",
                    backupFormat.name().toLowerCase()
            );

            try (Writer writer =
                         Files.newBufferedWriter(CONFIG_PATH)) {

                properties.store(
                        writer,
                        "Auto Backup configuration"
                );
            }

        } catch (IOException e) {
            AutoBackup.LOGGER.error(
                    "保存自动备份配置失败",
                    e
            );
        }
    }

    private synchronized void read() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        Properties properties =
                new Properties();

        try (Reader reader =
                     Files.newBufferedReader(CONFIG_PATH)) {

            properties.load(reader);

            worldIntervalMinutes =
                    positiveInt(
                            properties.getProperty("worldIntervalMinutes"),
                            5
                    );
            serverIntervalMinutes =
                    positiveInt(
                            properties.getProperty("serverIntervalMinutes"),
                            60
                    );
            backupFormat =
                    BackupFormat.fromString(
                            properties.getProperty("backupFormat")
                    );

        } catch (IOException e) {
            AutoBackup.LOGGER.error(
                    "读取自动备份配置失败，使用默认配置",
                    e
            );
        }
    }

    public synchronized int getWorldIntervalMinutes() {
        return worldIntervalMinutes;
    }

    public synchronized void setWorldIntervalMinutes(
            int worldIntervalMinutes) {

        this.worldIntervalMinutes =
                Math.max(1, worldIntervalMinutes);
    }

    public synchronized int getServerIntervalMinutes() {
        return serverIntervalMinutes;
    }

    public synchronized void setServerIntervalMinutes(
            int serverIntervalMinutes) {

        this.serverIntervalMinutes =
                Math.max(1, serverIntervalMinutes);
    }

    public synchronized BackupFormat getBackupFormat() {
        return backupFormat;
    }

    public synchronized void setBackupFormat(
            BackupFormat backupFormat) {

        this.backupFormat =
                backupFormat == null
                        ? BackupFormat.FOLDER
                        : backupFormat;
    }

    private static int positiveInt(
            String value,
            int fallback) {

        try {
            int parsed =
                    Integer.parseInt(value);

            return Math.max(1, parsed);

        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
