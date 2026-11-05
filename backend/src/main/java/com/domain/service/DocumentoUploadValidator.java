package com.domain.service;

import com.domain.exceptions.types.negocial.NegocioException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocumentoUploadValidator {
    public static final long MAX_BYTES = 20L * 1024 * 1024;

    public Documento validar(MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Selecione um arquivo PDF ou DOCX não vazio.");
        }
        if (arquivo.getSize() > MAX_BYTES) {
            throw new NegocioException("O arquivo deve ter no máximo 20 MB.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome == null || nome.isBlank() || nome.length() > 180
                || nome.contains("/") || nome.contains("\\") || nome.contains(":")
                || nome.chars().anyMatch(Character::isISOControl)) {
            throw new NegocioException("Nome de arquivo inválido.");
        }
        String extensao = nome.toLowerCase(Locale.ROOT);
        boolean pdf = extensao.endsWith(".pdf");
        if (!pdf && !extensao.endsWith(".docx")) {
            throw new NegocioException("Formato não suportado. Envie PDF ou DOCX.");
        }
        byte[] bytes;
        try (var input = arquivo.getInputStream()) {
            bytes = input.readNBytes((int) MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new NegocioException("O arquivo deve ter no máximo 20 MB.");
        }
        if (pdf) {
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new NegocioException("O conteúdo enviado não é um PDF.");
            }
        } else {
            validarDocx(bytes);
        }
        return new Documento(nome, pdf, bytes);
    }

    private void validarDocx(byte[] bytes) {
        Set<String> entradas = new HashSet<>();
        long descompactado = 0;
        byte[] tiposConteudo = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entrada;
            byte[] buffer = new byte[8192];
            while ((entrada = zip.getNextEntry()) != null) {
                if (entradas.size() >= 5000 || !entradas.add(entrada.getName())) {
                    throw new IOException("Quantidade ou duplicidade de entradas inválida");
                }
                ByteArrayOutputStream tipos = "[Content_Types].xml".equals(entrada.getName())
                        ? new ByteArrayOutputStream() : null;
                int lidos;
                while ((lidos = zip.read(buffer)) != -1) {
                    descompactado += lidos;
                    if (descompactado > 100L * 1024 * 1024 || (tipos != null && tipos.size() + lidos > 1024 * 1024)) {
                        throw new IOException("Limite de descompactação excedido");
                    }
                    if (tipos != null) tipos.write(buffer, 0, lidos);
                }
                if (tipos != null) tiposConteudo = tipos.toByteArray();
            }
            if (!entradas.contains("word/document.xml") || !entradas.contains("_rels/.rels") || tiposConteudo == null) {
                throw new IOException("Estrutura DOCX ausente");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            NodeList overrides = factory.newDocumentBuilder().parse(new ByteArrayInputStream(tiposConteudo))
                    .getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/content-types", "Override");
            boolean documentoWord = false;
            for (int i = 0; i < overrides.getLength(); i++) {
                var atributos = overrides.item(i).getAttributes();
                var parte = atributos.getNamedItem("PartName");
                var tipo = atributos.getNamedItem("ContentType");
                if (parte != null && tipo != null && "/word/document.xml".equals(parte.getNodeValue())
                        && "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml".equals(tipo.getNodeValue())) {
                    documentoWord = true;
                }
            }
            if (!documentoWord) throw new IOException("Tipo de documento incompatível");
        } catch (Exception e) {
            throw new NegocioException("O conteúdo enviado não é um DOCX válido ou excede os limites de processamento.", e);
        }
    }

    public record Documento(String nome, boolean pdf, byte[] bytes) {
        public String nomeAssinado() {
            return pdf ? nome.substring(0, nome.length() - 4) + "_assinado.pdf" : nome + ".p7s";
        }
        public String formato() { return pdf ? "PAdES" : "CAdES"; }
        public String contentType() { return pdf ? "application/pdf" : "application/pkcs7-mime"; }
    }
}
