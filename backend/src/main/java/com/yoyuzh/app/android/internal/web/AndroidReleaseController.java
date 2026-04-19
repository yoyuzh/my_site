package com.yoyuzh.app.android.internal.web;

import com.yoyuzh.app.android.api.AndroidReleaseDownload;
import com.yoyuzh.app.android.api.AndroidReleaseQueryApi;
import com.yoyuzh.app.android.api.AndroidReleaseResponse;
import com.yoyuzh.shared.kernel.ApiResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/android")
@RequiredArgsConstructor
public class AndroidReleaseController {

    private final AndroidReleaseQueryApi androidReleaseQueryApi;

    @GetMapping("/latest")
    public ApiResponse<AndroidReleaseResponse> getLatestRelease() {
        return ApiResponse.success(androidReleaseQueryApi.getLatestRelease());
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadLatestRelease() {
        return buildDownloadResponse(androidReleaseQueryApi.downloadLatestRelease());
    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<byte[]> downloadVersionedRelease(@PathVariable String fileName) {
        AndroidReleaseDownload download = androidReleaseQueryApi.downloadLatestRelease();
        if (!download.fileName().equals(fileName)) {
            return ResponseEntity.notFound().build();
        }
        return buildDownloadResponse(download);
    }

    private ResponseEntity<byte[]> buildDownloadResponse(AndroidReleaseDownload download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(download.content().length)
                .body(download.content());
    }
}
