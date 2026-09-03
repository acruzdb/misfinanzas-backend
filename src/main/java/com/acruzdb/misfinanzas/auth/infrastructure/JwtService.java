package com.acruzdb.misfinanzas.auth.infrastructure;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Genera y valida los access tokens (JWT) y los refresh tokens
 * (cadenas opacas, no JWT) de la aplicación.
 * <p>
 * El access token es un JWT firmado con HS256 que contiene el id del
 * usuario como {@code subject}, de corta duración. El refresh token es
 * una cadena aleatoria sin estructura; su valor en claro solo existe
 * en el momento de emitirlo — a partir de ahí, solo se guarda y compara
 * su hash SHA-256 (ver {@link #hashToken}).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un access token JWT para el usuario indicado.
     *
     * @param userId id del usuario autenticado
     * @return JWT firmado, listo para enviar en {@code Authorization: Bearer ...}
     */
    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.accessTokenMinutes()));
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida un access token y extrae el id de usuario que contiene.
     *
     * @param token JWT recibido del cliente
     * @return id del usuario, si el token es válido y no ha expirado
     * @throws io.jsonwebtoken.JwtException si el token no es válido,
     *         está expirado, o la firma no coincide
     */
    public UUID validateAndGetUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }

    /**
     * Genera un nuevo refresh token en claro (solo se devuelve una vez
     * al cliente; nunca se persiste este valor, solo su hash).
     *
     * @return cadena aleatoria de 256 bits, codificada en Base64 URL-safe
     */
    public String generateRefreshTokenValue() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** @return días de validez configurados para el refresh token */
    public int refreshTokenValidityDays() {
        return properties.refreshTokenDays();
    }

    /**
     * Calcula el hash SHA-256 de un refresh token, para guardarlo o
     * compararlo sin nunca tener el valor en claro en la base de datos.
     *
     * @param rawToken refresh token en claro
     * @return hash en hexadecimal
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en cualquier JVM estándar;
            // si esto salta, algo está gravemente mal con el entorno.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}