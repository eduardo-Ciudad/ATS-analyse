# ATSReady

API REST que analisa currículos contra descrições de vagas usando métricas de sistemas ATS (Applicant Tracking System). O usuário envia o PDF do currículo e a descrição da vaga; a API extrai o texto, delega a análise semântica ao Claude (Anthropic) e retorna um score geral, scores por categoria e sugestões de melhoria.

## O problema

75% dos currículos são rejeitados por sistemas ATS antes de chegarem a um recrutador humano. Candidatos raramente sabem o motivo. O ATSReady torna esse processo transparente, decompondo a avaliação em quatro dimensões objetivas com feedback acionável.

## Stack

- **Backend:** Java 17, Spring Boot 3, Maven
- **Extração de PDF:** Apache PDFBox 2.0.32
- **IA:** Anthropic Claude API (claude-sonnet-4-6)
- **Documentação:** SpringDoc OpenAPI (Swagger)
- **Rate Limiting:** Bucket4j
- **Frontend:** HTML, CSS, JavaScript (glassmorphism design)

## Arquitetura

Layered architecture, stateless — sem banco de dados. Cada requisição é autocontida e não persiste dados.

```
eduar.atsanalyzer
├── controllers         # Camada REST
│   └── AnaliseController
├── services            # Orquestração e extração
│   ├── AnaliseService
│   └── PdfExtractorService
├── domain              # Enums de domínio
│   ├── StatusAderencia
│   └── CategoriaAnalise
├── dtos
│   ├── request
│   │   └── AnaliseRequest
│   └── response
│       ├── AnaliseResponse
│       └── CategoriaResponse
├── infra
│   ├── ia
│   │   ├── AnthropicClient
│   │   └── PromptBuilder
│   └── security
│       ├── CorsConfig
│       ├── RestTemplateConfig
│       └── RateLimitFilter
├── exceptions
│   ├── PdfProcessingException
│   ├── AnaliseException
│   └── GlobalExceptionHandler
└── SwaggerConfig
```

## Métricas ATS avaliadas

| Categoria | O que avalia |
|---|---|
| **Palavras-Chave** | Match de termos técnicos e habilidades da vaga no currículo |
| **Cronologia** | Ordem cronológica inversa, gaps de carreira, tempo de experiência |
| **Impacto** | Resultados quantificáveis — métricas financeiras, de tempo e escala |
| **Formatação** | Coluna única, sem tabelas/gráficos/imagens, seções parseáveis por ATS |

## Faixas de score

| Score | Status | Significado |
|---|---|---|
| 75–100 | `ALTA` | Alta aderência — currículo no topo da fila |
| 50–74 | `MEDIA` | Média aderência — fila de revisão, sem prioridade |
| 0–49 | `BAIXA` | Baixa aderência — risco de descarte automático |

## Endpoint

```
POST /api/analise
Content-Type: multipart/form-data
```

| Parâmetro | Tipo | Descrição |
|---|---|---|
| `curriculo` | `MultipartFile` (PDF) | Arquivo do currículo |
| `descricaoVaga` | `String` | Descrição completa da vaga |

### Exemplo de resposta

```json
{
  "scoreGeral": 72,
  "statusGeral": "MEDIA",
  "categorias": [
    {
      "categoria": "KEYWORDS",
      "descricao": "Palavras-Chave",
      "score": 80,
      "status": "ALTA",
      "feedback": "Bom match de termos como 'Spring Boot' e 'REST'. Faltam 'Docker' e 'CI/CD'."
    },
    {
      "categoria": "CRONOLOGIA",
      "descricao": "Cronologia da Carreira",
      "score": 65,
      "status": "MEDIA",
      "feedback": "Ordem cronológica inversa correta, mas há um gap não explicado."
    },
    {
      "categoria": "IMPACTO",
      "descricao": "Métricas de Impacto",
      "score": 55,
      "status": "MEDIA",
      "feedback": "Poucos resultados quantificados. Adicione métricas concretas."
    },
    {
      "categoria": "FORMATACAO",
      "descricao": "Formatação",
      "score": 88,
      "status": "ALTA",
      "feedback": "Layout em coluna única, sem tabelas ou imagens."
    }
  ],
  "sugestoes": [
    "Inclua 'Docker' e 'CI/CD' na seção de habilidades.",
    "Quantifique pelo menos três realizações com métricas concretas."
  ]
}
```

### Códigos HTTP

| Código | Quando |
|---|---|
| `200` | Análise concluída |
| `400` | PDF inválido, vazio ou descrição da vaga ausente |
| `429` | Rate limit excedido (10 req/min por IP) |
| `500` | Falha na comunicação com a IA ou parsing da resposta |

## Como rodar

### Pré-requisitos

- Java 17+
- Maven
- API key da Anthropic ([console.anthropic.com](https://console.anthropic.com))

### Configuração

No `application.properties`:

```properties
anthropic.api.key=${ANTHROPIC_API_KEY}
anthropic.api.model=claude-sonnet-4-6
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB
```

### Execução

```bash
export ANTHROPIC_API_KEY=sua-chave-aqui
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.  
Swagger UI em `http://localhost:8080/swagger-ui.html`.

## Decisões técnicas

- **Stateless:** sem banco de dados — `(PDF, vaga) → análise` é uma transformação pura, sem necessidade de persistência
- **Records para DTOs:** imutabilidade nativa do Java 17, sem dependência de Lombok para contratos de dados
- **`StatusAderencia.fromScore` no enum:** Information Expert — a regra de classificação pertence ao conceito que ela classifica
- **`PdfExtractorService` separado:** SRP — isola I/O binário (PDFBox) da lógica de orquestração
- **`PromptBuilder` como componente:** prompt engineering é uma preocupação volátil e independente da orquestração
- **`RestTemplate` (não WebClient):** fluxo síncrono por natureza, sem benefício em reatividade
- **`CorsFilter` como Bean:** atua no nível de servlet, mais previsível para preflight e compatível com Spring Security

## Autor

**Eduardo Ciudad** — Backend Java Developer

- GitHub: [github.com/eduardo-Ciudad](https://github.com/eduardo-Ciudad)
- Portfolio: [eduardo-ciudad-portfolio.vercel.app](https://eduardo-ciudad-portfolio.vercel.app)