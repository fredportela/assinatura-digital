# Assinador digital — Angular 22

Aplicação para upload de um PDF ou DOCX (até 20 MB), seleção de certificado A1/A3 pelo Web PKI e download do resultado após validação no backend.

## Executar

Requer Node.js 24.15 ou superior da linha 24 (recomendado; consulte `engines` no `package.json`) e o backend Java da pasta vizinha em execução na porta 8080. O projeto usa TypeScript 6 e Angular 22.

A atualização mantém Zone.js explicitamente no bootstrap para preservar a atualização da tela nos retornos assíncronos do Web PKI. O ESLint usa a configuração moderna em `eslint.config.cjs`.

```powershell
npm install
npm start
```

Abra http://localhost:4200. O proxy encaminha /api ao backend; ajuste proxy.conf.json se a porta mudar. Em produção, publique o conteúdo de dist/assinatura-digital/browser e encaminhe /api ao backend pelo mesmo domínio.

No backend, configure RESTPKI_TOKEN. WEBPKI_RESTPKI_URL deve corresponder à instância Rest PKI usada pelo backend. A aplicação obtém somente a URL por GET /api/web-pki/config; o token de acesso Rest PKI permanece no servidor.

Instale o componente Web PKI e conecte o token/cartão ou disponibilize seu certificado A1 no sistema. A seleção e a autorização da assinatura acontecem no navegador pelo Web PKI. A chave privada não é enviada pela aplicação.

## Formatos de saída

- PDF: assinatura PAdES incorporada; download como *_assinado.pdf.
- DOCX: assinatura CAdES encapsulada; download como *.docx.p7s. O pacote contém os bytes originais do DOCX e a assinatura. Não é uma assinatura interna do Microsoft Word; use um leitor CAdES para verificar e extrair o documento.

## Fluxo da API

1. GET /api/web-pki/config retorna { restPkiUrl }.
2. POST /api/assinaturas/iniciar recebe multipart/form-data com o campo arquivo e retorna { token, nomeArquivoAssinado, formato, expiraEm }.
3. O frontend chama Web PKI signWithRestPki com o thumbprint escolhido e o token.
4. POST /api/assinaturas/finalizar recebe { token } e retorna os bytes do arquivo assinado. O token enviado é o da sessão original.

O download fica disponível até trocar/remover o documento ou fechar a página. Em caso de falha ou cancelamento, a próxima tentativa inicia uma nova sessão. Não há armazenamento local persistente de arquivos ou tokens.

## Verificação

### Lint e formatação

```powershell
npm run lint
npm run lint:fix
npm run format
npm run format:check
```

ESLint verifica TypeScript e templates Angular; Stylelint verifica CSS e SCSS. Prettier formata os arquivos com `bracketSameLine: true`, mantendo o fechamento `>` da tag HTML junto do último atributo, e `htmlWhitespaceSensitivity: "ignore"`, evitando que tags sejam partidas para preservar espaços entre elementos. Os atributos ainda podem ocupar várias linhas.

No VS Code, instale as extensões recomendadas do workspace para usar a formatação ao salvar. A regra `no-descending-specificity` do Stylelint fica desativada para permitir os seletores por seção e os estados de foco existentes sem reordenar a cascata do CSS.

```powershell
npm test
npm run typecheck
npm run build
```

Os testes verificam a coordenação entre upload, Web PKI e finalização, incluindo cancelamento, sessão expirada, certificado vencido, arquivos inválidos e bloqueio de alterações durante a assinatura. A validação criptográfica real requer o backend configurado, acesso ao Rest PKI e um certificado disponível no dispositivo.

## Erro intent:// ou wpki-lcn

O Web PKI seleciona o transporte móvel quando detecta Android/iOS no user agent. Para assinar com o certificado do computador, abra em Chrome ou Edge desktop. Se estiver usando a emulação de celular no DevTools, desative-a com Ctrl+Shift+M e recarregue a página, pois o SDK detecta a plataforma ao carregar. Instale tanto a extensão quanto o componente nativo pelo site https://get.webpkiplugin.com/. Este fluxo usa os certificados locais do computador; a integração com o aplicativo móvel exige um fluxo próprio.
