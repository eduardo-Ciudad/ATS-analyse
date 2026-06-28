# ATSReady — Documentação Técnica do Backend

> Documentação gerada a partir da análise do código-fonte real (`src/main/java/eduar/atsanalyzer`).
> Onde a estrutura difere da descrição original do projeto, este documento descreve **o que está efetivamente no código**.

---

## 1. Visão Geral

O **ATSReady** é uma API REST, construída em Java 17 com Spring Boot, que avalia o grau de aderência de um currículo (PDF) a uma descrição de vaga, simulando os critérios usados por sistemas de triagem automática de candidatos (ATS — *Applicant Tracking System*). O usuário envia o arquivo do currículo e o texto da vaga; a aplicação extrai o texto do PDF, monta um prompt estruturado, delega a análise semântica ao modelo Claude (Anthropic), interpreta a resposta JSON e devolve um resultado tipado contendo um score geral, scores por categoria e sugestões de melhoria.

O problema que resolve é prático: a maioria dos currículos é filtrada por software de ATS antes de chegar a um recrutador humano. Candidatos raramente sabem **por que** seus currículos são reprovados. O ATSReady torna esse processo transparente, decompondo a avaliação em quatro dimensões objetivas — palavras-chave, cronologia, métricas de impacto e formatação — cada uma com um score e um feedback acionável.

A aplicação é deliberadamente **stateless**: não há banco de dados, sessão ou persistência. Cada requisição é autocontida (PDF + texto da vaga) e produz uma resposta sem efeitos colaterais. A inteligência da análise não está em regras codificadas no backend, mas é terceirizada para o LLM; o backend atua como orquestrador — extrai texto, constrói o prompt, chama a API externa e mapeia o resultado para um contrato de resposta estável.

### Stack tecnológica

| Componente | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java | 17 |
| Framework | Spring Boot | 3.5.16 |
| Web | `spring-boot-starter-web` (Spring MVC + Tomcat embutido) | (gerenciado pelo parent) |
| Validação | `spring-boot-starter-validation` (Jakarta Bean Validation / Hibernate Validator) | (gerenciado) |
| Extração de PDF | Apache PDFBox | 2.0.32 |
| Cliente HTTP | `RestTemplate` (spring-web) | (gerenciado) |
| Serialização JSON | Jackson (`ObjectMapper`) | (gerenciado) |
| Boilerplate | Lombok | (gerenciado, `optional`) |
| LLM | Anthropic Claude — modelo `claude-sonnet-4-6` | API `2023-06-01` |
| Build | Maven (Spring Boot Maven Plugin) | — |
| Testes | `spring-boot-starter-test` (JUnit 5, Mockito) | (gerenciado) |

---

## 2. Arquitetura

### Tipo

Arquitetura **em camadas (layered architecture)**, com uma separação adicional de *infraestrutura* (pacote `infra`) isolando integrações externas (IA, HTTP, CORS). O fluxo de dependência é unidirecional, de cima para baixo: `controller → service → (infra / domain / dto)`. Não há inversão de controle manual — toda a injeção é feita via construtor pelo container do Spring.

### Estrutura de pacotes

```
eduar.atsanalyzer
├── AtsanalyzerApplication.java        # bootstrap Spring Boot
│
├── controllers
│   └── AnaliseController.java         # camada de apresentação (REST)
│
├── services
│   ├── AnaliseService.java            # orquestração + parsing da resposta
│   └── PdfExtractorService.java       # extração de texto do PDF
│
├── domain
│   ├── StatusAderencia.java           # enum (ALTA/MEDIA/BAIXA) + regra fromScore
│   └── CategoriaAnalise.java          # enum das 4 categorias + descrição
│
├── dtos
│   ├── request
│   │   └── AnaliseRequest.java        # record (atualmente não usado pelo controller)
│   └── response
│       ├── AnaliseResponse.java       # contrato de saída (raiz)
│       └── CategoriaResponse.java     # contrato de saída (por categoria)
│
├── infra
│   ├── ia
│   │   ├── AnthropicClient.java       # cliente HTTP da API Anthropic
│   │   └── PromptBuilder.java         # construção do prompt
│   └── security
│       ├── CorsConfig.java            # CorsFilter como Bean
│       └── RestTemplateConfig.java    # Bean RestTemplate
│
└── exceptions
    ├── PdfProcessingException.java    # erro de processamento de PDF (→ 400)
    ├── AnaliseException.java          # erro de análise / IA (→ 500)
    └── GlobalExceptionHandler.java    # @RestControllerAdvice
```

