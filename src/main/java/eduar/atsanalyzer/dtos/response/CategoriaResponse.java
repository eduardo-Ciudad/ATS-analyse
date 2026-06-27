package eduar.atsanalyzer.dtos.response;

import eduar.atsanalyzer.domain.CategoriaAnalise;
import eduar.atsanalyzer.domain.StatusAderencia;

public record CategoriaResponse(
        CategoriaAnalise categoria,
        String descricao,
        int score,
        StatusAderencia status,
        String feedback
) {
}
