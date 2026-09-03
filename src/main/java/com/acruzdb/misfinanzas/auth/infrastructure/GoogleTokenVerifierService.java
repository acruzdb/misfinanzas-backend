package com.acruzdb.misfinanzas.auth.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

/**
 * Verifica los ID tokens emitidos por Google Identity Services.
 * <p>
 * Comprueba la firma criptográfica del token (contra las claves
 * públicas de Google, que la librería cachea y refresca sola) y que
 * el token fue emitido específicamente para nuestro Client ID — esto
 * último evita que un token válido de otra aplicación se cuele aquí.
 */
@Service
public class GoogleTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifierService(GoogleAuthProperties properties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(properties.clientId()))
                .build();
    }

    /**
     * Verifica un ID token de Google y extrae los datos básicos del usuario.
     *
     * @param idTokenString ID token recibido del cliente (app web/móvil)
     * @return email, nombre y si el email está verificado por Google
     * @throws ResponseStatusException 401 si el token es inválido, ha
     *         expirado, o no fue emitido para nuestro Client ID
     */
    public GoogleUserInfo verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google inválido");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String name = Optional.ofNullable((String) payload.get("name")).orElse("Usuario");
            return new GoogleUserInfo(payload.getEmail(), name, payload.getEmailVerified());
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo verificar el token de Google", e);
        }
    }

    /**
     * Datos mínimos extraídos de un ID token de Google ya verificado.
     *
     * @param email          email de la cuenta de Google
     * @param name           nombre visible en la cuenta de Google
     * @param emailVerified  si Google confirma que el email es verificado
     */
    public record GoogleUserInfo(String email, String name, boolean emailVerified) {}
}