package com.domain.api;

import com.domain.service.FluxoAssinaturaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssinaturaControllerTest {
    @Test
    void recebeMultipartERetornaArquivoComoAnexo() throws Exception {
        var fluxo = mock(FluxoAssinaturaService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AssinaturaController(fluxo))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(fluxo.iniciar(any())).thenReturn(new FluxoAssinaturaService.Inicio("token", "arquivo_assinado.pdf",
                "PAdES", Instant.now().plusSeconds(1200)));
        byte[] bytes = new byte[]{1, 2, 3};
        when(fluxo.finalizar("token")).thenReturn(new FluxoAssinaturaService.Resultado("arquivo_assinado.pdf", "application/pdf", bytes));
        mvc.perform(multipart("/api/assinaturas/iniciar")
                        .file(new MockMultipartFile("arquivo", "arquivo.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("token"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(post("/api/assinaturas/finalizar").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"token\"}"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void uploadAusenteRetorna400() throws Exception {
        var fluxo = mock(FluxoAssinaturaService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AssinaturaController(fluxo))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(multipart("/api/assinaturas/iniciar")).andExpect(status().isBadRequest());
        verifyNoInteractions(fluxo);
    }
}
