package com.yoyuzh.files.content.api;

public interface ContentDuplicationApi {

    RegisteredContentFile duplicateBlobBackedFile(ContentRegistrationCommand command);
}
