package eduar.atsanalyzer;

import eduar.atsanalyzer.domain.StatusAderencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class StatusAderenciaTest {

    @Test
    @DisplayName("score 75 (fronteira)  -> ALTA")
    void deveRetornarAlta_quandoScoreMaximo() {
        assertEquals(StatusAderencia.ALTA, StatusAderencia.fromScore(75));
    }

    @Test
    @DisplayName("score 74 (fronteira) → MEDIA")
    void deveRetornarMedia_quandoScore74() {
        assertEquals(StatusAderencia.MEDIA, StatusAderencia.fromScore(74));
    }

    @Test
    @DisplayName("score 50 (fronteira) → MEDIA")
    void deveRetornarMedia_quandoScoreExatamente50() {
        assertEquals(StatusAderencia.MEDIA, StatusAderencia.fromScore(50));
    }

    // === FRONTEIRA BAIXA (< 50) ===

    @Test
    @DisplayName("score 49 (fronteira) → BAIXA")
    void deveRetornarBaixa_quandoScore49() {
        assertEquals(StatusAderencia.BAIXA, StatusAderencia.fromScore(49));
    }

    @Test
    @DisplayName("score 0 → BAIXA")
    void deveRetornarBaixa_quandoScoreZero() {
        assertEquals(StatusAderencia.BAIXA, StatusAderencia.fromScore(0));
    }

}
