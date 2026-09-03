package com.acruzdb.misfinanzas.auth.infrastructure;

import com.acruzdb.misfinanzas.auth.application.AuthService;
import com.acruzdb.misfinanzas.auth.dto.AuthResponse;
import com.acruzdb.misfinanzas.auth.dto.GoogleLoginRequest;
import com.acruzdb.misfinanzas.auth.dto.RefreshRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de autenticación (no requieren un JWT previo).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Login (o alta implícita) a partir de un ID token de Google. */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                        HttpServletRequest httpRequest) {
        AuthResponse response = authService.loginWithGoogle(
                request.idToken(), httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(response);
    }

    /** Intercambia un refresh token válido por un nuevo par de tokens. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(
                request.refreshToken(), httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(response);
    }

    /** Cierra sesión revocando el refresh token indicado. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}