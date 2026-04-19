package com.yoyuzh.files.sharing.api;

import com.yoyuzh.api.v2.shares.ShareV2Response;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.core.FileMetadataResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface SharingApi {

    ShareV2Response createShare(User user, CreateShareCommand command);

    ShareV2Response getShare(String token);

    ShareV2Response verifyPassword(String token, String password);

    FileMetadataResponse importSharedFile(User recipient, String token, ImportShareCommand command);

    ResponseEntity<?> downloadSharedFile(String token, String password);

    Page<ShareV2Response> listOwnedShares(User user, Pageable pageable);

    void deleteOwnedShare(User user, Long id);
}