> **Nota de divergência em relação à descrição original:**
> - Os pacotes são `controllers` e `services` (plural), `dtos.request`/`dtos.response` (e não `dto.*`).
> - `RestTemplateConfig` está em `infra.security` (junto de `CorsConfig`), não em `infra` diretamente.
> - `AnaliseRequest` (record) **existe mas não é usado** pelo `AnaliseController`, que recebe a descrição da vaga via `@RequestParam` solto. Ver §3 e §8.

### Fluxo completo de uma requisição

```
Cliente (frontend / Postman)
   │  POST /api/analise  (multipart/form-data)
   │  ├── curriculo: <arquivo.pdf>
   │  └── descricaoVaga: <texto>
   ▼
[CorsFilter]  ── valida origem (127.0.0.1:5500 / localhost:5500)
   ▼
AnaliseController.analisar(MultipartFile, String)
   │  - @Valid / @NotBlank na descrição da vaga
   ▼
AnaliseService.analisar(curriculo, descricaoVaga)
   │
   ├─► PdfExtractorService.extrairTexto(curriculo)
   │       PDFBox carrega o PDF → PDFTextStripper → String
   │       (PDF vazio/ilegível → PdfProcessingException → HTTP 400)
   │
   ├─► PromptBuilder.build(textoCurriculo, descricaoVaga)
   │       text block parametrizado → prompt único
   │
   ├─► AnthropicClient.enviar(prompt)
   │       POST https://api.anthropic.com/v1/messages
   │       headers: x-api-key, anthropic-version
   │       body: { model, max_tokens, messages }
   │       extrai content[0].text da resposta
   │       (qualquer falha → AnaliseException → HTTP 500)
   │
   └─► AnaliseService.parsearResposta(json)
           Jackson lê a árvore JSON → AnaliseResponse
           score → StatusAderencia.fromScore(score)
           chave → CategoriaAnalise (via CATEGORIA_MAP)
   ▼
ResponseEntity<AnaliseResponse>  →  HTTP 200 + JSON
```

### Responsabilidade de cada camada

- **`controllers` (apresentação):** traduz HTTP para chamadas de serviço. Recebe o multipart, aplica validação de entrada e devolve `ResponseEntity`. Não contém lógica de negócio.
- **`services` (aplicação/negócio):** orquestra o caso de uso (`AnaliseService`) e isola a extração de PDF (`PdfExtractorService`). É onde o fluxo é coordenado e a resposta da IA é interpretada.
- **`domain` (modelo de domínio):** enums que carregam regra de classificação (`StatusAderencia.fromScore`) e metadados (`CategoriaAnalise`). É o vocabulário do domínio ATS.
- **`dtos` (contratos):** records imutáveis que definem entrada e saída da API, desacoplando o modelo interno do JSON exposto.
- **`infra` (infraestrutura):** detalhes técnicos voláteis — integração com a Anthropic, construção do prompt, configuração de HTTP e CORS. Isola o "como" externo do "o quê" do negócio.
- **`exceptions` (tratamento transversal):** exceções de domínio + handler global que mapeia cada tipo para um status HTTP.

---

## 3. Detalhamento por Pacote

### `controllers` — `AnaliseController`

- **Responsabilidade:** expor o único endpoint da API (`POST /api/analise`), receber o currículo (`MultipartFile`) e a descrição da vaga, delegar ao `AnaliseService` e devolver `200 OK` com o `AnaliseResponse`.
- **Dependências:** `AnaliseService` (injetado via construtor gerado por `@RequiredArgsConstructor`). Injeta o serviço, e não os componentes de IA/PDF, mantendo o controller fino.
- **Padrões/princípios:**
  - **SRP** — limita-se a tradução HTTP↔serviço; nenhuma regra de negócio.
  - **DIP** — depende da abstração de caso de uso (`AnaliseService`), não dos detalhes de extração/IA.
  - **Injeção por construtor** — campo `final`, favorecendo imutabilidade e testabilidade.
