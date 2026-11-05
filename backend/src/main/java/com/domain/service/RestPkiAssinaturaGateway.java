package com.domain.service;

import com.domain.exceptions.types.negocial.NegocioException;
import com.lacunasoftware.restpki.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RestPkiAssinaturaGateway {
    private static final Logger LOG = LoggerFactory.getLogger(RestPkiAssinaturaGateway.class);
    private final RestPkiClient client;

    public RestPkiAssinaturaGateway(RestPkiClient client) { this.client = client; }

    public String iniciar(DocumentoUploadValidator.Documento documento) {
        try {
            if (documento.pdf()) {
                PadesSignatureStarter starter = new PadesSignatureStarter(client);
                starter.setPdfToSign(documento.bytes());
                // Equivalente Rest PKI de PadesBasicWithLTV do PKI Express.
                // O serviço incorpora cadeia e evidências de revogação ao PDF.
                starter.setSignaturePolicy(SignaturePolicy.PadesBasicWithPkiBrazilCerts);
                starter.setSecurityContext(SecurityContext.pkiBrazil);
                return starter.startWithWebPki();
            }
            CadesSignatureStarter starter = new CadesSignatureStarter(client);
            starter.setContentToSign(documento.bytes());
            starter.setEncapsulateContent(true);
            starter.setSignaturePolicy(SignaturePolicy.PkiBrazilAdrBasica);
            starter.setSecurityContext(SecurityContext.pkiBrazil);
            return starter.startWithWebPki();
        } catch (RestException e) {
            LOG.error("Falha ao iniciar assinatura no Rest PKI", e);
            throw new NegocioException("Não foi possível iniciar a assinatura. Verifique o documento e a disponibilidade do Rest PKI.", e);
        }
    }

    public byte[] finalizar(String token, boolean pdf) {
        try {
            // finish() valida a assinatura e o certificado com a política/contexto definidos no início.
            if (pdf) {
                PadesSignatureFinisher finisher = new PadesSignatureFinisher(client);
                finisher.setToken(token);
                byte[] assinado = finisher.finish();
                validarPdfAssinado(assinado);
                return assinado;
            }
            CadesSignatureFinisher finisher = new CadesSignatureFinisher(client);
            finisher.setToken(token);
            return finisher.finish();
        } catch (RestException e) {
            LOG.error("Falha ao finalizar assinatura no Rest PKI", e);
            throw new NegocioException("Não foi possível validar ou finalizar a assinatura. Inicie novamente o processo.", e);
        }
    }

    private void validarPdfAssinado(byte[] pdf) throws RestException {
        if (pdf == null || pdf.length == 0) {
            throw new NegocioException("O serviço não retornou o PDF assinado.");
        }
        // Reabre o resultado final: o sucesso de finish() não substitui esta validação.
        PadesSignatureExplorer explorer = new PadesSignatureExplorer(client);
        explorer.setSignatureFile(pdf);
        explorer.setValidate(true);
        explorer.setDefaultSignaturePolicy(SignaturePolicy.PadesBasicWithPkiBrazilCerts);
        explorer.setSecurityContext(SecurityContext.pkiBrazil);
        explorer.setIgnoreRevocationStatusUnknown(false);
        explorer.setTrustUncertifiedSigningTime(false);
        PadesSignature assinatura = explorer.open();
        if (assinatura == null || assinatura.getSigners() == null || assinatura.getSigners().isEmpty()) {
            throw new NegocioException("O PDF retornado não contém assinaturas verificáveis.");
        }
        boolean temSignatario = false;
        for (PadesSignerInfo signatario : assinatura.getSigners()) {
            ValidationResults validacao = signatario == null ? null : signatario.getValidationResults();
            if (validacao == null || validacao.getChecksPerformed() == 0 || !validacao.isValid()) {
                throw new NegocioException("O PDF assinado não passou na validação de integridade, cadeia ICP-Brasil ou revogação.");
            }
            if (validacao.hasWarnings()) {
                LOG.warn("A validação do PDF assinado retornou advertências: {}", validacao.getSummary());
            }
            temSignatario |= !signatario.isDocumentTimestamp();
        }
        if (!temSignatario) {
            throw new NegocioException("O PDF retornado contém somente carimbos do tempo, sem assinatura do documento.");
        }
    }
}
