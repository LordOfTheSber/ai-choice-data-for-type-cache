package com.example.logdownloader.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);
    private static final int MAX = 4096;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isBinaryStreamingEndpoint(request)) {
            long start = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            long took = System.currentTimeMillis() - start;
            log.info("HTTP {} {} status={} tookMs={} binaryStream=true", request.getMethod(), request.getRequestURI(), response.getStatus(), took);
            return;
        }

        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(req, res);
        } finally {
            long took = System.currentTimeMillis() - start;
            String reqBody = body(req.getContentAsByteArray());
            String resBody = body(res.getContentAsByteArray());
            log.info("HTTP {} {} status={} tookMs={} reqBody={} resBody={}",
                    request.getMethod(), request.getRequestURI(), res.getStatus(), took, reqBody, resBody);
            res.copyBodyToResponse();
        }
    }

    private boolean isBinaryStreamingEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/api/v1/logs/download") || (uri.contains("/api/v1/logs/collect/") && uri.endsWith("/download"));
    }

    private String body(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String s = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return s.length() > MAX ? s.substring(0, MAX) + "...<truncated>" : s;
    }
}