- **Decisões técnicas:** recebe `@RequestPart("curriculo")` + `@RequestParam("descricaoVaga") @NotBlank`. Note que o `AnaliseRequest` (record) **não é usado aqui** — em multipart com arquivo, é mais simples receber os campos soltos do que tentar bindar um record de partes mistas. O `consumes = MULTIPART_FORM_DATA_VALUE` documenta e restringe o content-type aceito.

### `services` — `AnaliseService`

- **Responsabilidade:** orquestrar o caso de uso completo (extrair → construir prompt → chamar IA → parsear) e converter o JSON bruto da IA em `AnaliseResponse`.
- **Dependências:** `PdfExtractorService`, `AnthropicClient`, `PromptBuilder`, `ObjectMapper` — todos por construtor. Cada dependência cobre uma etapa distinta do fluxo; o serviço apenas as encadeia.
- **Padrões/princípios:**
  - **SRP / coordenação** — o método `analisar` é um *orchestrator* de 3 linhas; a complexidade está distribuída nos colaboradores.
  - **DIP** — usa colaboradores injetados em vez de instanciá-los.
  - **Mapa estático `CATEGORIA_MAP`** (`Map.of`) — tabela de tradução chave-string → `CategoriaAnalise`, evitando `switch`/`if` encadeados (substituição de condicional por *lookup table*).
- **Decisões técnicas:** o parsing usa a API de árvore do Jackson (`readTree`/`JsonNode`) em vez de desserialização direta para um DTO. Isso é deliberado porque o JSON da IA não bate 1:1 com `AnaliseResponse` (a IA devolve `chave`, e o backend enriquece com `descricao` e `status` derivados). A captura é de `JsonProcessingException`, convertida em `AnaliseException`.
  - *Ponto de atenção (ver §8):* se a IA devolver uma `chave` fora das quatro previstas, `CATEGORIA_MAP.get` retorna `null` e a chamada seguinte `categoria.getDescricao()` lança `NullPointerException`, que **não** é capturada pelo `catch (JsonProcessingException)` — escaparia como 500 genérico.

### `services` — `PdfExtractorService`

- **Responsabilidade:** extrair texto puro de um `MultipartFile` PDF usando Apache PDFBox.
- **Dependências:** nenhuma injetada — usa diretamente `PDDocument`/`PDFTextStripper`.
- **Padrões/princípios:**
  - **SRP** — faz exatamente uma coisa: PDF → texto. Está separado do `AnaliseService` justamente para isolar a única dependência de I/O binário do fluxo.
  - **try-with-resources** — `PDDocument` é `Closeable`; o recurso é fechado deterministicamente.
- **Decisões técnicas:** valida explicitamente texto em branco (`texto.isBlank()`) e lança `PdfProcessingException`, tratando o caso de PDFs baseados em imagem/escaneados (sem camada de texto extraível). `IOException` é envolvida na mesma exceção de domínio, preservando a causa.

### `domain` — `StatusAderencia` (enum)

- **Responsabilidade:** representar o nível de aderência (`ALTA`, `MEDIA`, `BAIXA`) e classificar um score numérico em uma dessas faixas via `fromScore`.
- **Dependências:** nenhuma.
- **Padrões/princípios:**
  - **Information Expert (GRASP)** — a regra de faixa vive junto do dado que ela classifica. O enum é o "especialista" sobre o que é alta/média/baixa aderência.
  - **SRP** — encapsula a regra de classificação num único lugar reutilizável (usado tanto para o score geral quanto para cada categoria).
- **Decisões técnicas:** faixas `>=75 → ALTA`, `>=50 → MEDIA`, senão `BAIXA`. Como *factory method* estático, mantém a regra coesa e testável isoladamente, sem espalhar `if`s pelo serviço.

### `domain` — `CategoriaAnalise` (enum)

- **Responsabilidade:** enumerar as quatro categorias de análise ATS e carregar um rótulo legível (`descricao`) para cada uma.
- **Dependências:** nenhuma.
- **Padrões/princípios:** **enum com estado/comportamento** — cada constante carrega seu `descricao` final, fornecido no `CategoriaResponse`. Centraliza os rótulos exibidos, evitando *magic strings* dispersas.
- **Decisões técnicas:** as chaves técnicas (`keywords`, `cronologia`, etc., usadas no JSON da IA) são mantidas **fora** do enum — o vínculo string→enum é feito no `CATEGORIA_MAP` do serviço. Isso mantém o enum focado no domínio e o *wire format* na camada que o consome.

