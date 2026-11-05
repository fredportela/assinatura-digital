# Assinatura digital com Web PKI e Rest PKI

Aplicação Java 21/Spring Boot e Angular para assinar PDFs e DOCX com certificado do usuário, incluindo A3. A chave privada permanece no dispositivo e é utilizada pelo Lacuna Web PKI.

- PDF: PAdES básico com LTV e certificados ICP-Brasil, com validação do PDF final antes do download.
- DOCX: CAdES ICP-Brasil AD-RB encapsulado, entregue como `.docx.p7s`.

A integração utiliza Rest PKI, com `PadesBasicWithPkiBrazilCerts` e `SecurityContext.pkiBrazil`. Essa política corresponde a `PadesBasicWithLTV` no PKI Express, conforme a [tabela oficial da Lacuna](https://docs.lacunasoftware.com/articles/pki-express/java/convert-from-restpki).

Consulte [backend/README.md](backend/README.md) para execução, configuração, API e limites da validação/LTV; [frontend/README.md](frontend/README.md) para a interface Angular. A homologação com certificado real e inspeção do PDF no Acrobat/ITI ainda é necessária.
