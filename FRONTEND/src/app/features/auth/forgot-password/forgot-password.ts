import { AfterViewChecked, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';
import { passwordPolicyIssues } from '../../../shared/password-policy';

type RecoveryStep = 'identifier' | 'code' | 'password' | 'done';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe, GlassShell],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword implements AfterViewChecked, OnDestroy {
  @ViewChild('identifierInput') private identifierInput?: ElementRef<HTMLInputElement>;
  @ViewChild('codeInput') private codeInput?: ElementRef<HTMLInputElement>;
  @ViewChild('newPasswordInput') private newPasswordInput?: ElementRef<HTMLInputElement>;

  readonly identifierForm: FormGroup<{ identifier: FormControl<string> }>;
  readonly codeForm: FormGroup<{ code: FormControl<string> }>;
  readonly passwordForm: FormGroup<{ newPassword: FormControl<string>; confirmPassword: FormControl<string> }>;
  readonly stepLabels: Record<RecoveryStep, string> = {
    identifier: 'Forgot password',
    code: 'Enter code',
    password: 'Choose a new password',
    done: 'Choose a new password',
  };

  step: RecoveryStep = 'identifier';
  busy = false;
  errorMessage = '';
  serverPasswordError = '';
  neutralMessage = '';
  resetToken = '';
  cooldownSeconds = 0;
  private cooldownTimer: ReturnType<typeof setInterval> | null = null;
  private focusPending = true;

  constructor(private readonly fb: FormBuilder, private readonly auth: AuthService, private readonly router: Router) {
    this.identifierForm = this.fb.nonNullable.group({
      identifier: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/.*\S.*/)]],
    });
    this.codeForm = this.fb.nonNullable.group({
      code: ['', [Validators.required, Validators.pattern(/^[0-9]{6}$/)]],
    });
    this.passwordForm = this.fb.nonNullable.group({
      newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(100), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/)]],
      confirmPassword: ['', Validators.required],
    });
  }

  ngAfterViewChecked(): void {
    if (!this.focusPending) return;
    const target = this.step === 'identifier' ? this.identifierInput
      : this.step === 'code' ? this.codeInput
        : this.step === 'password' ? this.newPasswordInput : undefined;
    if (target) {
      target.nativeElement.focus();
      this.focusPending = false;
    } else if (this.step === 'done') {
      this.focusPending = false;
    }
  }

  ngOnDestroy(): void {
    this.stopCooldown();
    this.clearSensitiveFields();
  }

  get identifier(): string {
    return this.identifierForm.controls.identifier.value.trim();
  }

  get passwordIssues(): string[] {
    const password = this.passwordForm.controls.newPassword.value;
    return password ? passwordPolicyIssues(password, this.identifier) : [];
  }

  get passwordsMismatch(): boolean {
    const { newPassword, confirmPassword } = this.passwordForm.getRawValue();
    return !!confirmPassword && newPassword !== confirmPassword;
  }

  sendCode(): void {
    const identifierControl = this.identifierForm.controls.identifier;
    identifierControl.setValue(identifierControl.value.trim());
    if (identifierControl.invalid) {
      identifierControl.markAsTouched();
      return;
    }
    this.busy = true;
    this.errorMessage = '';
    this.auth.forgotPassword(this.identifier).subscribe({
      next: () => {
        this.busy = false;
        this.codeForm.reset({ code: '' });
        this.neutralMessage = 'If an account matches, we have sent a 6-digit code to its registered email.';
        this.serverPasswordError = '';
        this.resetToken = '';
        this.setStep('code');
        this.startCooldown();
      },
      error: error => {
        this.busy = false;
        this.errorMessage = this.friendlyError(error, 'Could not send a code. Please try again later.');
      },
    });
  }

  verifyCode(): void {
    const codeControl = this.codeForm.controls.code;
    if (codeControl.invalid) {
      codeControl.markAsTouched();
      return;
    }
    this.busy = true;
    this.errorMessage = '';
    this.auth.verifyPasswordResetCode(this.identifier, codeControl.value).subscribe({
      next: result => {
        this.busy = false;
        this.resetToken = result.resetToken;
        this.codeForm.reset({ code: '' });
        this.neutralMessage = '';
        this.serverPasswordError = '';
        this.passwordForm.reset({ newPassword: '', confirmPassword: '' });
        this.setStep('password');
      },
      error: error => {
        this.busy = false;
        this.errorMessage = this.friendlyError(error, 'The code is incorrect or has expired.');
      },
    });
  }

  resendCode(): void {
    if (!this.busy && this.cooldownSeconds === 0) this.sendCode();
  }

  backToIdentifier(): void {
    this.errorMessage = '';
    this.neutralMessage = '';
    this.serverPasswordError = '';
    this.resetToken = '';
    this.codeForm.reset({ code: '' });
    this.passwordForm.reset({ newPassword: '', confirmPassword: '' });
    this.setStep('identifier');
  }

  resetPassword(): void {
    this.passwordForm.markAllAsTouched();
    this.serverPasswordError = '';
    if (this.passwordForm.invalid || this.passwordsMismatch || this.passwordIssues.length > 0 || !this.resetToken) return;

    this.busy = true;
    this.errorMessage = '';
    const newPassword = this.passwordForm.controls.newPassword.value;
    this.auth.resetPassword(this.resetToken, newPassword).subscribe({
      next: response => {
        this.busy = false;
        this.clearSensitiveFields();
        if (response?.token) {
          this.setStep('done');
          void this.router.navigate(['/dashboard']);
          return;
        }
        this.setStep('done');
        void this.router.navigate(['/login'], { state: { passwordResetSuccess: true } });
      },
      error: error => {
        this.busy = false;
        const serverMessage = this.serverMessage(error);
        if (serverMessage === 'The reset token is invalid or has expired.') {
          this.backToIdentifier();
          this.errorMessage = 'Your reset session expired. Start again to request a new code.';
        } else if (error instanceof HttpErrorResponse && error.status === 429) {
          this.errorMessage = 'Too many attempts. Please wait a few minutes.';
        } else if (error instanceof HttpErrorResponse && error.status === 503) {
          this.errorMessage = 'Password reset is not available right now. Please try again later.';
        } else {
          this.serverPasswordError = serverMessage || 'The request could not be completed. Please try again later.';
        }
      },
    });
  }

  private setStep(step: RecoveryStep): void {
    this.step = step;
    this.errorMessage = '';
    this.focusPending = true;
  }

  private startCooldown(): void {
    this.stopCooldown();
    this.cooldownSeconds = 60;
    this.cooldownTimer = setInterval(() => {
      this.cooldownSeconds = Math.max(0, this.cooldownSeconds - 1);
      if (this.cooldownSeconds === 0) this.stopCooldown();
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.cooldownTimer !== null) clearInterval(this.cooldownTimer);
    this.cooldownTimer = null;
  }

  private clearSensitiveFields(): void {
    this.codeForm.reset({ code: '' });
    this.passwordForm.reset({ newPassword: '', confirmPassword: '' });
    this.resetToken = '';
  }

  private serverMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return '';
    const body = error.error as { message?: unknown; error?: unknown } | null;
    const message = body?.message ?? body?.error;
    return typeof message === 'string' ? message : '';
  }

  private friendlyError(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 429) return 'Too many attempts. Please wait a few minutes.';
      if (error.status === 503) return 'Password reset is not available right now. Please try again later.';
    }
    return this.serverMessage(error) || fallback;
  }
}
