package com.acruzdb.misfinanzas.shared.domain;

import com.acruzdb.misfinanzas.auth.domain.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Relación de pertenencia entre un {@link User} y un {@link Household}.
 * <p>
 * El primer miembro (quien crea el household) recibe el rol
 * {@code "owner"}; el resto entra como {@code "member"}. De momento no
 * hay lógica de transferencia de propiedad ni restricciones especiales
 * para el owner más allá de poder añadir nuevos miembros — se ampliará
 * si el caso de uso lo requiere.
 */
@Entity
@Table(name = "household_members")
public class HouseholdMember {

    @EmbeddedId
    private HouseholdMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("householdId")
    @JoinColumn(name = "household_id")
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    protected HouseholdMember() {
    }

    /**
     * Crea una nueva pertenencia.
     *
     * @param household household al que se une el usuario
     * @param user      usuario que se une
     * @param role      {@code "owner"} o {@code "member"}
     */
    public HouseholdMember(Household household, User user, String role) {
        this.id = new HouseholdMemberId(household.getId(), user.getId());
        this.household = household;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void onJoin() {
        this.joinedAt = OffsetDateTime.now();
    }

    /** @return true si este miembro tiene rol de propietario del household */
    public boolean isOwner() {
        return "owner".equals(role);
    }

    public HouseholdMemberId getId() { return id; }
    public Household getHousehold() { return household; }
    public User getUser() { return user; }
    public String getRole() { return role; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
}