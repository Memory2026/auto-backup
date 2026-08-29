package com.xingci.autobackup;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public class BackupCommand {

    private static final Permission BACKUP_PERMISSION =
            new Permission.HasCommandLevel(PermissionLevel.ALL);

    private static final SuggestionProvider<CommandSourceStack> BACKUP_SUGGESTIONS =
            (context, builder) -> suggestBackups(
                    context,
                    builder,
                    false
            );

    private static final SuggestionProvider<CommandSourceStack> SERVER_BACKUP_SUGGESTIONS =
            (context, builder) -> suggestBackups(
                    context,
                    builder,
                    true
            );

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, selection) -> dispatcher.register(
                        Commands.literal("backup")
                                .requires(source ->
                                        source.permissions()
                                                .hasPermission(BACKUP_PERMISSION))
                                .executes(BackupCommand::executeBackup)
                                .then(Commands.literal("create")
                                        .then(Commands.argument(
                                                        "name",
                                                        StringArgumentType.string())
                                                .executes(BackupCommand::executeBackupCreate)))
                                .then(Commands.literal("list")
                                        .executes(BackupCommand::executeBackupList))
                                .then(Commands.literal("del")
                                        .then(Commands.literal("all")
                                                .executes(BackupCommand::executeBackupDeleteAll))
                                        .then(Commands.argument(
                                                        "name",
                                                        StringArgumentType.string())
                                                .suggests(BACKUP_SUGGESTIONS)
                                                .executes(BackupCommand::executeBackupDelete)))
                                .then(Commands.literal("reload")
                                        .then(Commands.argument(
                                                        "name",
                                                        StringArgumentType.string())
                                                .suggests(BACKUP_SUGGESTIONS)
                                                .executes(BackupCommand::executeBackupReload)))
                                .then(Commands.literal("memory")
                                        .executes(BackupCommand::executeBackupMemory)
                                        .then(Commands.literal("list")
                                                .executes(BackupCommand::executeBackupMemoryList)))
                                .then(Commands.literal("gui")
                                        .executes(BackupCommand::executeBackupGui))
                                .then(Commands.literal("server")
                                        .executes(BackupCommand::executeServerBackup)
                                        .then(Commands.literal("create")
                                                .then(Commands.argument(
                                                                "name",
                                                                StringArgumentType.string())
                                                        .executes(BackupCommand::executeServerBackupCreate)))
                                        .then(Commands.literal("list")
                                                .executes(BackupCommand::executeServerBackupList))
                                        .then(Commands.literal("del")
                                                .then(Commands.literal("all")
                                                        .executes(BackupCommand::executeServerBackupDeleteAll))
                                                .then(Commands.argument(
                                                                "name",
                                                                StringArgumentType.string())
                                                        .suggests(SERVER_BACKUP_SUGGESTIONS)
                                                        .executes(BackupCommand::executeServerBackupDelete)))
                                        .then(Commands.literal("reload")
                                                .then(Commands.argument(
                                                                "name",
                                                                StringArgumentType.string())
                                                        .suggests(SERVER_BACKUP_SUGGESTIONS)
                                                        .executes(BackupCommand::executeServerBackupReload)))
                                        .then(Commands.literal("memory")
                                                .executes(BackupCommand::executeServerBackupMemory)
                                                .then(Commands.literal("list")
                                                        .executes(BackupCommand::executeServerBackupMemoryList))))
                )
        );
    }

    private static int executeBackup(
            CommandContext<CommandSourceStack> context) {

        try {
            BackupResult result =
                    BackupManager.createBackupWithCleanup(
                            context.getSource().getServer()
                    );

            sendSuccess(
                    context,
                    AutoBackup.formatWorldBackupMessage(result)
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "创建世界备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeBackupCreate(
            CommandContext<CommandSourceStack> context) {

        String name =
                StringArgumentType.getString(context, "name");

        try {
            BackupResult result =
                    BackupManager.createBackupWithCleanup(
                            context.getSource().getServer(),
                            name
                    );

            sendSuccess(
                    context,
                    AutoBackup.formatWorldBackupMessage(result)
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "创建世界备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeBackupList(
            CommandContext<CommandSourceStack> context) {

        return sendNameList(
                context,
                false
        );
    }

    private static int executeBackupDelete(
            CommandContext<CommandSourceStack> context) {

        try {
            String name =
                    StringArgumentType.getString(context, "name");

            BackupManager.deleteBackup(
                    context.getSource().getServer(),
                    name
            );

            sendSuccess(
                    context,
                    "世界备份已删除: " + name
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "删除世界备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeBackupDeleteAll(
            CommandContext<CommandSourceStack> context) {

        try {
            BackupResult result =
                    BackupManager.deleteAllBackupsKeepLatest(
                            context.getSource().getServer()
                    );

            sendSuccess(
                    context,
                    "世界备份清理完成 | "
                            + result.deletedName()
                            + " | 删除大小："
                            + BackupManager.formatSize(result.deletedSize())
                            + " | 当前总大小："
                            + BackupManager.formatSize(result.totalSize())
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "清理世界备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeBackupReload(
            CommandContext<CommandSourceStack> context) {

        String name =
                StringArgumentType.getString(context, "name");

        sendSuccess(
                context,
                "正在恢复世界备份，完成后服务器会自动重启: " + name
        );

        Thread.startVirtualThread(() -> {
            try {
                MinecraftServer server =
                        context.getSource().getServer();

                server.saveEverything(
                        false,
                        true,
                        true
                );
                BackupManager.restoreBackup(
                        server,
                        name
                );
                server.halt(true);

            } catch (IOException e) {
                AutoBackup.LOGGER.error(
                        "恢复世界备份失败",
                        e
                );
                context.getSource().sendFailure(
                        Component.literal(
                                "恢复世界备份失败: " + e.getMessage()
                        )
                );
            }
        });

        return 1;
    }

    private static int executeBackupMemory(
            CommandContext<CommandSourceStack> context) {

        try {
            long size =
                    BackupManager.getTotalBackupFolderSize(
                            context.getSource().getServer()
                    );

            sendSuccess(
                    context,
                    "世界备份文件夹总大小："
                            + BackupManager.formatSize(size)
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "统计世界备份大小失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeBackupMemoryList(
            CommandContext<CommandSourceStack> context) {

        return sendMemoryList(
                context,
                false
        );
    }

    private static int executeBackupGui(
            CommandContext<CommandSourceStack> context) {

        sendSuccess(
                context,
                "请在客户端聊天栏输入 !backup gui 打开图形化配置界面。"
        );
        return 1;
    }

    private static int executeServerBackup(
            CommandContext<CommandSourceStack> context) {

        sendSuccess(
                context,
                "正在备份整个服务器，请稍候..."
        );

        Thread.startVirtualThread(() -> {
            try {
                BackupResult result =
                        BackupManager.createServerBackupWithCleanup(
                                context.getSource().getServer()
                        );

                sendSuccess(
                        context,
                        AutoBackup.formatServerBackupMessage(result)
                );

            } catch (IOException e) {
                AutoBackup.LOGGER.error(
                        "整个服务器备份失败",
                        e
                );
                context.getSource().sendFailure(
                        Component.literal(
                                "整个服务器备份失败: " + e.getMessage()
                        )
                );
            }
        });

        return 1;
    }

    private static int executeServerBackupCreate(
            CommandContext<CommandSourceStack> context) {

        String name =
                StringArgumentType.getString(context, "name");

        sendSuccess(
                context,
                "正在创建服务器备份: " + name
        );

        Thread.startVirtualThread(() -> {
            try {
                BackupResult result =
                        BackupManager.createServerBackupWithCleanup(
                                context.getSource().getServer(),
                                name
                        );

                sendSuccess(
                        context,
                        AutoBackup.formatServerBackupMessage(result)
                );

            } catch (IOException e) {
                AutoBackup.LOGGER.error(
                        "自定义服务器备份失败",
                        e
                );
                context.getSource().sendFailure(
                        Component.literal(
                                "整个服务器备份失败: " + e.getMessage()
                        )
                );
            }
        });

        return 1;
    }

    private static int executeServerBackupList(
            CommandContext<CommandSourceStack> context) {

        return sendNameList(
                context,
                true
        );
    }

    private static int executeServerBackupDelete(
            CommandContext<CommandSourceStack> context) {

        try {
            String name =
                    StringArgumentType.getString(context, "name");

            BackupManager.deleteServerBackup(
                    context.getSource().getServer(),
                    name
            );

            sendSuccess(
                    context,
                    "服务器备份已删除: " + name
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "删除服务器备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeServerBackupDeleteAll(
            CommandContext<CommandSourceStack> context) {

        try {
            BackupResult result =
                    BackupManager.deleteAllServerBackupsKeepLatest(
                            context.getSource().getServer()
                    );

            sendSuccess(
                    context,
                    "服务器备份清理完成 | "
                            + result.deletedName()
                            + " | 删除大小："
                            + BackupManager.formatSize(result.deletedSize())
                            + " | 当前总大小："
                            + BackupManager.formatSize(result.totalSize())
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "清理服务器备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeServerBackupReload(
            CommandContext<CommandSourceStack> context) {

        String name =
                StringArgumentType.getString(context, "name");

        sendSuccess(
                context,
                "正在恢复服务器备份，完成后服务器会自动重启: " + name
        );

        Thread.startVirtualThread(() -> {
            try {
                MinecraftServer server =
                        context.getSource().getServer();

                server.saveEverything(
                        false,
                        true,
                        true
                );
                BackupManager.restoreServerBackup(
                        server,
                        name
                );
                server.halt(true);

            } catch (IOException e) {
                AutoBackup.LOGGER.error(
                        "恢复服务器备份失败",
                        e
                );
                context.getSource().sendFailure(
                        Component.literal(
                                "恢复服务器备份失败: " + e.getMessage()
                        )
                );
            }
        });

        return 1;
    }

    private static int executeServerBackupMemory(
            CommandContext<CommandSourceStack> context) {

        try {
            long size =
                    BackupManager.getTotalServerBackupFolderSize(
                            context.getSource().getServer()
                    );

            sendSuccess(
                    context,
                    "服务器备份文件夹总大小："
                            + BackupManager.formatSize(size)
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "统计服务器备份大小失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int executeServerBackupMemoryList(
            CommandContext<CommandSourceStack> context) {

        return sendMemoryList(
                context,
                true
        );
    }

    private static int sendNameList(
            CommandContext<CommandSourceStack> context,
            boolean serverBackups) {

        try {
            List<String> backups =
                    serverBackups
                            ? BackupManager.listServerBackups(
                                    context.getSource().getServer())
                            : BackupManager.listBackups(
                                    context.getSource().getServer());

            if (backups.isEmpty()) {
                sendSuccess(
                        context,
                        serverBackups
                                ? "当前没有服务器备份。"
                                : "当前世界没有备份。"
                );
                return 1;
            }

            StringBuilder message =
                    new StringBuilder(
                            serverBackups
                                    ? "服务器备份列表：\n"
                                    : "世界备份列表：\n"
                    );

            backups.forEach(backup ->
                    message.append("  - ")
                            .append(backup)
                            .append("\n"));

            sendSuccess(
                    context,
                    message.toString()
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "列出备份失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static int sendMemoryList(
            CommandContext<CommandSourceStack> context,
            boolean serverBackups) {

        try {
            List<BackupEntry> entries =
                    serverBackups
                            ? BackupManager.listServerBackupEntries(
                                    context.getSource().getServer())
                            : BackupManager.listBackupEntries(
                                    context.getSource().getServer());

            if (entries.isEmpty()) {
                sendSuccess(
                        context,
                        serverBackups
                                ? "服务器备份文件夹为空。"
                                : "世界备份文件夹为空。"
                );
                return 1;
            }

            StringBuilder message =
                    new StringBuilder(
                            serverBackups
                                    ? "服务器备份文件大小列表（最新在最下面）：\n"
                                    : "世界备份文件大小列表（最新在最下面）：\n"
                    );

            for (BackupEntry entry : entries) {
                message.append("  - ")
                        .append(entry.name())
                        .append(" | ")
                        .append(BackupManager.formatSize(entry.size()))
                        .append("\n");
            }

            sendSuccess(
                    context,
                    message.toString()
            );
            return 1;

        } catch (IOException e) {
            return fail(
                    context,
                    "统计备份列表失败: " + e.getMessage(),
                    e
            );
        }
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBackups(
            CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            boolean serverBackups) {

        try {
            List<String> backups =
                    serverBackups
                            ? BackupManager.listServerBackups(
                                    context.getSource().getServer())
                            : BackupManager.listBackups(
                                    context.getSource().getServer());

            return SharedSuggestionProvider.suggest(
                    backups,
                    builder
            );

        } catch (IOException e) {
            AutoBackup.LOGGER.warn(
                    "获取备份补全列表失败: {}",
                    e.getMessage()
            );
            return builder.buildFuture();
        }
    }

    private static void sendSuccess(
            CommandContext<CommandSourceStack> context,
            String message) {

        context.getSource().sendSuccess(
                () -> Component.literal(message),
                false
        );
    }

    private static int fail(
            CommandContext<CommandSourceStack> context,
            String message,
            Exception exception) {

        context.getSource().sendFailure(
                Component.literal(message)
        );

        AutoBackup.LOGGER.error(
                message,
                exception
        );

        return 0;
    }
}
