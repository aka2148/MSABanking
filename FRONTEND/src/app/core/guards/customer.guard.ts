import { inject } from '@angular/core';
import { CanActivateFn,Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
export const customerGuard:CanActivateFn=()=>inject(AuthService).currentRole()==='ROLE_CUSTOMER'?true:inject(Router).createUrlTree(['/dashboard']);
