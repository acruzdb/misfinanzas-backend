package com.acruzdb.misfinanzas.auth.dto;

import com.acruzdb.misfinanzas.auth.domain.User;
import java.util.UUID;

/**
 * Respuesta de login/refresh: los dos tokens y un resumen del usuario.
 *
 * @param accessToken  JWT de corta duración, va en el header {@code Authorization}
 * @param refreshToken cadena opaca de larga duración, usada solo para renovar el access token
 */
public record AuthResponse(String accessToken, String refreshToken, UserSummary user) {

    public record UserSummary(UUID id, String email, String displayName) {
        public static UserSummary from(User u) {
            return new UserSummary(u.getId(), u.getEmail(), u.getDisplayName());
        }
    }
}