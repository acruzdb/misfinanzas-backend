package com.acruzdb.misfinanzas.shared.domain;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clave primaria compuesta de {HouseholdMember}: un usuario solo
 * puede pertenecer una vez al mismo household (par único household+usuario).
 * <p>
 * Las claves compuestas con {@code @EmbeddedId} en JPA requieren
 * implementar {@code equals}/{@code hashCode} manualmente basados en
 * todos los campos — Hibernate los usa para identificar la fila de forma
 * única en su caché de primer nivel; sin ellos, el comportamiento de
 * persistencia es indefinido.
 */
@Embeddable
public class HouseholdMemberId implements Serializable {

    private UUID householdId;
    private UUID userId;

    protected HouseholdMemberId() {
    }

    public HouseholdMemberId(UUID householdId, UUID userId) {
        this.householdId = householdId;
        this.userId = userId;
    }

    public UUID getHouseholdId() { return householdId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HouseholdMemberId that)) return false;
        return Objects.equals(householdId, that.householdId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(householdId, userId);
    }
}