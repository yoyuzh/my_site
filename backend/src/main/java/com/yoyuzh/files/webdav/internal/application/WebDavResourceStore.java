package com.yoyuzh.files.webdav.internal.application;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface WebDavResourceStore {

    Optional<WebDavStoredResource> find(WebDavPrincipal principal, String path);

    List<WebDavStoredResource> list(WebDavPrincipal principal, String directoryPath);

    WebDavReadResult read(WebDavPrincipal principal, String path);

    void write(WebDavPrincipal principal,
               String path,
               String contentType,
               long size,
               InputStream content,
               boolean overwrite);

    void createDirectory(WebDavPrincipal principal, String path);

    void copy(WebDavPrincipal principal, String fromPath, String toPath, boolean overwrite);

    void move(WebDavPrincipal principal, String fromPath, String toPath, boolean overwrite);

    void delete(WebDavPrincipal principal, String path);
}
