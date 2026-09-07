package com.acruzdb.misfinanzas.statements.infrastructure;

import com.acruzdb.misfinanzas.statements.application.ExcelParsingService;
import com.acruzdb.misfinanzas.statements.dto.StatementPreviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API REST del módulo de extractos (import/export de movimientos vía fichero).
 */
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final ExcelParsingService excelParsingService;

    public StatementController(ExcelParsingService excelParsingService) {
        this.excelParsingService = excelParsingService;
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
}