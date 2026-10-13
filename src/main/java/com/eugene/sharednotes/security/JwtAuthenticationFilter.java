package com.eugene.sharednotes.security;

import com.eugene.sharednotes.auth.jwt.JwtService;
import com.eugene.sharednotes.common.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            continueChain(request, response, filterChain);
            return;
        }

        String accessToken = authHeader.substring(7);
        if (accessToken.isBlank()
                || accessToken.equals("null")
                || accessToken.equals("undefined")) {
            continueChain(request, response, filterChain);
            return;
        }

        try {
            Long userId = jwtService.extractUserId(accessToken);
            Role role = jwtService.extractRole(accessToken);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Пропуск — запрос уже аутентифицирован раньше в цепочке.
            if (authentication != null && authentication.isAuthenticated()) {
                continueChain(request, response, filterChain);
                return;
            }

            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

            UsernamePasswordAuthenticationToken passwordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    new JwtPrincipal(userId),
                    null,
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(passwordAuthenticationToken);
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            SecurityResponseWriter.writeErrorResponse(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token expired!"
            );
            return;
        } catch (JwtException e) {
            log.error("JWT authentication failed", e);
            SecurityResponseWriter.writeErrorResponse(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid token!"
            );
            return;
        }

        continueChain(request, response, filterChain);
    }

    private void continueChain(HttpServletRequest request,
                               HttpServletResponse response,
                               FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }
}
