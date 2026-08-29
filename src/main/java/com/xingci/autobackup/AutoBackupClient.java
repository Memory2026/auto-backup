package com.xingci.autobackup;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

public class AutoBackupClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!message.equals("!backup gui")) {
                return true;
            }

            Minecraft minecraft =
                    Minecraft.getInstance();

            minecraft.execute(() ->
                    minecraft.setScreenAndShow(
                            new BackupConfigScreen(null)
                    )
            );

            return false;
        });
    }
}
