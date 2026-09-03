package com.acruzdb.misfinanzas.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos de entrada para crear un household.
 *
 * @param name nombre visible del grupo, entre 1 y 100 caracteres
 */
public record CreateHouseholdRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String name
) {}