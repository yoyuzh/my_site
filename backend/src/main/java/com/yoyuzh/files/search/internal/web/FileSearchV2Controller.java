package com.yoyuzh.files.search.internal.web;

import com.yoyuzh.boot.web.v2.ApiV2ErrorCode;
import com.yoyuzh.boot.web.v2.ApiV2Exception;
import com.yoyuzh.boot.web.v2.ApiV2Response;
import com.yoyuzh.boot.security.CustomUserDetailsService;
import com.yoyuzh.shared.kernel.PageResponse;
import com.yoyuzh.files.search.api.FileSearchApi;
import com.yoyuzh.files.search.api.SearchFilesQuery;
import com.yoyuzh.files.workspace.api.FileMetadataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/v2/files")
@RequiredArgsConstructor
public class FileSearchV2Controller {

    private final FileSearchApi fileSearchApi;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping("/search")
    public ApiV2Response<PageResponse<FileMetadataResponse>> search(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @RequestParam(required = false) String name,
                                                                    @RequestParam(required = false) String category,
                                                                    @RequestParam(required = false) String type,
                                                                    @RequestParam(required = false) Long sizeGte,
                                                                    @RequestParam(required = false) Long sizeLte,
                                                                    @RequestParam(required = false)
                                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                    LocalDateTime createdGte,
                                                                    @RequestParam(required = false)
                                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                    LocalDateTime createdLte,
                                                                    @RequestParam(required = false)
                                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                    LocalDateTime updatedGte,
                                                                    @RequestParam(required = false)
                                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                    LocalDateTime updatedLte,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        Boolean directory = parseType(type);
        return ApiV2Response.success(fileSearchApi.search(
                currentUserId(userDetails),
                new SearchFilesQuery(name, parseCategory(category), directory, sizeGte, sizeLte, createdGte, createdLte, updatedGte, updatedLte, page, size)
        ));
    }

    private String parseCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }

        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "image", "video", "audio", "document" -> category.trim().toLowerCase(Locale.ROOT);
            default -> throw new ApiV2Exception(ApiV2ErrorCode.INVALID_INPUT, "分类筛选只支持 image、video、audio 或 document");
        };
    }

    private Boolean parseType(String type) {
        if (!StringUtils.hasText(type) || "all".equalsIgnoreCase(type.trim())) {
            return null;
        }

        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "file" -> false;
            case "directory", "folder" -> true;
            default -> throw new ApiV2Exception(ApiV2ErrorCode.INVALID_INPUT, "文件类型筛选只支持 file 或 directory");
        };
    }

    private Long currentUserId(UserDetails userDetails) {
        return userDetailsService.loadUserId(userDetails.getUsername());
    }
}
