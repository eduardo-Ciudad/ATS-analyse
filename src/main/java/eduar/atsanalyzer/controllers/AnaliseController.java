package eduar.atsanalyzer.controllers;

import eduar.atsanalyzer.dtos.request.AnaliseRequest;
import eduar.atsanalyzer.dtos.response.AnaliseResponse;
import eduar.atsanalyzer.services.AnaliseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analise")
public class AnaliseController {

    private final AnaliseService analiseService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseResponse> analisar(
            @RequestPart("curriculo") MultipartFile curriculo,
            @RequestPart("vaga") @Valid AnaliseRequest request) {

        return ResponseEntity.ok(analiseService.analisar(curriculo, request));
    }
}
