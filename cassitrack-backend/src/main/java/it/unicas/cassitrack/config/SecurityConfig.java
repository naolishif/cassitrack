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
import jakarta.servlet.DispatcherType;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // Disable CSRF - we use JWT, not sessions
                .csrf(csrf -> csrf.disable())

                // Allow CORS from frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless — no HTTP sessions, JWT only
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // Allow internal forwards and error routing to pass through
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                        // ── 1. Public Access points (UI and Auth) ───────────────
                        .requestMatchers("/error", "/", "/cassitrack-login.html", "/api/v1/auth/login").permitAll()

                        // ── 2. Public APIs (GTFS, Live Tracking, Swagger) ───────────────
                        .requestMatchers(org.springframework.http.HttpMethod.GET, // Public for OMNIMOVE
                                "/api/v1/vehicles/**",
                                "/api/v1/stops/**",
                                "/api/v1/siri/**",
                                "/api/v1/feed/**",
                                "/api/v1/journeys/**",
                                "/api/v1/telemetry/**"
                         ).permitAll()

                        .requestMatchers( // Dev tools
                                "/h2-console/**",
                                "/ws/**",
                                "/api/docs/**",
                                "/api/swagger-ui/**",
                                "/api/swagger-ui.html",
                                "/ws/**",
                                "/api/v1/auth/**",
                                "/api/static/**"
                        ).permitAll()

                        // ── 3. Role-Specific Protected HTML ───────────────
                        .requestMatchers(
                                "/cassitrack-fleetmanager.html",
                                "/api/v1/analytics/**",
                                "cassitrack-analytics.html",
                                "/api/v1/ai/**"
                        ).hasAnyAuthority("FLEET_MANAGER", "ROLE_FLEET_MANAGER")

                        .requestMatchers(
                                "/cassitrack-admin.html",
                                "/api/v1/users/**",
                                "/api/v1/telemetry/**",
                                "/api/v1/ai/**",
                                "/api/v1/auth/register"
                        ).hasAnyAuthority("ADMIN", "ROLE_ADMIN")

                        .requestMatchers("/api/v1/driver/**"
                        ).hasAnyAuthority("DRIVER", "ROLE_DRIVER")

                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/vehicles/**", "/api/v1/stops/**", "/api/v1/journeys/**").hasAnyAuthority("FLEET_MANAGER", "ROLE_FLEET_MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/vehicles/**", "/api/v1/stops/**", "/api/v1/journeys/**").hasAnyAuthority("FLEET_MANAGER", "ROLE_FLEET_MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/vehicles/**", "/api/v1/stops/**", "/api/v1/journeys/**").hasAnyAuthority("FLEET_MANAGER", "ROLE_FLEET_MANAGER")

                        // ── 4. Everything else requires authentication ───────
                        .anyRequest().authenticated()
                );

        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationProvider authenticationProvider(
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService) {
        org.springframework.security.authentication.dao.DaoAuthenticationProvider authProvider =
                new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
}
