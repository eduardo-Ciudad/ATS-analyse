package eduar.atsanalyzer.services;

import eduar.atsanalyzer.exceptions.PdfProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;

@Service
public class PdfExtractorService {
    public String extrairTexto(MultipartFile file) {

        if(file.isEmpty()) {
            throw new PdfProcessingException("O arquivo enviado esta vazio");
        }
        String filename = file.getOriginalFilename();
        if(filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new PdfProcessingException("apenas arquivos PDF sao aceitos");
        }

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(document);

            if (texto.isBlank()) {
                throw new PdfProcessingException("O PDF não contém texto extraível");
            }

            return texto;

        } catch (IOException e) {
            throw new PdfProcessingException("Erro ao processar o arquivo PDF", e);
        }
    }
}
