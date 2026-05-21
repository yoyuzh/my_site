package com.yoyuzh.files.content.api;

public interface ContentPrimaryEntityApi {

    ContentPrimaryEntity createOrReferencePrimaryEntity(Long userId, ContentBlobReference blob);

    void savePrimaryEntityRelation(ContentPrimaryEntityRelationCommand command);

    void releasePrimaryEntity(Long storedFileId, Long primaryEntityId);
}
