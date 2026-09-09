package com.acruzdb.misfinanzas.statements.infrastructure;

import com.acruzdb.misfinanzas.statements.domain.StatementImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatementImportRepository extends JpaRepository<StatementImport, UUID> {

    /** Historial de importaciones de un usuario, la más reciente primero. */
    List<StatementImport> findByUser_IdOrderByCreatedAtDesc(UUID userId);
}