package eduar.atsanalyzer.domain;

public enum StatusAderencia {
    ALTA,
    MEDIA,
    BAIXA;

    public static StatusAderencia fromScore(int score) {
        if (score >= 75) return ALTA;
        if (score >= 50) return MEDIA;
        return BAIXA;
    }
}
