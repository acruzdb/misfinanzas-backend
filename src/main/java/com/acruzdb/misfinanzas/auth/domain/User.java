package com.acruzdb.misfinanzas.auth.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representa a una persona registrada en MisFinanzas.
 * <p>
 * Un usuario puede autenticarse con Google, por teléfono (OTP), o ambos
 * a la vez (ver {@code auth_identities} en el esquema de base de datos).
 * Es el propietario de sus movimientos personales y puede pertenecer
 * a uno o varios households (grupos compartidos).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Constructor vacío requerido por JPA. No usar directamente. */
    protected User() {
        // constructor vacío requerido por JPA, no lo uses tú directamente
    }

    /**
     * Crea un nuevo usuario.
     * <p>
     * El email se normaliza a minúsculas antes de guardarlo, para
     * conseguir una comparación case-insensitive sin depender de un
     * tipo de columna específico de PostgreSQL (CITEXT), que Hibernate
     * no puede validar de forma nativa.
     *
     * @param email        email del usuario, puede ser null si solo usa teléfono
     * @param phoneNumber  teléfono en formato E.164, puede ser null si solo usa email
     * @param displayName  nombre visible en la app, obligatorio
     */
    public User(String email, String phoneNumber, String displayName) {
        this.email = email != null ? email.toLowerCase() : null;
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** @return identificador único del usuario */
    public UUID getId() { return id; }
    /** @return email del usuario */
    public String getEmail() { return email; }
    /** @return teléfono del usuario */
    public String getPhoneNumber() { return phoneNumber; }
    /** @return nombre del usuario */
    public String getDisplayName() { return displayName; }
    /** @return avatar del usuario */
    public String getAvatarUrl() { return avatarUrl; }
    /** @return está verificado el teléfono del usuario */
    public boolean isPhoneVerified() { return phoneVerified; }
    /** @return está verificado el email del usuario */
    public boolean isEmailVerified() { return emailVerified; }
    /** @return estado del usuario */
    public String getStatus() { return status; }
    /** @return fecha de creación del usuario */
    public OffsetDateTime getCreatedAt() { return createdAt; }
    /** @return fecha de actualización del usuario */
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}