### `dtos.request` — `AnaliseRequest` (record)

- **Responsabilidade:** representar a entrada textual (descrição da vaga) com validação `@NotBlank`.
- **Dependências:** anotação de validação Jakarta.
- **Padrões/princípios:** **DTO imutável** via `record`.
- **Decisões técnicas / status real:** atualmente **não é referenciado** pelo controller (que usa `@RequestParam`). Permanece como contrato candidato — útil caso a entrada migre para JSON puro, mas hoje é código morto. Ver §8.

### `dtos.response` — `AnaliseResponse` (record)

- **Responsabilidade:** contrato de saída raiz: `scoreGeral`, `statusGeral`, lista de `CategoriaResponse` e lista de `sugestoes`.
- **Padrões/princípios:** **DTO imutável**; separação entre modelo de resposta e modelo de domínio.
- **Decisões técnicas:** expõe o enum `StatusAderencia` diretamente — serializado como string pelo Jackson, o que dá ao frontend um valor estável e legível.

### `dtos.response` — `CategoriaResponse` (record)

- **Responsabilidade:** resultado de uma categoria: o enum `categoria`, sua `descricao`, o `score`, o `status` derivado e o `feedback` textual da IA.
- **Padrões/princípios:** **DTO imutável**; agrega dado da IA (`score`, `feedback`) com dado derivado no backend (`descricao`, `status`).
- **Decisões técnicas:** enriquece a resposta da IA — a IA só devolve `chave`/`score`/`feedback`; `descricao` vem do enum e `status` de `fromScore`. O backend é a fonte da verdade da classificação, não a IA.

### `infra.ia` — `AnthropicClient`

- **Responsabilidade:** encapsular toda a comunicação HTTP com a API de Mensagens da Anthropic: montar headers, corpo, fazer o POST e extrair o texto da resposta.
- **Dependências:** `RestTemplate` (injetado), `ObjectMapper` (injetado), e os valores `anthropic.api.key`/`anthropic.api.model` via `@Value`.
- **Padrões/princípios:**
  - **Adapter / Gateway** — adapta o protocolo da Anthropic para uma interface interna simples: `String enviar(String prompt)`.
  - **SRP** — só conhece "como falar com a Anthropic"; não sabe nada sobre PDFs, categorias ou scores.
  - **DIP** — recebe `RestTemplate` por injeção (configurado em `RestTemplateConfig`), permitindo substituição/mock.
- **Decisões técnicas:** corpo montado com `Map.of` (sem DTO de request da Anthropic) por simplicidade. `max_tokens=2048`. Extrai `content[0].text` navegando a árvore JSON. Captura **`Exception`** ampla e converte em `AnaliseException` — qualquer falha (rede, status != 2xx, JSON inesperado) vira erro de análise (→ 500). A chave de API nunca aparece no código (vem de variável de ambiente via `application.properties`).

### `infra.ia` — `PromptBuilder`

- **Responsabilidade:** construir o prompt enviado ao Claude, definindo o papel ("analisador especialista em ATS"), as categorias a avaliar, as faixas de pontuação e o **esquema JSON exato** da resposta.
- **Dependências:** nenhuma.
- **Padrões/princípios:**
  - **SRP / Builder simples** — isolar a engenharia de prompt num componente próprio significa que ajustar o prompt não toca a lógica de orquestração nem a de parsing.
  - **Componente sem estado** — `build` é uma função pura `(curriculo, vaga) → String`.
- **Decisões técnicas:** usa *text block* (`"""`) com `formatted(...)` para interpolar currículo e vaga, mantendo o template legível. Instrui o modelo a responder "APENAS com JSON, sem markdown" — crítico para que o `parsearResposta` consiga ler a árvore diretamente. As faixas no prompt (50/75) espelham as de `StatusAderencia.fromScore`, mantendo IA e backend coerentes.

### `infra.security` — `CorsConfig`

