package com.acruzdb.misfinanzas.categories.infrastructure;

import com.acruzdb.misfinanzas.categories.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a datos de {@link Category}.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Lista las categorías visibles para un usuario: las suyas propias
     * (personales) más las de sistema (compartidas por todos).
     * <p>
     * De momento no incluye categorías de household, porque ese módulo
     * todavía no existe — se ampliará esta consulta cuando lo construyamos.
     *
     * @param userId id del usuario
     * @return categorías personales del usuario + categorías de sistema,
     *         ordenadas por nombre
     */
    @Query("SELECT c FROM Category c WHERE c.ownerUser.id = :userId OR c.isSystem = true ORDER BY c.name")
    List<Category> findVisibleForUser(@Param("userId") UUID userId);
}