package com.acruzdb.misfinanzas.statements.infrastructure;

import com.acruzdb.misfinanzas.statements.domain.StatementImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StatementImportRepository extends JpaRepository<StatementImport, UUID> {
}