import { Component, inject, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Certificate, PkiError, PkiService } from './service/lacuna/pki.service';
import { SignatureApiService } from './signature-api.service';
type Stage = 'idle' | 'uploading' | 'signing' | 'finishing' | 'done';
@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './app.component.html',
})
export class AppComponent implements OnDestroy {
  private readonly pki = inject(PkiService);
  private readonly api = inject(SignatureApiService);
  certificates: Certificate[] = [];
  thumbprint = '';
  ready = false;
  connecting = false;
  missingPlugin = false;
  error = '';
  notice = '';
  file: File | null = null;
  stage: Stage = 'idle';
  uploadProgress = 0;
  dragging = false;
  resultUrl = '';
  resultName = '';
  resultFormat = '';
  get busy() {
    return this.stage === 'uploading' || this.stage === 'signing' || this.stage === 'finishing';
  }
  get selected() {
    return this.certificates.find((c) => c.thumbprint === this.thumbprint);
  }
  get canSign() {
    return (
      !!this.file && this.ready && !!this.selected && !this.expired(this.selected) && !this.busy && !this.connecting
    );
  }
  get isPdf() {
    return this.file?.name.toLowerCase().endsWith('.pdf') ?? false;
  }
  expired(cert: Certificate): boolean {
    return !!cert.validityEnd && new Date(cert.validityEnd).getTime() <= Date.now();
  }
  async connect() {
    if (this.connecting || this.busy) return;
    this.connecting = true;
    this.error = '';
    this.notice = '';
    this.ready = false;
    this.missingPlugin = false;
    this.certificates = [];
    this.thumbprint = '';
    try {
      await this.pki.initPki();
      this.certificates = await this.pki.listCertificates();
      this.ready = true;
      this.thumbprint = this.certificates.find((c) => !this.expired(c))?.thumbprint || '';
      if (!this.certificates.length)
        this.notice = 'Nenhum certificado encontrado. Conecte seu token ou instale seu certificado e atualize a lista.';
      else if (this.certificates.every((c) => this.expired(c)))
        this.notice = 'Os certificados encontrados estão vencidos. Instale um certificado válido.';
    } catch (error) {
      this.error = await this.api.describe(error);
      this.missingPlugin = error instanceof PkiError && error.code === 'pkiNotInstalled';
    } finally {
      this.connecting = false;
    }
  }
  onFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.choose(file);
    input.value = '';
  }
  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragging = false;
    if (this.busy) return;
    if (event.dataTransfer?.files.length !== 1) {
      this.error = 'Selecione um único documento por assinatura.';
      return;
    }
    this.choose(event.dataTransfer.files[0]);
  }
  choose(file: File) {
    if (this.busy) return;
    this.error = '';
    this.notice = '';
    if (!/\.(pdf|docx)$/i.test(file.name)) {
      this.error = 'Escolha um arquivo PDF ou DOCX.';
      return;
    }
    if (!file.size || file.size > 20 * 1024 * 1024) {
      this.error = 'O arquivo precisa ter conteúdo e no máximo 20 MB.';
      return;
    }
    this.clearResult();
    this.file = file;
    this.stage = 'idle';
  }
  remove() {
    if (this.busy) return;
    this.file = null;
    this.stage = 'idle';
    this.clearResult();
    this.error = '';
  }
  private clearResult() {
    if (this.resultUrl) URL.revokeObjectURL(this.resultUrl);
    this.resultUrl = '';
    this.resultName = '';
    this.resultFormat = '';
  }
  async sign() {
    if (!this.canSign || !this.file) return;
    const file = this.file;
    const thumbprint = this.thumbprint;
    this.error = '';
    this.notice = '';
    this.clearResult();
    this.stage = 'uploading';
    this.uploadProgress = 0;
    try {
      const session = await this.api.start(file, (percent) => (this.uploadProgress = percent));
      if (
        !session.token ||
        !Number.isFinite(Date.parse(session.expiraEm)) ||
        Date.parse(session.expiraEm) <= Date.now()
      )
        throw new Error('Sessão inválida ou expirada. Inicie novamente a assinatura.');
      this.stage = 'signing';
      await this.pki.signSingleToken(thumbprint, session.token);
      this.stage = 'finishing';
      const result = await this.api.finish(session.token);
      if (!result.size) throw new Error('O servidor não retornou o arquivo assinado. Inicie novamente a assinatura.');
      this.resultName = session.nomeArquivoAssinado.replace(/[\\/]/g, '_');
      this.resultFormat = session.formato;
      this.resultUrl = URL.createObjectURL(result);
      this.stage = 'done';
    } catch (error) {
      this.stage = 'idle';
      this.error = await this.api.describe(error);
    }
  }
  ngOnDestroy() {
    this.clearResult();
  }
}
