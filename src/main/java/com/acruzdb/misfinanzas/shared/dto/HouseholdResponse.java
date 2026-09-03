package com.acruzdb.misfinanzas.shared.dto;

import com.acruzdb.misfinanzas.shared.domain.Household;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;

import java.util.List;
import java.util.UUID;

/**
 * Representación de un household devuelta por la API, con sus miembros.
 *
 * @param id      identificador del household
 * @param name    nombre visible
 * @param members lista de miembros actuales
 */
public record HouseholdResponse(UUID id, String name, List<MemberSummary> members) {

    public static HouseholdResponse from(Household household, List<HouseholdMember> members) {
        List<MemberSummary> summaries = members.stream().map(MemberSummary::from).toList();
        return new HouseholdResponse(household.getId(), household.getName(), summaries);
    }

    /**
     * Resumen de un miembro del household.
     *
     * @param userId      id del usuario
     * @param displayName nombre visible
     * @param role        {@code "owner"} o {@code "member"}
     */
    public record MemberSummary(UUID userId, String displayName, String role) {
        public static MemberSummary from(HouseholdMember member) {
            return new MemberSummary(member.getUser().getId(), member.getUser().getDisplayName(), member.getRole());
        }
    }
}