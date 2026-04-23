package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.internal.infra.FileBlobRepository;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.files.workspace.api.WorkspaceContentBindingApi;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class FileEntityBackfillService implements CommandLineRunner {

    private final ContentAssetApi contentAssetApi;

    public FileEntityBackfillService(WorkspaceContentBindingApi workspaceContentBindingApi,
                                     FileEntityRepository fileEntityRepository,
                                     StoredFileEntityRepository storedFileEntityRepository,
                                     StoragePolicyQuery storagePolicyQuery) {
        this(
                workspaceContentBindingApi,
                null,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    @Autowired
    public FileEntityBackfillService(WorkspaceContentBindingApi workspaceContentBindingApi,
                                     FileBlobRepository fileBlobRepository,
                                     FileEntityRepository fileEntityRepository,
                                     StoredFileEntityRepository storedFileEntityRepository,
                                     StoragePolicyQuery storagePolicyQuery) {
        this.contentAssetApi = new RuntimeContentAssetApi(
                workspaceContentBindingApi,
                fileBlobRepository,
                fileEntityRepository,
                storedFileEntityRepository,
                storagePolicyQuery
        );
    }

    @Override
    @Transactional
    public void run(String... args) {
        backfillPrimaryEntities();
    }

    @Transactional
    public void backfillPrimaryEntities() {
        contentAssetApi.backfillPrimaryEntities();
    }
}
