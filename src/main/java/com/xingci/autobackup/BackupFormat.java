package com.xingci.autobackup;

public enum BackupFormat {
    FOLDER,
    ZIP;

    public static BackupFormat fromString(String value) {
        if (value == null) {
            return FOLDER;
        }

        return switch (value.trim().toLowerCase()) {
            case "zip" -> ZIP;
            default -> FOLDER;
        };
    }
}
