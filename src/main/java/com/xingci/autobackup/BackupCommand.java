package com.xingci.autobackup;

import java.io.IOException;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

    // =========================================================
    // 权限
    // =========================================================

    private static final Permission BACKUP_PERMISSION =
            new Permission.HasCommandLevel(PermissionLevel.ALL);


    // =========================================================
    // 世界备份 Tab 补全
    // =========================================================

    /**
     * /backup del <TAB>
     *
     * 获取当前世界的所有备份名称。
     */
    private static final SuggestionProvider<CommandSourceStack> BACKUP_SUGGESTIONS =
            (context, builder) -> {

                MinecraftServer server =
                        context.getSource().getServer();

                try {

                    List<String> backups =
                            BackupManager.listBackups(server);

                    return SharedSuggestionProvider.suggest(
                            backups,
                            builder
                    );

                } catch (IOException e) {

                    AutoBackup.LOGGER.warn(
                            "Failed to obtain the world backup list: {}",
                            e.getMessage()
                    );

                    return builder.buildFuture();
                }
            };


    // =========================================================
    // 服务器备份 Tab 补全
    // =========================================================

    /**
     * /backup server del <TAB>
     *
     * 获取所有整个服务器备份名称。
     */
    private static final SuggestionProvider<CommandSourceStack> SERVER_BACKUP_SUGGESTIONS =
            (context, builder) -> {

                MinecraftServer server =
                        context.getSource().getServer();

                try {

                    List<String> backups =
                            BackupManager.listServerBackups(server);

                    return SharedSuggestionProvider.suggest(
                            backups,
                            builder
                    );

                } catch (IOException e) {

                    AutoBackup.LOGGER.warn(
                            "Failed to obtain the server backup list: {}",
                            e.getMessage()
                    );

                    return builder.buildFuture();
                }
            };


    // =========================================================
    // 注册命令
    // =========================================================

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, selection) -> {

                    // =================================================
                    // /backup
                    // =================================================

                    LiteralArgumentBuilder<CommandSourceStack> backupCommand =
                            Commands.literal("backup")
                                    .requires(source ->
                                            source.permissions()
                                                    .hasPermission(
                                                            BACKUP_PERMISSION
                                                    )
                                    )
                                    .executes(
                                            BackupCommand::executeBackup
                                    );


                    // =================================================
                    // /backup create <name>
                    // =================================================

                    backupCommand.then(
                            Commands.literal("create")
                                    .then(
                                            Commands.argument(
                                                            "name",
                                                            StringArgumentType.string()
                                                    )
                                                    .executes(
                                                            BackupCommand::executeBackupCreate
                                                    )
                                    )
                    );


                    // =================================================
                    // /backup list
                    // =================================================

                    backupCommand.then(
                            Commands.literal("list")
                                    .executes(
                                            BackupCommand::executeBackupList
                                    )
                    );


                    // =================================================
                    // /backup del <name>
                    // =================================================

                    backupCommand.then(
                            Commands.literal("del")
                                    .then(
                                            Commands.argument(
                                                            "name",
                                                            StringArgumentType.string()
                                                    )

                                                    // Tab 自动补全
                                                    .suggests(
                                                            BACKUP_SUGGESTIONS
                                                    )

                                                    .executes(
                                                            BackupCommand::executeBackupDelete
                                                    )
                                    )
                    );


                    // =================================================
                    // /backup server
                    // =================================================

                    backupCommand.then(
                            Commands.literal("server")
                                    .executes(
                                            BackupCommand::executeServerBackup
                                    )
                    );


                    // =================================================
                    // /backup server create <name>
                    // =================================================

                    backupCommand.then(
                            Commands.literal("server")
                                    .then(
                                            Commands.literal("create")
                                                    .then(
                                                            Commands.argument(
                                                                            "name",
                                                                            StringArgumentType.string()
                                                                    )
                                                                    .executes(
                                                                            BackupCommand::executeServerBackupCreate
                                                                    )
                                                    )
                                    )
                    );


                    // =================================================
                    // /backup server list
                    // =================================================

                    backupCommand.then(
                            Commands.literal("server")
                                    .then(
                                            Commands.literal("list")
                                                    .executes(
                                                            BackupCommand::executeServerBackupList
                                                    )
                                    )
                    );


                    // =================================================
                    // /backup server del <name>
                    // =================================================

                    backupCommand.then(
                            Commands.literal("server")
                                    .then(
                                            Commands.literal("del")
                                                    .then(
                                                            Commands.argument(
                                                                            "name",
                                                                            StringArgumentType.string()
                                                                    )

                                                                    // Tab 自动补全
                                                                    .suggests(
                                                                            SERVER_BACKUP_SUGGESTIONS
                                                                    )

                                                                    .executes(
                                                                            BackupCommand::executeServerBackupDelete
                                                                    )
                                                    )
                                    )
                    );


                    // =================================================
                    // 注册
                    // =================================================

                    dispatcher.register(
                            backupCommand
                    );
                }
        );
    }


    // =========================================================
    // 世界备份
    // =========================================================

    /**
     * /backup
     *
     * 自动创建一个世界备份。
     */
    private static int executeBackup(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();

        try {

            String backupName =
                    BackupManager.createBackup(
                            server
                    );

            source.sendSuccess(
                    () -> Component.literal(
                            "The backup has been created.: "
                                    + backupName
                    ),
                    false
            );

            return 1;

        } catch (IOException e) {

            source.sendFailure(
                    Component.literal(
                            "Backup creation failed: "
                                    + e.getMessage()
                    )
            );

            AutoBackup.LOGGER.error(
                    "The world backup creation failed",
                    e
            );

            return 0;
        }
    }


    /**
     * /backup create <name>
     *
     * 创建指定名称的世界备份。
     */
    private static int executeBackupCreate(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();

        String name =
                StringArgumentType.getString(
                        context,
                        "name"
                );


        if (name.trim().isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The backup name cannot be empty!"
                    )
            );

            return 0;
        }


        String sanitizedName =
                sanitizeName(name);


        if (sanitizedName.isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The backup name is invalid!"
                    )
            );

            return 0;
        }


        try {

            String backupName =
                    BackupManager.createBackup(
                            server,
                            sanitizedName
                    );

            source.sendSuccess(
                    () -> Component.literal(
                            "The backup has been created: "
                                    + backupName
                    ),
                    false
            );

            return 1;

        } catch (IOException e) {

            source.sendFailure(
                    Component.literal(
                            "Failed to create a backup: "
                                    + e.getMessage()
                    )
            );

            AutoBackup.LOGGER.error(
                    "The backup for creating a custom world failed",
                    e
            );

            return 0;
        }
    }


    /**
     * /backup list
     *
     * 查看当前世界的所有备份。
     */
    private static int executeBackupList(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();

        try {

            List<String> backups =
                    BackupManager.listBackups(
                            server
                    );


            if (backups.isEmpty()) {

                source.sendSuccess(
                        () -> Component.literal(
                                "There is no backup in the current world"
                        ),
                        false
                );

                return 1;
            }


            StringBuilder message =
                    new StringBuilder();

            message.append(
                    "World Backup List:\n"
            );


            for (String backup : backups) {

                message
                        .append("  - ")
                        .append(backup)
                        .append("\n");
            }


            source.sendSuccess(
                    () -> Component.literal(
                            message.toString()
                    ),
                    false
            );

            return 1;

        } catch (IOException e) {

            source.sendFailure(
                    Component.literal(
                            "List backup failures: "
                                    + e.getMessage()
                    )
            );

            return 0;
        }
    }


    /**
     * /backup del <name>
     *
     * 删除世界备份。
     */
    private static int executeBackupDelete(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();

        String name =
                StringArgumentType.getString(
                        context,
                        "name"
                );


        if (name.trim().isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The backup name cannot be empty!"
                    )
            );

            return 0;
        }


        String sanitizedName =
                sanitizeName(name);


        if (sanitizedName.isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The backup name is invalid!"
                    )
            );

            return 0;
        }


        try {

            BackupManager.deleteBackup(
                    server,
                    sanitizedName
            );

            source.sendSuccess(
                    () -> Component.literal(
                            "The backup has been deleted: "
                                    + sanitizedName
                    ),
                    false
            );

            return 1;

        } catch (IOException e) {

            source.sendFailure(
                    Component.literal(
                            "Failed to delete the backup: "
                                    + e.getMessage()
                    )
            );

            return 0;
        }
    }


    // =========================================================
    // 整个服务器备份
    // =========================================================

    /**
     * /backup server
     *
     * 自动备份整个 Minecraft 服务器。
     */
    private static int executeServerBackup(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();


        source.sendSuccess(
                () -> Component.literal(
                        "The entire server is being backed up. Please wait a moment..."
                ),
                false
        );


        /*
         * 使用虚拟线程进行文件复制。
         *
         * 防止大型服务器备份时卡住主线程。
         */
        Thread.startVirtualThread(() -> {

            try {

                String backupName =
                        BackupManager.createServerBackup(
                                server
                        );


                source.sendSuccess(
                        () -> Component.literal(
                                "The entire server backup has been completed: "
                                        + backupName
                        ),
                        false
                );


            } catch (IOException e) {

                AutoBackup.LOGGER.error(
                        "The entire server backup failed",
                        e
                );


                source.sendFailure(
                        Component.literal(
                                "The entire server backup failed: "
                                        + e.getMessage()
                        )
                );
            }
        });


        return 1;
    }


    /**
     * /backup server create <name>
     *
     * 使用自定义名称创建整个服务器备份。
     */
    private static int executeServerBackupCreate(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();


        String name =
                StringArgumentType.getString(
                        context,
                        "name"
                );


        if (name.trim().isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The name of the server backup cannot be empty!"
                    )
            );

            return 0;
        }


        String sanitizedName =
                sanitizeName(name);


        if (sanitizedName.isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The server backup name is invalid!"
                    )
            );

            return 0;
        }


        source.sendSuccess(
                () -> Component.literal(
                        "A server backup is being created: "
                                + sanitizedName
                ),
                false
        );


        Thread.startVirtualThread(() -> {

            try {

                String backupName =
                        BackupManager.createServerBackup(
                                server,
                                sanitizedName
                        );


                source.sendSuccess(
                        () -> Component.literal(
                                "The entire server backup has been completed: "
                                        + backupName
                        ),
                        false
                );


            } catch (IOException e) {

                AutoBackup.LOGGER.error(
                        "The backup of the custom server failed",
                        e
                );


                source.sendFailure(
                        Component.literal(
                                "The entire server backup failed: "
                                        + e.getMessage()
                        )
                );
            }
        });


        return 1;
    }


    /**
     * /backup server list
     *
     * 查看所有整个服务器备份。
     */
    private static int executeServerBackupList(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();


        try {

            List<String> backups =
                    BackupManager.listServerBackups(
                            server
                    );


            if (backups.isEmpty()) {

                source.sendSuccess(
                        () -> Component.literal(
                                "There is no server backup currently"
                        ),
                        false
                );

                return 1;
            }


            StringBuilder message =
                    new StringBuilder();

            message.append(
                    "Server backup list:\n"
            );


            for (String backup : backups) {

                message
                        .append("  - ")
                        .append(backup)
                        .append("\n");
            }


            source.sendSuccess(
                    () -> Component.literal(
                            message.toString()
                    ),
                    false
            );


            return 1;

        } catch (IOException e) {

            source.sendFailure(
                    Component.literal(
                            "List server backup failure: "
                                    + e.getMessage()
                    )
            );

            return 0;
        }
    }


    /**
     * /backup server del <name>
     *
     * 删除整个服务器备份。
     */
    private static int executeServerBackupDelete(
            CommandContext<CommandSourceStack> context) {

        CommandSourceStack source =
                context.getSource();

        MinecraftServer server =
                source.getServer();


        String name =
                StringArgumentType.getString(
                        context,
                        "name"
                );


        if (name.trim().isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The name of the server backup cannot be empty!"
                    )
            );

            return 0;
        }


        String sanitizedName =
                sanitizeName(name);


        if (sanitizedName.isEmpty()) {

            source.sendFailure(
                    Component.literal(
                            "The server backup name is invalid!"
                    )
            );

            return 0;
        }


        try {

            BackupManager.deleteServerBackup(
                    server,
                    sanitizedName
            );


            source.sendSuccess(
                    () -> Component.literal(
                            "The server backup has been deleted: "
                                    + sanitizedName
                    ),
                    false
            );


            return 1;

        } catch (IOException e) {

            source.sendFailure(
                    Component.literal(
                            "Failed to delete the server backup: "
                                    + e.getMessage()
                    )
            );

            return 0;
        }
    }


    // =========================================================
    // 文件名安全处理
    // =========================================================

    /**
     * 清理备份名称。
     *
     * 允许：
     *
     * A-Z
     * a-z
     * 0-9
     * _
     * -
     * 中文
     */
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
}