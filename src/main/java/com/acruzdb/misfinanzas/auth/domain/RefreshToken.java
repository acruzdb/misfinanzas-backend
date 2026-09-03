package com.acruzdb.misfinanzas.auth.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representa una sesión activa (o revocada) de un usuario.
 * <p>
 * Nunca se guarda el refresh token en claro, solo su hash SHA-256
 * (ver {@code JwtService#hashToken}). Cuando el usuario cierra sesión,
 * o si se detecta un uso sospechoso, {@code revokedAt} se rellena en
 * vez de borrar la fila, para conservar el histórico de sesiones.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RefreshToken() {
    }

    /**
     * Crea una nueva sesión (refresh token) para un usuario.
     *
     * @param user       usuario propietario de la sesión
     * @param tokenHash  hash SHA-256 del refresh token (nunca el valor en claro)
     * @param deviceInfo descripción del dispositivo/cliente, opcional
     * @param ipAddress  IP desde la que se creó la sesión, opcional
     * @param expiresAt  momento de expiración
     */
    public RefreshToken(User user, String tokenHash, String deviceInfo, String ipAddress, OffsetDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /** Marca esta sesión como revocada en el momento actual. */
    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }

    /** @return true si la sesión no ha expirado ni ha sido revocada */
    public boolean isValid() {
        return revokedAt == null && expiresAt.isAfter(OffsetDateTime.now());
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
}