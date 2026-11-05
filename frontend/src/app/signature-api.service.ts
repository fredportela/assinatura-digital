import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
export interface SignatureSession {
  token: string;
  nomeArquivoAssinado: string;
  formato: string;
  expiraEm: string;
}
@Injectable({ providedIn: 'root' })
export class SignatureApiService {
  private readonly http = inject(HttpClient);
  start(file: File, onProgress: (percent: number) => void): Promise<SignatureSession> {
    const form = new FormData();
    form.append('arquivo', file, file.name);
    return new Promise((resolve, reject) => {
      this.http
        .post<SignatureSession>('/api/assinaturas/iniciar', form, { observe: 'events', reportProgress: true })
        .pipe(timeout(120000))
        .subscribe({
          next: (event) => {
            if (event.type === HttpEventType.UploadProgress && event.total)
              onProgress(Math.round((event.loaded * 100) / event.total));
            if (event.type === HttpEventType.Response) {
              if (event.body) resolve(event.body);
              else reject(new Error('Resposta vazia ao enviar o documento.'));
            }
          },
          error: reject,
        });
    });
  }
  finish(token: string) {
    return firstValueFrom(
      this.http.post('/api/assinaturas/finalizar', { token }, { responseType: 'blob' }).pipe(timeout(120000)),
    );
  }
  async describe(error: unknown): Promise<string> {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0)
        return 'Não foi possível acessar a API. Verifique a conexão e se o backend está em execução.';
      let body = error.error;
      if (body instanceof Blob) {
        try {
          body = JSON.parse(await body.text());
        } catch {
          body = null;
        }
      }
      return (
        body?.detail ||
        (error.status === 413
          ? 'O arquivo excede o limite de 20 MB.'
          : 'Não foi possível processar o documento. Inicie novamente a assinatura.')
      );
    }
    return error instanceof Error ? error.message : 'Não foi possível concluir a operação.';
  }
}
