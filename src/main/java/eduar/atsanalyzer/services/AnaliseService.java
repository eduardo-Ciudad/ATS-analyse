package eduar.atsanalyzer.services;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eduar.atsanalyzer.domain.CategoriaAnalise;
import eduar.atsanalyzer.domain.StatusAderencia;
import eduar.atsanalyzer.dtos.request.AnaliseRequest;
import eduar.atsanalyzer.dtos.response.AnaliseResponse;
import eduar.atsanalyzer.dtos.response.CategoriaResponse;
import eduar.atsanalyzer.exceptions.AnaliseException;
import eduar.atsanalyzer.infra.ia.AnthropicClient;
import eduar.atsanalyzer.infra.ia.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
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

    public AnaliseResponse analisar(MultipartFile curriculo, String descricaoVaga) {
        log.info("Iniciando análise - arquivo: {}, tamanho: {} bytes", curriculo.getOriginalFilename(), curriculo.getSize());
        String textoCurriculo = pdfExtractorService.extrairTexto(curriculo);
        String prompt = promptBuilder.build(textoCurriculo, descricaoVaga);

        long inicio = System.currentTimeMillis();
        String respostaJson = anthropicClient.enviar(prompt);
        long latencia = System.currentTimeMillis();

        log.info("resposta da Anthropic recebida em {}ms", latencia);

        AnaliseResponse response = parsearResposta(respostaJson);
        log.info("Análise concluida - score: {}, status: {}", response.scoreGeral(), response.statusGeral());

        return response;
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

                if (categoria == null) continue;

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

        } catch (JsonProcessingException e) {
            throw new AnaliseException("Erro ao processar resposta da IA", e);

        }
    }
}
