package com.yoyuzh.files.webdav.internal.protocol;

import com.yoyuzh.files.webdav.internal.application.WebDavStoredResource;

import java.io.PrintWriter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class WebDavXmlResponseWriter {

    private final PrintWriter writer;

    WebDavXmlResponseWriter(PrintWriter writer) {
        this.writer = writer;
    }

    void startMultistatus() {
        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        writer.write("<D:multistatus xmlns:D=\"DAV:\">");
    }

    void writeResponse(WebDavStoredResource resource, String href) {
        writer.write("<D:response>");
        writer.write("<D:href>");
        writer.write(escape(href));
        writer.write("</D:href>");
        writer.write("<D:propstat><D:prop>");
        writer.write("<D:displayname>");
        writer.write(escape(resource.name()));
        writer.write("</D:displayname>");
        writer.write("<D:creationdate>");
        writer.write(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(resource.createdAt().atOffset(ZoneOffset.UTC)));
        writer.write("</D:creationdate>");
        writer.write("<D:getlastmodified>");
        writer.write(DateTimeFormatter.RFC_1123_DATE_TIME.format(resource.lastModifiedAt().atOffset(ZoneOffset.UTC)));
        writer.write("</D:getlastmodified>");
        if (resource.directory()) {
            writer.write("<D:resourcetype><D:collection/></D:resourcetype>");
        } else {
            writer.write("<D:resourcetype/>");
            writer.write("<D:getcontentlength>");
            writer.write(Long.toString(resource.contentLength()));
            writer.write("</D:getcontentlength>");
            writer.write("<D:getcontenttype>");
            writer.write(escape(resource.contentType()));
            writer.write("</D:getcontenttype>");
            writer.write("<D:getetag>");
            writer.write(escape(resource.etag()));
            writer.write("</D:getetag>");
        }
        writer.write("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>");
        writer.write("</D:response>");
    }

    void endMultistatus() {
        writer.write("</D:multistatus>");
        writer.flush();
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
