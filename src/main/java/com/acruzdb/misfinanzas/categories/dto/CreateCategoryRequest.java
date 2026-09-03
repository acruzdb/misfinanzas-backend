package com.acruzdb.misfinanzas.categories.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Datos de entrada para crear una categoría, personal o de household.
 *
 * @param name       nombre visible, entre 1 y 60 caracteres
 * @param kind       {@code "income"} o {@code "expense"}
 * @param colorHex   color en formato hexadecimal (p.ej. {@code #EF4444}), opcional
 * @param icon       identificador de icono, opcional
 * @param householdId si se indica, la categoría se crea como compartida
 *                    para ese household en vez de personal; requiere que
 *                    el usuario sea miembro
 */
public record CreateCategoryRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
        String name,

        @NotBlank(message = "El tipo es obligatorio")
        @Pattern(regexp = "income|expense", message = "El tipo debe ser 'income' o 'expense'")
        String kind,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser hexadecimal, p.ej. #EF4444")
        String colorHex,

        String icon,

        UUID householdId
) {}