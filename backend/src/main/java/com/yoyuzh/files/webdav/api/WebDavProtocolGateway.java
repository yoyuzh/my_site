package com.yoyuzh.files.webdav.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface WebDavProtocolGateway {

    void dispatch(WebDavRequestPrincipal principal,
                  HttpServletRequest request,
                  HttpServletResponse response) throws IOException, ServletException;
}
