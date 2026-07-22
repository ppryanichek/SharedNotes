package com.eugene.sharednotes.security;

import com.eugene.sharednotes.auth.jwt.JwtService;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

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

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Пропуск — либо не смогли извлечь userId, либо запрос уже аутентифицирован раньше в цепочке.
            if (userId == null || authentication != null) {
                continueChain(request, response, filterChain);
                return;
            }

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("User is not found with id: " + userId));

            SecurityUser securityUser = new SecurityUser(user);

            if (!jwtService.isAccessTokenValid(accessToken, securityUser)) {
                respondUnauthorized(response, "Invalid token!");
                return;
            }

            UsernamePasswordAuthenticationToken passwordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    securityUser,
                    null,
                    securityUser.getAuthorities()
            );

            SecurityContextHolder.getContext().setAuthentication(passwordAuthenticationToken);
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            respondUnauthorized(response, "Token expired!");
            return;
        } catch (JwtException | UsernameNotFoundException e) {
            log.error("JWT authentication failed", e);
            respondUnauthorized(response, "Invalid token!");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void continueChain(HttpServletRequest request,
                               HttpServletResponse response,
                               FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }

    private void respondUnauthorized(HttpServletResponse response,
                                     String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(message);
    }
}