- **Responsabilidade:** liberar requisições cross-origin do frontend de desenvolvimento (`127.0.0.1:5500` e `localhost:5500` — *Live Server*).
- **Dependências:** nenhuma.
- **Padrões/princípios:** configuração via **Bean** (`@Configuration` + `@Bean`).
- **Decisões técnicas:** registra um `CorsFilter` (filtro de servlet) em vez de `WebMvcConfigurer`. Métodos permitidos: `POST`, `GET`, `OPTIONS` (OPTIONS necessário para o *preflight*). Origens restritas (não usa `*`), o que é a postura correta. Ver justificativa em §6.

### `infra.security` — `RestTemplateConfig`

- **Responsabilidade:** expor um `RestTemplate` como Bean gerenciado pelo Spring.
- **Dependências:** nenhuma.
- **Padrões/princípios:** **Factory de Bean / DIP** — centraliza a criação do cliente HTTP para que `AnthropicClient` o receba por injeção em vez de instanciar `new RestTemplate()`.
- **Decisões técnicas:** instância padrão, sem timeouts/interceptors configurados (ponto de melhoria — §8). Existir como Bean já habilita evoluir a configuração (timeouts, retry) sem tocar no cliente.

### `exceptions` — `PdfProcessingException`

- **Responsabilidade:** sinalizar falha na extração/validação do PDF (arquivo ilegível, corrompido ou sem texto).
- **Padrões/princípios:** **exceção de domínio** estendendo `RuntimeException` (não checada) — não polui as assinaturas com `throws`.
- **Decisões técnicas:** dois construtores (com e sem `cause`), permitindo tanto erro de validação ("PDF sem texto") quanto encapsulamento de `IOException`. Mapeada para **400** no handler — é erro do cliente (arquivo ruim).

### `exceptions` — `AnaliseException`

- **Responsabilidade:** sinalizar falha no processo de análise — comunicação com a Anthropic ou parsing da resposta.
- **Padrões/princípios:** **exceção de domínio** (`RuntimeException`).
- **Decisões técnicas:** só tem construtor com `cause`, refletindo que sempre encapsula uma falha subjacente. Mapeada para **500** — é falha interna/de dependência externa, não do cliente.

### `exceptions` — `GlobalExceptionHandler`

- **Responsabilidade:** traduzir exceções de domínio em respostas HTTP padronizadas (`{"erro": "..."}`).
- **Dependências:** nenhuma.
- **Padrões/princípios:**
  - **`@RestControllerAdvice`** — tratamento de erro transversal e centralizado, fora dos controllers (SRP aplicado à camada de apresentação).
  - **Exception Handler pattern** do Spring MVC.
- **Decisões técnicas:** `PdfProcessingException → 400`, `AnaliseException → 500`, ambos com corpo `Map<String,String>` consistente. *Ponto de atenção (§8):* não há handler para `MethodArgumentNotValidException`/`ConstraintViolationException` (validação de `@NotBlank`) nem um *fallback* genérico para `Exception` — esses casos caem no tratamento default do Spring, com formato de corpo diferente.

---

## 4. Integração com IA

### Construção do prompt (`PromptBuilder`)

O prompt é um *text block* único que combina quatro blocos: (1) definição de papel ("analisador especialista em ATS"); (2) instruções de avaliação por categoria; (3) faixas de pontuação; (4) **esquema JSON de saída obrigatório**, seguido das seções `=== CURRÍCULO ===` e `=== DESCRIÇÃO DA VAGA ===` preenchidas via `formatted()`. A instrução "Responda APENAS com o JSON abaixo, sem markdown, sem explicação" é o que permite ao backend parsear a resposta sem pré-processamento (sem precisar remover *code fences*).

### Métricas ATS avaliadas

| Categoria (`chave`) | Enum | O que avalia | Por quê |
|---|---|---|---|
| `keywords` | `KEYWORDS` | Match de palavras-chave e termos técnicos da vaga | ATS pontua currículos por correspondência de termos; é o filtro primário |
| `cronologia` | `CRONOLOGIA` | Ordem cronológica inversa, gaps, tempo de experiência | Parsers de ATS extraem histórico; formato cronológico inverso é o esperado |
| `impacto` | `IMPACTO` | Resultados quantificáveis (métricas financeiras, tempo, escala) | Recrutadores e ranqueamento valorizam realizações mensuráveis |
| `formatacao` | `FORMATACAO` | Coluna única, sem tabelas/gráficos/imagens, seções claras | Layouts complexos quebram o parsing de ATS, derrubando o score |

