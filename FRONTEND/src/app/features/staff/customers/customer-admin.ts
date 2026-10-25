import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CustomerAdminPage, CustomerAdminRecord, CustomerAdminService } from '../../../core/services/customer-admin.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';
import { MpinProofService } from '../../../core/services/mpin-proof.service';

@Component({
  selector: 'app-customer-admin',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, WorkspaceShell],
  templateUrl: './customer-admin.html',
  styleUrl: './customer-admin.scss',
})
export class CustomerAdmin implements OnInit {
  page: CustomerAdminPage | null = null;
  search = '';
  loading = false;
  savingId: number | null = null;
  error = '';
  message = '';
  resetConfirmId:number|null=null;resettingId:number|null=null;
  readonly kycStatuses = ['PENDING', 'VERIFIED', 'REJECTED'];
  readonly riskCategories = ['LOW', 'MEDIUM', 'HIGH'];

  constructor(private readonly customers: CustomerAdminService,private readonly mpin:MpinProofService) {}

  ngOnInit(): void { this.load(0); }

  load(page: number): void {
    this.loading = true;
    this.error = '';
    this.customers.list(page, this.search).subscribe({
      next: (result) => { this.page = result; this.loading = false; },
      error: (error: HttpErrorResponse) => { this.error = this.errorText(error); this.loading = false; },
    });
  }

  save(customer: CustomerAdminRecord): void {
    this.savingId = customer.id;
    this.error = '';
    this.message = '';
    this.customers.update(customer.id, {
      fullName: customer.fullName.trim(),
      kycStatus: customer.kycStatus,
      riskCategory: customer.riskCategory,
      legalEntityType: customer.legalEntityType,
    }).subscribe({
      next: (updated) => {
        if (this.page) this.page.content = this.page.content.map((row) => row.id === updated.id ? updated : row);
        this.message = `Customer ${updated.customerNumber} updated.`;
        this.savingId = null;
      },
      error: (error: HttpErrorResponse) => { this.error = this.errorText(error); this.savingId = null; },
    });
  }
  resetMpin(customer:CustomerAdminRecord):void{
    if(this.resetConfirmId!==customer.id){this.resetConfirmId=customer.id;return;}
    this.resettingId=customer.id;this.error='';this.message='';this.mpin.reset(customer.id).subscribe({next:()=>{this.resettingId=null;this.resetConfirmId=null;this.message='MPIN reset. The customer must set a new MPIN with their account password.';},error:e=>{this.resettingId=null;this.error=this.errorText(e);}});
  }

  private errorText(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Your session expired. Sign in again.';
    if (error.status === 403) return 'Customer management requires a staff account.';
    return error.error?.message ?? error.error?.error ?? 'Could not load or update customers.';
  }
}
