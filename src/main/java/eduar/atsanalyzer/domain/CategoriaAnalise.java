package eduar.atsanalyzer.domain;

public enum CategoriaAnalise {
    KEYWORDS("Palavras-Chave"),
    CRONOLOGIA("Cronologia da Carreira"),
    IMPACTO("Métricas de Impacto"),
    FORMATACAO("Formatação");

    private final String descricao;

    CategoriaAnalise(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}