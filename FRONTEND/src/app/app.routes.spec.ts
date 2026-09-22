import { authGuard } from './core/guards/auth.guard';
import { RouterLink } from '@angular/router';
import { DashboardStub } from './features/dashboard/dashboard-stub';
import { routes } from './app.routes';

describe('application routes', () => {
  const route = (path: string) => routes.find((candidate) => candidate.path === path);

  it('keeps the landing and authentication routes public', () => {
    expect(route('')?.canActivate).toBeUndefined();
    expect(route('login')?.canActivate).toBeUndefined();
    expect(route('signup')?.canActivate).toBeUndefined();
  });

  it('protects the dashboard and complete FD route tree with the shared guard', () => {
    expect(route('dashboard')?.canActivate).toEqual([authGuard]);

    const fdRoute = route('fd');
    expect(fdRoute?.canActivate).toEqual([authGuard]);
    expect(fdRoute?.children?.map((child) => child.path)).toEqual([
      '',
      'book/:productId',
      'accounts/lookup',
      'accounts/:id',
      'not-found',
      '**',
    ]);
    expect(fdRoute?.children?.every((child) => child.canActivate === undefined)).toBeTrue();
  });

  it('routes unknown FD children to the safe not-found screen', () => {
    const fdRoute = route('fd');
    const fallback = fdRoute?.children?.find((child) => child.path === '**');

    expect(fallback?.redirectTo).toBe('not-found');
  });

  it('keeps the dashboard compatibility component wired for FD navigation', () => {
    expect(DashboardStub.ɵcmp.imports).toContain(RouterLink);
  });
});
