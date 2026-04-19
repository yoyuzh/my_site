package com.yoyuzh.files.content.api;

import com.yoyuzh.shared.kernel.PageResponse;

public interface ContentAdminInspectionApi {

    PageResponse<ContentAdminFileBlobView> listFileBlobsAsAdmin(ContentAdminFileBlobQuery query);

    long totalBlobSize();

    long countBlobsAsAdmin();

    long countEntitiesAsAdmin();
}