### Parsing e mapeamento (`AnaliseService.parsearResposta`)

1. `objectMapper.readTree(json)` → `JsonNode root`.
2. `scoreGeral` lido como `int`; `statusGeral = StatusAderencia.fromScore(scoreGeral)` (derivado no backend, não confiando na IA para classificar).
3. Itera `root.get("categorias")`: para cada nó, lê `chave` → resolve `CategoriaAnalise` via `CATEGORIA_MAP`, lê `score` e `feedback`, e monta `CategoriaResponse` enriquecendo com `categoria.getDescricao()` e `StatusAderencia.fromScore(score)`.
4. Itera `root.get("sugestoes")` → `List<String>`.
5. Retorna `AnaliseResponse(scoreGeral, statusGeral, categorias, sugestoes)`.

O backend usa a IA apenas como fonte de **scores e textos**; a **classificação** (`status`) e os **rótulos** (`descricao`) são determinísticos e controlados localmente.

### Tratamento de erros na comunicação externa

`AnthropicClient.enviar` envolve a chamada e o parsing em `try { ... } catch (Exception e)`, convertendo qualquer falha em `AnaliseException("Erro na comunicação com a API da Anthropic", e)`. Isso cobre erros de rede, HTTP não-2xx (o `RestTemplate` padrão lança em 4xx/5xx) e estrutura de resposta inesperada. No `AnaliseService`, o `parsearResposta` converte falhas de JSON da resposta da IA em `AnaliseException`. Ambos resultam em **HTTP 500** via handler. A captura ampla é simples, mas perde a granularidade do erro (ver §8).

---

## 5. Endpoint da API

| Item | Valor |
|---|---|
| Método | `POST` |
| URL | `/api/analise` |
| Content-Type (request) | `multipart/form-data` |
| Content-Type (response) | `application/json` |

### Parâmetros de entrada

| Nome | Tipo | Origem | Obrigatório |
|---|---|---|---|
| `curriculo` | `MultipartFile` (PDF) | `@RequestPart` | sim |
| `descricaoVaga` | `String` | `@RequestParam`, `@NotBlank` | sim (não pode ser vazio/branco) |

