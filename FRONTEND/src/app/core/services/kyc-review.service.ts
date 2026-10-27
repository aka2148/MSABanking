import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { API_BASE_URL, apiUrl } from './api-config';
import { BehaviorSubject, tap } from 'rxjs';
export interface KycSubmission {customerId:number;customerNumber:string;fullName:string;submittedAt:string;cycleNumber:number;attemptNumber:number;isResubmission:boolean;state:string;proofType:string;proofNumberMasked:string;hasDocument:boolean;decidedBy:string|null;decidedAt:string|null;reason:string|null;}
export interface KycQueue {content:KycSubmission[];totalElements:number;number:number;size:number;}
export interface KycReviewDetail {customerId:number;cycleNumber:number;attemptNumber:number;isResubmission:boolean;state:string;account:Record<string,string|null>;personal:Record<string,string|null>;proof:{type:string;numberMasked:string;issueDate:string};document:{name:string;size:number;contentType:string}|null;history:Array<{eventType:string;remarks:string|null;reviewer:string|null;createdAt:string}>;previousDecisions:Array<{cycleNumber:number;decision:string;reason:string|null;decidedAt:string}>;}
@Injectable({providedIn:'root'})
export class KycReviewService {
  readonly awaitingCount$=new BehaviorSubject<number>(0);
  private readonly url:string;
  constructor(private http:HttpClient,@Inject(API_BASE_URL) base:string){this.url=apiUrl(base,'/api/v1/customers/kyc/submissions');}
  queue(status='AWAITING_REVIEW',search='',page=0,size=20){return this.http.get<KycQueue>(this.url,{params:new HttpParams().set('status',status).set('search',search).set('page',page).set('size',size)}).pipe(tap(q=>{if(status==='AWAITING_REVIEW'&&!search)this.awaitingCount$.next(q.totalElements);}));}
  refreshAwaiting(){this.queue('AWAITING_REVIEW','',0,1).subscribe();}
  detail(id:number){return this.http.get<KycReviewDetail>(`${this.url}/${id}`);}
  document(id:number){return this.http.get(`${this.url}/${id}/document`,{responseType:'blob'});}
  decide(id:number,decision:'APPROVE'|'REJECT',reason:string){return this.http.post<void>(`${this.url}/${id}/decision`,{decision,reason});}
}
