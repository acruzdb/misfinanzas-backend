package com.acruzdb.misfinanzas.transactions.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representa un ingreso o gasto, personal o compartido.
 * <p>
 * Si {@code householdId} es {@code null}, el movimiento es personal.
 * Si tiene valor, pertenece a un grupo compartido (household).
 * El campo {@code type} distingue ingreso ({@code income}) de
 * gasto ({@code expense}); el importe ({@code amount}) es siempre
 * positivo, el signo lo determina {@code type}.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // household_id y category_id se quedan como UUID "sueltos" por ahora:
    // los módulos shared y categories todavía no existen, así que evitamos
    // crear una dependencia prematura hacia entidades que no hemos diseñado.
    // Cuando lleguemos a esos módulos, los convertimos en @ManyToOne reales.
    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false, length = 10)
    private String type; // "income" | "expense" — validado en el DTO de entrada

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 20)
    private String source = "manual"; // "manual" | "excel_import" | "bank_sync"

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Transaction() {
    }

    /**
     * Crea un nuevo movimiento personal (sin categoría ni household todavía).
     *
     * @param user             usuario que da de alta el movimiento
     * @param type             {@code "income"} o {@code "expense"}
     * @param amount           importe, siempre positivo
     * @param transactionDate  fecha del movimiento, no puede ser futura
     */
    public Transaction(User user, String type, BigDecimal amount, LocalDate transactionDate) {
        this.user = user;
        this.type = type;
        this.amount = amount;
        this.transactionDate = transactionDate;
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

    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public void setDescription(String description) { this.description = description; }
    public void setCurrency(String currency) { this.currency = currency; }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public UUID getHouseholdId() { return householdId; }
    public UUID getCategoryId() { return categoryId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getSource() { return source; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}