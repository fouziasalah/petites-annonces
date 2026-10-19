import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from '../notification/notification.service';
import { fakeAuth, testProviders } from '../testing';
import { httpErrorInterceptor } from './http-error.interceptor';

describe('httpErrorInterceptor', () => {
  const url = 'http://localhost:8080/api/annonces';
  let auth: ReturnType<typeof fakeAuth>;
  let http: HttpClient;
  let backend: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    auth = fakeAuth();
    TestBed.configureTestingModule({
      providers: [
        ...testProviders(auth),
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  function repondre(status: number, headers: Record<string, string> = {}): void {
    http.get(url).subscribe({ error: () => undefined });
    backend.expectOne(url).flush(null, { status, statusText: 'Erreur', headers });
  }

  it('401 → retour au login', () => {
    repondre(401);
    expect(auth.login).toHaveBeenCalled();
  });

  it('403 → message « accès refusé » sans déconnexion (FR-06)', () => {
    repondre(403);
    expect(notifications.notifications()[0].message).toContain('Accès refusé');
    expect(auth.login).not.toHaveBeenCalled();
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('429 → message avec le délai lu dans Retry-After (FR-05)', () => {
    repondre(429, { 'Retry-After': '42' });
    expect(notifications.notifications()[0].message).toContain('42 secondes');
  });
});
