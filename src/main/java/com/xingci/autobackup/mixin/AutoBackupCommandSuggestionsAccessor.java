package com.xingci.autobackup.mixin;

public interface AutoBackupCommandSuggestionsAccessor {

    void autoBackup$setBangCommand(boolean bangCommand);

    boolean autoBackup$isBangCommand();
}
