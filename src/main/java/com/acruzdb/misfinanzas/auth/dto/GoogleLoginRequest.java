package com.acruzdb.misfinanzas.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Petición de login con el ID token obtenido de Google Identity Services. */
public record GoogleLoginRequest(@NotBlank(message = "El idToken es obligatorio") String idToken) {}