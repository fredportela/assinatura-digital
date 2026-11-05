# Assinador digital — Spring Boot

Recebe PDF ou DOCX por upload e assina usando o certificado selecionado pelo usuário no frontend com Lacuna Web PKI. O backend usa Rest PKI para iniciar, validar e finalizar a assinatura. A chave privada permanece no dispositivo do usuário.

## Executar

Requer Java 21, Maven 3.6.3 ou superior, credencial Rest PKI e extensão/aplicativo Web PKI instalado na máquina do usuário. O backend usa Spring Boot 4.1.2, com os starters `spring-boot-starter-webmvc` e `spring-boot-starter-webmvc-test`.

Esta atualização migra do Spring Boot 3.4.3 para a linha 4, baseada em Spring Framework 7 e Tomcat 11. O Java permanece na versão 21. Confira os [requisitos oficiais](https://docs.spring.io/spring-boot/system-requirements.html) e o [guia de migração para Spring Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide). Antes da implantação, execute `mvn clean verify` e valide o fluxo real de assinatura com o Rest PKI e o certificado A3.

```powershell
$env:RESTPKI_TOKEN = 'sua-credencial-rest-pki'
mvn clean verify
mvn spring-boot:run
```

Abra **http://localhost:8081/assinador.html**. Selecione o documento, escolha o certificado, clique em **Assinar documento**, confirme a operação no Web PKI e baixe o resultado. O frontend não solicita upload de PFX nem a senha do certificado; a interação com a chave é feita pelo Web PKI.

`RESTPKI_ENDPOINT` altera o endereço do serviço no backend (padrão `https://pki.rest/`). `WEBPKI_RESTPKI_URL` altera o endereço acessado pelo Web PKI no navegador (padrão `https://restpki.lacunasoftware.com/`). Para instância própria, configure ambos para a mesma instalação. `PORT` altera a porta HTTP, cujo padrão é 8081.

O token de acesso Rest PKI é exclusivo do servidor e não é exposto pelo endpoint de configuração. O script Web PKI é carregado do CDN oficial da Lacuna; o navegador precisa ter acesso a ele e ao Rest PKI.

## Gerar e configurar os tokens usados pelo Web PKI

Há dois tokens distintos neste projeto:

| Token | Origem | Uso |
| --- | --- | --- |
| Credencial de acesso (`RESTPKI_TOKEN`) | Painel do Rest PKI | Autentica o backend no serviço; permanece no servidor. |
| Token de assinatura (`inicio.token`) | Rest PKI, ao iniciar o documento pelo backend | Identifica uma sessão de assinatura e é enviado ao Web PKI no navegador. |

### 1. Obter a credencial de acesso do backend

1. Acesse [Rest PKI](https://pki.rest/), crie uma conta ou entre na conta da organização.
2. No **Control Panel** (painel de controle), clique em **Generate new access token**.
3. Copie a credencial gerada e configure a variável de ambiente no terminal em que iniciará o backend:

```powershell
$env:RESTPKI_TOKEN = 'cole-aqui-a-credencial-gerada-no-painel'
mvn spring-boot:run
```

Esse comando define a variável apenas na sessão atual do PowerShell. Se iniciar pela IDE, configure `RESTPKI_TOKEN` nas variáveis de ambiente da configuração de execução. A aplicação não carrega arquivos `.env` automaticamente.

Não coloque essa credencial no Angular, em `assinador.js`, no README ou no Git. Para uma instalação própria de Rest PKI, obtenha a credencial com o administrador e configure os endpoints da mesma instalação.

Passos do painel conforme a [documentação oficial de configuração e autenticação do Rest PKI](https://docs.lacunasoftware.com/en-us/articles/rest-pki/quick-reference).

### 2. Gerar o token de uma assinatura

O token da assinatura é gerado pelo Rest PKI; o Web PKI recebe esse token para executar a assinatura local.

1. Instale a extensão e o [componente Web PKI](https://get.webpkiplugin.com/). Para A3, conecte o token/cartão e instale o middleware do fabricante.
2. Com o backend iniciado, abra `http://localhost:8081/assinador.html` ou o Angular em `http://localhost:4200` com o proxy configurado.
3. Selecione o certificado e envie um PDF ou DOCX.
4. Ao clicar em **Assinar documento**, o frontend chama `POST /api/assinaturas/iniciar`. O backend chama `startWithWebPki()` no Rest PKI e devolve o campo `token`, junto de `expiraEm` e dos metadados do arquivo.
5. O frontend passa esse valor para `pki.signWithRestPki({ token: inicio.token, thumbprint: thumbprintSelecionado })`. Autorize o acesso ao certificado e informe o PIN quando solicitado pelo componente.
6. Somente após sucesso do Web PKI, o frontend chama `POST /api/assinaturas/finalizar` com `{ "token": inicio.token }` e disponibiliza o download.

O fluxo de troca do token está descrito na [referência oficial do Web PKI](https://docs.lacunasoftware.com/en-us/articles/web-pki/quick-reference). Neste projeto, a sessão expira localmente em 20 minutos e é consumida na tentativa de finalização. Após erro ou cancelamento, inicie um novo upload para obter outra sessão. A credencial `RESTPKI_TOKEN` não deve ser usada no lugar de `inicio.token`.

## Formatos

| Upload | Assinatura | Download |
| --- | --- | --- |
| `documento.pdf` | PAdES básico com LTV e certificados ICP-Brasil, embutida no PDF | `documento_assinado.pdf` |
| `documento.docx` | CAdES ICP-Brasil AD-RB, com conteúdo encapsulado | `documento.docx.p7s` |

O `.p7s` contém o DOCX original e sua assinatura. Não é um DOCX com assinatura interna exibida pelo Word, nem uma conversão para PDF. O PDF recebe assinatura criptográfica sem carimbo visual. Não há uso de NFS, dados de escola, representante ou regras de diplomas/XML.

## Cadeia ICP-Brasil e validação de longo prazo

O PDF usa `SignaturePolicy.PadesBasicWithPkiBrazilCerts` com `SecurityContext.pkiBrazil`. Na biblioteca Rest PKI 1.15.9, essa é a política equivalente a `PadesBasicWithLTV` do PKI Express. O mecanismo Rest PKI constrói a cadeia e incorpora os certificados e as evidências de revogação ao PDF. A disponibilidade dos certificados intermediários e dos serviços de revogação deve ser garantida na instalação Rest PKI; em instância própria, verifique rede, proxy e repositórios de ACs.

A política anterior era `PkiBrazilPadesAdrBasica` (AD-RB). O perfil atual é PAdES básico com certificados ICP-Brasil e LTV; ele não declara a política explícita AD-RB. Se houver obrigação de AD-RB, essa escolha deve ser revisada. A confiança permanece restrita à ICP-Brasil, sem raízes de teste. O DOCX continua usando CAdES AD-RB.

Após `PadesSignatureFinisher.finish()`, o backend reabre o PDF com `PadesSignatureExplorer`, habilita validação e aplica a mesma política/contexto. Só devolve o resultado se houver ao menos uma assinatura de documento e todos os signatários retornarem verificações executadas sem erros. Revogação desconhecida não é ignorada e horário declarado pelo signatário não é aceito como referência temporal certificada. Advertências são registradas. Se a consulta de validação falhar, o download é bloqueado. Isso acrescenta uma chamada ao Rest PKI e depende de sua disponibilidade, inclusive para PDFs que já tinham assinaturas anteriores.

O SDK Java 1.15.9 não expõe um indicador de presença de LTV em `PadesSignerInfo`. Essa validação confirma o resultado segundo o serviço, mas não é uma inspeção independente dos objetos DSS nem prova que todas as evidências estejam disponíveis offline. Antes da homologação, assine com o A3 real, confira os certificados incorporados e as evidências de revogação/DSS no Acrobat, confirme que a cadeia chega à raiz ICP-Brasil e submeta o PDF ao [VALIDAR do ITI](https://validar.iti.gov.br/). A inclusão da raiz no arquivo, por si só, não estabelece confiança no verificador.

Não há TSA adicional configurada por esta alteração e LTV não deve ser apresentado como carimbo do tempo. A chave privada e a solicitação de PIN permanecem no Web PKI/token do usuário. O fluxo `signWithRestPki()` e os endpoints existentes são preservados; não é necessário instalar PKI Express para esta integração.

Referências oficiais: [equivalência das políticas Java](https://docs.lacunasoftware.com/articles/pki-express/java/convert-from-restpki), [políticas Rest PKI Java](https://docs.lacunasoftware.com/en-us/content/javadocs/restpki-client/com/lacunasoftware/restpki/SignaturePolicy.html) e [validação de assinaturas](https://docs.lacunasoftware.com/en-us/articles/rest-pki/validate-signatures).

## API e integração com outro frontend

### 1. Upload e início

`POST /api/assinaturas/iniciar`, `multipart/form-data`, campo **arquivo**. Limite de 20 MB. Não defina manualmente o cabeçalho Content-Type ao enviar FormData: o navegador inclui o boundary.

```javascript
const data = new FormData();
data.append('arquivo', arquivoSelecionado);
const response = await fetch('/api/assinaturas/iniciar', { method: 'POST', body: data });
if (!response.ok) throw await response.json();
const inicio = await response.json();
// { token, nomeArquivoAssinado, formato, expiraEm }
```

### 2. Assinatura pelo certificado selecionado

Depois de inicializar o Web PKI e listar certificados, passe o thumbprint escolhido pelo usuário:

```javascript
pki.signWithRestPki({ token: inicio.token, thumbprint: thumbprintSelecionado })
  .success(() => finalizar(inicio.token))
  .fail(erro => tratarErro(erro));
```

O certificado não é escolhido nem instalado pelo backend. A página `assinador.html` implementa o fluxo completo; o código em `assinador.js` também serve como referência para integrar o serviço Angular existente.

### 3. Finalização e download

`POST /api/assinaturas/finalizar`, `application/json`:

```json
{"token":"token-retornado-no-inicio"}
```

Chame somente após sucesso de `signWithRestPki`. A resposta é binária, com Content-Disposition attachment e Content-Type `application/pdf` ou `application/pkcs7-mime`. Use `response.blob()` para disponibilizar o download. O Rest PKI valida assinatura e certificado conforme a política ICP-Brasil definida no início.

`GET /api/web-pki/config` retorna somente a URL de acesso do navegador ao Rest PKI.

## Estado e erros

Os tokens ficam associados ao nome e formato do upload no servidor. O cliente não informa um caminho de arquivo nem escolhe o formato na finalização. As sessões expiram localmente em 20 minutos, são consumidas na tentativa de finalização e se perdem ao reiniciar. Após cancelamento ou erro, inicie um novo upload. Há no máximo 1.000 sessões pendentes. O serviço mantém apenas metadados em memória; o documento enviado é processado pelo Rest PKI. O resultado é devolvido diretamente, sem armazenamento permanente ou recuperação posterior pelo backend.

Esta implementação usa uma única instância, com operações de assinatura serializadas. O frontend é servido pela mesma origem, dispensando CORS. Autenticação e vínculo das sessões a uma conta de usuário ainda não estão implementados.

Erros usam `application/problem+json`: 400 para requisição inválida, 413 para upload acima do limite, 415 para Content-Type incompatível, 422 para documento/token inválido ou falha no fluxo de assinatura e 500 para falhas inesperadas. O upload verifica extensão e cabeçalho de PDF; a validação do PDF é concluída pelo Rest PKI. DOCX exige estrutura ZIP/OOXML compatível e tem limites de descompactação.

## Testes e pacote

```powershell
mvn clean verify
mvn clean package
java -jar target/assinatura-digital-backend-1.0.0-SNAPSHOT.jar
```

Os testes incluem a escolha da política PAdES com LTV, configuração de confiança ICP-Brasil, validação antes do download e rejeição de resultados inválidos, sem verificações ou contendo somente carimbos do tempo. Também cobrem upload, rejeição de formatos inconsistentes, estrutura DOCX, consumo único de tokens, resposta multipart e download binário. Usam mocks para o Rest PKI. A validação integrada requer credencial real e certificado ICP-Brasil disponível no Web PKI.

**Validação pendente neste ambiente:** a execução de processos foi negada pelo sistema operacional, impedindo executar Maven e os testes. O build e o fluxo real com certificado precisam ser verificados no ambiente de execução.

Referências: [Rest PKI](https://docs.lacunasoftware.com/pt-br/articles/rest-pki/quick-reference), [Web PKI](https://docs.lacunasoftware.com/en-us/articles/web-pki/quick-reference).
