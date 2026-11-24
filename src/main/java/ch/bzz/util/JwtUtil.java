package ch.bzz.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and validates JWTs (HS256, issuer "AccountingApp", valid for one hour).
 * The subject is the project name.
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String ISSUER = "AccountingApp";
    private static final long VALIDITY_MILLIS = 60 * 60 * 1000L; // 1 hour

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 needs at least 256 bits. Fail at startup instead of running with a weak key.
            throw new IllegalStateException("jwt.secret must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String projectName) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(projectName)
                .setIssuer(ISSUER)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + VALIDITY_MILLIS))
                .setId(UUID.randomUUID().toString())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * @return the project name (subject) if the token is valid, otherwise empty
     */
    public Optional<String> validateToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseClaimsJws(token);
            // Only accept the algorithm we sign with, never what the token header claims.
            if (!SignatureAlgorithm.HS256.getValue().equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            return Optional.ofNullable(jws.getBody().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
