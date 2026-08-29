package com.xingci.autobackup;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    private static ScheduledFuture<?> worldBackupTask;

    private static ScheduledFuture<?> serverBackupTask;


    @Override
    public void onInitialize() {

        BackupConfig.load();

        /*
         * 注册命令。
         */
        BackupCommand.register();
        registerBangCommands();

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

        reloadSchedules();
    }

    public static void reloadSchedules() {
        MinecraftServer currentServer =
                server;

        if (currentServer == null) {
            return;
        }

        if (worldBackupTask != null) {
            worldBackupTask.cancel(false);
        }

        if (serverBackupTask != null) {
            serverBackupTask.cancel(false);
        }

        BackupConfig config =
                BackupConfig.get();

        worldBackupTask =
                SCHEDULER.scheduleAtFixedRate(
                        AutoBackup::automaticWorldBackup,
                        config.getWorldIntervalMinutes(),
                        config.getWorldIntervalMinutes(),
                        TimeUnit.MINUTES
                );

        serverBackupTask =
                SCHEDULER.scheduleAtFixedRate(
                AutoBackup::automaticServerBackup,
                config.getServerIntervalMinutes(),
                config.getServerIntervalMinutes(),
                TimeUnit.MINUTES
        );
    }

    private static void registerBangCommands() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                (message, sender, boundChatType) -> {

                    String content =
                            message.signedContent();

                    if (!content.startsWith("!backup")) {
                        return true;
                    }

                    executeBangCommand(
                            sender,
                            content.substring(1)
                    );

                    return false;
                }
        );
    }

    private static void executeBangCommand(
            ServerPlayer player,
            String command) {

        MinecraftServer currentServer =
                player.level().getServer();

        if (currentServer == null) {
            return;
        }

        currentServer.execute(() ->
                currentServer.getCommands()
                        .performPrefixedCommand(
                                player.createCommandSourceStack(),
                                "/" + command
                        )
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
                BackupResult result =
                        BackupManager.createBackupWithCleanup(
                                currentServer
                        );


                /*
                 * 获取刚刚创建的备份路径。
                 */
                String message =
                        formatWorldBackupMessage(result);


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
                BackupResult result =
                        BackupManager.createServerBackupWithCleanup(
                                currentServer
                        );


                /*
                 * 获取本次服务器备份路径。
                 */
                String message =
                        formatServerBackupMessage(result);


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

    public static String formatWorldBackupMessage(
            BackupResult result) {

        return "世界备份已完成"
                + " | 删除文件："
                + deletedText(result)
                + " | 世界已备份："
                + result.backupName()
                + " "
                + BackupManager.formatSize(result.backupSize())
                + " | 世界总备份文件夹大小："
                + BackupManager.formatSize(result.totalSize());
    }

    public static String formatServerBackupMessage(
            BackupResult result) {

        return "服务器备份已完成"
                + " | 删除服务器备份文件："
                + deletedText(result)
                + " | 服务器已备份："
                + result.backupName()
                + " "
                + BackupManager.formatSize(result.backupSize())
                + " | 总服务器备份文件夹大小："
                + BackupManager.formatSize(result.totalSize());
    }

    private static String deletedText(
            BackupResult result) {

        if (!result.deletedBackup()) {
            return "无";
        }

        return result.deletedName()
                + " "
                + BackupManager.formatSize(result.deletedSize());
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
        if (worldBackupTask != null) {
            worldBackupTask.cancel(false);
            worldBackupTask = null;
        }

        if (serverBackupTask != null) {
            serverBackupTask.cancel(false);
            serverBackupTask = null;
        }
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
