package com.domain.service;

import com.domain.config.properties.RestPkiProperties;
import com.domain.exceptions.types.negocial.NegocioException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class FluxoAssinaturaService {
    private final DocumentoUploadValidator validator;
    private final RestPkiAssinaturaGateway gateway;
    private final RestPkiProperties properties;
    private final Map<String, Sessao> sessoes = new HashMap<>();

    public FluxoAssinaturaService(DocumentoUploadValidator validator, RestPkiAssinaturaGateway gateway,
                                 RestPkiProperties properties) {
        this.validator = validator;
        this.gateway = gateway;
        this.properties = properties;
    }

    public synchronized Inicio iniciar(MultipartFile arquivo) throws IOException {
        limparExpiradas();
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank()) {
            throw new NegocioException("Configure RESTPKI_TOKEN antes de iniciar uma assinatura.");
        }
        if (sessoes.size() >= 1000) throw new NegocioException("Limite de assinaturas pendentes atingido.");
        var documento = validator.validar(arquivo);
        String token = gateway.iniciar(documento);
        Instant expiraEm = Instant.now().plusSeconds(1200);
        // Guarda somente metadados; o conteúdo fica com o Rest PKI durante o fluxo.
        sessoes.put(token, new Sessao(documento.nomeAssinado(), documento.pdf(), documento.contentType(), expiraEm));
        return new Inicio(token, documento.nomeAssinado(), documento.formato(), expiraEm);
    }

    public synchronized Resultado finalizar(String token) {
        limparExpiradas();
        Sessao sessao = sessoes.remove(token);
        if (sessao == null) {
            throw new NegocioException("Token inexistente, expirado ou já utilizado. Inicie uma nova assinatura.");
        }
        byte[] bytes = gateway.finalizar(token, sessao.pdf());
        if (bytes == null || bytes.length == 0) throw new NegocioException("O serviço não retornou o documento assinado.");
        return new Resultado(sessao.nome(), sessao.contentType(), bytes);
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void limparExpiradas() {
        Instant agora = Instant.now();
        sessoes.values().removeIf(sessao -> !sessao.expiraEm().isAfter(agora));
    }

    private record Sessao(String nome, boolean pdf, String contentType, Instant expiraEm) {}
    public record Inicio(String token, String nomeArquivoAssinado, String formato, Instant expiraEm) {}
    public record Resultado(String nome, String contentType, byte[] bytes) {}
}
