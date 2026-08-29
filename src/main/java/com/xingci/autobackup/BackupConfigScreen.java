package com.xingci.autobackup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class BackupConfigScreen extends Screen {

    private final Screen parent;
    private EditBox worldIntervalBox;
    private EditBox serverIntervalBox;
    private Button formatButton;
    private BackupFormat selectedFormat;

    public BackupConfigScreen(
            Screen parent) {

        super(Component.literal("自动备份配置"));
        this.parent =
                parent;
    }

    @Override
    protected void init() {
        BackupConfig config =
                BackupConfig.get();

        selectedFormat =
                config.getBackupFormat();

        int center =
                width / 2;

        worldIntervalBox =
                new EditBox(
                        font,
                        center - 100,
                        50,
                        200,
                        20,
                        Component.literal("世界自动备份间隔（分钟）")
                );
        worldIntervalBox.setValue(
                Integer.toString(config.getWorldIntervalMinutes())
        );
        worldIntervalBox.setHint(
                Component.literal("世界自动备份间隔（分钟）")
        );
        addRenderableWidget(worldIntervalBox);

        serverIntervalBox =
                new EditBox(
                        font,
                        center - 100,
                        80,
                        200,
                        20,
                        Component.literal("服务器自动备份间隔（分钟）")
                );
        serverIntervalBox.setValue(
                Integer.toString(config.getServerIntervalMinutes())
        );
        serverIntervalBox.setHint(
                Component.literal("服务器自动备份间隔（分钟）")
        );
        addRenderableWidget(serverIntervalBox);

        formatButton =
                Button.builder(
                                formatText(),
                                button -> {
                                    selectedFormat =
                                            selectedFormat == BackupFormat.FOLDER
                                                    ? BackupFormat.ZIP
                                                    : BackupFormat.FOLDER;
                                    button.setMessage(formatText());
                                }
                        )
                        .bounds(center - 100, 110, 200, 20)
                        .build();
        addRenderableWidget(formatButton);

        addRenderableWidget(
                Button.builder(
                                Component.literal("保存配置"),
                                button -> saveConfig()
                        )
                        .bounds(center - 100, 140, 200, 20)
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("打开世界备份文件夹"),
                                button -> openFolder(false)
                        )
                        .bounds(center - 100, 170, 200, 20)
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("打开服务器备份文件夹"),
                                button -> openFolder(true)
                        )
                        .bounds(center - 100, 200, 200, 20)
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("完成"),
                                button -> onClose()
                        )
                        .bounds(center - 100, 230, 200, 20)
                        .build()
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private void saveConfig() {
        BackupConfig config =
                BackupConfig.get();

        config.setWorldIntervalMinutes(
                parsePositiveInt(
                        worldIntervalBox.getValue(),
                        config.getWorldIntervalMinutes()
                )
        );
        config.setServerIntervalMinutes(
                parsePositiveInt(
                        serverIntervalBox.getValue(),
                        config.getServerIntervalMinutes()
                )
        );
        config.setBackupFormat(selectedFormat);
        config.save();
        AutoBackup.reloadSchedules();
    }

    private void openFolder(
            boolean serverBackupFolder) {

        Path folder =
                resolveFolder(serverBackupFolder);

        try {
            Files.createDirectories(folder);
            Util.getPlatform()
                    .openPath(folder);
        } catch (IOException e) {
            AutoBackup.LOGGER.error(
                    "打开备份文件夹失败: {}",
                    folder,
                    e
            );
        }
    }

    private Path resolveFolder(
            boolean serverBackupFolder) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.getSingleplayerServer() != null) {
            if (serverBackupFolder) {
                return BackupManager.getServerBackupDirectory(
                        minecraft.getSingleplayerServer()
                );
            }

            return BackupManager.getBackupDirectory(
                    minecraft.getSingleplayerServer()
            );
        }

        return Path.of(
                        serverBackupFolder
                                ? "server-backups"
                                : "backups"
                )
                .toAbsolutePath()
                .normalize();
    }

    private Component formatText() {
        return Component.literal(
                "备份格式："
                        + (selectedFormat == BackupFormat.ZIP
                        ? "zip"
                        : "文件夹")
        );
    }

    private static int parsePositiveInt(
            String value,
            int fallback) {

        try {
            return Math.max(
                    1,
                    Integer.parseInt(value.trim())
            );
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
