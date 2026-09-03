package com.acruzdb.misfinanzas.categories.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.auth.infrastructure.JwtService;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.categories.application.CategoryService;
import com.acruzdb.misfinanzas.categories.dto.CategoryResponse;
import com.acruzdb.misfinanzas.categories.dto.CreateCategoryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test de la capa web (slice) para {@link CategoryController}.
 * <p>
 * Igual que en {@code TransactionControllerTest}: filtros de seguridad
 * desactivados, autenticación simulada a mano en el
 * {@link SecurityContextHolder}, y {@link JwtService} mockeado para que
 * {@code JwtAuthFilter} pueda construirse dentro del slice aunque no
 * llegue a ejecutarse.
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    private UUID authenticatedUserId;

    @BeforeEach
    void setUpAuthentication() {
        authenticatedUserId = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(authenticatedUserId), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/categories devuelve 201 con la categoría creada")
    void create_devuelve201() throws Exception {
        User user = new User("alex@test.com", null, "Alex");
        when(userRepository.findById(authenticatedUserId)).thenReturn(Optional.of(user));

        CategoryResponse fakeResponse = new CategoryResponse(
                UUID.randomUUID(), "Suscripciones", "expense", "#6B7280", null, false
        );
        when(categoryService.create(eq(user), any())).thenReturn(fakeResponse);

        CreateCategoryRequest request = new CreateCategoryRequest("Suscripciones", "expense", null, null, null);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Suscripciones"))
                .andExpect(jsonPath("$.kind").value("expense"));
    }

    @Test
    @DisplayName("POST /api/categories devuelve 400 si el tipo no es income ni expense")
    void create_devuelve400SiTipoInvalido() throws Exception {
        String jsonInvalido = """
                {"name":"Rara","kind":"factura"}
                """;

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/categories devuelve 404 si el usuario autenticado ya no existe en BD")
    void create_devuelve404SiUsuarioNoExiste() throws Exception {
        when(userRepository.findById(authenticatedUserId)).thenReturn(Optional.empty());

        CreateCategoryRequest request = new CreateCategoryRequest("Ocio", "expense", null, null, null);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/categories devuelve 200 con las categorías visibles para el usuario")
    void list_devuelve200ConLista() throws Exception {
        CategoryResponse response = new CategoryResponse(
                UUID.randomUUID(), "Comida", "expense", "#EF4444", null, true
        );
        when(categoryService.listForUser(authenticatedUserId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Comida"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} devuelve 204 si se borra correctamente")
    void delete_devuelve204() throws Exception {
        UUID categoryId = UUID.randomUUID();
        doNothing().when(categoryService).delete(categoryId, authenticatedUserId);

        mockMvc.perform(delete("/api/categories/{id}", categoryId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} devuelve 409 si es una categoría de sistema")
    void delete_devuelve409SiEsDeSistema() throws Exception {
        UUID categoryId = UUID.randomUUID();
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Las categorías de sistema no se pueden borrar"))
                .when(categoryService).delete(categoryId, authenticatedUserId);

        mockMvc.perform(delete("/api/categories/{id}", categoryId))
                .andExpect(status().isConflict());
    }
}