### Exemplo de resposta (`200 OK`)

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
      "feedback": "Bom match de termos técnicos como 'Spring Boot' e 'REST'. Faltam 'Docker' e 'CI/CD' citados na vaga."
    },
    {
      "categoria": "CRONOLOGIA",
      "descricao": "Cronologia da Carreira",
      "score": 65,
      "status": "MEDIA",
      "feedback": "Ordem cronológica inversa correta, mas há um gap de 8 meses não explicado em 2023."
    },
    {
      "categoria": "IMPACTO",
      "descricao": "Métricas de Impacto",
      "score": 55,
      "status": "MEDIA",
      "feedback": "Poucos resultados quantificados. Adicione métricas (%, R$, tempo) às realizações."
    },
    {
      "categoria": "FORMATACAO",
      "descricao": "Formatação",
      "score": 88,
      "status": "ALTA",
      "feedback": "Layout em coluna única, sem tabelas ou imagens. Ótimo para parsing de ATS."
    }
  ],
  "sugestoes": [
    "Inclua as palavras-chave 'Docker' e 'CI/CD' na seção de habilidades.",
    "Explique o gap de carreira de 2023 ou remova-o reformulando as datas.",
    "Quantifique pelo menos três realizações com métricas concretas."
  ]
}
```

### Códigos HTTP

| Código | Quando ocorre |
|---|---|
| `200 OK` | Análise concluída com sucesso. |
| `400 Bad Request` | `PdfProcessingException` — PDF corrompido, ilegível ou sem texto extraível (`{"erro": "..."}`). Também: validação de entrada (`@NotBlank` em `descricaoVaga`), porém com formato de corpo padrão do Spring (não passa pelo handler customizado). |
| `500 Internal Server Error` | `AnaliseException` — falha na chamada à Anthropic ou no parsing da resposta da IA (`{"erro": "..."}`). |

---

## 6. Decisões Arquiteturais

**Por que não há banco de dados (stateless by design).** O caso de uso é uma transformação pura: `(PDF, vaga) → análise`. Não há entidade a persistir, histórico requerido nem identidade de usuário. Manter o serviço stateless elimina infraestrutura de persistência, simplifica o deploy, facilita escala horizontal (qualquer instância atende qualquer request) e reduz superfície de risco com dados sensíveis (currículos não ficam armazenados).

**Records em vez de classes com Lombok para DTOs.** Records são imutabilidade nativa da linguagem (Java 17), com `equals`/`hashCode`/`toString`/acessores gerados pelo compilador — sem dependência de processador de anotações para o contrato de dados. São mais concisos e expressam intenção ("isto é um dado imutável") melhor que `@Data`/`@Value`. Lombok continua no projeto, mas para **comportamento de infraestrutura** (`@RequiredArgsConstructor` em controller/services), não para modelar dados.

**Por que `fromScore` fica no enum e não no service.** É a aplicação do princípio *Information Expert*: a regra que define o que é "alta/média/baixa aderência" pertence ao próprio conceito `StatusAderencia`. Concentrá-la ali evita duplicação (a regra é usada para o score geral **e** para cada categoria), mantém o serviço enxuto e torna a classificação testável de forma isolada, sem subir contexto Spring.

**Por que `PdfExtractorService` é separado do `AnaliseService` (SRP).** Extração de PDF é a única parte do fluxo que lida com I/O binário e com a dependência PDFBox. Isolá-la dá uma fronteira de responsabilidade clara, permite mockar a extração nos testes do orquestrador e abre espaço para trocar a biblioteca (ou suportar outros formatos) sem tocar a lógica de análise.

**Por que `PromptBuilder` é um componente separado e não um método no service.** Engenharia de prompt é uma preocupação volátil e independente: o prompt muda com frequência (ajuste de instruções, novas categorias) por razões diferentes das que mudam a orquestração. Separar respeita o SRP e o princípio "uma classe, uma razão para mudar", além de tornar o prompt facilmente testável e versionável.

**Por que `CorsFilter` como Bean em vez de `WebMvcConfigurer`.** O `CorsFilter` atua no nível de filtro de servlet, antes do despacho do Spring MVC, o que o torna mais previsível para *preflight* (`OPTIONS`) e compatível mesmo se Spring Security for adicionado depois (ponto onde `WebMvcConfigurer` sozinho costuma falhar). Como Bean único, a configuração fica centralizada e fácil de evoluir.

**Por que `RestTemplate` em vez de `WebClient`.** O fluxo é síncrono e bloqueante por natureza (uma chamada externa por request, e o request HTTP do cliente já está bloqueado aguardando). `WebClient` (reativo) traria a dependência `spring-webflux` e um modelo assíncrono sem benefício real aqui. `RestTemplate` é mais simples, suficiente e adequado ao perfil de carga. *(Observação: o `RestTemplate` está em modo de manutenção no ecossistema Spring; para evolução de longo prazo, `RestClient` — síncrono e moderno — seria o sucessor natural.)*

---

## 7. Padrões e Princípios Identificados

**Princípios SOLID:**
- **SRP (Single Responsibility):** cada classe tem uma única razão de mudar — `PdfExtractorService` (extração), `PromptBuilder` (prompt), `AnthropicClient` (HTTP da IA), `GlobalExceptionHandler` (tratamento de erro), controller (HTTP↔serviço).
- **DIP (Dependency Inversion):** injeção por construtor em toda parte; `AnthropicClient` recebe `RestTemplate`/`ObjectMapper`; `AnaliseService` recebe seus colaboradores. `RestTemplateConfig` fornece a abstração concreta como Bean.
- **OCP (parcial):** novas categorias podem ser adicionadas em `CategoriaAnalise` + `CATEGORIA_MAP` sem alterar a estrutura do parsing; novas faixas de status mudam só `StatusAderencia`.

**Design patterns / idioms aplicados:**
- **Adapter/Gateway:** `AnthropicClient` adapta o protocolo da Anthropic para `String enviar(String)`.
- **Information Expert (GRASP):** `StatusAderencia.fromScore` e `CategoriaAnalise.getDescricao`.
- **Factory Method estático:** `StatusAderencia.fromScore`.
- **DTO imutável (records):** `AnaliseRequest`, `AnaliseResponse`, `CategoriaResponse`.
- **Lookup table / substituição de condicional:** `CATEGORIA_MAP` (`Map.of`) no lugar de `switch`.
- **Exception Handler centralizado (`@RestControllerAdvice`):** mapeamento exceção→HTTP fora dos controllers.
- **Bean Factory de configuração:** `RestTemplateConfig`, `CorsConfig`.
- **Component sem estado / função pura:** `PromptBuilder.build`.
- **try-with-resources:** gestão determinística do `PDDocument`.
- **Injeção por construtor com campos `final` (`@RequiredArgsConstructor`):** imutabilidade de dependências e testabilidade.

---

## 8. Pontos de Melhoria

**Testes unitários (ausentes hoje — só há o `contextLoads` default).**
- `StatusAderencia.fromScore`: testes de fronteira (49/50/74/75) — JUnit 5 puro, sem Spring.
- `AnaliseService.parsearResposta`: alimentar JSONs (válido, com `chave` desconhecida, campos faltando) com `AnthropicClient`/`PdfExtractorService` mockados (Mockito).
- `PdfExtractorService`: PDF com texto, PDF sem texto (deve lançar `PdfProcessingException`), arquivo inválido.
- `AnthropicClient`: `MockRestServiceServer` para simular respostas/erros da Anthropic.
- `AnaliseController`: `@WebMvcTest` + `MockMvc` para multipart, validação e códigos HTTP.

**Robustez no parsing (bug latente).** `CATEGORIA_MAP.get(chave)` pode retornar `null` (chave inesperada da IA) e provocar `NullPointerException` não tratada → 500 genérico. Validar a chave e lançar `AnaliseException` com mensagem clara; idem para campos ausentes (`root.get("scoreGeral")` nulo). Considerar validar o intervalo `0–100`.

**Tratamento de erro de validação.** Adicionar `@ExceptionHandler` para `MethodArgumentNotValidException`/`ConstraintViolationException` e um *fallback* `Exception → 500`, padronizando o corpo `{"erro": ...}` para todos os casos (hoje a validação de `@NotBlank` retorna o formato default do Spring).

**Validação de upload.** Verificar `content-type`/extensão do `MultipartFile` (aceitar só `application/pdf`) e impor limite de tamanho (`spring.servlet.multipart.max-file-size`) para evitar PDFs gigantes consumindo memória do PDFBox.

**Retry policy + timeouts na chamada externa.** O `RestTemplate` está sem timeouts (risco de thread pendurada indefinidamente). Configurar `connectTimeout`/`readTimeout` via `ClientHttpRequestFactory` e adicionar retry com backoff (Spring Retry/Resilience4j) para erros transitórios (`429`, `5xx`) da Anthropic. Acrescentar circuit breaker para degradar com elegância.

**Granularidade de exceções no `AnthropicClient`.** `catch (Exception)` mascara causas distintas. Separar `RestClientException` (rede/HTTP) de erro de parsing e, idealmente, distinguir `429`/`401` para mensagens e status mais informativos.

**Logging.** Não há logs. Adicionar logging estruturado (SLF4J) em pontos-chave: início/fim da análise, latência da chamada à Anthropic, e logs de erro com *stack trace* no handler (preservar a `cause`, hoje perdida no corpo da resposta).

**Rate limiting.** Como cada request dispara uma chamada paga ao LLM, expor sem limite é risco de custo/abuso. Aplicar rate limiting (ex.: Bucket4j) por IP/API key.

**Segurança e segredos.** A chave da Anthropic vem de env var (bom). Em produção, considerar autenticação na própria API (API key/JWT) e restringir as origens CORS por perfil (as atuais são de desenvolvimento local).

**Código morto / coerência.** `AnaliseRequest` não é usado — ou adotá-lo, ou removê-lo. Avaliar tornar `max_tokens`/`model` configuráveis e o `API_URL` externalizável. Padronizar nomenclatura de métodos do handler (`handlerPdf` vs `handleAnalise`).

**Modernização do cliente HTTP.** Migrar de `RestTemplate` (modo de manutenção) para `RestClient` (Spring 6.1+), mantendo o estilo síncrono, porém com API mais ergonômica e melhor integração com observabilidade.