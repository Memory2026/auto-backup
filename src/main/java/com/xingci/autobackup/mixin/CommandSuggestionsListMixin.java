package com.xingci.autobackup.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.gui.components.CommandSuggestions;

@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public class CommandSuggestionsListMixin {

    @Shadow
    @Final
    CommandSuggestions this$0;

    @ModifyArg(
            method = "useSuggestion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/EditBox;setValue(Ljava/lang/String;)V"
            )
    )
    private String autoBackup$keepBangPrefix(
            String value) {

        if (((AutoBackupCommandSuggestionsAccessor) this$0).autoBackup$isBangCommand()
                && value.startsWith("/backup")) {
            return "!" + value.substring(1);
        }

        return value;
    }
}
