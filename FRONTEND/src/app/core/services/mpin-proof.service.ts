import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL, apiUrl } from './api-config';

export type MpinPurpose = 'TRANSFER'|'WITHDRAWAL'|'FD_FUNDING'|'FD_CLOSE'|'FD_PREMATURE_REQUEST'|'LOAN_APPLICATION'|'FOREX_REQUEST';
export interface MpinProof { proofToken: string; expiresAt: string; }
export function weakMpin(pin:string):boolean {
  if(!/^\d{6}$/.test(pin))return true;
  if(/^([0-9])\1{5}$/.test(pin))return true;
  const d=[...pin].map(Number);if(d.every((n,i)=>i===0||n===d[i-1]+1)||d.every((n,i)=>i===0||n===d[i-1]-1))return true;
  return pin.slice(0,3)===pin.slice(3)||pin.slice(0,2)===pin.slice(2,4)&&pin.slice(2,4)===pin.slice(4);
}

@Injectable({providedIn:'root'})
export class MpinProofService {
  private readonly url:string;
  constructor(private readonly http:HttpClient,@Inject(API_BASE_URL)base:string){this.url=apiUrl(base,'/api/v1/auth/mpin');}
  status():Observable<{set:boolean;locked:boolean;lockedUntil:string|null}>{return this.http.get<{set:boolean;locked:boolean;lockedUntil:string|null}>(`${this.url}/status`);}
  setup(password:string,mpin:string):Observable<void>{return this.http.post<void>(`${this.url}/setup`,{password,mpin});}
  change(currentMpin:string,newMpin:string):Observable<void>{return this.http.post<void>(`${this.url}/change`,{currentMpin,newMpin});}
  requestProof(purpose:MpinPurpose,fields:Record<string,unknown>,mpin:string):Observable<MpinProof>{return this.http.post<MpinProof>(`${this.url}/verify`,{mpin,purpose,fields});}
  verifyAtTeller(customerId:number,accountId:number,amount:number,mpin:string):Observable<MpinProof>{return this.http.post<MpinProof>(apiUrl(this.urlBase,'/api/v1/customers/'+customerId+'/mpin/verify'),{accountId,amount,mpin});}
  reset(customerId:number):Observable<void>{return this.http.post<void>(apiUrl(this.urlBase,`/api/v1/customers/${customerId}/mpin/reset`),{});}
  private get urlBase():string{return this.url.replace(/\/api\/v1\/auth\/mpin$/,'');}
}

export function mpinErrorMessage(error:any):string {
  const code=error?.error?.code;
  if(code==='MPIN_NOT_SET')return 'MPIN_NOT_SET';
  if(code==='MPIN_INVALID')return `MPIN_INVALID:${error?.error?.attemptsRemaining ?? ''}`;
  if(code==='MPIN_LOCKED')return `MPIN_LOCKED:${error?.error?.lockedUntil ?? ''}`;
  return error?.error?.message ?? 'MPIN verification failed.';
}
