import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { staffGuard } from './core/guards/staff.guard';
import { adminGuard } from './core/guards/admin.guard';

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
    path: 'staff/fd-products',
    canActivate: [authGuard, staffGuard],
    loadComponent: () =>
      import('./features/staff/fd-products/fd-products').then((m) => m.FdProductsAdmin),
  },
  {
    path: 'staff/product-builder',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/staff/product-builder/product-builder').then((m) => m.ProductBuilder),
  },
  {
    path: 'staff/customers',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/staff/customers/customer-admin').then((m) => m.CustomerAdmin),
  },
  {
    path: 'staff/fd',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/staff/accounts/staff-account-dashboard').then(m => m.StaffAccountDashboard),
  },
  {
    path: 'staff/savings',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/staff/accounts/staff-account-dashboard').then(m => m.StaffAccountDashboard),
  },
  {
    path: 'staff/operations/clock',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/staff/operations/clock-dashboard').then(m => m.ClockDashboard),
  },
  {
    path: 'compliance',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/compliance/fd-compliance').then(m => m.FdCompliance),
  },
  {
    path: 'savings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/savings/savings-accounts').then(m => m.SavingsAccounts),
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
        path: 'accounts',
        loadComponent: () =>
          import('./features/fd/my-deposits/my-deposits').then((m) => m.MyDeposits),
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
