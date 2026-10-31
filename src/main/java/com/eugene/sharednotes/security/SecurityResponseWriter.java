package com.eugene.sharednotes.security;

import com.eugene.sharednotes.error.dto.ErrorCode;
import com.eugene.sharednotes.error.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class SecurityResponseWriter {
    private final JsonMapper jsonMapper;

    public void writeErrorResponse(HttpServletResponse response,
                                    int status,
                                   ErrorCode errorCode,
                                   String message) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse errorResponse = ErrorResponse.of(status, errorCode, message);
        jsonMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
