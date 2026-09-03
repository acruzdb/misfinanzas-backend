package com.acruzdb.misfinanzas.categories.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.categories.application.CategoryService;
import com.acruzdb.misfinanzas.categories.dto.CategoryResponse;
import com.acruzdb.misfinanzas.categories.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * API REST del módulo de categorías (etiquetas de ingresos/gastos).
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final UserRepository userRepository;

    public CategoryController(CategoryService categoryService, UserRepository userRepository) {
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    /** Crea una nueva categoría personal para el usuario autenticado. */
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateCategoryRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        CategoryResponse response = categoryService.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lista las categorías visibles para el usuario autenticado (propias + de sistema). */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(categoryService.listForUser(principal.id()));
    }

    /** Elimina una categoría personal del usuario autenticado. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        categoryService.delete(id, principal.id());
        return ResponseEntity.noContent().build();
    }
}