package com.acruzdb.misfinanzas.categories.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.dto.CategoryResponse;
import com.acruzdb.misfinanzas.categories.dto.CreateCategoryRequest;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Lógica de negocio del módulo de categorías.
 * <p>
 * Igual que en {@code TransactionService}, las operaciones de lectura y
 * borrado sobre una categoría concreta comprueban que pertenece al
 * usuario solicitante antes de actuar, devolviendo 404 (no 403) si no
 * es así — por los mismos motivos de seguridad que ya comentamos allí.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private final HouseholdMemberRepository householdMemberRepository;

    public CategoryService(CategoryRepository categoryRepository, HouseholdMemberRepository householdMemberRepository) {
        this.categoryRepository = categoryRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    /**
     * Crea una nueva categoría, personal o de household según venga
     * {@code householdId} en la petición.
     *
     * @param user    usuario que hace la petición
     * @param request datos validados de la categoría
     * @return la categoría creada
     * @throws org.springframework.web.server.ResponseStatusException 403
     *         si se indica un householdId del que el usuario no es miembro
     */
    @Transactional
    public CategoryResponse create(User user, CreateCategoryRequest request) {
        Category category;
        if (request.householdId() != null) {
            validateHouseholdMembership(request.householdId(), user.getId());
            category = new Category(request.householdId(), request.name(), request.kind());
        } else {
            category = new Category(user, request.name(), request.kind());
        }

        if (request.colorHex() != null) {
            category.setColorHex(request.colorHex());
        }
        category.setIcon(request.icon());

        Category saved = categoryRepository.save(category);
        return CategoryResponse.from(saved);
    }

    private void validateHouseholdMembership(UUID householdId, UUID userId) {
        boolean isMember = householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId).isPresent();
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a ese household");
        }
    }

    /**
     * Lista las categorías visibles para el usuario: las suyas propias
     * más las de sistema.
     *
     * @param userId id del usuario
     * @return lista de categorías, ordenada por nombre
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listForUser(UUID userId) {
        return categoryRepository.findVisibleForUser(userId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Elimina una categoría personal del usuario.
     *
     * @param id           id de la categoría a borrar
     * @param requesterId  id del usuario que hace la petición
     * @throws ResponseStatusException 404 si no existe o no pertenece al
     *         solicitante; 409 si es una categoría de sistema (no se
     *         pueden borrar, son compartidas por todos los usuarios)
     */
    @Transactional
    public void delete(UUID id, UUID requesterId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));

        if (category.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las categorías de sistema no se pueden borrar");
        }
        if (!category.belongsTo(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada");
        }
        categoryRepository.delete(category);
    }
}