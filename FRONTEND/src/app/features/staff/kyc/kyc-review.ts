import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { KycReviewService, KycQueue, KycReviewDetail } from '../../../core/services/kyc-review.service';
import { normalizeApiError } from '../../../core/services/fd.service';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';

@Component({selector:'app-kyc-review',standalone:true,imports:[CommonModule,FormsModule,TranslatePipe,WorkspaceShell],templateUrl:'./kyc-review.html',styleUrl:'./kyc-review.scss'})
export class KycReview implements OnInit,OnDestroy {
  private api=inject(KycReviewService);private route=inject(ActivatedRoute);private sanitizer=inject(DomSanitizer);
  queue:KycQueue|null=null;detail:KycReviewDetail|null=null;status='AWAITING_REVIEW';search='';loading=false;busy=false;detailLoading=false;error='';detailError='';message='';reason='';confirm:'APPROVE'|'REJECT'|null=null;
  documentUrl:string|null=null;pdfUrl:SafeResourceUrl|null=null;documentError='';documentLoading=false;
  private generation=0;private queueGeneration=0;
  readonly filters=[{value:'AWAITING_REVIEW',label:'Awaiting review'},{value:'APPROVED',label:'Approved'},{value:'REJECTED',label:'Rejected'},{value:'ALL',label:'All'}];
  readonly accountFields=[['customerNumber','Customer number'],['username','Username'],['fullName','Full name'],['email','Email'],['mobile','Mobile number']];
  readonly personalFields=[['date_of_birth','Date of birth'],['gender','Gender'],['father_or_spouse_name',"Father's / Spouse's / Mother's name"],['relationship','Relationship'],['occupation','Occupation'],['country_of_origin','Country of origin']];
  ngOnInit(){this.load();const id=Number(this.route.snapshot.queryParamMap.get('customerId'));if(id>0)this.open(id);}
  load(page=0){const generation=++this.queueGeneration;this.loading=true;this.error='';this.api.queue(this.status,this.search,page).subscribe({next:q=>{if(generation!==this.queueGeneration)return;this.queue=q;this.loading=false;},error:e=>{if(generation!==this.queueGeneration)return;this.error=normalizeApiError(e).message;this.loading=false;}});}
  open(id:number){const generation=++this.generation;this.clearDocument();this.detail=null;this.detailLoading=true;this.detailError='';this.reason='';this.confirm=null;this.api.detail(id).subscribe({next:d=>{if(generation!==this.generation)return;this.detail=d;this.detailLoading=false;},error:e=>{if(generation!==this.generation)return;this.detailError=normalizeApiError(e).message;this.detailLoading=false;}});}
  viewDocument(){if(!this.detail)return;const generation=this.generation;this.documentLoading=true;this.documentError='';this.api.document(this.detail.customerId).subscribe({next:blob=>{if(generation!==this.generation)return;this.clearDocument();this.documentUrl=URL.createObjectURL(blob);if(blob.type==='application/pdf')this.pdfUrl=this.sanitizer.bypassSecurityTrustResourceUrl(this.documentUrl);this.documentLoading=false;},error:e=>{if(generation!==this.generation)return;this.documentError=normalizeApiError(e).message;this.documentLoading=false;}});}
  decide(decision:'APPROVE'|'REJECT'){
    if(!this.detail||this.detail.state!=='AWAITING_REVIEW'||this.busy)return;
    const note=this.reason.trim();this.detailError='';
    if((decision==='REJECT'||note.length>0)&&(note.length<10||note.length>500||/[<>\u0000-\u001f\u007f]/.test(this.reason))){this.detailError='Enter a decision reason of 10-500 characters without markup or control characters.';return;}
    if(this.confirm!==decision){this.confirm=decision;return;}
    const id=this.detail.customerId;this.busy=true;
    this.api.decide(id,decision,note).subscribe({next:()=>{this.busy=false;this.message='KYC decision recorded.';this.load(this.queue?.number??0);this.open(id);},error:e=>{this.busy=false;this.confirm=null;this.detailError=normalizeApiError(e).message;}});
  }
  eventLabel(event:string){return ({KYC_SUBMITTED:'Submitted',STAFF_VERIFIED:'Approved',STAFF_REJECTED:'Rejected',REKYC_REQUESTED:'Re-KYC requested',AUTO_VERIFIED:'Verified'} as Record<string,string>)[event]??event;}
  private clearDocument(){if(this.documentUrl)URL.revokeObjectURL(this.documentUrl);this.documentUrl=null;this.pdfUrl=null;this.documentError='';this.documentLoading=false;}
  ngOnDestroy(){this.generation++;this.queueGeneration++;this.clearDocument();}
}
