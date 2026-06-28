package eduar.atsanalyzer.infra.ia;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    public String build(String textoCurriculo, String descricaoVaga) {
        return """
                Você é um analisador de currículos especialista em ATS (Applicant Tracking System).
                
                Analise o currículo abaixo em relação à descrição da vaga fornecida.
                
                Avalie nas seguintes categorias:
                - keywords: Match de palavras-chave e termos técnicos da vaga presentes no currículo
                - cronologia: Ordem cronológica inversa, gaps de carreira, tempo de experiência
                - impacto: Resultados quantificáveis com métricas financeiras, de tempo ou escala
                - formatacao: Estrutura de coluna única, sem tabelas/gráficos/imagens, seções claras
                
                Faixas de pontuação:
                - Abaixo de 50: Baixa aderência
                - De 50 a 74: Média aderência
                - 75 ou acima: Alta aderência
                
                Responda APENAS com o JSON abaixo, sem markdown, sem explicação:
                {
                    "scoreGeral": <0-100>,
                    "categorias": [
                        {
                            "chave": "<keywords|cronologia|impacto|formatacao>",
                            "score": <0-100>,
                            "feedback": "<feedback específico e acionável>"
                        }
                    ],
                    "sugestoes": ["<sugestão 1>", "<sugestão 2>"]
                }
                
                === CURRÍCULO ===
                %s
                
                === DESCRIÇÃO DA VAGA ===
                %s
                """.formatted(textoCurriculo, descricaoVaga);
    }
}

