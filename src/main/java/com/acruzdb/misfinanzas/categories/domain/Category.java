package com.acruzdb.misfinanzas.categories.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Categoría o etiqueta usada para clasificar movimientos (ingresos o gastos).
 * <p>
 * Una categoría es de exactamente uno de estos tres tipos, nunca una
 * combinación:
 * <ul>
 *   <li><b>Personal</b>: {@code ownerUser} tiene valor, {@code householdId} es null.</li>
 *   <li><b>Compartida</b>: {@code householdId} tiene valor, {@code ownerUser} es null.
 *       Se guarda como UUID suelto (no como relación JPA) porque el módulo
 *       {@code shared} (households) todavía no existe — se convertirá en
 *       una relación real cuando lo construyamos.</li>
 *   <li><b>De sistema</b>: {@code isSystem} es true, ambos campos anteriores
 *       son null. Son las categorías predefinidas (Comida, Ocio, Fijos...)
 *       visibles para todos los usuarios.</li>
 * </ul>
 * Esta invariante también está protegida a nivel de base de datos con un
 * {@code CHECK constraint} (ver {@code schema_init.sql}), como red de
 * seguridad adicional por si algún día se inserta un dato saltándose la
 * capa de aplicación.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "household_id")
    private UUID householdId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex = "#6B7280";

    private String icon;

    @Column(nullable = false, length = 10)
    private String kind; // "income" | "expense"

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Category() {
    }

    /**
     * Crea una categoría personal para un usuario.
     *
     * @param ownerUser usuario propietario de la categoría
     * @param name      nombre visible, p.ej. "Comida"
     * @param kind      {@code "income"} o {@code "expense"}
     */
    public Category(User ownerUser, String name, String kind) {
        this.ownerUser = ownerUser;
        this.name = name;
        this.kind = kind;
    }

    /**
     * Crea una categoría compartida para un household.
     *
     * @param householdId id del household propietario
     * @param name        nombre visible, p.ej. "Alquiler"
     * @param kind        {@code "income"} o {@code "expense"}
     */
    public Category(UUID householdId, String name, String kind) {
        this.householdId = householdId;
        this.name = name;
        this.kind = kind;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public void setIcon(String icon) { this.icon = icon; }

    /**
     * Comprueba si esta categoría pertenece al usuario indicado.
     * <p>
     * Usa {@link Objects#equals} en vez de {@code ownerUser.getId().equals(userId)}
     * a propósito: si {@code ownerUser} existe pero todavía no tiene id
     * asignado (por ejemplo, una entidad recién construida y aún no
     * persistida), esta comparación devuelve {@code false} de forma
     * segura en vez de lanzar {@link NullPointerException}.
     *
     * @param userId id del usuario a comprobar
     * @return true si esta categoría pertenece al usuario indicado
     */
    public boolean belongsTo(UUID userId) {
        return ownerUser != null && Objects.equals(ownerUser.getId(), userId);
    }

    public UUID getId() { return id; }
    public User getOwnerUser() { return ownerUser; }
    public UUID getHouseholdId() { return householdId; }
    public String getName() { return name; }
    public String getColorHex() { return colorHex; }
    public String getIcon() { return icon; }
    public String getKind() { return kind; }
    public boolean isSystem() { return isSystem; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}