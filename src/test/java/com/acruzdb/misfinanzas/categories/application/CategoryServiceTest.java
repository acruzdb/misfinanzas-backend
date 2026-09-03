package com.acruzdb.misfinanzas.categories.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.dto.CategoryResponse;
import com.acruzdb.misfinanzas.categories.dto.CreateCategoryRequest;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link CategoryService}, con {@link CategoryRepository} mockeado.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    private CategoryService categoryService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        categoryService = new CategoryService(categoryRepository,householdMemberRepository);
        testUser = new User("alex@test.com", null, "Alex");
        setId(testUser, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() guarda la categoría con los valores por defecto si no se especifica color")
    void create_usaColorPorDefectoSiNoSeEspecifica() {
        CreateCategoryRequest request = new CreateCategoryRequest("Suscripciones", "expense", null, null, null);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.create(testUser, request);

        assertThat(response.name()).isEqualTo("Suscripciones");
        assertThat(response.colorHex()).isEqualTo("#6B7280"); // color por defecto de la entidad
    }

    @Test
    @DisplayName("create() crea una categoría de household si el usuario es miembro")
    void create_creaCategoriaDeHouseholdSiEsMiembro() {
        UUID householdId = UUID.randomUUID();
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUser.getId()))
                .thenReturn(Optional.of(mock(com.acruzdb.misfinanzas.shared.domain.HouseholdMember.class)));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCategoryRequest request = new CreateCategoryRequest("Alquiler", "expense", null, null, householdId);

        CategoryResponse response = categoryService.create(testUser, request);

        assertThat(response.name()).isEqualTo("Alquiler");
    }

    @Test
    @DisplayName("create() lanza 403 si el usuario no pertenece al household indicado")
    void create_lanza403SiNoPerteneceAlHousehold() {
        UUID householdId = UUID.randomUUID();
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUser.getId()))
                .thenReturn(Optional.empty());

        CreateCategoryRequest request = new CreateCategoryRequest("Alquiler", "expense", null, null, householdId);

        assertThatThrownBy(() -> categoryService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No perteneces");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete() lanza 409 si la categoría es de sistema")
    void delete_lanza409SiEsDeSistema() {
        Category systemCategory = new Category((User) null, "Comida", "expense");
        UUID categoryId = UUID.randomUUID();
        markAsSystem(systemCategory);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(systemCategory));

        assertThatThrownBy(() -> categoryService.delete(categoryId, testUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no se pueden borrar");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete() lanza 404 si la categoría pertenece a otro usuario")
    void delete_lanza404SiNoEsElPropietario() {
        Category category = new Category(testUser, "Gimnasio", "expense");
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        UUID otroUsuarioId = UUID.randomUUID();

        assertThatThrownBy(() -> categoryService.delete(categoryId, otroUsuarioId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("listForUser() delega en el repositorio y mapea correctamente")
    void listForUser_devuelveCategoriasMapeadas() {
        Category category = new Category(testUser, "Ocio", "expense");
        when(categoryRepository.findVisibleForUser(testUser.getId())).thenReturn(List.of(category));

        List<CategoryResponse> result = categoryService.listForUser(testUser.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Ocio");
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void markAsSystem(Category category) {
        try {
            Field field = Category.class.getDeclaredField("isSystem");
            field.setAccessible(true);
            field.set(category, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}