package com.yoyuzh.transfer.api;

import java.util.List;

public interface RemoteDownloadApi {

    RemoteDownloadDetailResponse create(Long userId, CreateRemoteDownloadCommand command);

    List<RemoteDownloadListItemResponse> listOwned(Long userId);

    RemoteDownloadDetailResponse getOwned(Long userId, Long id);

    RemoteDownloadDetailResponse selectFiles(Long userId, Long id, SelectRemoteDownloadFilesCommand command);

    RemoteDownloadDetailResponse cancel(Long userId, Long id);
}
