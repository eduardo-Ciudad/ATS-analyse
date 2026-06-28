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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Análise", description = "Análise de currículo contra métricas ATS")

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analise")
public class AnaliseController {

    private final AnaliseService analiseService;



    @Operation(summary = "Analisar currículo", description = "Recebe um PDF de currículo e a descrição da vaga, retorna score e feedback por categoria ATS")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Análise concluída com sucesso"),
            @ApiResponse(responseCode = "400", description = "PDF inválido ou descrição da vaga ausente"),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido"),
            @ApiResponse(responseCode = "500", description = "Erro na comunicação com a IA")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseResponse> analisar(
            @RequestPart("curriculo") MultipartFile curriculo,
            @RequestParam("descricaoVaga") @NotBlank(message = "A descrição da vaga é obrigatória") String descricaoVaga) {

        return ResponseEntity.ok(analiseService.analisar(curriculo, descricaoVaga));
    }
}
