package com.acruzdb.misfinanzas.auth.application;

import com.acruzdb.misfinanzas.auth.domain.RefreshToken;
import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.dto.AuthResponse;
import com.acruzdb.misfinanzas.auth.infrastructure.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/**
 * Lógica de negocio de autenticación: login con Google, renovación de
 * sesión (refresh) y cierre de sesión (logout).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       GoogleTokenVerifierService googleTokenVerifierService, JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.jwtService = jwtService;
    }

    /**
     * Autentica a un usuario a partir de un ID token de Google.
     * <p>
     * Si es la primera vez que ese email inicia sesión, se crea el
     * usuario automáticamente (alta implícita en el primer login,
     * sin un formulario de registro separado).
     *
     * @param idToken    ID token emitido por Google Identity Services
     * @param deviceInfo descripción del cliente (User-Agent), opcional
     * @param ipAddress  IP de origen, opcional
     * @return tokens de sesión y resumen del usuario
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken, String deviceInfo, String ipAddress) {
        GoogleTokenVerifierService.GoogleUserInfo googleUser = googleTokenVerifierService.verify(idToken);

        User user = userRepository.findByEmailIgnoreCase(googleUser.email())
                .orElseGet(() -> userRepository.save(new User(googleUser.email(), null, googleUser.name())));

        return issueTokens(user, deviceInfo, ipAddress);
    }

    /**
     * Intercambia un refresh token válido por un nuevo par de tokens.
     * <p>
     * El refresh token usado se revoca (rotación): cada renovación
     * emite uno nuevo, así que un refresh token robado deja de servir
     * en cuanto el dueño legítimo lo use una vez más.
     *
     * @param refreshTokenValue refresh token en claro recibido del cliente
     * @param deviceInfo        descripción del cliente, opcional
     * @param ipAddress         IP de origen, opcional
     * @return nuevo par de tokens
     * @throws ResponseStatusException 401 si el refresh token no existe,
     *         expiró, o ya fue revocado
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue, String deviceInfo, String ipAddress) {
        String hash = jwtService.hashToken(refreshTokenValue);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));

        if (!existing.isValid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada o revocada");
        }

        existing.revoke();
        return issueTokens(existing.getUser(), deviceInfo, ipAddress);
    }

    /**
     * Cierra una sesión revocando su refresh token.
     * <p>
     * Es una operación idempotente a propósito: si el token ya no
     * existe o ya estaba revocado, no lanza error — el resultado que
     * el usuario quiere ("quedar deslogueado") ya se cumple.
     *
     * @param refreshTokenValue refresh token en claro a revocar
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        String hash = jwtService.hashToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(RefreshToken::revoke);
    }

    private AuthResponse issueTokens(User user, String deviceInfo, String ipAddress) {
        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshTokenValue = jwtService.generateRefreshTokenValue();

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(jwtService.refreshTokenValidityDays());
        RefreshToken refreshToken = new RefreshToken(
                user, jwtService.hashToken(refreshTokenValue), deviceInfo, ipAddress, expiresAt
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshTokenValue, AuthResponse.UserSummary.from(user));
    }
}