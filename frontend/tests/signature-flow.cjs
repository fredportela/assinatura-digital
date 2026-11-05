const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const source = fs.readFileSync(path.join(__dirname, '../src/app/app.component.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true },
}).outputText;
function setup(overrides = {}) {
  const calls = [];
  class PkiService {}
  class SignatureApiService {}
  const pki = {
    initPki: async () => {},
    listCertificates: async () => [],
    signSingleToken: async (...args) => {
      calls.push(['sign', ...args]);
      return 'response-token';
    },
    ...overrides.pki,
  };
  const api = {
    start: async (file) => {
      calls.push(['upload', file.name]);
      return {
        token: 'original-token',
        expiraEm: new Date(Date.now() + 60000).toISOString(),
        nomeArquivoAssinado: 'signed.pdf',
        formato: 'PAdES',
      };
    },
    finish: async (token) => {
      calls.push(['finish', token]);
      return { size: 100 };
    },
    describe: async (error) => error.message,
    ...overrides.api,
  };
  const exported = {};
  vm.runInNewContext(compiled, {
    exports: exported,
    require: (name) => {
      if (name === '@angular/core')
        return {
          Component: () => (target) => target,
          ChangeDetectionStrategy: { Eager: 0 },
          inject: (cls) => (cls === PkiService ? pki : api),
        };
      if (name === '@angular/common' || name === '@angular/forms') return {};
      if (name.includes('pki.service')) return { PkiService, PkiError: class extends Error {} };
      if (name.includes('signature-api')) return { SignatureApiService };
      throw new Error('Unexpected import ' + name);
    },
    URL: { createObjectURL: () => 'blob:signed', revokeObjectURL: () => {} },
    Date,
    console,
  });
  const app = new exported.AppComponent();
  app.ready = true;
  app.certificates = [{ thumbprint: 'cert', subjectName: 'Titular', validityEnd: new Date(Date.now() + 60000) }];
  app.thumbprint = 'cert';
  return { app, calls };
}
async function run() {
  let { app, calls } = setup();
  app.choose({ name: 'documento.pdf', size: 100 });
  await app.sign();
  assert.equal(app.stage, 'done');
  assert.equal(app.resultUrl, 'blob:signed');
  assert.deepEqual(calls, [
    ['upload', 'documento.pdf'],
    ['sign', 'cert', 'original-token'],
    ['finish', 'original-token'],
  ]);
  ({ app, calls } = setup({
    pki: {
      signSingleToken: async () => {
        throw new Error('Cancelado pelo usuário');
      },
    },
  }));
  app.choose({ name: 'documento.docx', size: 100 });
  await app.sign();
  assert.equal(app.stage, 'idle');
  assert.match(app.error, /Cancelado/);
  assert.equal(
    calls.some((c) => c[0] === 'finish'),
    false,
  );
  ({ app, calls } = setup({ api: { start: async () => ({ token: 'expired', expiraEm: '2000-01-01T00:00:00Z' }) } }));
  app.choose({ name: 'documento.pdf', size: 100 });
  await app.sign();
  assert.equal(app.stage, 'idle');
  assert.equal(calls.length, 0);
  ({ app, calls } = setup());
  for (const file of [
    { name: 'arquivo.exe', size: 10 },
    { name: 'arquivo.pdf', size: 0 },
    { name: 'arquivo.docx', size: 21 * 1024 * 1024 },
  ]) {
    app.choose(file);
    assert.equal(app.file, null);
    assert.ok(app.error);
  }
  app.choose({ name: 'arquivo.DOCX', size: 100 });
  assert.ok(app.file);
  app.certificates[0].validityEnd = new Date(0);
  await app.sign();
  assert.equal(calls.length, 0);
  app.stage = 'signing';
  app.remove();
  assert.ok(app.file);
  return {
    passed: 6,
    scenarios: [
      'fluxo completo',
      'cancelamento sem finalizar',
      'sessão expirada',
      'arquivos inválidos',
      'certificado vencido',
      'bloqueio durante assinatura',
    ],
  };
}
module.exports = run();
if (require.main === module)
  module.exports
    .then((result) => console.log(result))
    .catch((error) => {
      console.error(error);
      process.exitCode = 1;
    });
