package com.yoyuzh.transfer.internal.infra;

import java.util.List;

public interface QbittorrentClient {

    String submitMagnet(String sourceValue, String downloadNodeId);

    String submitTorrent(String torrentFilename, byte[] torrentContent, String downloadNodeId);

    TorrentStatus queryTorrent(String hash);

    List<TorrentFile> listFiles(String hash);

    void updateFileSelection(String hash, List<String> selectedFileKeys, List<String> unselectedFileKeys);

    void delete(String hash, boolean deleteFiles);

    record TorrentStatus(
            String hash,
            String state,
            double progress,
            String contentPath,
            String savePath
    ) {
    }

    record TorrentFile(
            String fileKey,
            String relativePath,
            long size,
            int priority
    ) {
    }
}
