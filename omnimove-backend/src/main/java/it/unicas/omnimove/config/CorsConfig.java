package it.unicas.omnimove.config;

/*
 * CORS is handled by SecurityConfig.corsSource() which reads allowed origins
 * from the 'omnimove.cors.allowed-origins' property in application.yml.
 *
 * This class is intentionally left empty. The previous CorsFilter bean that
 * lived here registered "null" as an allowed origin (permitting requests from
 * sandboxed iframes and file:// pages) and combined credentials:true with
 * a wildcard header list (rejected by browsers, confusing in logs). Both
 * issues are avoided by delegating entirely to the Spring Security CORS chain.
 */
public class CorsConfig {}
