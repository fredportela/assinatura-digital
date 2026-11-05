import { inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import LacunaWebPKI from 'web-pki';
export interface Certificate {
  thumbprint: string;
  subjectName: string;
  issuerName?: string;
  validityEnd?: string | Date;
}
export class PkiError extends Error {
  constructor(
    public code: unknown,
    message: string,
    public origin?: unknown,
    public complete?: unknown,
    public cause?: unknown,
  ) {
    super(message);
    this.name = 'PkiError';
  }
}
@Injectable({ providedIn: 'root' })
export class PkiService {
  private pki = new LacunaWebPKI();
  private readonly http = inject(HttpClient);
  private readonly zone = inject(NgZone);
  private error(error: unknown): Error {
    if (error instanceof PkiError) return error;
    const value = error as {
      userMessage?: string;
      message?: string;
      code?: unknown;
      origin?: unknown;
      complete?: unknown;
    } | null;
    const message =
      value?.userMessage ||
      value?.message ||
      (typeof error === 'string' ? error : 'Falha na comunicação com o Web PKI.');
    const providerFailure =
      /tipo de provedor inv[aá]lido|invalid provider type|NTE_BAD_PROV_TYPE|0x80090014|-2146893804/i.test(
        [message, value?.message, typeof value?.complete === 'string' ? value.complete : '', value?.code].join(' '),
      );
    const guidance = providerFailure
      ? ' Verifique se selecionou o certificado correto e se a chave privada está acessível no Windows. Confira o driver/provedor do certificado ou token com o suporte responsável. Após corrigir o acesso à chave, inicie uma nova assinatura.'
      : '';
    return new PkiError(value?.code, message + guidance, value?.origin, value?.complete, error);
  }
  async initPki(): Promise<void> {
    // The SDK chooses its mobile transport from the user agent when loaded.
    // Local A1/A3 certificates require the desktop extension/native component.
    if (this.pki.isSupportedMobile) {
      throw new PkiError(
        'desktopRequired',
        'O Web PKI detectou um navegador móvel. Para usar o certificado deste computador, desative a emulação de celular no DevTools (Ctrl+Shift+M), recarregue a página e tente novamente no Chrome ou Edge desktop.',
      );
    }
    const config = await firstValueFrom(
      this.http.get<{ restPkiUrl: string }>('/api/web-pki/config').pipe(timeout(15000)),
    );
    this.pki = new LacunaWebPKI();
    return new Promise((resolve, reject) =>
      this.pki.init({
        restPkiUrl: config.restPkiUrl || 'https://pki.rest/',
        ready: () => this.zone.run(() => resolve()),
        notInstalled: () =>
          this.zone.run(() =>
            reject(
              new PkiError(
                'pkiNotInstalled',
                'Instale ou atualize a extensão Web PKI e o componente para o computador. Depois, recarregue a página e tente novamente.',
              ),
            ),
          ),
        defaultFail: (error: unknown) => this.zone.run(() => reject(this.error(error))),
      }),
    );
  }
  listCertificates(): Promise<Certificate[]> {
    return new Promise((resolve, reject) =>
      this.pki
        .listCertificates()
        .success((certs: Certificate[]) => this.zone.run(() => resolve(certs)))
        .fail((error: unknown) => this.zone.run(() => reject(this.error(error)))),
    );
  }
  signSingleToken(thumbprint: string, token: string): Promise<string> {
    return new Promise((resolve, reject) =>
      this.pki
        .signWithRestPki({ thumbprint, token })
        .success((result: string) => this.zone.run(() => resolve(result)))
        .fail((error: unknown) => this.zone.run(() => reject(this.error(error)))),
    );
  }
}
