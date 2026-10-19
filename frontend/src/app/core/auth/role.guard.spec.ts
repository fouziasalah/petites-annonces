import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { fakeAuth, testProviders } from '../testing';
import { roleGuard } from './role.guard';

describe('roleGuard (FR-03)', () => {
  function executer(roles: string[]) {
    TestBed.configureTestingModule({ providers: [...testProviders(fakeAuth(roles)), provideRouter([])] });
    return TestBed.runInInjectionContext(() =>
      roleGuard('MODERATEUR')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  it('laisse passer un modérateur', () => {
    expect(executer(['MODERATEUR', 'USER'])).toBe(true);
  });

  it('redirige un simple USER vers /annonces', () => {
    const result = executer(['USER']);
    expect(result instanceof UrlTree && result.toString()).toBe('/annonces');
  });
});
