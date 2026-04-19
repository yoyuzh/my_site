package com.yoyuzh.files.workspace.api;

@FunctionalInterface
public interface WorkspaceQuotaGuard {

    void ensureWithinQuota(long additionalBytes);
}
