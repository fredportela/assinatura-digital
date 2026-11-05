package com.domain.service;

import com.domain.exceptions.types.negocial.NegocioException;
import com.lacunasoftware.restpki.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestPkiAssinaturaGatewayTest {
    private final RestPkiClient client = mock(RestPkiClient.class);
    private final RestPkiAssinaturaGateway gateway = new RestPkiAssinaturaGateway(client);
    private final byte[] pdf = "%PDF-assinado".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void iniciaPdfComPoliticaLtvEConfiancaRestritaAIcpBrasil() throws Exception {
        try (var starters = mockConstruction(PadesSignatureStarter.class,
                (starter, context) -> when(starter.startWithWebPki()).thenReturn("token"))) {
            assertEquals("token", gateway.iniciar(new DocumentoUploadValidator.Documento("arquivo.pdf", true, pdf)));
            var starter = starters.constructed().getFirst();
            verify(starter).setPdfToSign(pdf);
            verify(starter).setSignaturePolicy(SignaturePolicy.PadesBasicWithPkiBrazilCerts);
            verify(starter).setSecurityContext(SecurityContext.pkiBrazil);
        }
    }

    @Test
    void soLiberaPdfDepoisDeReabrirEValidar() throws Exception {
        var signature = mock(PadesSignature.class);
        when(signature.getSigners()).thenReturn(List.of(signatario(true, 3, false)));
        try (var finishers = mockConstruction(PadesSignatureFinisher.class,
                (finisher, context) -> when(finisher.finish()).thenReturn(pdf));
             var explorers = mockConstruction(PadesSignatureExplorer.class,
                (explorer, context) -> when(explorer.open()).thenReturn(signature))) {
            assertArrayEquals(pdf, gateway.finalizar("token", true));
            verify(finishers.constructed().getFirst()).setToken("token");
            var explorer = explorers.constructed().getFirst();
            verify(explorer).setSignatureFile(pdf);
            verify(explorer).setValidate(true);
            verify(explorer).setDefaultSignaturePolicy(SignaturePolicy.PadesBasicWithPkiBrazilCerts);
            verify(explorer).setSecurityContext(SecurityContext.pkiBrazil);
            verify(explorer).setIgnoreRevocationStatusUnknown(false);
            verify(explorer).setTrustUncertifiedSigningTime(false);
        }
    }

    @Test
    void rejeitaAssinaturaInvalidaAusenteOuSemValidacao() throws Exception {
        var semResultado = mock(PadesSignerInfo.class);
        List<List<PadesSignerInfo>> casos = List.of(
                List.of(), List.of(signatario(false, 3, false)),
                List.of(signatario(true, 0, false)), List.of(semResultado),
                List.of(signatario(true, 3, true)),
                List.of(signatario(true, 3, false), signatario(false, 3, false)),
                Arrays.asList((PadesSignerInfo) null));
        for (var signers : casos) {
            var signature = mock(PadesSignature.class);
            when(signature.getSigners()).thenReturn(signers);
            try (var finishers = mockConstruction(PadesSignatureFinisher.class,
                    (finisher, context) -> when(finisher.finish()).thenReturn(pdf));
                 var explorers = mockConstruction(PadesSignatureExplorer.class,
                    (explorer, context) -> when(explorer.open()).thenReturn(signature))) {
                assertThrows(NegocioException.class, () -> gateway.finalizar("token", true));
            }
        }
    }

    @Test
    void rejeitaRetornoVazioSemChamarExplorer() throws Exception {
        try (var finishers = mockConstruction(PadesSignatureFinisher.class,
                (finisher, context) -> when(finisher.finish()).thenReturn(new byte[0]));
             var explorers = mockConstruction(PadesSignatureExplorer.class)) {
            assertThrows(NegocioException.class, () -> gateway.finalizar("token", true));
            assertTrue(explorers.constructed().isEmpty());
        }
    }

    @Test
    void falhaNaConsultaDeValidacaoImpedeDownload() throws Exception {
        try (var finishers = mockConstruction(PadesSignatureFinisher.class,
                (finisher, context) -> when(finisher.finish()).thenReturn(pdf));
             var explorers = mockConstruction(PadesSignatureExplorer.class,
                (explorer, context) -> when(explorer.open()).thenThrow(mock(RestException.class)))) {
            assertThrows(NegocioException.class, () -> gateway.finalizar("token", true));
        }
    }

    @Test
    void preservaFinalizacaoCadesDoDocx() throws Exception {
        byte[] cms = {1, 2, 3};
        try (var finishers = mockConstruction(CadesSignatureFinisher.class,
                (finisher, context) -> when(finisher.finish()).thenReturn(cms));
             var explorers = mockConstruction(PadesSignatureExplorer.class)) {
            assertArrayEquals(cms, gateway.finalizar("token", false));
            assertTrue(explorers.constructed().isEmpty());
        }
    }

    private PadesSignerInfo signatario(boolean valido, int verificacoes, boolean timestamp) {
        var signer = mock(PadesSignerInfo.class);
        var results = mock(ValidationResults.class);
        when(results.isValid()).thenReturn(valido);
        when(results.getChecksPerformed()).thenReturn(verificacoes);
        when(signer.getValidationResults()).thenReturn(results);
        when(signer.isDocumentTimestamp()).thenReturn(timestamp);
        return signer;
    }
}
