package com.acruzdb.misfinanzas.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Petición para intercambiar un refresh token por un nuevo access token. */
public record RefreshRequest(@NotBlank String refreshToken) {}