package com.acruzdb.misfinanzas.shared.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.auth.infrastructure.JwtService;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.shared.application.HouseholdService;
import com.acruzdb.misfinanzas.shared.dto.AddMemberRequest;
import com.acruzdb.misfinanzas.shared.dto.CreateHouseholdRequest;
import com.acruzdb.misfinanzas.shared.dto.HouseholdResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
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
 * Test de la capa web (slice) para {@link HouseholdController}.
 * <p>
 * Mismo patrón que {@code TransactionControllerTest} y
 * {@code CategoryControllerTest}: filtros de seguridad desactivados,
 * autenticación simulada, y {@link JwtService} mockeado para que
 * {@code JwtAuthFilter} se pueda construir dentro del slice.
 */
@WebMvcTest(HouseholdController.class)
@AutoConfigureMockMvc(addFilters = false)
class HouseholdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HouseholdService householdService;

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
    @DisplayName("POST /api/households devuelve 201 con el household creado")
    void create_devuelve201() throws Exception {
        User user = new User("alex@test.com", null, "Alex");
        when(userRepository.findById(authenticatedUserId)).thenReturn(Optional.of(user));

        HouseholdResponse fakeResponse = new HouseholdResponse(
                UUID.randomUUID(), "Casa Alex & Sam",
                List.of(new HouseholdResponse.MemberSummary(authenticatedUserId, "Alex", "owner"))
        );
        when(householdService.create(eq(user), eq("Casa Alex & Sam"))).thenReturn(fakeResponse);

        CreateHouseholdRequest request = new CreateHouseholdRequest("Casa Alex & Sam");

        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Casa Alex & Sam"))
                .andExpect(jsonPath("$.members[0].role").value("owner"));
    }

    @Test
    @DisplayName("POST /api/households devuelve 400 si el nombre está vacío")
    void create_devuelve400SiNombreVacio() throws Exception {
        String jsonInvalido = """
                {"name":""}
                """;

        mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/households devuelve 200 con los households del usuario")
    void list_devuelve200ConLista() throws Exception {
        HouseholdResponse response = new HouseholdResponse(UUID.randomUUID(), "Casa", List.of());
        when(householdService.listForUser(authenticatedUserId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/households"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Casa"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/households/{id} devuelve 200 con el detalle del household")
    void getById_devuelve200() throws Exception {
        UUID householdId = UUID.randomUUID();
        HouseholdResponse response = new HouseholdResponse(householdId, "Casa", List.of());
        when(householdService.getById(householdId, authenticatedUserId)).thenReturn(response);

        mockMvc.perform(get("/api/households/{id}", householdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(householdId.toString()));
    }

    @Test
    @DisplayName("GET /api/households/{id} devuelve 404 si el usuario no es miembro")
    void getById_devuelve404SiNoEsMiembro() throws Exception {
        UUID householdId = UUID.randomUUID();
        when(householdService.getById(householdId, authenticatedUserId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Household no encontrado"));

        mockMvc.perform(get("/api/households/{id}", householdId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/households/{id}/members devuelve 200 con el household actualizado")
    void addMember_devuelve200() throws Exception {
        UUID householdId = UUID.randomUUID();
        HouseholdResponse response = new HouseholdResponse(
                householdId, "Casa",
                List.of(new HouseholdResponse.MemberSummary(UUID.randomUUID(), "Sam", "member"))
        );
        when(householdService.addMember(householdId, authenticatedUserId, "sam@test.com")).thenReturn(response);

        AddMemberRequest request = new AddMemberRequest("sam@test.com");

        mockMvc.perform(post("/api/households/{id}/members", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].role").value("member"));
    }

    @Test
    @DisplayName("POST /api/households/{id}/members devuelve 403 si quien lo pide no es owner")
    void addMember_devuelve403SiNoEsOwner() throws Exception {
        UUID householdId = UUID.randomUUID();
        when(householdService.addMember(householdId, authenticatedUserId, "sam@test.com"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el propietario puede añadir miembros"));

        AddMemberRequest request = new AddMemberRequest("sam@test.com");

        mockMvc.perform(post("/api/households/{id}/members", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/households/{id}/members/me devuelve 204 al abandonar el household")
    void leave_devuelve204() throws Exception {
        UUID householdId = UUID.randomUUID();
        doNothing().when(householdService).leave(householdId, authenticatedUserId);

        mockMvc.perform(delete("/api/households/{id}/members/me", householdId))
                .andExpect(status().isNoContent());
    }
}