package com.acruzdb.misfinanzas.transactions.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representa la parte que le corresponde pagar a un miembro concreto
 * de un gasto compartido.
 * <p>
 * Solo existe para movimientos de tipo {@code expense} con
 * {@code householdId} no nulo. La suma de todos los {@link #shareAmount}
 * de un mismo movimiento debe coincidir exactamente con su importe
 * total — se valida en {@code TransactionService} antes de persistir,
 * esta entidad en sí no conoce el importe total del movimiento.
 */
@Entity
@Table(name = "expense_splits")
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ExpenseSplit() {
    }

    /**
     * Crea el reparto de un gasto para un participante concreto.
     *
     * @param transaction  movimiento al que pertenece este reparto
     * @param user         participante al que le corresponde esta parte
     * @param shareAmount  importe que le toca pagar, siempre positivo o cero
     */
    public ExpenseSplit(Transaction transaction, User user, BigDecimal shareAmount) {
        this.transaction = transaction;
        this.user = user;
        this.shareAmount = shareAmount;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public User getUser() { return user; }
    public BigDecimal getShareAmount() { return shareAmount; }
}