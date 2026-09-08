package com.acruzdb.misfinanzas.shared.infrastructure;

import com.acruzdb.misfinanzas.shared.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a datos de {@link Settlement}.
 * <p>
 * {@code findByHousehold_Id} usa el guion bajo para indicar a Spring
 * Data que navegue la relación {@code household} hasta su campo
 * {@code id} — necesario porque {@code household} es en sí mismo una
 * entidad, no un campo escalar directo de {@code Settlement}.
 */
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findByHousehold_Id(UUID householdId);
}