import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LANGUAGES } from '../../../core/i18n/language';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component, inject, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ReactiveFormsModule,
  FormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import {
  trigger,
  transition,
  style,
  animate,
} from '@angular/animations';

import { AuthService } from '../../../core/services/auth.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';
import {
  GlassSelect,
  GlassSelectOption,
} from '../../../shared/components/glass-select/glass-select';
import { PreferredCurrency } from '../../../core/models/auth.models';
import { INDIA_LOCATIONS, INDIA_STATES } from './india-location-data';
import { passwordPolicyIssues } from '../../../shared/password-policy';

const CURRENCY_FOCAL_POINTS: Record<
  PreferredCurrency,
  { x: number; y: number }
> = {
  INR: { x: 71.9, y: 42.0 }, // India
  USD: { x: 22.8, y: 31.5 }, // USA
  JPY: { x: 88.8, y: 33.5 }, // Japan
  KWD: { x: 63.3, y: 37.4 }, // Kuwait
};

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [
    TranslatePipe,
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    WorkspaceShell,
    GlassSelect,
  ],
  templateUrl: './signup.html',
  styleUrl: './signup.scss',
  animations: [
    trigger('stepTransition', [
      transition(':increment', [
        style({
          transform: 'translateX(8px)',
          opacity: 0,
        }),
        animate(
          '180ms ease-out',
          style({
            transform: 'translateX(0)',
            opacity: 1,
          })
        ),
      ]),
      transition(':decrement', [
        style({
          transform: 'translateX(-8px)',
          opacity: 0,
        }),
        animate(
          '180ms ease-out',
          style({
            transform: 'translateX(0)',
            opacity: 1,
          })
        ),
      ]),
    ]),
  ],
})
export class Signup {
  private readonly translations = inject(TranslationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly reducedMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  loading = false;
  sendingEmailCode = false;
  verifyingEmailCode = false;
  emailCodeSent = false;
  emailVerified = false;
  emailCode = '';
  emailStatus = '';
  emailError = '';
  errorMessage = '';
  currentStep = 0;

  readonly stepKeys = ['personal', 'account'];

  readonly stepLabels = [
    'Basic details',
    'Account setup',
  ];

  languageOptions: GlassSelectOption[] = [
    ...LANGUAGES.map(language => ({ label: language.name, value: language.profileName })),
    { label: 'தமிழ்', value: 'Tamil' },
  ];

  currencyOptions: GlassSelectOption[] = [
    { label: 'INR - Indian Rupee', value: 'INR' },
    { label: 'USD - US Dollar', value: 'USD' },
    { label: 'JPY - Japanese Yen', value: 'JPY' },
    { label: 'KWD - Kuwaiti Dinar', value: 'KWD' },
  ];

  legalEntityOptions: GlassSelectOption[] = [
    { label: 'Individual', value: 'INDIVIDUAL' },
    { label: 'Joint', value: 'JOINT' },
    { label: 'Corporate', value: 'CORPORATE' },
  ];

  // Placeholder branch list — no live branches endpoint yet.
  // Replace with a real API call once the branches table is populated/exposed.
  homeBranchOptions: GlassSelectOption[] = [
    {
      label: 'Mumbai Main Branch (MUM001)',
      value: 'MUM001',
    },
    {
      label: 'Bengaluru MG Road (BLR001)',
      value: 'BLR001',
    },
    {
      label: 'Delhi Connaught Place (DEL001)',
      value: 'DEL001',
    },
    {
      label: 'Chennai Anna Salai (CHN001)',
      value: 'CHN001',
    },
    {
      label: 'Manipal Campus Branch (MPL001)',
      value: 'MPL001',
    },
  ];

  readonly stateOptions: GlassSelectOption[] = INDIA_STATES.map(state => ({ label: state, value: state }));
  cityOptions: GlassSelectOption[] = [];
  postalCodeOptions: GlassSelectOption[] = [];

  focalX = CURRENCY_FOCAL_POINTS['INR'].x;
  focalY = CURRENCY_FOCAL_POINTS['INR'].y;

  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      personal: this.fb.group({
        fullName: ['', Validators.required],
        preferredLanguage: [LANGUAGES.find(language => language.code === this.translations.language())!.profileName, Validators.required],
      }),

      account: this.fb.group({
        legalEntityType: ['INDIVIDUAL', Validators.required],
        homeBranchId: ['', Validators.required],

        username: [
          '',
          [
            Validators.required,
            Validators.minLength(4),
            Validators.maxLength(30),
            Validators.pattern(/^[A-Za-z0-9._-]+$/),
          ],
        ],

        mobileNumber: [
          '',
          [
            Validators.required,
            Validators.pattern(/^[0-9]{10}$/),
          ],
        ],

        email: [
          '',
          [
            Validators.required,
            Validators.email,
          ],
        ],

        addressLine: ['', Validators.required],

        state: ['', Validators.required],

        city: ['', Validators.required],

        postalCode: [
          '',
          [
            Validators.required,
            Validators.pattern(/^[0-9]{6}$/),
          ],
        ],

        password: [
          '',
          [
            Validators.required,
            Validators.minLength(12),
            Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/),
          ],
        ],

        confirmPassword: ['', Validators.required],

        preferredCurrency: [
          'INR' as PreferredCurrency,
          Validators.required,
        ],
      }),
    });

    this.personalGroup.get('preferredLanguage')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe((name: string) => {
        const language = LANGUAGES.find(option => option.profileName === name);
        if (language) this.translations.setLanguage(language.code);
      });

    this.accountGroup.get('email')!.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.emailVerified = false;
      this.emailCodeSent = false;
      this.emailCode = '';
      this.emailStatus = '';
      this.emailError = '';
    });
    this.accountGroup.get('state')!.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(state => {
      const cities = Object.keys(INDIA_LOCATIONS[state] ?? {}).sort((a, b) => a.localeCompare(b));
      this.cityOptions = cities.map(city => ({ label: city, value: city }));
      this.accountGroup.patchValue({ city: '', postalCode: '' }, { emitEvent: false });
      this.postalCodeOptions = [];
    });
    this.accountGroup.get('city')!.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(city => {
      const state = this.accountGroup.get('state')!.value;
      const postalCodes = [...(INDIA_LOCATIONS[state]?.[city] ?? [])].sort((a, b) => a.localeCompare(b));
      this.postalCodeOptions = postalCodes.map(code => ({ label: code, value: code }));
      this.accountGroup.patchValue({ postalCode: '' }, { emitEvent: false });
    });

    /*
     * Currency background focal point
     */
    this.accountGroup
      .get('preferredCurrency')!
      .valueChanges.subscribe(
        (currency: PreferredCurrency) => {
          const point = CURRENCY_FOCAL_POINTS[currency];

          if (point) {
            this.focalX = point.x;
            this.focalY = point.y;
          }
        }
      );
  }

  get personalGroup(): FormGroup {
    return this.form.get('personal') as FormGroup;
  }

  get accountGroup(): FormGroup {
    return this.form.get('account') as FormGroup;
  }

  get currentGroup(): FormGroup {
    return this.form.get(
      this.stepKeys[this.currentStep]
    ) as FormGroup;
  }

  get isLastStep(): boolean {
    return this.currentStep === this.stepKeys.length - 1;
  }

  get passwordsMismatch(): boolean {
    const {
      password,
      confirmPassword,
    } = this.accountGroup.getRawValue();

    return !!confirmPassword && password !== confirmPassword;
  }

  get passwordIssues(): string[] {
    const password = String(this.accountGroup.get('password')?.value ?? '');
    const username = String(this.accountGroup.get('username')?.value ?? '');
    return passwordPolicyIssues(password, username);
  }

  sendEmailCode(): void {
    const emailControl = this.accountGroup.get('email')!;
    if (emailControl.invalid) { emailControl.markAsTouched(); return; }
    this.sendingEmailCode = true;
    this.emailStatus = '';
    this.emailError = '';
    this.authService.sendEmailVerificationCode(String(emailControl.value).trim()).subscribe({
      next: response => { this.sendingEmailCode = false; this.emailCodeSent = true; this.emailStatus = response.message; },
      error: error => { this.sendingEmailCode = false; this.emailError = this.apiError(error, 'Could not send a verification code.'); },
    });
  }

  verifyEmailCode(): void {
    const email = String(this.accountGroup.get('email')?.value ?? '').trim();
    if (!/^\d{6}$/.test(this.emailCode)) { this.emailError = 'Enter the 6-digit code from your email.'; return; }
    this.verifyingEmailCode = true;
    this.emailStatus = '';
    this.emailError = '';
    this.authService.verifyEmailCode(email, this.emailCode).subscribe({
      next: response => { this.verifyingEmailCode = false; this.emailVerified = true; this.emailStatus = response.message; },
      error: error => { this.verifyingEmailCode = false; this.emailError = this.apiError(error, 'That verification code is not valid.'); },
    });
  }

  showError(
    group: FormGroup,
    controlName: string
  ): boolean {
    const c = group.get(controlName);

    return !!c && c.invalid && c.touched;
  }

  errorText(
    group: FormGroup,
    controlName: string,
    label: string
  ): string {
    const c = group.get(controlName);

    if (!c || !c.errors) {
      return '';
    }

    if (c.errors['required']) {
      return this.translations.translate('{0} is required.', [this.translations.translate(label)]);
    }

    if (c.errors['email']) {
      return 'Enter a valid email address.';
    }

    if (c.errors['pattern']) {
      return this.translations.translate('Enter a valid value for {0}.', [this.translations.translate(label)]);
    }

    if (c.errors['minlength']) {
      return this.translations.translate('{0} is too short.', [this.translations.translate(label)]);
    }

    return 'Invalid value.';
  }

  next(): void {
    if (this.currentGroup.invalid) {
      this.currentGroup.markAllAsTouched();

      this.errorMessage =
        'Please fill in all required fields before continuing.';

      return;
    }

    if (this.isLastStep && this.passwordIssues.length) {
      this.errorMessage = this.passwordIssues[0];
      this.accountGroup.get('password')?.markAsTouched();
      return;
    }
    if (this.isLastStep && !this.emailVerified) {
      this.errorMessage = 'Verify your email before creating your account.';
      return;
    }

    this.errorMessage = '';

    if (this.isLastStep) {
      this.submit();
      return;
    }

    this.currentStep++;
  }

  back(): void {
    this.errorMessage = '';

    if (this.currentStep > 0) {
      this.currentStep--;
    }
  }

  submit(): void {
    if (this.passwordsMismatch || this.form.invalid || this.passwordIssues.length || !this.emailVerified) {
      this.form.markAllAsTouched();

      this.errorMessage =
        'Please fill in all required fields before continuing.';

      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const {
      personal,
      account,
    } = this.form.getRawValue();

    const {
      confirmPassword,
      ...accountRest
    } = account;

    const nameParts = personal.fullName.trim().split(/\s+/);
    const firstName = nameParts.shift() ?? '';
    const lastName = nameParts.join(' ') || firstName;

    this.authService
      .signup({
        ...personal,
        ...accountRest,
        firstName,
        lastName,
        phone: account.mobileNumber,
        address: account.addressLine,
        credentialType: 'PASSWORD',
        userType: 'CUSTOMER',
      })
      .subscribe({
        next: (res) => {
          this.loading = false;

          if (res.success) {
            this.router.navigate(['/login']);
          } else {
            this.errorMessage =
              res.message ??
              'Could not create your account.';
          }
        },

        error: (error: HttpErrorResponse) => {
          this.loading = false;
          this.errorMessage = this.apiError(error, 'Could not reach the server. Try again shortly.');
        },
      });
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    const message = error.error?.message ?? error.error?.error;
    return typeof message === 'string' && message ? message : fallback;
  }
}
