package eduar.atsanalyzer.controllers;

import eduar.atsanalyzer.dtos.request.AnaliseRequest;
import eduar.atsanalyzer.dtos.response.AnaliseResponse;
import eduar.atsanalyzer.services.AnaliseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analise")
public class AnaliseController {

    private final AnaliseService analiseService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseResponse> analisar(
            @RequestPart("curriculo") MultipartFile curriculo,
            @RequestParam("descricaoVaga") @NotBlank(message = "A descrição da vaga é obrigatória") String descricaoVaga) {

        return ResponseEntity.ok(analiseService.analisar(curriculo, descricaoVaga));
    }
}
