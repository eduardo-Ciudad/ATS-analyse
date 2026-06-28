package eduar.atsanalyzer.services;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eduar.atsanalyzer.domain.CategoriaAnalise;
import eduar.atsanalyzer.domain.StatusAderencia;
import eduar.atsanalyzer.dtos.request.AnaliseRequest;
import eduar.atsanalyzer.dtos.response.AnaliseResponse;
import eduar.atsanalyzer.dtos.response.CategoriaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnaliseService {

    private final PdfExtractorService pdfExtractorService;
    private final AnthropicClient anthropicClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    private static final Map<String, CategoriaAnalise> CATEGORIA_MAP =  Map.of(
            "keywords", CategoriaAnalise.KEYWORDS,
            "cronologia", CategoriaAnalise.CRONOLOGIA,
            "impacto", CategoriaAnalise.IMPACTO,
            "formatacao", CategoriaAnalise.FORMATACAO
    );

    public AnaliseResponse analisar(MultipartFile curriculo, AnaliseRequest analiseRequest){
        String textoCurriculo = pdfExtractorService.extrairTexto(curriculo);
        String prompt = promptBuilder.build(textoCurriculo, analiseRequest.descricaoVaga());
        String respostaJson = anthropicClient.enviar(prompt);
    }

    private AnaliseResponse parsearResposta(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            int scoreGeral = root.get("scoreGeral").asInt();
            StatusAderencia statusGeral = StatusAderencia.fromScore(scoreGeral);

            List<CategoriaResponse> categorias = new ArrayList<>();
            for (JsonNode node : root.get("categorias")) {
                String chave = node.get("chave").asText();
                CategoriaAnalise categoria = CATEGORIA_MAP.get(chave);
                int score = node.get("score").asInt();

                categorias.add(new CategoriaResponse(
                        categoria,
                        categoria.getDescricao(),
                        score,
                        StatusAderencia.fromScore(score),
                        node.get("feedback").asText()
                ));
            }

            List<String> sugestoes = new ArrayList<>();
            for (JsonNode node : root.get("sugestoes")) {
                sugestoes.add(node.asText());
            }

            return new AnaliseResponse(scoreGeral, statusGeral, categorias, sugestoes);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar resposta da IA", e);
        }
    }
}
