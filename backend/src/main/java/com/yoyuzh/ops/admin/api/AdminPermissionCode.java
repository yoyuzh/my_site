package com.yoyuzh.ops.admin.api;

public enum AdminPermissionCode {
    ADMIN_OVERVIEW_READ("admin.overview.read"),
    ADMIN_USERS_READ("admin.users.read"),
    ADMIN_USERS_WRITE("admin.users.write"),
    ADMIN_SETTINGS_READ("admin.settings.read"),
    ADMIN_SETTINGS_WRITE("admin.settings.write"),
    ADMIN_STORAGE_READ("admin.storage.read"),
    ADMIN_STORAGE_WRITE("admin.storage.write"),
    ADMIN_FILES_READ("admin.files.read"),
    ADMIN_FILES_WRITE("admin.files.write"),
    ADMIN_SHARES_READ("admin.shares.read"),
    ADMIN_SHARES_WRITE("admin.shares.write"),
    ADMIN_TASKS_READ("admin.tasks.read"),
    ADMIN_AUDIT_READ("admin.audit.read"),
    ADMIN_SYSTEM_READ("admin.system.read");

    private final String code;

    AdminPermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
