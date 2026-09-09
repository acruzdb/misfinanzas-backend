package com.acruzdb.misfinanzas.statements.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de una importación de extracto, con su resultado.
 * <p>
 * A diferencia de {@code Transaction} o {@code Category}, el fichero
 * original no se persiste en ningún almacenamiento (ver nota de
 * alcance en el módulo) — esta entidad guarda solo el resultado del
 * proceso, útil para que el usuario vea un historial de sus
 * importaciones y para depurar si algo falló.
 */
@Entity
@Table(name = "statement_imports")
public class StatementImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_format", nullable = false, length = 20)
    private String fileFormat = "xlsx";

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "rows_total")
    private Integer rowsTotal;

    @Column(name = "rows_imported")
    private Integer rowsImported;

    @Column(name = "rows_failed")
    private Integer rowsFailed;

    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected StatementImport() {
    }

    public StatementImport(User user, UUID householdId, String originalFilename) {
        this.user = user;
        this.householdId = householdId;
        this.originalFilename = originalFilename;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /** Marca la importación como completada con éxito, registrando el resultado. */
    public void markCompleted(int total, int imported, int failed, String errorSummary) {
        this.status = "completed";
        this.rowsTotal = total;
        this.rowsImported = imported;
        this.rowsFailed = failed;
        this.errorSummary = errorSummary;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public Integer getRowsTotal() { return rowsTotal; }
    public Integer getRowsImported() { return rowsImported; }
    public Integer getRowsFailed() { return rowsFailed; }
    public String getErrorSummary() { return errorSummary; }
    public String getOriginalFilename() { return originalFilename; }
    public java.time.OffsetDateTime getCreatedAt() { return createdAt; }
}