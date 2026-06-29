package eduar.atsanalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import eduar.atsanalyzer.exceptions.AnaliseException;
import eduar.atsanalyzer.infra.ia.AnthropicClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnthropicClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private AnthropicClient anthropicClient;


    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(anthropicClient, "apiKey", "test-key");
        ReflectionTestUtils.setField(anthropicClient, "model", "claude-haiku-4-5-20241022");
    }

    @Test
    @DisplayName("resposta válida → extrai texto do content[0]")
    void deveRetornarTexto_quandoRespostaValida() {
        String respostaApi = """
                {
                    "content": [
                        {"type": "text", "text": "{\\"scoreGeral\\": 85}"}
                    ]
                }
                """;

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respostaApi, HttpStatus.OK));

        String resultado = anthropicClient.enviar("prompt qualquer");

        assertEquals("{\"scoreGeral\": 85}", resultado);
    }

    @Test
    @DisplayName("RestTemplate lança exceção → AnaliseException")
    void deveLancarAnaliseException_quandoRestTemplateFalha() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThrows(AnaliseException.class, () -> anthropicClient.enviar("prompt"));
    }

    @Test
    @DisplayName("resposta com body null → AnaliseException")
    void deveLancarAnaliseException_quandoBodyNull() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(AnaliseException.class, () -> anthropicClient.enviar("prompt"));
    }
}
