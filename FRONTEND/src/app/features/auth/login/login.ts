import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, GlassShell],
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
      password: ['', [Validators.required, Validators.minLength(8)]],
    });
  }

submit(): void {
  if (this.form.invalid) {
    this.form.markAllAsTouched();
    this.errorMessage = 'Please fill in all required fields.';
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
      this.errorMessage =
        err.error?.message ??
        err.error?.error ??
        'Invalid username or password.';
    },
  });
}
}
