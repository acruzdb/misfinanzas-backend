package com.acruzdb.misfinanzas.auth.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de la entidad {@link User}.
 * <p>
 * Además de las operaciones CRUD heredadas de {@link JpaRepository}
 * (save, findById, findAll, deleteById...), expone los buscadores
 * necesarios para el login: por email (Google OAuth) y por número
 * de teléfono (verificación OTP).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca un usuario por su email, sin distinguir mayúsculas/minúsculas.
     * <p>
     * Aunque el email se normaliza a minúsculas al crear el usuario
     * (ver {@link User#User}), esta búsqueda usa {@code IgnoreCase} como
     * segunda capa de seguridad, por si el valor de entrada no viene
     * ya normalizado.
     *
     * @param email email del usuario, en cualquier combinación de mayúsculas/minúsculas
     * @return el usuario si existe, vacío en caso contrario
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Busca un usuario por su número de teléfono, usado en el flujo
     * de login por OTP.
     *
     * @param phoneNumber número en formato E.164 (p.ej. +34600000000)
     * @return el usuario si existe, vacío en caso contrario
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Comprueba si ya existe un usuario registrado con ese email,
     * sin distinguir mayúsculas/minúsculas.
     *
     * @param email email a comprobar
     * @return true si ya existe un usuario con ese email
     */
    boolean existsByEmailIgnoreCase(String email);
}