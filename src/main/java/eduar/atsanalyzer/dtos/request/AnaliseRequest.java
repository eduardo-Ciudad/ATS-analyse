package eduar.atsanalyzer.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record AnaliseRequest(
        @NotBlank(message = "A descrição da vaga é obrigatória")
        String descricaoVaga
) {
}
