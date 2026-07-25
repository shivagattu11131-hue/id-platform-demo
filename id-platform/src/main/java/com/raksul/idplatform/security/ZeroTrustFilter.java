package com.raksul.idplatform.security;

import com.raksul.idplatform.config.JwksConfig;
import com.raksul.idplatform.model.AuthToken;
import com.raksul.idplatform.repository.AuthTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ZeroTrustFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ZeroTrustFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";

    private final JwksConfig jwksConfig;
    private final AuthTokenRepository tokenRepository;

    public ZeroTrustFilter(JwksConfig jwksConfig, AuthTokenRepository tokenRepository) {
        this.jwksConfig = jwksConfig;
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendError(response, 401, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwksConfig.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Optional<AuthToken> authToken = tokenRepository.findByToken(token);

            if (authToken.isEmpty() || authToken.get().isRevoked()) {
                sendError(response, 401, "Token has been revoked");
                return;
            }

            if (authToken.get().getType() != AuthToken.TokenType.ACCESS) {
                sendError(response, 401, "Invalid token type");
                return;
            }

            request.setAttribute("userId", claims.getSubject());
            request.setAttribute("userEmail", claims.get("email", String.class));

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Zero-trust validation passed for user: {}", claims.getSubject());
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            sendError(response, 401, "Token has expired");
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            sendError(response, 401, "Invalid token");
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/.well-known") ||
               path.equals("/jwks.json") ||
               path.equals("/api/health") ||
               path.startsWith("/oauth2/authorize") ||
               path.equals("/oauth2/token") ||
               path.equals("/api/auth/login") ||
               path.equals("/api/auth/register") ||
               path.equals("/api/migration/import") ||
               path.equals("/api/migration/shadow-validate") ||
               path.equals("/api/migration/status") ||
               path.equals("/api/migration/cutover") ||
               path.equals("/api/migration/rollback") ||
               path.startsWith("/api/migration/dual-write") ||
               path.startsWith("/h2-console") ||
               path.startsWith("/error") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
