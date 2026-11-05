package com.domain.api;

import com.domain.service.FluxoAssinaturaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/assinaturas")
public class AssinaturaController {
    private final FluxoAssinaturaService fluxo;

    public AssinaturaController(FluxoAssinaturaService fluxo) {
        this.fluxo = fluxo;
    }

    @PostMapping(value = "/iniciar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FluxoAssinaturaService.Inicio> iniciar(@RequestPart("arquivo") MultipartFile arquivo) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fluxo.iniciar(arquivo));
    }

    @PostMapping("/finalizar")
    public ResponseEntity<byte[]> finalizar(@Valid @RequestBody FinalizarRequest request) {
        var resultado = fluxo.finalizar(request.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(resultado.contentType()))
                .contentLength(resultado.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resultado.nome(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resultado.bytes());
    }

    public record FinalizarRequest(@NotBlank String token) {}
}
