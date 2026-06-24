package it.unicas.cassitrack.security;

import it.unicas.cassitrack.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.http.Cookie;

import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = parseJwt(request);

        if (jwt != null && jwtUtil.validateToken(jwt) && !tokenBlacklistService.isBlacklisted(jwt)) {
            String username = jwtUtil.getUsernameFromToken(jwt);

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    // V-04 FIX (OWASP A02): Read JWT from httpOnly cookie first; fall back to Authorization header
    // for API clients (mobile apps, CLI tools, Swagger, etc.).
    private String parseJwt(HttpServletRequest request) {
        // 1. Prefer httpOnly cookie — not accessible to JavaScript
        if (request.getCookies() != null) {
            String cookieToken = Arrays.stream(request.getCookies())
                    .filter(c -> "cassitrack_jwt".equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
            if (cookieToken != null) return cookieToken;
        }

        // 2. Fall back to Authorization: Bearer <token> header (API clients)
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
