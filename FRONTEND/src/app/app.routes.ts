import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { staffGuard } from './core/guards/staff.guard';
import { adminGuard } from './core/guards/admin.guard';
import { customerGuard } from './core/guards/customer.guard';

export const routes: Routes = [
  { path: 'kyc', canActivate: [authGuard], canDeactivate: [(component: { beforeLeave: () => Promise<boolean> }) => component.beforeLeave()], loadComponent: () => import('./features/kyc/kyc').then(m => m.Kyc) },
  {
    path: '',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then(m => m.ForgotPassword),
  },
  {
    path: 'signup',
    loadComponent: () => import('./features/auth/signup/signup').then((m) => m.Signup),
  },
  {
    path: 'calculator',
    loadComponent: () => import('./features/fd/calculator/fd-calculator').then((m) => m.FdCalculator),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard-stub').then((m) => m.DashboardStub),
  },
  {
    path: 'current-accounts', pathMatch: 'full', canActivate: [authGuard, customerGuard],
    loadComponent: () => import('./features/current-accounts/current-accounts').then(m => m.CurrentAccountsPage),
  },
  {
    path: 'current-accounts/open/:productId', canActivate: [authGuard, customerGuard],
    loadComponent: () => import('./features/current-accounts/current-accounts').then(m => m.CurrentAccountsPage),
  },
  {
    path: 'current-accounts/accounts', canActivate: [authGuard, customerGuard],
    loadComponent: () => import('./features/current-accounts/current-accounts').then(m => m.CurrentAccountsPage),
  },
  {
    path: 'reports',
    canActivate: [authGuard, customerGuard],
    loadComponent: () => import('./features/reports/reports').then((m) => m.Reports),
  },
  {
    path: 'staff/reports',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/reports/reports').then((m) => m.Reports),
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
    path: 'staff/kyc',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./features/staff/kyc/kyc-review').then(m => m.KycReview),
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
    path: 'staff/ledger',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/staff/ledger/ledger-admin').then(m => m.LedgerAdmin),
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
  { path: 'mpin', canActivate: [authGuard, customerGuard], loadComponent: () => import('./features/auth/mpin/mpin-page').then(m => m.MpinPage) },
  { path: 'loans', canActivate: [authGuard], data: { mode: 'catalog' }, loadComponent: () => import('./features/loans/loan-page').then(m => m.LoanPage) },
  { path: 'loans/:id', canActivate: [authGuard], data: { mode: 'product' }, loadComponent: () => import('./features/loans/loan-page').then(m => m.LoanPage) },
  { path: 'applications', canActivate: [authGuard], data: { mode: 'applications' }, loadComponent: () => import('./features/loans/loan-page').then(m => m.LoanPage) },
  { path: 'applications/:id', canActivate: [authGuard], data: { mode: 'detail' }, loadComponent: () => import('./features/loans/loan-page').then(m => m.LoanPage) },
  { path: 'forex', canActivate: [authGuard], data: { mode: 'catalog' }, loadComponent: () => import('./features/forex/forex-page').then(m => m.ForexPage) },
  { path: 'forex/requests', canActivate: [authGuard], data: { mode: 'requests' }, loadComponent: () => import('./features/forex/forex-page').then(m => m.ForexPage) },
  { path: 'forex/requests/:id', canActivate: [authGuard], data: { mode: 'detail' }, loadComponent: () => import('./features/forex/forex-page').then(m => m.ForexPage) },
  { path: 'forex/:id', canActivate: [authGuard], data: { mode: 'product' }, loadComponent: () => import('./features/forex/forex-page').then(m => m.ForexPage) },
  { path: 'staff/loans', canActivate: [authGuard, adminGuard], loadComponent: () => import('./features/loans/loan-admin').then(m => m.LoanAdmin) },
  { path: 'staff/loans/:id', canActivate: [authGuard, adminGuard], loadComponent: () => import('./features/loans/loan-admin').then(m => m.LoanAdmin) },
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
