import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { APP_CONFIG, AppConfig } from './core/config/app-config';
import { httpErrorInterceptor } from './core/http/http-error.interceptor';

// Dates et montants au format français (250,00 €).
registerLocaleData(localeFr);

export function appConfig(config: AppConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      { provide: LOCALE_ID, useValue: 'fr' },
      { provide: APP_CONFIG, useValue: config },
      provideRouter(routes, withComponentInputBinding()),
      provideHttpClient(withInterceptors([authInterceptor, httpErrorInterceptor])),
      // Sans configuration « resourceServer » : l'intercepteur intégré de la librairie
      // n'ajoute aucun token, c'est authInterceptor qui décide (FR-01).
      provideOAuthClient(),
      // L'application ne s'affiche qu'une fois l'utilisateur authentifié.
      provideAppInitializer(() => inject(AuthService).init()),
    ],
  };
}
