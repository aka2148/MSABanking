import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'signup',
    loadComponent: () => import('./features/auth/signup/signup').then((m) => m.Signup),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard-stub').then((m) => m.DashboardStub),
  },
  {
    path: 'fd',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/fd/fd-home/fd-home').then((m) => m.FdHome),
      },
      {
        path: 'book/:productId',
        loadComponent: () => import('./features/fd/booking/fd-booking').then((m) => m.FdBooking),
      },
      {
        path: 'accounts/lookup',
        loadComponent: () =>
          import('./features/fd/account-lookup/fd-account-lookup').then((m) => m.FdAccountLookup),
      },
      {
        path: 'accounts/:id',
        loadComponent: () =>
          import('./features/fd/account-detail/fd-account-detail').then((m) => m.FdAccountDetail),
      },
      {
        path: 'not-found',
        loadComponent: () =>
          import('./features/fd/fd-not-found/fd-not-found').then((m) => m.FdNotFound),
      },
      { path: '**', redirectTo: 'not-found' },
    ],
  },
  { path: '**', redirectTo: '' },
];
