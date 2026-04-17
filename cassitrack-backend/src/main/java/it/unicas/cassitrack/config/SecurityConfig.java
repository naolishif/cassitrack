package it.unicas.cassitrack.config;

import it.unicas.cassitrack.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration for CASSITRACK.
 * <p>
 * Public endpoints (no auth needed):
 * <ul>
 *   <li>GET /api/v1/vehicles - real-time bus positions (for OMNIMOVE + public)</li>
 *   <li>GET /api/v1/stops/{stopId}/arrivals - ETA at stops (for OMNIMOVE + public)</li>
 *   <li>GET /api/v1/feed/gtfs-rt - GTFS Realtime feed (for NAP + 3rd parties)</li>
 *   <li>GET /api/docs/** - Swagger UI</li>
 *   <li>WS /ws/vehicles - WebSocket for fleet dashboard</li>
 * </ul>
 * <p>
 * Protected endpoints (JWT required):
 * <ul>
 *   <li>GET /api/v1/analytics/** - fleet analytics (fleet manager role)</li>
 *   <li>GET /api/v1/alerts - alerts (fleet manager role)</li>
 *   <li>POST /api/v1/auth/** - login/register</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF - we use JWT, not sessions
            .csrf(csrf -> csrf.disable())

            // Allow CORS from frontend (React dev server on localhost:3000)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless — no HTTP sessions, JWT only
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth
                // ── Public endpoints ──────────────────────────────
                .requestMatchers(
                    "/api/v1/vehicles",
                    "/api/v1/vehicles/{id}",
                    "/api/v1/stops/{stopId}/arrivals",
                        "/api/v1/feed/**",
                    "/api/v1/feed/gtfs-rt",
                    "/api/v1/ai/**",
                        "/api/v1/journeys/**",
                    "/api/docs/**",
                    "/api/swagger-ui/**",
                    "/api/swagger-ui.html",
                    "/ws/**"
                ).permitAll()

                // ── Auth endpoints ────────────────────────────────
                .requestMatchers("/api/v1/auth/**").permitAll()

                // ── Everything else requires authentication ───────
                .anyRequest().authenticated()
            );

        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS config: allow requests from the React frontend
     * during local development.
     *
     * In production, restrict origins to your actual domain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",   // React dev server (OMNIMOVE PWA)
            "http://localhost:5173",    // Vite dev server (alternative)
                "null"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
