package eduar.atsanalyzer;

import eduar.atsanalyzer.infra.ia.PromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder promptBuilder= new PromptBuilder();

    @Test
    void deveConterTextoDoCurriculo() {
        String resultado = promptBuilder.build("Experiencia em Java e Spring Boot", "Vaga backend");
        System.out.println("=== OUTPUT ===");
        System.out.println(resultado);
        assertTrue(resultado.contains("Experiencia em Java e Spring Boot"));
    }
    @Test
    @DisplayName("prompt contém a descrição da vaga")
    void deveConterDescricaoDaVaga() {
        String resultado = promptBuilder.build("Currículo qualquer", "Desenvolvedor Java Pleno");

        assertTrue(resultado.contains("Desenvolvedor Java Pleno"));
    }

    @Test
    @DisplayName("prompt contém as seções de separação")
    void deveConterSecoesDeSeparacao() {
        String resultado = promptBuilder.build("texto", "vaga");

        assertAll(
                () -> assertTrue(resultado.contains("=== CURRÍCULO ===")),
                () -> assertTrue(resultado.contains("=== DESCRIÇÃO DA VAGA ==="))
        );
    }

    @Test
    @DisplayName("prompt contém as 4 categorias de avaliação")
    void deveConterCategoriasDeAvaliacao() {
        String resultado = promptBuilder.build("texto", "vaga");

        assertAll(
                () -> assertTrue(resultado.contains("keywords")),
                () -> assertTrue(resultado.contains("cronologia")),
                () -> assertTrue(resultado.contains("impacto")),
                () -> assertTrue(resultado.contains("formatacao"))
        );
    }
}
