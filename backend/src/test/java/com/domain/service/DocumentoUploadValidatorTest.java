package com.domain.service;

import com.domain.exceptions.types.negocial.NegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DocumentoUploadValidatorTest {
    private final DocumentoUploadValidator validator = new DocumentoUploadValidator();

    @Test
    void aceitaPdfComCabecalhoESugereNomeDeDownload() throws Exception {
        var documento = validator.validar(new MockMultipartFile("arquivo", "Relatorio.PDF", "application/octet-stream",
                "%PDF-1.7\nconteudo".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(documento.pdf());
        assertEquals("Relatorio_assinado.pdf", documento.nomeAssinado());
    }

    @Test
    void rejeitaArquivoRenomeadoEPathNoNome() {
        assertThrows(NegocioException.class, () -> validator.validar(new MockMultipartFile("arquivo", "falso.pdf",
                "application/pdf", "não é PDF".getBytes(StandardCharsets.UTF_8))));
        assertThrows(NegocioException.class, () -> validator.validar(new MockMultipartFile("arquivo", "../arquivo.pdf",
                "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))));
        assertThrows(NegocioException.class, () -> validator.validar(new MockMultipartFile("arquivo", "arquivo.exe",
                "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void aceitaDocxERejeitaZipGenerico() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            adicionar(zip, "word/document.xml", "<document/>");
            adicionar(zip, "_rels/.rels", "<Relationships/>");
            adicionar(zip, "[Content_Types].xml", """
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
        }
        var documento = validator.validar(new MockMultipartFile("arquivo", "contrato.docx", null, bytes.toByteArray()));
        assertFalse(documento.pdf());
        assertEquals("contrato.docx.p7s", documento.nomeAssinado());
        assertArrayEquals(bytes.toByteArray(), documento.bytes());

        var generico = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(generico)) { adicionar(zip, "texto.txt", "conteudo"); }
        assertThrows(NegocioException.class, () -> validator.validar(new MockMultipartFile("arquivo", "falso.docx", null,
                generico.toByteArray())));
    }

    private static void adicionar(ZipOutputStream zip, String nome, String conteudo) throws Exception {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
