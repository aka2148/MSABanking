import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { API_BASE_URL, apiUrl } from './api-config';
export interface KycSubmission {customerId:number;customerNumber:string;fullName:string;submittedAt:string;cycleNumber:number;state:string;proofType:string;proofNumberMasked:string;hasDocument:boolean;decidedBy:string|null;decidedAt:string|null;reason:string|null;}
export interface KycQueue {content:KycSubmission[];totalElements:number;number:number;size:number;}
export interface KycReviewDetail {customerId:number;cycleNumber:number;state:string;account:Record<string,string|null>;personal:Record<string,string|null>;proof:{type:string;numberMasked:string;issueDate:string};document:{name:string;size:number;contentType:string}|null;history:Array<{eventType:string;remarks:string|null;reviewer:string|null;createdAt:string}>;}
@Injectable({providedIn:'root'})
export class KycReviewService {
  private readonly url:string;
  constructor(private http:HttpClient,@Inject(API_BASE_URL) base:string){this.url=apiUrl(base,'/api/v1/customers/kyc/submissions');}
  queue(status='AWAITING_REVIEW',search='',page=0,size=20){return this.http.get<KycQueue>(this.url,{params:new HttpParams().set('status',status).set('search',search).set('page',page).set('size',size)});}
  detail(id:number){return this.http.get<KycReviewDetail>(`${this.url}/${id}`);}
  document(id:number){return this.http.get(`${this.url}/${id}/document`,{responseType:'blob'});}
  decide(id:number,decision:'APPROVE'|'REJECT',reason:string){return this.http.post<void>(`${this.url}/${id}/decision`,{decision,reason});}
}
