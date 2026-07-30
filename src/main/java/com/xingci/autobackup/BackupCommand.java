package com.xingci.autobackup;

import java.io.IOException;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public class BackupCommand {

    private static final Permission BACKUP_PERMISSION = new Permission.HasCommandLevel(PermissionLevel.ALL);

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            // /backup - Create backup with auto-generated name
            LiteralArgumentBuilder<CommandSourceStack> backupCommand = Commands
                    .literal("backup")
                    .requires(source -> source.permissions().hasPermission(BACKUP_PERMISSION))
                    .executes(BackupCommand::executeBackup);

            // /backup create <name> - Create backup with custom name
            backupCommand.then(
                    Commands.literal("create")
                            .then(
                                    Commands.argument("name", StringArgumentType.string())
                                            .executes(BackupCommand::executeBackupCreate)
                            )
            );

            // /backup list - List all backups
            backupCommand.then(
                    Commands.literal("list")
                            .executes(BackupCommand::executeBackupList)
            );

            // /backup del <name> - Delete a backup
            backupCommand.then(
                    Commands.literal("del")
                            .then(
                                    Commands.argument("name", StringArgumentType.string())
                                            .executes(BackupCommand::executeBackupDelete)
                            )
            );

            dispatcher.register(backupCommand);
        });
    }

    private static int executeBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        
        try {
            String backupName = BackupManager.createBackup(server);
            source.sendSuccess(
                    () -> Component.literal("备份已创建: " + backupName),
                    false
            );
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("创建备份失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBackupCreate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(context, "name");
        
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            source.sendFailure(Component.literal("备份名称不能为空!"));
            return 0;
        }
        
        // Sanitize name
        String sanitizedName = name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
        
        try {
            String backupName = BackupManager.createBackup(server, sanitizedName);
            source.sendSuccess(
                    () -> Component.literal("备份已创建: " + backupName),
                    false
            );
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("创建备份失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBackupList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        
        try {
            List<String> backups = BackupManager.listBackups(server);
            
            if (backups.isEmpty()) {
                source.sendSuccess(
                        () -> Component.literal("当前世界没有备份。"),
                        false
                );
                return 1;
            }
            
            StringBuilder message = new StringBuilder("备份列表:\n");
            for (String backup : backups) {
                message.append("  - ").append(backup).append("\n");
            }
            
            source.sendSuccess(
                    () -> Component.literal(message.toString()),
                    false
            );
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("列出备份失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeBackupDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(context, "name");
        
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            source.sendFailure(Component.literal("备份名称不能为空!"));
            return 0;
        }
        
        // Sanitize name
        String sanitizedName = name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
        
        try {
            BackupManager.deleteBackup(server, sanitizedName);
            source.sendSuccess(
                    () -> Component.literal("备份已删除: " + sanitizedName),
                    false
            );
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("删除备份失败: " + e.getMessage()));
            return 0;
        }
    }
}
