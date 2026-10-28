import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router,RouterLink } from '@angular/router';
import { Subject, catchError, concatMap, finalize, of } from 'rxjs';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';
import { KYC_FIELDS, KycDraft, KycField, KycProfile } from '../../core/models/kyc.models';
import { AuthService } from '../../core/services/auth.service';
import { KycService } from '../../core/services/kyc.service';
import { GlassSelect } from '../../shared/components/glass-select/glass-select';
import { WorkspaceShell } from '../../shared/components/workspace-shell/workspace-shell';
import { MpinInput } from '../../shared/components/mpin-input';
import { MpinProofService, classifyMpinError } from '../../core/services/mpin-proof.service';
import { countryOptions, validId } from '../../shared/kyc-validation';

@Component({
  selector: 'app-kyc', standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, GlassSelect, WorkspaceShell,MpinInput,RouterLink],
  templateUrl: './kyc.html', styleUrl: './kyc.scss',
})
export class Kyc implements OnInit, OnDestroy {
  private readonly api = inject(KycService);
  private readonly mpinProof = inject(MpinProofService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translations = inject(TranslationService);
  readonly countries = computed(() => countryOptions(this.translations.language()));
  profile: KycProfile | null = null;
  loading = true;
  error = '';
  documentError = '';
  remarksError = '';
  saved = false;
  verified = false;
  busy = false;
  requesting = false;
  remarks = '';
  rekycMpin='';mpinNotSet=false; reapplyMpin=''; reapplyNote=''; reapplyError=''; reapplyConfirm=false; reapplyBusy=false;
  form = Object.fromEntries(KYC_FIELDS.map(f => [f, ''])) as Record<KycField, string>;
  errors: Partial<Record<KycField, string>> = {};
  private pending: KycDraft = {};
  private timer?: ReturnType<typeof setTimeout>;
  private readonly saves = new Subject<{ draft: KycDraft; versions: Partial<Record<KycField, number>> }>();
  private readonly versions: Partial<Record<KycField, number>> = {};
  private savingCount = 0;
  private leaving: (() => void)[] = [];
  readonly accountFields = [
    { key: 'fullName' as const, label: 'Full name' }, { key: 'username' as const, label: 'Username' },
    { key: 'email' as const, label: 'Email' }, { key: 'mobile' as const, label: 'Mobile number' },
    { key: 'customerNumber' as const, label: 'Customer number' },
  ];
  readonly sections = [
    { label: 'Personal details', fields: ['dateOfBirth', 'gender', 'fatherOrSpouseName', 'relationship', 'occupation', 'countryOfOrigin'] as KycField[] },
    { label: 'Identity proof', fields: ['proofOfIdType', 'proofOfIdValue', 'idIssueDate'] as KycField[] },
  ];
  readonly labels: Record<KycField, string> = {
    dateOfBirth: 'Date of birth', gender: 'Gender', relationship: 'Relationship', fatherOrSpouseName: "Father's / Spouse's / Mother's name",
    occupation: 'Occupation', countryOfOrigin: 'Country of origin', proofOfIdType: 'Proof of ID type', proofOfIdValue: 'ID number', idIssueDate: 'ID issue date',
  };
  readonly options: Partial<Record<KycField, { label: string; value: string }[]>> = {
    gender: this.choices(['MALE', 'Male'], ['FEMALE', 'Female'], ['OTHER', 'Other']),
    relationship: this.choices(['FATHER', 'Father'], ['MOTHER', 'Mother'], ['SPOUSE', 'Spouse']),
    occupation: this.choices(['STUDENT', 'Student'], ['SALARIED', 'Salaried'], ['SELF_EMPLOYED', 'Self-employed'], ['BUSINESS', 'Business'], ['PROFESSIONAL', 'Professional'], ['HOMEMAKER', 'Homemaker'], ['RETIRED', 'Retired'], ['UNEMPLOYED', 'Unemployed'], ['OTHER', 'Other']),
    proofOfIdType: this.choices(['AADHAAR', 'Aadhaar'], ['PAN', 'PAN'], ['PASSPORT', 'Passport'], ['DRIVING_LICENSE', 'Driving Licence'], ['VOTER_ID', 'Voter ID']),
  };
  private choices(...pairs: string[][]) { return pairs.map(([value, label]) => ({ value, label })); }
  get utcToday(): string { return new Date().toISOString().slice(0, 10); }
  get isMinor(): boolean {
    const birth = this.form.dateOfBirth;
    if (!birth || birth < '1900-01-01' || birth > this.utcToday) return false;
    const cutoff = `${Number(this.utcToday.slice(0, 4)) - 18}${this.utcToday.slice(4)}`;
    return birth > cutoff;
  }
  proofLabel(): string { return this.options.proofOfIdType?.find(o => o.value === this.profile?.values.proofOfIdType)?.label ?? '—'; }

  ngOnInit(): void {
    if (this.auth.currentRole() !== 'ROLE_CUSTOMER') { void this.router.navigate(['/dashboard']); return; }
    // concatMap retains in-flight writes on navigation and drains the final flush before completing.
    this.saves.pipe(concatMap(({ draft, versions }) => {
      if (!this.profile?.editingAllowed) { this.saveFinished(); return of(null); }
      return this.api.save(draft).pipe(concatMap(profile => {
        this.applySave(profile, draft, versions); return of(profile);
      }), catchError(err => { this.error = this.message(err); this.saved = false; return of(null); }), finalize(() => this.saveFinished()));
    })).subscribe();
    this.api.get().subscribe({ next: p => { this.restore(p); this.loading = false; }, error: err => { this.loading = false; this.error = this.message(err); } });
  }
  private restore(profile: KycProfile): void {
    this.profile = profile;
    for (const field of KYC_FIELDS) this.form[field] = profile.values[field] ?? '';
    this.errors = {}; this.pending = {};
  }
  changed(field: KycField, value: string): void {
    this.form[field] = value;
    this.versions[field] = (this.versions[field] ?? 0) + 1;
    this.pending[field] = value; this.saved = false;
    delete this.errors[field];
    if ((field === 'dateOfBirth' || field === 'idIssueDate') && value && value > this.utcToday)
      this.errors[field] = field === 'dateOfBirth' ? "Date of birth can't be in the future." : "ID issue date can't be in the future.";
    if (field === 'proofOfIdValue' && value && !validId(this.form.proofOfIdType, value)) this.errors[field] = 'Enter a valid ID number for the selected proof type.';
    clearTimeout(this.timer); this.timer = setTimeout(() => this.flush(), 800);
  }
  flush(): void {
    clearTimeout(this.timer);
    if (Object.keys(this.pending).length && this.profile?.editingAllowed) {
      const draft = this.pending; this.pending = {}; this.enqueue(draft);
    }
  }
  saveAll(): void {
    clearTimeout(this.timer);
    const draft: KycDraft = { ...this.form };
    if (!this.form.proofOfIdValue && this.profile?.proofNumberMasked && !Object.hasOwn(this.pending, 'proofOfIdValue')) {
      delete draft.proofOfIdValue; draft.confirmSavedProof = true;
    }
    this.pending = {}; this.enqueue(draft);
  }
  private enqueue(draft: KycDraft): void { this.savingCount++; this.saves.next({ draft, versions: { ...this.versions } }); }
  private saveFinished(): void {
    this.savingCount--;
    if (!this.savingCount) { this.leaving.forEach(resolve => resolve()); this.leaving = []; }
  }
  async submitKyc(): Promise<void> {
    if (!this.profile || this.profile.completionPercent !== 100 || this.busy) return;
    clearTimeout(this.timer);
    this.error = '';
    this.busy = true;
    this.flush();
    if (this.savingCount) await new Promise<void>(resolve => this.leaving.push(resolve));
    if (this.error) { this.busy = false; return; }
    this.api.submit().subscribe({
      next: profile => { this.restore(profile); this.verified = profile.kycStatus === 'VERIFIED'; this.busy = false; this.saved = false; },
      error: err => { this.busy = false; this.error = this.message(err); },
    });
  }
  async beforeLeave(): Promise<boolean> {
    this.flush();
    if (this.savingCount) await new Promise<void>(resolve => this.leaving.push(resolve));
    return true;
  }
  private applySave(profile: KycProfile, draft: KycDraft, versions: Partial<Record<KycField, number>>): void {
    this.profile = profile; this.error = '';
    for (const field of KYC_FIELDS) {
      if (this.versions[field] !== versions[field] || Object.hasOwn(this.pending, field)) continue;
      const dependentClear = (field === 'proofOfIdValue' && 'proofOfIdType' in draft && !profile.proofNumberMasked)
        || (field === 'idIssueDate' && 'dateOfBirth' in draft && !profile.values.idIssueDate);
      if (!(field in draft) && !dependentClear) continue;
      if (profile.fieldErrors[field]) this.errors[field] = profile.fieldErrors[field];
      else {
        delete this.errors[field];
        // Never replace an invalid typed value with a previously saved value.
        if (field !== 'proofOfIdValue' || field in draft) this.form[field] = profile.values[field] ?? '';
      }
    }
    this.saved = Object.keys(profile.fieldErrors).length === 0;
    if (profile.kycStatus === 'VERIFIED') { this.verified = true; clearTimeout(this.timer); this.pending = {}; }
  }
  requestRekyc(): void {
    this.busy = true; this.remarksError = '';
    this.api.rekyc(this.remarks,this.rekycMpin).subscribe({
      next: p => { this.restore(p); this.busy = false; this.requesting = false; this.remarks = ''; this.rekycMpin='';this.saved = false; this.verified = false; },
      error: err => { this.busy = false;this.mpinNotSet=err?.error?.code==='MPIN_NOT_SET'; this.remarksError = classifyMpinError('verify',err);this.rekycMpin=''; },
    });
  }
  requestReapply(): void {
    if (!this.profile?.canReapply || this.reapplyBusy) return;
    const note=this.reapplyNote.trim(); this.reapplyError='';
    if ((note && (note.length<10||note.length>500||/[<>\u0000-\u001f\u007f]/.test(this.reapplyNote)))) { this.reapplyError='Enter a re-apply note of 10-500 characters without markup or control characters.'; return; }
    if (!this.reapplyConfirm) { this.reapplyConfirm=true; return; }
    this.reapplyBusy=true;
    this.mpinProof.requestProof('KYC_REAPPLY',{customerUserId:this.profile.accountDetails.userId,cycleNumber:this.profile.cycleNumber},this.reapplyMpin).subscribe({
      next: proof=>this.api.reapply(note,proof.proofToken).subscribe({next:p=>{this.restore(p);this.reapplyBusy=false;this.reapplyConfirm=false;this.reapplyMpin='';this.reapplyNote='';},error:e=>{this.reapplyBusy=false;this.reapplyError=classifyMpinError('operation',e);}}),
      error:e=>{this.reapplyBusy=false;this.reapplyMpin='';this.mpinNotSet=e?.error?.code==='MPIN_NOT_SET';this.reapplyError=classifyMpinError('verify',e);}
    });
  }
  upload(event: Event): void {
    const input = event.target as HTMLInputElement, file = input.files?.[0];
    input.value = ''; this.documentError = '';
    if (!file) return;
    if (!file.size || file.size > 5 * 1024 * 1024) { this.documentError = 'Choose a non-empty document up to 5 MB.'; return; }
    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) { this.documentError = 'Choose a PDF, JPEG or PNG document.'; return; }
    this.busy = true;
    this.api.upload(file).subscribe({ next: p => { if (this.profile) this.profile.document = p.document; this.busy = false; }, error: err => { this.busy = false; this.documentError = this.message(err); } });
  }
  removeDocument(): void {
    this.busy = true; this.documentError = '';
    this.api.removeDocument().subscribe({ next: () => { if (this.profile) this.profile.document = null; this.busy = false; }, error: err => { this.busy = false; this.documentError = this.message(err); } });
  }
  private message(err: { error?: { message?: string } }): string { return err.error?.message || 'Unable to save KYC. Please try again.'; }
  ngOnDestroy(): void { this.rekycMpin='';this.flush(); this.saves.complete(); }
}
