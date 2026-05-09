package com.yoyuzh.transfer.internal.web;

import com.yoyuzh.transfer.api.CreateRemoteDownloadCommand;
import com.yoyuzh.transfer.api.RemoteDownloadSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public class CreateRemoteDownloadRequest {

    @NotNull(message = "sourceType 不能为空")
    private RemoteDownloadSourceType sourceType;

    private String sourceValue;

    private MultipartFile torrentFile;

    @NotBlank(message = "targetPath 不能为空")
    private String targetPath;

    public RemoteDownloadSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(RemoteDownloadSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceValue() {
        return sourceValue;
    }

    public void setSourceValue(String sourceValue) {
        this.sourceValue = sourceValue;
    }

    public MultipartFile getTorrentFile() {
        return torrentFile;
    }

    public void setTorrentFile(MultipartFile torrentFile) {
        this.torrentFile = torrentFile;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public CreateRemoteDownloadCommand toCommand() {
        String torrentFilename = torrentFile == null ? null : torrentFile.getOriginalFilename();
        byte[] torrentContent = null;
        if (torrentFile != null) {
            try {
                torrentContent = torrentFile.getBytes();
            } catch (java.io.IOException ex) {
                throw new IllegalArgumentException("种子文件读取失败", ex);
            }
        }
        return new CreateRemoteDownloadCommand(
                sourceType,
                sourceValue,
                torrentFilename,
                torrentContent,
                targetPath
        );
    }
}
