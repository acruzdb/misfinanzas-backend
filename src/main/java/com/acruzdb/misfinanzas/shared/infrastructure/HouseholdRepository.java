package com.acruzdb.misfinanzas.shared.infrastructure;

import com.acruzdb.misfinanzas.shared.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Acceso a datos de {@link Household}. */
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
}