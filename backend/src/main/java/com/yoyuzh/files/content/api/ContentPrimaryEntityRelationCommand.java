package com.yoyuzh.files.content.api;

public record ContentPrimaryEntityRelationCommand(
        Long storedFileId,
        Long primaryEntityId
) {
}
