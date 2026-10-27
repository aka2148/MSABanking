import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ReactiveFormsModule, RouterLink, GlassShell],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  loading = false;
  errorMessage = '';
  challengeId = '';
  emailHint = '';
  otp = '';
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
  if (this.challengeId) {
    this.verifyOtp();
    return;
  }
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
      if (res.mfaRequired && res.mfaToken) {
        this.challengeId = res.mfaToken;
        this.emailHint = res.email ?? 'your registered email';
        return;
      }
      this.finishLogin(res);
    },
    error: (err) => this.handleLoginError(err),
  });
}

  verifyOtp(): void {
    if (!/^\d{6}$/.test(this.otp)) {
      this.errorMessage = 'Enter the six digit code sent to your registered email.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.authService.verifyLoginOtp(this.challengeId, this.otp).subscribe({
      next: (res) => { this.loading = false; this.finishLogin(res); },
      error: (err) => { this.loading = false; this.otp = ''; this.handleLoginError(err); },
    });
  }

  resendOtp(): void {
    this.challengeId = '';
    this.emailHint = '';
    this.otp = '';
    this.submit();
  }

  private finishLogin(res: import('../../../core/models/auth.models').LoginResponse): void {
      if (!res.token || !res.username) {
        this.errorMessage = res.message ?? 'Login response did not include a valid session.';
        return;
      }
      localStorage.setItem('token', res.token);
      localStorage.setItem('username', res.username);
      localStorage.setItem('roles', JSON.stringify(res.roles));
      this.router.navigate(['/dashboard']);
  }

  private handleLoginError(err: { status: number; error?: { message?: string; error?: string } }): void {
      this.loading = false;
      if (err.status === 401) {
        this.errorMessage = this.challengeId ? 'The code is invalid or expired. Request a new sign-in code.' : 'Invalid username or password.';
      } else if (err.status === 0) {
        this.errorMessage = 'Cannot reach the sign-in service. Check that the local banking services are running, then try again.';
      } else if (err.status >= 500) {
        this.errorMessage = 'The sign-in service could not send or verify your code. Please try again shortly.';
      } else {
        this.errorMessage = err.error?.message ?? err.error?.error ?? 'Could not sign in. Please try again.';
      }
  }
}
