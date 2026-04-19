package com.yoyuzh.files.share;

import com.yoyuzh.api.v2.shares.CreateShareV2Request;
import com.yoyuzh.api.v2.shares.ImportShareV2Request;
import com.yoyuzh.api.v2.shares.ShareV2Response;
import com.yoyuzh.api.v2.shares.VerifySharePasswordV2Request;
import com.yoyuzh.auth.User;
import com.yoyuzh.files.sharing.api.CreateShareCommand;
import com.yoyuzh.files.sharing.api.ImportShareCommand;
import com.yoyuzh.files.sharing.api.SharingApi;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ShareV2Service {

    private final SharingApi sharingApi;

    public ShareV2Service(SharingApi sharingApi) {
        this.sharingApi = sharingApi;
    }

    public ShareV2Response createShare(User user, CreateShareV2Request request) {
        return sharingApi.createShare(user, new CreateShareCommand(
                request.fileId(),
                request.password(),
                request.shareName(),
                request.allowImport(),
                request.allowDownload(),
                request.expiresAt(),
                request.maxDownloads()
        ));
    }

    public ShareV2Response getShare(String token) {
        return sharingApi.getShare(token);
    }

    public ShareV2Response verifyPassword(String token, VerifySharePasswordV2Request request) {
        return sharingApi.verifyPassword(token, request.password());
    }

    public FileMetadataResponse importSharedFile(User recipient, String token, ImportShareV2Request request) {
        return sharingApi.importSharedFile(recipient, token, new ImportShareCommand(request.path(), request.password()));
    }

    public ResponseEntity<?> downloadSharedFile(String token, String password) {
        return sharingApi.downloadSharedFile(token, password);
    }

    public Page<ShareV2Response> listOwnedShares(User user, Pageable pageable) {
        return sharingApi.listOwnedShares(user, pageable);
    }

    public void deleteOwnedShare(User user, Long id) {
        sharingApi.deleteOwnedShare(user, id);
    }
}
