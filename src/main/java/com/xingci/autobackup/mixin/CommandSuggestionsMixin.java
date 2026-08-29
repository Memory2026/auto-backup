package com.xingci.autobackup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin
        implements AutoBackupCommandSuggestionsAccessor {

    @Shadow
    private EditBox input;

    @Unique
    private boolean autoBackup$bangCommand;

    @Unique
    private boolean autoBackup$remapping;

    @Inject(
            method = "updateCommandInfo",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autoBackup$updateBangCommandSuggestions(
            CallbackInfo callbackInfo) {

        if (autoBackup$remapping) {
            return;
        }

        String value =
                input.getValue();

        if (!value.startsWith("!backup")) {
            autoBackup$bangCommand = false;
            return;
        }

        int cursor =
                input.getCursorPosition();

        autoBackup$bangCommand = true;
        autoBackup$remapping = true;

        input.setValue("/" + value.substring(1));
        input.setCursorPosition(cursor);

        ((CommandSuggestions) (Object) this).updateCommandInfo();

        input.setValue(value);
        input.setCursorPosition(cursor);

        autoBackup$remapping = false;
        callbackInfo.cancel();
    }

    @Override
    public void autoBackup$setBangCommand(
            boolean bangCommand) {

        autoBackup$bangCommand =
                bangCommand;
    }

    @Override
    public boolean autoBackup$isBangCommand() {
        return autoBackup$bangCommand;
    }
}
