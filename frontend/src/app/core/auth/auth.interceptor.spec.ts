import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { fakeAuth, testProviders } from '../testing';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor (FR-01)', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  function setup(token: string | null = 'jeton-test'): void {
    TestBed.configureTestingModule({
      providers: [
        ...testProviders(fakeAuth(['USER'], token)),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  }

  afterEach(() => backend.verify());

  it('ajoute le Bearer aux appels vers la gateway', () => {
    setup();
    http.get('http://localhost:8080/api/annonces').subscribe();
    expect(backend.expectOne('http://localhost:8080/api/annonces').request.headers.get('Authorization')).toBe(
      'Bearer jeton-test',
    );
  });

  it("n'envoie jamais le token ailleurs (Keycloak, autre hôte, hôte piège)", () => {
    setup();
    const urls = [
      'http://localhost:8180/realms/annonces/protocol/openid-connect/token',
      'https://exemple.com/api',
      'http://localhost:8080.evil.com/api', // même préfixe sans « / » : doit être refusé
    ];
    for (const url of urls) {
      http.get(url).subscribe();
      expect(backend.expectOne(url).request.headers.has('Authorization')).toBe(false);
    }
  });

  it("n'ajoute rien sans token", () => {
    setup(null);
    http.get('http://localhost:8080/api/me').subscribe();
    expect(backend.expectOne('http://localhost:8080/api/me').request.headers.has('Authorization')).toBe(false);
  });
});
