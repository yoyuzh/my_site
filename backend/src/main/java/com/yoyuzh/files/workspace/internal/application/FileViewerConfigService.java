package com.yoyuzh.files.workspace.internal.application;

import com.yoyuzh.files.content.api.FileContentStorage;
import com.yoyuzh.files.workspace.api.FileViewerConfigResponse;
import com.yoyuzh.files.workspace.api.FileViewerDefinition;
import com.yoyuzh.files.workspace.api.FileViewerTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileViewerConfigService {

    private final boolean externalViewerEnabled;

    @Autowired
    public FileViewerConfigService(ObjectProvider<FileContentStorage> fileContentStorageProvider) {
        this(fileContentStorageProvider.getIfAvailable() != null
                && fileContentStorageProvider.getIfAvailable().supportsDirectDownload());
    }

    public FileViewerConfigService(boolean externalViewerEnabled) {
        this.externalViewerEnabled = externalViewerEnabled;
    }

    public FileViewerConfigResponse defaultConfig() {
        List<FileViewerDefinition> viewers = new ArrayList<>(List.of(
                builtin("image", "图片查看器", "image", List.of(
                        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif", "ico", "heic"
                ), null, true, List.of(), Map.of()),
                builtin("photopea", "Photopea", "layers", List.of(
                        "psd", "psb", "ai", "xd", "sketch", "xcf", "raw", "tiff", "tif"
                ), null, false, List.of(), Map.of("urlTemplate", "https://www.photopea.com#{$src}")),
                builtin("code-monaco", "文本编辑器", "code", List.of(
                        "txt", "log", "json", "xml", "yaml", "yml", "ini", "conf", "env",
                        "properties", "csv", "tsv", "html", "css", "scss", "less", "js", "jsx",
                        "ts", "tsx", "vue", "svelte", "java", "kt", "py", "go", "rs", "c", "h",
                        "cpp", "hpp", "cs", "php", "rb", "swift", "sh", "bash", "zsh", "sql"
                ), 20L * 1024 * 1024, true, List.of(
                        template("code-monaco", "txt", "文本文档", "未命名.txt", "", "text/plain")
                ), Map.of()),
                builtin("drawio", "draw.io", "diagram", List.of("drawio", "dio"), 50L * 1024 * 1024, true, List.of(
                        template("drawio", "drawio", "draw.io 图表", "未命名.drawio", "", "application/xml")
                ), Map.of("host", "https://embed.diagrams.net")),
                builtin("markdown", "Markdown 编辑器", "markdown", List.of("md", "markdown", "mdown"), 20L * 1024 * 1024, true, List.of(
                        template("markdown", "md", "Markdown 文档", "未命名.md", "", "text/markdown")
                ), Map.of()),
                builtin("video", "视频播放器", "video", List.of("mp4", "webm", "ogg", "mov", "m4v", "avi", "mkv"), null, true, List.of(), Map.of()),
                builtin("pdf", "PDF 阅读器", "file-text", List.of("pdf"), null, true, List.of(), Map.of()),
                builtin("epub", "EPUB 阅读器", "book-open", List.of("epub"), null, true, List.of(), Map.of()),
                builtin("music", "音乐播放器", "music", List.of("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus"), null, true, List.of(), Map.of()),
                builtin("excalidraw", "Excalidraw", "pen-tool", List.of("excalidraw"), 50L * 1024 * 1024, true, List.of(
                        template("excalidraw", "excalidraw", "Excalidraw 画板", "未命名.excalidraw", "", "application/json")
                ), Map.of()),
                builtin("archive", "压缩包浏览器", "archive", List.of(
                        "zip", "rar", "7z", "tar", "tgz", "gz", "tbz2", "tbz", "bz2", "txz", "xz"
                ), null, true, List.of(), Map.of())
        ));
        if (externalViewerEnabled) {
            viewers.add(custom("google-docs", "Google 阅读器", "google", List.of(
                    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "odt", "ods", "odp"
            ), true, Map.of(
                    "urlTemplate", "https://docs.google.com/gview?embedded=1&url={$src_urlencoded}",
                    "embed", true
            )));
            viewers.add(wopi("microsoft-office", "Microsoft 阅读器", "microsoft", List.of(
                    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"
            ), true, Map.of(
                    "urlTemplate", "https://view.officeapps.live.com/op/embed.aspx?src={$src_urlencoded}",
                    "preferredAction", "view",
                    "supportsEdit", true
            )));
        }
        return new FileViewerConfigResponse(viewers, defaultViewerMapping());
    }

    private FileViewerDefinition builtin(String id,
                                         String displayName,
                                         String icon,
                                         List<String> extensions,
                                         Long maxSizeBytes,
                                         boolean recommended,
                                         List<FileViewerTemplate> templates,
                                         Map<String, Object> props) {
        return new FileViewerDefinition(id, "builtin", displayName, icon, extensions, maxSizeBytes, false, recommended, templates, props);
    }

    private FileViewerDefinition custom(String id,
                                        String displayName,
                                        String icon,
                                        List<String> extensions,
                                        boolean recommended,
                                        Map<String, Object> props) {
        return new FileViewerDefinition(id, "custom", displayName, icon, extensions, null, false, recommended, List.of(), props);
    }

    private FileViewerDefinition wopi(String id,
                                      String displayName,
                                      String icon,
                                      List<String> extensions,
                                      boolean recommended,
                                      Map<String, Object> props) {
        return new FileViewerDefinition(id, "wopi", displayName, icon, extensions, null, false, recommended, List.of(), props);
    }

    private FileViewerTemplate template(String viewerId,
                                        String extension,
                                        String displayName,
                                        String filename,
                                        String content,
                                        String contentType) {
        return new FileViewerTemplate(viewerId, extension, displayName, filename, content, contentType);
    }

    private Map<String, String> defaultViewerMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        putAll(mapping, "image", "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif", "ico", "heic");
        putAll(mapping, "photopea", "psd", "psb", "ai", "xd", "sketch", "xcf", "raw", "tiff", "tif");
        putAll(mapping, "code-monaco", "txt", "log", "json", "xml", "yaml", "yml", "ini", "conf", "env",
                "properties", "csv", "tsv", "html", "css", "scss", "less", "js", "jsx", "ts", "tsx", "vue",
                "svelte", "java", "kt", "py", "go", "rs", "c", "h", "cpp", "hpp", "cs", "php", "rb", "swift",
                "sh", "bash", "zsh", "sql");
        putAll(mapping, "drawio", "drawio", "dio");
        putAll(mapping, "markdown", "md", "markdown", "mdown");
        putAll(mapping, "video", "mp4", "webm", "ogg", "mov", "m4v", "avi", "mkv");
        putAll(mapping, "pdf", "pdf");
        putAll(mapping, "epub", "epub");
        putAll(mapping, "music", "mp3", "wav", "flac", "aac", "m4a", "opus");
        putAll(mapping, "excalidraw", "excalidraw");
        putAll(mapping, "archive", "zip", "rar", "7z", "tar", "tgz", "gz", "tbz2", "tbz", "bz2", "txz", "xz");
        if (externalViewerEnabled) {
            putAll(mapping, "microsoft-office", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp");
        }
        return Map.copyOf(mapping);
    }

    private void putAll(Map<String, String> mapping, String viewerId, String... extensions) {
        for (String extension : extensions) {
            mapping.put(extension, viewerId);
        }
    }
}
