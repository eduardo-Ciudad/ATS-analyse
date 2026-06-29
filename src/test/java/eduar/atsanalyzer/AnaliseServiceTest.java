package eduar.atsanalyzer;


import com.fasterxml.jackson.databind.ObjectMapper;
import eduar.atsanalyzer.domain.CategoriaAnalise;
import eduar.atsanalyzer.domain.StatusAderencia;
import eduar.atsanalyzer.dtos.response.AnaliseResponse;
import eduar.atsanalyzer.dtos.response.CategoriaResponse;
import eduar.atsanalyzer.exceptions.AnaliseException;
import eduar.atsanalyzer.infra.ia.AnthropicClient;
import eduar.atsanalyzer.infra.ia.PromptBuilder;
import eduar.atsanalyzer.services.AnaliseService;
import eduar.atsanalyzer.services.PdfExtractorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AnaliseServiceTest {

    @Mock
    private PdfExtractorService pdfExtractorService;

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private PromptBuilder promptBuilder;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private AnaliseService analiseService;

    private static final String JSON_RESPOSTA_VALIDA = """
            {
                "scoreGeral": 82,
                "categorias": [
                    {"chave": "keywords", "score": 90, "feedback": "Boa cobertura de palavras-chave"},
                    {"chave": "cronologia", "score": 75, "feedback": "Cronologia adequada"},
                    {"chave": "impacto", "score": 60, "feedback": "Faltam métricas quantificáveis"},
                    {"chave": "formatacao", "score": 85, "feedback": "Formatação limpa"}
                ],
                "sugestoes": ["Adicionar métricas de impacto", "Incluir certificações"]
            }
            """;

    // === HAPPY PATH — FLUXO COMPLETO ===

    @Test
    @DisplayName("fluxo completo: extrai → builda → envia → parseia")
    void deveRetornarAnalise_quandoFluxoCompleto() {
        MockMultipartFile file = criarMockPdf();

        when(pdfExtractorService.extrairTexto(any())).thenReturn("texto do currículo");
        when(promptBuilder.build(anyString(), anyString())).thenReturn("prompt montado");
        when(anthropicClient.enviar(anyString())).thenReturn(JSON_RESPOSTA_VALIDA);

        AnaliseResponse response = analiseService.analisar(file, "vaga de dev java");

        // verifica orquestração — cada dependência chamada uma vez
        verify(pdfExtractorService).extrairTexto(file);
        verify(promptBuilder).build("texto do currículo", "vaga de dev java");
        verify(anthropicClient).enviar("prompt montado");

        // verifica parsing do score geral
        assertEquals(82, response.scoreGeral());
        assertEquals(StatusAderencia.ALTA, response.statusGeral());
    }

    // === PARSING DE CATEGORIAS ===

    @Test
    @DisplayName("parseia 4 categorias com scores e status corretos")
    void deveMapearCategoriasCorretamente() {
        when(pdfExtractorService.extrairTexto(any())).thenReturn("texto");
        when(promptBuilder.build(anyString(), anyString())).thenReturn("prompt");
        when(anthropicClient.enviar(anyString())).thenReturn(JSON_RESPOSTA_VALIDA);

        AnaliseResponse response = analiseService.analisar(criarMockPdf(), "vaga");

        assertEquals(4, response.categorias().size());

        CategoriaResponse keywords = response.categorias().get(0);
        assertAll(
                () -> assertEquals(CategoriaAnalise.KEYWORDS, keywords.categoria()),
                () -> assertEquals("Palavras-Chave", keywords.descricao()),
                () -> assertEquals(90, keywords.score()),
                () -> assertEquals(StatusAderencia.ALTA, keywords.status()),
                () -> assertEquals("Boa cobertura de palavras-chave", keywords.feedback())
        );

        // impacto com score 60 → MEDIA
        CategoriaResponse impacto = response.categorias().get(2);
        assertEquals(StatusAderencia.MEDIA, impacto.status());
    }

    @Test
    @DisplayName("parseia lista de sugestões")
    void deveMapearSugestoes() {
        when(pdfExtractorService.extrairTexto(any())).thenReturn("texto");
        when(promptBuilder.build(anyString(), anyString())).thenReturn("prompt");
        when(anthropicClient.enviar(anyString())).thenReturn(JSON_RESPOSTA_VALIDA);

        AnaliseResponse response = analiseService.analisar(criarMockPdf(), "vaga");

        assertEquals(2, response.sugestoes().size());
        assertEquals("Adicionar métricas de impacto", response.sugestoes().get(0));
    }

    // === CHAVE DESCONHECIDA — CONTINUE ===

    @Test
    @DisplayName("chave desconhecida no JSON → ignorada, não quebra")
    void deveIgnorarCategoriaDesconhecida() {
        String jsonComChaveExtra = """
                {
                    "scoreGeral": 70,
                    "categorias": [
                        {"chave": "keywords", "score": 80, "feedback": "ok"},
                        {"chave": "categoria_inventada", "score": 50, "feedback": "ignorar"},
                        {"chave": "formatacao", "score": 90, "feedback": "otimo"}
                    ],
                    "sugestoes": []
                }
                """;

        when(pdfExtractorService.extrairTexto(any())).thenReturn("texto");
        when(promptBuilder.build(anyString(), anyString())).thenReturn("prompt");
        when(anthropicClient.enviar(anyString())).thenReturn(jsonComChaveExtra);

        AnaliseResponse response = analiseService.analisar(criarMockPdf(), "vaga");

        // só 2 categorias — a inventada foi ignorada pelo continue
        assertEquals(2, response.categorias().size());
        assertEquals(CategoriaAnalise.KEYWORDS, response.categorias().get(0).categoria());
        assertEquals(CategoriaAnalise.FORMATACAO, response.categorias().get(1).categoria());
    }

    // === JSON INVÁLIDO ===

    @Test
    @DisplayName("JSON inválido da IA → AnaliseException")
    void deveLancarExcecao_quandoJsonInvalido() {
        when(pdfExtractorService.extrairTexto(any())).thenReturn("texto");
        when(promptBuilder.build(anyString(), anyString())).thenReturn("prompt");
        when(anthropicClient.enviar(anyString())).thenReturn("isso não é json");

        assertThrows(AnaliseException.class,
                () -> analiseService.analisar(criarMockPdf(), "vaga"));
    }

    private MockMultipartFile criarMockPdf() {
        return new MockMultipartFile(
                "curriculo", "curriculo.pdf", "application/pdf", "conteudo".getBytes()
        );
    }
}

