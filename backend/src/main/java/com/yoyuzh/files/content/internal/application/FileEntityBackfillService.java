package com.yoyuzh.files.content.internal.application;

import com.yoyuzh.files.content.api.ContentAssetApi;
import com.yoyuzh.files.content.internal.infra.FileEntityRepository;
import com.yoyuzh.files.content.internal.infra.StoredFileEntityRepository;
import com.yoyuzh.platform.storage.api.StoragePolicyQuery;
import com.yoyuzh.files.workspace.internal.infra.StoredFileRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class FileEntityBackfillService implements CommandLineRunner {

    private final ContentAssetApi contentAssetApi;

    public FileEntityBackfillService(StoredFileRepository storedFileRepository,
                                     FileEntityRepository fileEntityRepository,
                                     StoredFileEntityRepository storedFileEntityRepository,
                                     StoragePolicyQuery storagePolicyQuery) {
        this.contentAssetApi = new RuntimeContentAssetApi(
                storedFileRepository,
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
