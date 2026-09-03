package com.acruzdb.misfinanzas.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad HTTP.
 * <p>
 * ESTADO TEMPORAL (Paso 2 de la guía): mientras no exista autenticación
 * real (JWT / OAuth2 con Google / OTP, ver Paso 3), permitimos todas las
 * peticiones a {@code /api/**} sin autenticar, para poder desarrollar y
 * probar el CRUD de movimientos con curl/Postman.
 * <p>
 * Esta clase se sustituirá por completo en el Paso 3: se añadirá un
 * filtro JWT, se protegerán todos los endpoints de {@code /api/**} y
 * solo quedarán públicos los de login (p.ej. {@code /api/auth/**}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // CSRF no aplica a una API stateless sin cookies de sesión
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // TEMPORAL — se restringe en el Paso 3
                );
        return http.build();
    }
}