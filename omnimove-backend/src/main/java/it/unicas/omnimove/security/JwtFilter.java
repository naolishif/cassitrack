package it.unicas.omnimove.security;

import it.unicas.omnimove.service.TokenBlacklistService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String JWT_COOKIE_NAME = "omnimove_jwt";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Lazy
    public JwtFilter(JwtUtil jwtUtil, UserDetailsService uds, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = uds;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // V-04 FIX (OWASP A02): Prefer httpOnly cookie; fall back to Authorization header
        String token = resolveToken(req);

        if (token != null && jwtUtil.isValid(token) && !tokenBlacklistService.isBlacklisted(token)) {
            String email = jwtUtil.extractEmail(token);
            UserDetails ud = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(req, res);
    }

    private String resolveToken(HttpServletRequest req) {
        // 1. httpOnly cookie — not accessible to JavaScript
        if (req.getCookies() != null) {
            String cookieToken = Arrays.stream(req.getCookies())
                    .filter(c -> JWT_COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .orElse(null);
            if (cookieToken != null) return cookieToken;
        }

        // 2. Authorization: Bearer <token> header (API clients, Swagger, mobile)
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
