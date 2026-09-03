package com.acruzdb.misfinanzas.transactions.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.categories.domain.Category;
import com.acruzdb.misfinanzas.categories.infrastructure.CategoryRepository;
import com.acruzdb.misfinanzas.shared.domain.HouseholdMember;
import com.acruzdb.misfinanzas.shared.infrastructure.HouseholdMemberRepository;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.dto.CreateTransactionRequest;
import com.acruzdb.misfinanzas.transactions.dto.TransactionResponse;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
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
 * {@link TransactionRepository}, {@link CategoryRepository} y
 * {@link HouseholdMemberRepository} se sustituyen por mocks: no se toca
 * base de datos real, solo se verifica la lógica de negocio del
 * servicio (creación, validación de categoría, validación de
 * pertenencia a household, listado, y la comprobación de propiedad en
 * getById/delete).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    private TransactionService transactionService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        transactionService = new TransactionService(transactionRepository, categoryRepository, householdMemberRepository);
        testUser = new User("alex@test.com", null, "Alex");
        setId(testUser, UUID.randomUUID());
    }

    @Test
    @DisplayName("create() guarda el movimiento cuando no se indica categoría ni household")
    void create_guardaYDevuelveMovimiento() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("45.90"), LocalDate.now(), "Mercadona", null, null
        );
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        TransactionResponse response = transactionService.create(testUser, request);

        // Assert
        assertThat(response.type()).isEqualTo("expense");
        assertThat(response.amount()).isEqualByComparingTo("45.90");
        assertThat(response.description()).isEqualTo("Mercadona");
        assertThat(response.householdId()).isNull();
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(categoryRepository, never()).findById(any()); // no debe consultar si categoryId es null
        verify(householdMemberRepository, never()).findByHouseholdIdAndUserId(any(), any()); // ni household si es null
    }

    @Test
    @DisplayName("create() acepta una categoría de sistema aunque no sea del usuario")
    void create_aceptaCategoriaDeSistema() throws Exception {
        // Arrange
        Category systemCategory = new Category((User) null, "Comida", "expense");
        UUID categoryId = UUID.randomUUID();
        markAsSystem(systemCategory);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(systemCategory));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("20.00"), LocalDate.now(), null, categoryId, null
        );

        // Act
        TransactionResponse response = transactionService.create(testUser, request);

        // Assert
        assertThat(response.categoryId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("create() lanza 400 si la categoría no existe")
    void create_lanza400SiCategoriaNoExiste() {
        // Arrange
        UUID categoriaInexistente = UUID.randomUUID();
        when(categoryRepository.findById(categoriaInexistente)).thenReturn(Optional.empty());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("10.00"), LocalDate.now(), null, categoriaInexistente, null
        );

        // Act + Assert
        assertThatThrownBy(() -> transactionService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no existe");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create() lanza 400 si la categoría es de otro usuario")
    void create_lanza400SiCategoriaEsDeOtroUsuario() throws Exception {
        // Arrange: el otro usuario necesita un id real asignado, igual que
        // testUser, para que belongsTo() compare dos UUID de verdad y no
        // un id null contra uno real.
        User otroUsuario = new User("otro@test.com", null, "Otro");
        setId(otroUsuario, UUID.randomUUID());
        Category categoriaAjena = new Category(otroUsuario, "Privada", "expense");
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(categoriaAjena));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("10.00"), LocalDate.now(), null, categoryId, null
        );

        // Act + Assert
        assertThatThrownBy(() -> transactionService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no es válida");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("create() crea un movimiento compartido si el usuario es miembro del household")
    void create_creaMovimientoCompartidoSiEsMiembro() {
        // Arrange
        UUID householdId = UUID.randomUUID();
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUser.getId()))
                .thenReturn(Optional.of(mock(HouseholdMember.class)));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("30.00"), LocalDate.now(), "Compra semanal", null, householdId
        );

        // Act
        TransactionResponse response = transactionService.create(testUser, request);

        // Assert
        assertThat(response.householdId()).isEqualTo(householdId);
    }

    @Test
    @DisplayName("create() lanza 403 si el usuario no pertenece al household indicado")
    void create_lanza403SiNoPerteneceAlHousehold() {
        // Arrange
        UUID householdId = UUID.randomUUID();
        when(householdMemberRepository.findByHouseholdIdAndUserId(householdId, testUser.getId()))
                .thenReturn(Optional.empty());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "expense", new BigDecimal("30.00"), LocalDate.now(), null, null, householdId
        );

        // Act + Assert
        assertThatThrownBy(() -> transactionService.create(testUser, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No perteneces");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById() lanza 404 si el movimiento pertenece a otro usuario")
    void getById_lanza404SiNoEsElPropietario() {
        // Arrange
        Transaction transaction = new Transaction(testUser, "expense", new BigDecimal("10.00"), LocalDate.now());
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        UUID otroUsuarioId = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> transactionService.getById(transactionId, otroUsuarioId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Movimiento no encontrado");
    }

    @Test
    @DisplayName("getById() lanza 404 si el movimiento no existe en absoluto")
    void getById_lanza404SiNoExiste() {
        // Arrange
        UUID idInexistente = UUID.randomUUID();
        when(transactionRepository.findById(idInexistente)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> transactionService.getById(idInexistente, testUser.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    // Pequeño truco para poder asignar el id a entidades de prueba sin
    // exponer un setter público (el id lo pone JPA normalmente).
    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void markAsSystem(Category category) throws Exception {
        Field field = Category.class.getDeclaredField("isSystem");
        field.setAccessible(true);
        field.set(category, true);
    }
}