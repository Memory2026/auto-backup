package com.xingci.autobackup;

public record BackupResult(
        String backupName,
        long backupSize,
        String deletedName,
        long deletedSize,
        long totalSize
) {

    public boolean deletedBackup() {
        return deletedName != null
                && !deletedName.isEmpty();
    }
}
