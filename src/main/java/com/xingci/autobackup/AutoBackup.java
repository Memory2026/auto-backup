package com.xingci.autobackup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

public class AutoBackup implements ModInitializer {

    public static final String MOD_ID =
            "auto-backup";

    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);


    /**
     * 定时任务线程池。
     */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(2);


    /**
     * MinecraftServer 实例。
     */
    private static volatile MinecraftServer server;


    @Override
    public void onInitialize() {

        /*
         * 注册命令。
         */
        BackupCommand.register();

        LOGGER.info(
                "Auto Backup mod initialized!"
        );


        /*
         * 注册服务器启动事件。
         */
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED
                .register(
                        AutoBackup::onServerStarted
                );


        /*
         * 注册服务器停止事件。
         */
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING
                .register(
                        AutoBackup::onServerStopping
                );
    }


    /**
     * 服务器启动。
     */
    private static void onServerStarted(
            MinecraftServer minecraftServer) {

        server =
                minecraftServer;

        LOGGER.info(
                "Auto Backup scheduler started."
        );


        // =====================================================
        // 每 5 分钟自动备份世界
        // =====================================================

        SCHEDULER.scheduleAtFixedRate(
                AutoBackup::automaticWorldBackup,
                5,
                5,
                TimeUnit.MINUTES
        );


        // =====================================================
        // 每 60 分钟自动备份整个服务器
        // =====================================================

        SCHEDULER.scheduleAtFixedRate(
                AutoBackup::automaticServerBackup,
                60,
                60,
                TimeUnit.MINUTES
        );
    }


    /**
     * 每 5 分钟执行一次。
     */
    private static void automaticWorldBackup() {

        MinecraftServer currentServer =
                server;

        if (currentServer == null) {
            return;
        }


        /*
         * 在服务器聊天栏发送：
         *
         * 备份已开始
         */
        broadcast(
                currentServer,
                "备份已开始"
        );


        LOGGER.info(
                "Automatic world backup started."
        );


        /*
         * 文件复制属于 IO 操作。
         *
         * 使用虚拟线程执行。
         */
        Thread.startVirtualThread(() -> {

            try {

                /*
                 * 创建备份。
                 */
                String backupName =
                        BackupManager.createBackup(
                                currentServer
                        );


                /*
                 * 获取刚刚创建的备份路径。
                 */
                Path backupPath =
                        BackupManager
                                .getBackupDirectory(
                                        currentServer
                                )
                                .resolve(
                                        backupName
                                );


                /*
                 * 获取本次备份大小。
                 */
                long backupSize =
                        BackupManager.getBackupSize(
                                backupPath
                        );


                /*
                 * 获取整个世界备份文件夹大小。
                 */
                long totalSize =
                        BackupManager
                                .getTotalBackupFolderSize(
                                        currentServer
                                );


                String message =
                        "服务器备份已完成备份文件夹大小："
                                + backupName
                                + " | 备份文件夹大小："
                                + BackupManager.formatSize(
                                backupSize
                        )
                                + " | 总备份文件夹大小："
                                + BackupManager.formatSize(
                                totalSize
                        );


                /*
                 * 在服务器主线程发送聊天消息。
                 */
                broadcast(
                        currentServer,
                        message
                );


                LOGGER.info(
                        message
                );


            } catch (IOException e) {

                LOGGER.error(
                        "Automatic world backup failed.",
                        e
                );


                broadcast(
                        currentServer,
                        "备份失败："
                                + e.getMessage()
                );
            }
        });
    }


    /**
     * 每 60 分钟执行一次。
     */
    private static void automaticServerBackup() {

        MinecraftServer currentServer =
                server;

        if (currentServer == null) {
            return;
        }


        /*
         * 开始备份时发送：
         *
         * 服务器备份已开始
         */
        broadcast(
                currentServer,
                "服务器备份已开始"
        );


        LOGGER.info(
                "Automatic server backup started."
        );


        /*
         * 使用虚拟线程。
         */
        Thread.startVirtualThread(() -> {

            try {

                /*
                 * 创建整个服务器备份。
                 */
                String backupName =
                        BackupManager.createServerBackup(
                                currentServer
                        );


                /*
                 * 获取本次服务器备份路径。
                 */
                Path backupPath =
                        BackupManager
                                .getServerBackupDirectory(
                                        currentServer
                                )
                                .resolve(
                                        backupName
                                );


                /*
                 * 获取本次备份大小。
                 */
                long backupSize =
                        BackupManager.getBackupSize(
                                backupPath
                        );


                /*
                 * 获取整个 server-backups
                 * 文件夹大小。
                 */
                long totalSize =
                        BackupManager
                                .getTotalServerBackupFolderSize(
                                        currentServer
                                );


                String message =
                        "服务器备份已完成："
                                + backupName
                                + " | 备份文件夹大小："
                                + BackupManager.formatSize(
                                backupSize
                        )
                                + " | 总备份文件夹大小："
                                + BackupManager.formatSize(
                                totalSize
                        );


                /*
                 * 发送到服务器聊天栏。
                 */
                broadcast(
                        currentServer,
                        message
                );


                LOGGER.info(
                        message
                );


            } catch (IOException e) {

                LOGGER.error(
                        "服务器自动备份失败。",
                        e
                );


                broadcast(
                        currentServer,
                        "服务器备份失败："
                                + e.getMessage()
                );
            }
        });
    }


    /**
     * 向服务器聊天栏广播消息。
     *
     * 所有在线玩家都能看到。
     */
    private static void broadcast(
            MinecraftServer server,
            String message) {

        /*
         * Minecraft 的部分操作必须在服务器线程执行。
         */
        server.execute(() -> {

            server.getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(message),
                            false
                    );
        });
    }


    /**
     * 服务器停止。
     */
    private static void onServerStopping(
            MinecraftServer minecraftServer) {

        server = null;

        LOGGER.info(
                "Auto Backup scheduler stopping..."
        );

        /*
         * 停止定时任务。
         */
        SCHEDULER.shutdownNow();
    }


    /**
     * Mod ID。
     */
    public static Identifier id(
            String path) {

        return Identifier.fromNamespaceAndPath(
                MOD_ID,
                path
        );
    }
}