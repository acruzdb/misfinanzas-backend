package com.acruzdb.misfinanzas.shared.infrastructure;

import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de {@link HouseholdMember}.
 * <p>
 * Se usan consultas {@code @Query} explícitas en vez de derived query
 * methods sobre la clave compuesta ({@code findById_UserId(...)}), para
 * evitar la ambigüedad de nombres que la navegación por propiedades
 * anidadas de {@code @EmbeddedId} puede generar en Spring Data.
 */
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {

    /**
     * Lista todos los households a los que pertenece un usuario.
     *
     * @param userId id del usuario
     * @return las pertenencias del usuario, una por cada household
     */
    @Query("SELECT hm FROM HouseholdMember hm WHERE hm.user.id = :userId")
    List<HouseholdMember> findByUserId(@Param("userId") UUID userId);

    /**
     * Busca la pertenencia concreta de un usuario a un household, usado
     * para comprobar si tiene acceso antes de mostrar datos del grupo.
     *
     * @param householdId id del household
     * @param userId      id del usuario
     * @return la pertenencia si existe, vacía si el usuario no es miembro
     */
    @Query("SELECT hm FROM HouseholdMember hm WHERE hm.household.id = :householdId AND hm.user.id = :userId")
    Optional<HouseholdMember> findByHouseholdIdAndUserId(@Param("householdId") UUID householdId, @Param("userId") UUID userId);

    /**
     * Lista todos los miembros de un household.
     *
     * @param householdId id del household
     * @return los miembros, sin orden garantizado
     */
    @Query("SELECT hm FROM HouseholdMember hm WHERE hm.household.id = :householdId")
    List<HouseholdMember> findByHouseholdId(@Param("householdId") UUID householdId);
}