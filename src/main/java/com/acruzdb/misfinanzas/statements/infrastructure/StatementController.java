package com.acruzdb.misfinanzas.statements.infrastructure;

import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.statements.application.ExcelParsingService;
import com.acruzdb.misfinanzas.statements.application.StatementImportService;
import com.acruzdb.misfinanzas.statements.dto.ConfirmImportRequest;
import com.acruzdb.misfinanzas.statements.dto.StatementImportResponse;
import com.acruzdb.misfinanzas.statements.dto.StatementImportSummary;
import com.acruzdb.misfinanzas.statements.dto.StatementPreviewResponse;
import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * API REST del módulo de extractos (import/export de movimientos vía fichero).
 */
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final ExcelParsingService excelParsingService;
    private final StatementImportService statementImportService;
    private final UserRepository userRepository;

    public StatementController(ExcelParsingService excelParsingService,
                               StatementImportService statementImportService,
                               UserRepository userRepository) {
        this.excelParsingService = excelParsingService;
        this.statementImportService = statementImportService;
        this.userRepository = userRepository;
    }

    /**
     * Analiza un fichero recién subido sin importar nada todavía: devuelve
     * cabeceras, una muestra de filas, y un mapeo de columnas sugerido.
     *
     * @param file fichero .xlsx a previsualizar
     * @return la vista previa, para que el usuario confirme o corrija el mapeo
     */
    @PostMapping("/preview")
    public ResponseEntity<StatementPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(excelParsingService.preview(file));
    }

    /**
     * Importa un fichero ya con el mapeo de columnas confirmado por el usuario.
     *
     * @param file      fichero .xlsx a importar
     * @param request   mapeo de columnas y household de destino (opcional), como parte JSON
     * @param principal usuario autenticado
     * @return resumen de la importación
     */
    @PostMapping("/import")
    public ResponseEntity<StatementImportResponse> confirmImport(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") ConfirmImportRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        StatementImportResponse response = statementImportService.importFile(file, request.mapping(), user, request.householdId());
        return ResponseEntity.ok(response);
    }

    /**
     * Historial de importaciones recientes del usuario autenticado.
     *
     * @param principal usuario autenticado
     * @return las últimas importaciones, más reciente primero
     */
    @GetMapping
    public ResponseEntity<List<StatementImportSummary>> listRecent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(statementImportService.listRecent(principal.id()));
    }
}