package eduar.atsanalyzer;
import eduar.atsanalyzer.exceptions.PdfProcessingException;
import eduar.atsanalyzer.services.PdfExtractorService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfExtractorServiceTest {
    private PdfExtractorService  service = new PdfExtractorService();

    @Test
    @DisplayName("arquivo vazio -> PdfProcessingException")
    void deveLancarExcecao_quandoArquivoVazio() {
        MockMultipartFile file = new MockMultipartFile(
                "curriculo", "vazio.pdf", "application/pdf", new byte[0]
        );

        assertThrows(PdfProcessingException.class, () -> service.extrairTexto(file));
    }

    @Test
    @DisplayName("filename null → PdfProcessingException")
    void deveLancarExcecao_quandoFilenameNull() {
        MockMultipartFile file = new MockMultipartFile(
                "curriculo", null, "application/pdf", "conteudo".getBytes()
        );

        assertThrows(PdfProcessingException.class, () -> service.extrairTexto(file));
    }

    @Test
    @DisplayName("arquivo não-PDF → PdfProcessingException")
    void deveLancarExcecao_quandoNaoEhPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "curriculo", "curriculo.docx", "application/octet-stream", "conteudo".getBytes()
        );

        assertThrows(PdfProcessingException.class, () -> service.extrairTexto(file));
    }

    // === EXTRAÇÃO DE TEXTO ===

    @Test
    @DisplayName("PDF válido com texto → retorna texto extraído")
    void deveExtrairTexto_quandoPdfValido() throws IOException {
        byte[] pdfBytes = criarPdfComTexto("Eduardo Ciudad - Desenvolvedor Java");
        MockMultipartFile file = new MockMultipartFile(
                "curriculo", "curriculo.pdf", "application/pdf", pdfBytes
        );

        String resultado = service.extrairTexto(file);

        assertTrue(resultado.contains("Eduardo Ciudad"));
    }

    @Test
    @DisplayName("PDF sem texto extraível → PdfProcessingException")
    void deveLancarExcecao_quandoPdfSemTexto() throws IOException {
        // PDF com uma página em branco — sem nenhum conteúdo de texto
        byte[] pdfBytes = criarPdfVazio();
        MockMultipartFile file = new MockMultipartFile(
                "curriculo", "vazio.pdf", "application/pdf", pdfBytes
        );

        assertThrows(PdfProcessingException.class, () -> service.extrairTexto(file));
    }

    private byte[] criarPdfComTexto(String texto) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText(texto);
                content.endText();
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] criarPdfVazio() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }
}
