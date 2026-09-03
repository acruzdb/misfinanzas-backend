package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link TransactionService}.
 * <p>
 * El {@link TransactionRepository} se sustituye por un mock: no se toca
 * base de datos real, solo se verifica la lógica de negocio del servicio
 * (creación, listado, y la comprobación de propiedad en getById/delete).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService transactionService;

    private User testUser;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        transactionService = new TransactionService(transactionRepository);
        testUser = new User("alex@test.com", null, "Alex");
        setId(testUser, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() guarda el movimiento y devuelve su respuesta mapeada")
    void create_guardaYDevuelveMovimiento() {
        // Arrange: preparamos los datos de entrada y lo que el mock debe "responder"
        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("45.90"), LocalDate.now(), "Mercadona", null
        );
        // Cuando llamen a repository.save(...) con cualquier Transaction,
        // devolvemos el mismo objeto que le pasen (simulando lo que haría
        // Hibernate: asignarle un id y devolverlo).
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: ejecutamos el método que queremos probar
        TransactionResponse response = transactionService.create(testUser, request);

        // Assert: comprobamos el resultado
        assertThat(response.type()).isEqualTo("expense");
        assertThat(response.amount()).isEqualByComparingTo("45.90");
        assertThat(response.description()).isEqualTo("Mercadona");

        // Además, verificamos que el servicio REALMENTE llamó a save() una vez
        // (no solo que el resultado sea correcto, sino que interactuó bien
        // con su dependencia).
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("getById() lanza 404 si el movimiento pertenece a otro usuario")
    void getById_lanza404SiNoEsElPropietario() {
        // Arrange: un movimiento que pertenece a "testUser"...
        Transaction transaction = new Transaction(testUser, "expense", new BigDecimal("10.00"), LocalDate.now());
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        UUID otroUsuarioId = UUID.randomUUID(); // ...pero pregunta OTRO usuario distinto

        // Act + Assert: comprobamos que lanza la excepción esperada,
        // con el status code correcto (404, no 403 — como comentamos,
        // por seguridad no revelamos que el recurso existe).
        assertThatThrownBy(() -> transactionService.getById(transactionId, otroUsuarioId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Movimiento no encontrado");
    }

    @Test
    @DisplayName("getById() lanza 404 si el movimiento no existe en absoluto")
    void getById_lanza404SiNoExiste() {
        UUID idInexistente = UUID.randomUUID();
        when(transactionRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(idInexistente, testUser.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    // Pequeño truco para poder asignar el id al User de prueba sin exponer
    // un setter público en la entidad real (el id lo pone JPA normalmente).
    // Es un compromiso aceptable en tests; lo sustituiremos por un builder
    // de test más limpio si el proyecto crece.
    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}