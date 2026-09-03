package com.acruzdb.misfinanzas.shared.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Petición para añadir un miembro existente a un household por su email.
 * <p>
 * Solo funciona si esa persona ya tiene una cuenta creada (ha iniciado
 * sesión al menos una vez) — no envía invitaciones a emails desconocidos,
 * ver la nota de alcance en {@code HouseholdService}.
 *
 * @param email email del usuario a añadir
 */
public record AddMemberRequest(@NotBlank @Email String email) {}