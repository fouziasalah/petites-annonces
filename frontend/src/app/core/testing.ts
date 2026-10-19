import { Provider } from '@angular/core';
import { vi } from 'vitest';
import { AuthService } from './auth/auth.service';
import { APP_CONFIG, AppConfig } from './config/app-config';

export const TEST_CONFIG: AppConfig = {
  keycloakUrl: 'http://localhost:8180',
  realm: 'annonces',
  clientId: 'annonces-front',
  gatewayUrl: 'http://localhost:8080',
};

/** Double de test de AuthService : rôles et token contrôlés par le test. */
export function fakeAuth(roles: string[] = ['USER'], token: string | null = 'jeton-test') {
  return {
    getAccessToken: vi.fn(() => token),
    hasRole: vi.fn((role: string) => roles.includes(role)),
    login: vi.fn(),
    logout: vi.fn(),
  };
}

export function testProviders(auth = fakeAuth()): Provider[] {
  return [
    { provide: APP_CONFIG, useValue: TEST_CONFIG },
    { provide: AuthService, useValue: auth },
  ];
}
