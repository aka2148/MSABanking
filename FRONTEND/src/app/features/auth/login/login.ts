import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [TranslatePipe, CommonModule, ReactiveFormsModule, RouterLink, GlassShell],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  loading = false;
  errorMessage = '';
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      identifier: ['', Validators.required],
      // Sign-in must submit the stored credential as entered. Password policy
      // belongs on registration/change-password, not login-side validation.
      password: ['', Validators.required],
    });
  }

submit(): void {
  if (this.form.invalid) {
    this.form.markAllAsTouched();
    this.errorMessage = 'Enter both your username and password.';
    return;
  }
  this.loading = true;
  this.errorMessage = '';
  const { identifier, password } = this.form.getRawValue();

  this.authService.login(identifier, password).subscribe({
    next: (res) => {
      this.loading = false;
      if (!res.token || !res.username) {
        this.errorMessage = res.message ?? 'Login response did not include a valid session.';
        return;
      }
      localStorage.setItem('token', res.token);
      localStorage.setItem('username', res.username);
      localStorage.setItem('roles', JSON.stringify(res.roles));
      this.router.navigate(['/dashboard']);
    },
    error: (err) => {
      this.loading = false;
      if (err.status === 401) {
        this.errorMessage = 'Invalid username or password.';
      } else if (err.status === 0) {
        this.errorMessage = 'Cannot reach the sign-in service. Check that the local banking services are running, then try again.';
      } else if (err.status >= 500) {
        this.errorMessage = 'The sign-in service is temporarily unavailable. Please try again shortly.';
      } else {
        this.errorMessage = err.error?.message ?? err.error?.error ?? 'Could not sign in. Please try again.';
      }
    },
  });
}
}
