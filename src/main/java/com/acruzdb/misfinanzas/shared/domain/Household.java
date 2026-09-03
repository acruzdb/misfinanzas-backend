package com.acruzdb.misfinanzas.shared.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Grupo compartido (pareja, piso compartido...) sobre el que se pueden
 * registrar movimientos y categorías comunes a varios usuarios.
 * <p>
 * La pertenencia de usuarios al household vive en {HouseholdMember},
 * no aquí — esta entidad solo guarda los datos propios del grupo.
 */
@Entity
@Table(name = "households")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Household() {
    }

    /**
     * Crea un nuevo household.
     *
     * @param name      nombre visible, p.ej. "Casa Alex &amp; Sam"
     * @param createdBy usuario que lo crea (se convertirá en su owner,
     *                  ver {HouseholdMember})
     */
    public Household(String name, User createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public User getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}