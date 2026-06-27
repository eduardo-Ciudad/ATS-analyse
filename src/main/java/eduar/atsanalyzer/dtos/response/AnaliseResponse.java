package eduar.atsanalyzer.dtos.response;

import eduar.atsanalyzer.domain.StatusAderencia;

import java.util.List;

public record AnaliseResponse(
        int scoreGeral,
        StatusAderencia statusGeral,
        List<CategoriaResponse> categorias,
        List<String> sugestoes
) {
}
