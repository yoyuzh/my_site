package com.yoyuzh.files.workspace.api;

@FunctionalInterface
public interface WorkspaceExternalImportProgressListener {

    void onProgress(WorkspaceExternalImportProgress progress);
}
