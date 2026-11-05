import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { AppComponent } from './app/app.component';
registerLocaleData(localePt);
bootstrapApplication(AppComponent, {
  providers: [provideZoneChangeDetection(), provideHttpClient(withXhr()), { provide: LOCALE_ID, useValue: 'pt-BR' }],
}).catch(console.error);
