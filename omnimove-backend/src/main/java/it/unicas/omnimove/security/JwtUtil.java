package it.unicas.omnimove.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
@Component
public class JwtUtil {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration-ms}") private long expirationMs;
    private Key getKey() { return Keys.hmacShaKeyFor(secret.getBytes()); }
    public String generateToken(String email) {
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getKey(), SignatureAlgorithm.HS256)
            .compact();
    }
    public String extractEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(getKey()).build()
            .parseClaimsJws(token).getBody().getSubject();
    }
    public boolean isValid(String token) {
        try { Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token); return true; }
        catch (Exception e) { return false; }
    }

    public long getExpirationMs() { return expirationMs; }

    public long getRemainingValidityMs(String token) {
        try {
            java.util.Date expiry = Jwts.parserBuilder()
                .setSigningKey(getKey()).build()
                .parseClaimsJws(token).getBody().getExpiration();
            return Math.max(0, expiry.getTime() - System.currentTimeMillis());
        } catch (JwtException e) {
            return 0;
        }
    }
}
