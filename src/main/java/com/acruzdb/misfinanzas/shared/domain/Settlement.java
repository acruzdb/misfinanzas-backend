package com.acruzdb.misfinanzas.shared.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de una liquidación real entre dos miembros de un household
 * ("Bea le pagó 20€ a Adri en efectivo").
 * <p>
 * No mueve dinero de verdad — es solo un apunte para que el cálculo de
 * balances refleje que esa deuda ya quedó saldada fuera de la app.
 */
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "settled_at", nullable = false)
    private OffsetDateTime settledAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    protected Settlement() {
    }

    /**
     * Registra una liquidación.
     *
     * @param household  grupo al que pertenece
     * @param fromUser   quien pagó en la vida real
     * @param toUser     quien recibió el pago
     * @param amount     importe liquidado, siempre positivo
     * @param createdBy  quien registra el apunte (normalmente, quien pagó)
     */
    public Settlement(Household household, User fromUser, User toUser, BigDecimal amount, User createdBy) {
        this.household = household;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.settledAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public User getFromUser() { return fromUser; }
    public User getToUser() { return toUser; }
    public BigDecimal getAmount() { return amount; }
}