package eduar.atsanalyzer.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import eduar.atsanalyzer.domain.CategoriaAnalise;
import eduar.atsanalyzer.dtos.request.AnaliseRequest;
import eduar.atsanalyzer.dtos.response.AnaliseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
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
}
