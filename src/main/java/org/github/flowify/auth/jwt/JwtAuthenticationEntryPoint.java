package org.github.flowify.auth.jwt;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String AUTH_ERROR_CODE_ATTRIBUTE = "flowify.auth.errorCode";

    private final SecurityErrorResponseWriter responseWriter;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        ErrorCode errorCode = resolveErrorCode(request);
        responseWriter.write(response, errorCode);
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object errorCode = request.getAttribute(AUTH_ERROR_CODE_ATTRIBUTE);
        if (errorCode instanceof ErrorCode authErrorCode) {
            return authErrorCode;
        }

        return ErrorCode.AUTH_REQUIRED;
    }
}
