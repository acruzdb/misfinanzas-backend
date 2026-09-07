package com.acruzdb.misfinanzas.statements.application;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.statements.TestExcelFactory;
import com.acruzdb.misfinanzas.statements.domain.StatementImport;
import com.acruzdb.misfinanzas.statements.dto.ColumnMapping;
import com.acruzdb.misfinanzas.statements.dto.StatementImportResponse;
import com.acruzdb.misfinanzas.statements.infrastructure.StatementImportRepository;
import com.acruzdb.misfinanzas.transactions.domain.Transaction;
import com.acruzdb.misfinanzas.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de {@link StatementImportService}.
 * <p>
 * {@link TransactionRepository} y {@link StatementImportRepository} se
 * mockean; los ficheros Excel se generan reales en memoria con
 * {@link TestExcelFactory}.
 */
@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private StatementImportRepository statementImportRepository;

    private StatementImportService service;
    private User testUser;
    private final ColumnMapping mapping = new ColumnMapping("Fecha", "Concepto", "Importe", null);

    @BeforeEach
    void setUp() throws Exception {
        service = new StatementImportService(transactionRepository, statementImportRepository);
        testUser = new User("alex@test.com", null, "Alex");
        setId(testUser, UUID.randomUUID());

        // lenient(): este stub no se llega a usar en el test de fichero
        // inválido (importFile_lanza400SiNoEsExcelValido), porque ahí la
        // excepción se lanza antes de guardar el StatementImport. No es
        // un descuido -- le decimos a Mockito explícitamente que ese
        // caso es válido, en vez de que lo marque como stubbing sin usar.
        lenient().when(statementImportRepository.save(any(StatementImport.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("importFile() crea un movimiento por cada fila válida, con el tipo correcto según el signo")
    void importFile_creaMovimientosConTipoSegunSigno() throws IOException {
        MockMultipartFile file = TestExcelFactory.buildExcel(
                new String[]{"Fecha", "Concepto", "Importe"},
                new Object[][]{
                        {"01/09/2026", "Mercadona", "-64.20"},
                        {"02/09/2026", "Nomina", "2450.00"},
                }
        );

        StatementImportResponse response = service.importFile(file, mapping, testUser, null);

        assertThat(response.rowsTotal()).isEqualTo(2);
        assertThat(response.rowsImported()).isEqualTo(2);
        assertThat(response.rowsFailed()).isZero();

        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("importFile() interpreta correctamente importes con separador decimal español (coma)")
    void importFile_parseaImporteConComaDecimal() throws IOException {
        MockMultipartFile file = TestExcelFactory.buildExcel(
                new String[]{"Fecha", "Concepto", "Importe"},
                new Object[][]{{"01/09/2026", "Compra grande", "-1.234,56"}}
        );

        service.importFile(file, mapping, testUser, null);

        verify(transactionRepository).save(argThat(tx ->
                tx.getAmount().compareTo(new java.math.BigDecimal("1234.56")) == 0 && tx.getType().equals("expense")
        ));
    }

    @Test
    @DisplayName("importFile() no aborta toda la importación por una fila con fecha inválida, la cuenta como fallida")
    void importFile_toleraFilaConFechaInvalida() throws IOException {
        MockMultipartFile file = TestExcelFactory.buildExcel(
                new String[]{"Fecha", "Concepto", "Importe"},
                new Object[][]{
                        {"01/09/2026", "Movimiento bueno 1", "-10.00"},
                        {"fecha-rara", "Movimiento malo", "-20.00"},
                        {"03/09/2026", "Movimiento bueno 2", "-30.00"},
                }
        );

        StatementImportResponse response = service.importFile(file, mapping, testUser, null);

        assertThat(response.rowsTotal()).isEqualTo(3);
        assertThat(response.rowsImported()).isEqualTo(2);
        assertThat(response.rowsFailed()).isEqualTo(1);
        assertThat(response.errorSummary()).contains("fecha no reconocida");

        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("importFile() ignora las filas completamente en blanco, sin contarlas como fallidas")
    void importFile_ignoraFilasEnBlanco() throws IOException {
        MockMultipartFile file = TestExcelFactory.buildExcel(
                new String[]{"Fecha", "Concepto", "Importe"},
                new Object[][]{
                        {"01/09/2026", "Movimiento", "-10.00"},
                        {"", "", ""},
                }
        );

        StatementImportResponse response = service.importFile(file, mapping, testUser, null);

        assertThat(response.rowsTotal()).isEqualTo(1);
        assertThat(response.rowsImported()).isEqualTo(1);
    }

    @Test
    @DisplayName("importFile() lanza 400 si el fichero no es un Excel válido")
    void importFile_lanza400SiNoEsExcelValido() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "no-es-excel.xlsx", "application/octet-stream", "esto no es un excel".getBytes()
        );

        assertThatThrownBy(() -> service.importFile(file, mapping, testUser, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se pudo leer");
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}