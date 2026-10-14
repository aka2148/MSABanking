import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GlassShell } from '../../../shared/components/glass-shell/glass-shell';

@Component({
  selector: 'app-fd-account-lookup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, GlassShell],
  templateUrl: './fd-account-lookup.html',
  styleUrl: './fd-account-lookup.scss',
})
export class FdAccountLookup {
  loading = false;
  errorMessage = '';
  form: FormGroup<{ accountId: FormControl<string> }>;

  constructor(
    private readonly fb: FormBuilder,
    private readonly router: Router
  ) {
    this.form = this.fb.nonNullable.group({
      accountId: ['', [Validators.required, Validators.pattern(/^[1-9][0-9]*$/)]],
    });
  }

  openAccount(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Enter the positive numeric reference from your FD booking.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    const accountId = Number(this.form.controls.accountId.value);
    this.router.navigate(['/fd/accounts', accountId]).finally(() => {
      this.loading = false;
    });
  }
}
