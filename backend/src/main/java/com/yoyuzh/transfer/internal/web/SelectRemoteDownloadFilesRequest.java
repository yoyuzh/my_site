package com.yoyuzh.transfer.internal.web;

import com.yoyuzh.transfer.api.SelectRemoteDownloadFilesCommand;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SelectRemoteDownloadFilesRequest {

    @NotEmpty(message = "fileKeys 不能为空")
    private List<String> fileKeys;

    public List<String> getFileKeys() {
        return fileKeys;
    }

    public void setFileKeys(List<String> fileKeys) {
        this.fileKeys = fileKeys;
    }

    public SelectRemoteDownloadFilesCommand toCommand() {
        return new SelectRemoteDownloadFilesCommand(fileKeys);
    }
}
