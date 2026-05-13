package com.yoyuzh.files.webdav.internal.protocol;

import com.yoyuzh.files.webdav.internal.application.WebDavStoredResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class WebDavXmlResponseWriter {

    private static final DateTimeFormatter CREATION_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final Writer writer;

    WebDavXmlResponseWriter(Writer writer) {
        this.writer = writer;
    }

    void startMultistatus() {
        write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        write("<D:multistatus xmlns:D=\"DAV:\">");
    }

    void writeResponse(WebDavStoredResource resource, String href) {
        write("<D:response>");
        write("<D:href>");
        write(escape(href));
        write("</D:href>");
        write("<D:propstat><D:prop>");
        write("<D:displayname>");
        write(escape(resource.name()));
        write("</D:displayname>");
        write("<D:creationdate>");
        write(CREATION_DATE_FORMATTER.format(resource.createdAt()));
        write("</D:creationdate>");
        write("<D:getlastmodified>");
        write(HTTP_DATE_FORMATTER.format(resource.lastModifiedAt()));
        write("</D:getlastmodified>");
        if (resource.directory()) {
            write("<D:resourcetype><D:collection/></D:resourcetype>");
            write("<D:getcontentlength>0</D:getcontentlength>");
            write("<D:getcontenttype>httpd/unix-directory</D:getcontenttype>");
            write("<D:getetag>");
            write(escape(resource.etag()));
            write("</D:getetag>");
        } else {
            write("<D:resourcetype/>");
            write("<D:getcontentlength>");
            write(Long.toString(resource.contentLength()));
            write("</D:getcontentlength>");
            write("<D:getcontenttype>");
            write(escape(resource.contentType()));
            write("</D:getcontenttype>");
            write("<D:getetag>");
            write(escape(resource.etag()));
            write("</D:getetag>");
        }
        write("<D:supportedlock>");
        write("<D:lockentry><D:lockscope><D:exclusive/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry>");
        write("</D:supportedlock>");
        write("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>");
        write("</D:response>");
    }

    void endMultistatus() {
        write("</D:multistatus>");
        try {
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void write(String value) {
        try {
            writer.write(value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
