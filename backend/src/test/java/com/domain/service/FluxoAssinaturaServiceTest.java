package com.domain.service;

import com.domain.config.properties.RestPkiProperties;
import com.domain.exceptions.types.negocial.NegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FluxoAssinaturaServiceTest {
    @Test
    void finalizaFormatoAssociadoAoUploadEImpedeReutilizacao() throws Exception {
        RestPkiAssinaturaGateway gateway = mock(RestPkiAssinaturaGateway.class);
        RestPkiProperties properties = mock(RestPkiProperties.class);
        when(properties.getAccessToken()).thenReturn("credencial-teste");
        when(gateway.iniciar(any())).thenReturn("token-teste");
        byte[] assinado = "PDF assinado".getBytes(StandardCharsets.UTF_8);
        when(gateway.finalizar("token-teste", true)).thenReturn(assinado);
        var service = new FluxoAssinaturaService(new DocumentoUploadValidator(), gateway, properties);
        var inicio = service.iniciar(new MockMultipartFile("arquivo", "documento.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("PAdES", inicio.formato());
        var resultado = service.finalizar(inicio.token());
        assertEquals("documento_assinado.pdf", resultado.nome());
        assertArrayEquals(assinado, resultado.bytes());
        assertThrows(NegocioException.class, () -> service.finalizar(inicio.token()));
        verify(gateway, times(1)).finalizar("token-teste", true);
    }

    @Test
    void rejeitaTokenDesconhecidoSemChamadaRemota() {
        var gateway = mock(RestPkiAssinaturaGateway.class);
        var service = new FluxoAssinaturaService(new DocumentoUploadValidator(), gateway, mock(RestPkiProperties.class));
        assertThrows(NegocioException.class, () -> service.finalizar("desconhecido"));
        verifyNoInteractions(gateway);
    }
}
