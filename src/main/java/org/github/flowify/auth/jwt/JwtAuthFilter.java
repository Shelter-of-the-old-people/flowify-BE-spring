package org.github.flowify.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.user.entity.User;
import org.github.flowify.user.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtProvider.validateToken(token)) {
            request.setAttribute(
                    JwtAuthenticationEntryPoint.AUTH_ERROR_CODE_ATTRIBUTE,
                    resolveInvalidTokenErrorCode(token)
            );
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtProvider.getUserIdFromToken(token);
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            request.setAttribute(
                    JwtAuthenticationEntryPoint.AUTH_ERROR_CODE_ATTRIBUTE,
                    ErrorCode.AUTH_INVALID_TOKEN
            );
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private ErrorCode resolveInvalidTokenErrorCode(String token) {
        if (jwtProvider.isTokenExpired(token)) {
            return ErrorCode.AUTH_EXPIRED_TOKEN;
        }

        return ErrorCode.AUTH_INVALID_TOKEN;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
