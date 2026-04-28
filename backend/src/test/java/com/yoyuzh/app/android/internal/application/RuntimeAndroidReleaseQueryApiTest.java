package com.yoyuzh.app.android.internal.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoyuzh.app.android.api.AndroidReleaseDownload;
import com.yoyuzh.app.android.api.AndroidReleaseResponse;
import com.yoyuzh.app.android.internal.infra.AndroidReleaseProperties;
import com.yoyuzh.files.content.api.FileContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeAndroidReleaseQueryApiTest {

    @Mock
    private FileContentStorage fileContentStorage;

    private AndroidReleaseProperties properties;
    private RuntimeAndroidReleaseQueryApi runtimeAndroidReleaseQueryApi;

    @BeforeEach
    void setUp() {
        properties = new AndroidReleaseProperties();
        runtimeAndroidReleaseQueryApi = new RuntimeAndroidReleaseQueryApi(fileContentStorage, new ObjectMapper(), properties);
    }

    @Test
    void shouldBuildLatestReleaseFromStorageMetadata() {
        when(fileContentStorage.readBlob("android/releases/latest.json")).thenReturn("""
                {
                  "objectKey": "android/releases/yoyuzh-portal-2026.04.03.1754.apk",
                  "fileName": "yoyuzh-portal-2026.04.03.1754.apk",
                  "versionCode": "260931754",
                  "versionName": "2026.04.03.1754",
                  "publishedAt": "2026-04-03T09:54:00Z"
                }
                """.getBytes());

        AndroidReleaseResponse release = runtimeAndroidReleaseQueryApi.getLatestRelease();

        assertEquals("https://api.yoyuzh.xyz/api/app/android/download/yoyuzh-portal-2026.04.03.1754.apk", release.downloadUrl());
        assertEquals("yoyuzh-portal-2026.04.03.1754.apk", release.fileName());
        assertEquals("260931754", release.versionCode());
        assertEquals("2026.04.03.1754", release.versionName());
        assertEquals("2026-04-03T09:54:00Z", release.publishedAt());
    }

    @Test
    void shouldReadLatestReleaseContentFromStorage() {
        when(fileContentStorage.readBlob("android/releases/latest.json")).thenReturn("""
                {
                  "objectKey": "android/releases/yoyuzh-portal-2026.04.03.1754.apk",
                  "fileName": "yoyuzh-portal-2026.04.03.1754.apk"
                }
                """.getBytes());
        when(fileContentStorage.readBlob("android/releases/yoyuzh-portal-2026.04.03.1754.apk"))
                .thenReturn("apk-binary".getBytes());

        AndroidReleaseDownload download = runtimeAndroidReleaseQueryApi.downloadLatestRelease();

        assertEquals("yoyuzh-portal-2026.04.03.1754.apk", download.fileName());
        assertEquals("apk-binary", new String(download.content()));
    }
}
