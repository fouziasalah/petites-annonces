import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { loadAppConfig } from './app/core/config/app-config';

// La configuration (URLs Keycloak et gateway) est lue AVANT de démarrer Angular.
loadAppConfig()
  .then((config) => bootstrapApplication(App, appConfig(config)))
  .catch((err: unknown) => {
    console.error(err);
    document.body.textContent = 'Application indisponible : configuration introuvable.';
  });
