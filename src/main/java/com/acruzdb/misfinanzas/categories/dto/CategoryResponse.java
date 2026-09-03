package com.acruzdb.misfinanzas.categories.dto;

import com.acruzdb.misfinanzas.categories.domain.Category;
import java.util.UUID;

/**
 * Representación de una categoría devuelta por la API.
 *
 * @param id       identificador
 * @param name     nombre visible
 * @param kind     {@code "income"} o {@code "expense"}
 * @param colorHex color en hexadecimal para pintarla en el dashboard
 * @param icon     identificador de icono, puede ser null
 * @param isSystem true si es una categoría predefinida (no se puede borrar)
 */
public record CategoryResponse(UUID id, String name, String kind, String colorHex, String icon, boolean isSystem) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.getColorHex(), c.getIcon(), c.isSystem());
    }
}