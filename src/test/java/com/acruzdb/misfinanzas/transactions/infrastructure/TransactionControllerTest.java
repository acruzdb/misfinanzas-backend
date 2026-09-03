package com.acruzdb.misfinanzas.transactions.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.transactions.application.TransactionService;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test de la capa web (slice) para {@link TransactionController}.
 * <p>
 * Levanta únicamente el contexto MVC relacionado con este controller
 * (rutas, serialización JSON, validación) usando dobles Mockito para
 * {@link TransactionService} y {@link UserRepository}. No hay base de
 * datos real ni el resto de la aplicación implicados.
 */
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/transactions devuelve 201 con el movimiento creado")
    void create_devuelve201() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User("alex@test.com", null, "Alex");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        TransactionResponse fakeResponse = new TransactionResponse(
                UUID.randomUUID(), "expense", new BigDecimal("45.90"), "EUR",
                "Mercadona", LocalDate.now(), null
        );
        when(transactionService.create(eq(user), any())).thenReturn(fakeResponse);

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("45.90"), LocalDate.now(), "Mercadona", null
        );

        mockMvc.perform(post("/api/transactions")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("expense"))
                .andExpect(jsonPath("$.amount").value(45.90))
                .andExpect(jsonPath("$.description").value("Mercadona"));
    }

    @Test
    @DisplayName("POST /api/transactions devuelve 400 si el tipo no es income ni expense")
    void create_devuelve400SiTipoInvalido() throws Exception {
        String jsonInvalido = """
                {"type":"factura","amount":45.90,"transactionDate":"2026-09-01"}
                """;

        mockMvc.perform(post("/api/transactions")
                        .param("userId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/transactions devuelve 404 si el usuario no existe")
    void create_devuelve404SiUsuarioNoExiste() throws Exception {
        UUID userIdInexistente = UUID.randomUUID();
        when(userRepository.findById(userIdInexistente)).thenReturn(Optional.empty());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("10.00"), LocalDate.now(), null, null
        );

        mockMvc.perform(post("/api/transactions")
                        .param("userId", userIdInexistente.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/transactions devuelve 200 con la lista de movimientos")
    void list_devuelve200ConLista() throws Exception {
        UUID userId = UUID.randomUUID();
        TransactionResponse response = new TransactionResponse(
                UUID.randomUUID(), "income", new BigDecimal("2450.00"), "EUR",
                "Nómina", LocalDate.now(), null
        );
        when(transactionService.listForUser(userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/transactions").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("income"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }
}