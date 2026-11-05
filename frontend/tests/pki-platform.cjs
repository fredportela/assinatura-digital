const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const source = fs.readFileSync(path.join(__dirname, '../src/app/service/lacuna/pki.service.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
    experimentalDecorators: true,
    esModuleInterop: true,
  },
}).outputText;
function setup(mobile, missing = false) {
  const calls = [];
  class HttpClient {}
  class NgZone {}
  class WebPki {
    isSupportedMobile = mobile;
    init(options) {
      calls.push('init');
      missing ? options.notInstalled() : options.ready();
    }
  }
  const exported = {};
  vm.runInNewContext(compiled, {
    exports: exported,
    require: (name) => {
      if (name === '@angular/core')
        return {
          Injectable: () => (target) => target,
          NgZone,
          inject: (cls) =>
            cls === HttpClient
              ? {
                  get: () => {
                    calls.push('config');
                    return { pipe: () => Promise.resolve({ restPkiUrl: 'https://pki.rest/' }) };
                  },
                }
              : { run: (fn) => fn() },
        };
      if (name === '@angular/common/http') return { HttpClient };
      if (name === 'rxjs') return { firstValueFrom: (value) => value, timeout: () => {} };
      if (name === 'web-pki') return WebPki;
      throw new Error(name);
    },
  });
  return { service: new exported.PkiService(), calls };
}
async function run() {
  let { service, calls } = setup(true);
  await assert.rejects(
    service.initPki(),
    (error) => error.code === 'desktopRequired' && /Ctrl\+Shift\+M/.test(error.message),
  );
  assert.deepEqual(calls, []);
  ({ service, calls } = setup(false));
  await service.initPki();
  assert.deepEqual(calls, ['config', 'init']);
  ({ service, calls } = setup(false, true));
  await assert.rejects(service.initPki(), (error) => error.code === 'pkiNotInstalled');
  return {
    passed: 3,
    scenarios: [
      'mobile não inicia protocolo externo',
      'desktop inicializa normalmente',
      'componente ausente informa instalação',
    ],
  };
}
module.exports = run();
if (require.main === module)
  module.exports.then(console.log).catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
