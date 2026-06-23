package it.unicas.omnimove.config;
import it.unicas.omnimove.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.List;
import jakarta.servlet.DispatcherType;

@Configuration @EnableWebSecurity @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;

    @Value("${omnimove.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
                .cors(c -> c.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a

                        // Allow internal forwards and error routing to pass through
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                        // ── 1. Public — login page, static assets, auth endpoints ──────
                        .requestMatchers(
                                "/", "/error", "/omnimove-login.html", "/reset-password.html",
                                "/favicon.ico", "/*.css", "/*.js"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify"
                        ).permitAll()
                        .requestMatchers( // API docs — require authentication
                                "/api/docs/**",
                                "/api/swagger-ui/**",
                                "/api/swagger-ui.html"
                        ).authenticated()

                        // ── 2. Admin only ────────────────────────────────────────────────
                        .requestMatchers(
                                "/omnimove-admin.html",
                                "/api/v1/admin/**"
                        ).hasAnyAuthority("ADMIN", "ROLE_ADMIN")

                        // ── 3. Traveller only ────────────────────────────────────────────
                        .requestMatchers(
                                "/omnimove-traveller.html",
                                "/api/v1/traveller/**"
                        ).hasAnyAuthority("TRAVELLER", "ROLE_TRAVELLER")

                        // ── 4. Shared — any authenticated user (traveller or admin) ──────
                        .requestMatchers(
                                "/api/v1/journeys/**",
                                "/api/v1/ai/**",
                                "/api/v1/traffic/**"
                        ).hasAnyAuthority("TRAVELLER", "ADMIN", "ROLE_TRAVELLER", "ROLE_ADMIN")

                        // ── 5. Everything else requires authentication ────────────────────
                        .anyRequest().authenticated()
                )
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);  // JWT is sent in Authorization header, not cookies
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}