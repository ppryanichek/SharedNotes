package com.eugene.sharednotes.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class SecurityResponseWriter {
    private SecurityResponseWriter() {}

    public static void writeErrorResponse(HttpServletResponse response,
                                    int status,
                                    String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
                {"message": "%s"}
                """.formatted(message));
    }
}
