import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Product administration is for authenticated BANK_OFFICER/ADMIN users only. */
export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);

  return ['ROLE_BANK_OFFICER', 'ROLE_ADMIN'].includes(auth.currentRole() ?? '')
    ? true
    : router.createUrlTree(['/fd']);
};
