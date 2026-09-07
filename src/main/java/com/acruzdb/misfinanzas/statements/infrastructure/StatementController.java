package com.acruzdb.misfinanzas.statements.infrastructure;

import com.acruzdb.misfinanzas.auth.domain.User;
import com.acruzdb.misfinanzas.auth.infrastructure.AuthenticatedUser;
import com.acruzdb.misfinanzas.auth.infrastructure.UserRepository;
import com.acruzdb.misfinanzas.statements.application.ExcelParsingService;
import com.acruzdb.misfinanzas.statements.application.StatementImportService;
import com.acruzdb.misfinanzas.statements.dto.ConfirmImportRequest;
import com.acruzdb.misfinanzas.statements.dto.StatementImportResponse;
import com.acruzdb.misfinanzas.statements.dto.StatementPreviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * API REST del módulo de extractos (import/export de movimientos vía fichero).
 */
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final ExcelParsingService excelParsingService;

    private final UserRepository userRepository;

    private final StatementImportService statementImportService;

    public StatementController(ExcelParsingService excelParsingService, UserRepository userRepository, StatementImportService statementImportService) {
        this.excelParsingService = excelParsingService;
        this.userRepository = userRepository;
        this.statementImportService = statementImportService;
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
}