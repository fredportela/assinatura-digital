'use strict';

(() => {
  const form = document.getElementById('formulario');
  const fileInput = document.getElementById('arquivo');
  const certificates = document.getElementById('certificado');
  const signButton = document.getElementById('assinar');
  const refreshButton = document.getElementById('atualizar');
  const status = document.getElementById('status');
  const download = document.getElementById('download');
  let pki;
  let ready = false;
  let objectUrl;

  function busy(value) {
    fileInput.disabled = value;
    certificates.disabled = value || !ready;
    refreshButton.disabled = value || !ready;
    signButton.disabled = value || !ready || !certificates.value;
  }

  function clearDownload() {
    download.hidden = true;
    download.removeAttribute('href');
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = undefined;
  }

  function errorMessage(error) {
    return error?.userMessage || error?.message || 'Não foi possível concluir a operação.';
  }

  // Converte as operações assíncronas do Web PKI para Promises.
  function webPki(operation) {
    return new Promise((resolve, reject) => operation.success(resolve).fail(reject));
  }

  async function checked(response) {
    if (response.ok) return response;
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Falha na requisição (${response.status}).`);
  }

  async function loadCertificates() {
    busy(true);
    status.textContent = 'Buscando certificados…';
    certificates.replaceChildren(new Option('Selecione um certificado', ''));
    try {
      const list = await webPki(pki.listCertificates());
      for (const certificate of list) {
        certificates.add(new Option(certificate.subjectName || certificate.thumbprint, certificate.thumbprint));
      }
      status.textContent = list.length ? 'Selecione o certificado e o documento para assinar.'
        : 'Nenhum certificado encontrado. Conecte seu token ou instale seu certificado e atualize a lista.';
    } catch (error) {
      status.textContent = errorMessage(error);
    } finally {
      busy(false);
    }
  }

  certificates.addEventListener('change', () => busy(false));
  fileInput.addEventListener('change', clearDownload);
  refreshButton.addEventListener('click', loadCertificates);
  window.addEventListener('pagehide', clearDownload);

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const file = fileInput.files[0];
    const thumbprint = certificates.value;
    if (!file || !thumbprint || !ready) return;
    clearDownload();
    if (!/\.(pdf|docx)$/i.test(file.name) || !file.size || file.size > 20 * 1024 * 1024) {
      status.textContent = 'Selecione um PDF ou DOCX não vazio, de até 20 MB.';
      return;
    }
    busy(true);
    try {
      status.textContent = 'Enviando documento…';
      const data = new FormData();
      data.append('arquivo', file);
      const startResponse = await checked(await fetch('/api/assinaturas/iniciar', { method: 'POST', body: data }));
      const start = await startResponse.json();
      status.textContent = 'Assinando com o certificado selecionado. Confirme a operação no Web PKI.';
      await webPki(pki.signWithRestPki({ token: start.token, thumbprint }));
      status.textContent = 'Validando assinatura e preparando o download…';
      const finishResponse = await checked(await fetch('/api/assinaturas/finalizar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: start.token })
      }));
      objectUrl = URL.createObjectURL(await finishResponse.blob());
      download.href = objectUrl;
      download.download = start.nomeArquivoAssinado;
      download.hidden = false;
      status.textContent = 'Documento assinado. Baixe o arquivo abaixo.';
    } catch (error) {
      status.textContent = `${errorMessage(error)} Você pode iniciar uma nova tentativa.`;
    } finally {
      busy(false);
    }
  });

  (async () => {
    try {
      const response = await checked(await fetch('/api/web-pki/config'));
      const config = await response.json();
      if (typeof LacunaWebPKI === 'undefined') throw new Error('Não foi possível carregar o Web PKI. Verifique sua conexão.');
      pki = new LacunaWebPKI();
      pki.init({
        restPkiUrl: config.restPkiUrl,
        ready: () => { ready = true; loadCertificates(); },
        notInstalled: () => {
          status.textContent = 'Instale ou atualize o Web PKI e recarregue esta página.';
          document.getElementById('instalar').hidden = false;
        },
        defaultFail: error => { status.textContent = errorMessage(error); }
      });
    } catch (error) {
      status.textContent = errorMessage(error);
    }
  })();
})();
