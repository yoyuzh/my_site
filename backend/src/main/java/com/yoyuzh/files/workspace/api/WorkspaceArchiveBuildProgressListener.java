package com.yoyuzh.files.workspace.api;

@FunctionalInterface
public interface WorkspaceArchiveBuildProgressListener {

    void onProgress(WorkspaceArchiveBuildProgress progress);
}
