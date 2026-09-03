package com.acruzdb.misfinanzas.auth.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de {@link RefreshToken} (sesiones de usuario).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Busca una sesión por el hash de su refresh token, usado al
     * intercambiar un refresh token por un nuevo access token.
     *
     * @param tokenHash hash SHA-256 del refresh token recibido del cliente
     * @return la sesión si existe, vacía en caso contrario
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}