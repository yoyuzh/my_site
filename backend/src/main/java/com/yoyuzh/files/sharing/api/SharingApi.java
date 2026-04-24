package com.yoyuzh.files.sharing.api;

import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import com.yoyuzh.shared.kernel.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SharingApi {

    ShareV2Response createShare(Long ownerUserId, CreateShareCommand command);

    ShareV2Response getShare(String token);

    ShareStatsResponse getStats(Long ownerUserId, String token);

    ShareV2Response verifyPassword(String token, String password);

    FileMetadataResponse importSharedFile(Long recipientUserId, String token, ImportShareCommand command);

    ShareDownloadResult downloadSharedFile(String token, String password);

    Page<ShareV2Response> listOwnedShares(Long ownerUserId, Pageable pageable);

    ShareV2Response updatePolicy(Long ownerUserId, Long id, Integer maxDownloads);

    void deleteOwnedShare(Long ownerUserId, Long id);

    Optional<SharingAdminShareSnapshot> deleteShareAsAdmin(Long id);

    PageResponse<SharingAdminShareView> listSharesAsAdmin(SharingAdminShareQuery query);
}
