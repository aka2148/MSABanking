import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL, apiUrl } from './api-config';
import { KycDraft, KycProfile } from '../models/kyc.models';

@Injectable({ providedIn: 'root' })
export class KycService {
  private readonly url: string;
  constructor(private http: HttpClient, @Inject(API_BASE_URL) base: string) { this.url = apiUrl(base, '/api/v1/customers/me/kyc'); }
  get() { return this.http.get<KycProfile>(this.url); }
  save(draft: KycDraft) { return this.http.put<KycProfile>(`${this.url}/draft`, draft); }
  submit() { return this.http.post<KycProfile>(`${this.url}/submit`, {}); }
  rekyc(remarks: string,mpin:string) { return this.http.post<KycProfile>(`${this.url}/rekyc`, { remarks,mpin }); }
  upload(file: File) { const data = new FormData(); data.append('file', file); return this.http.post<KycProfile>(`${this.url}/document`, data); }
  removeDocument() { return this.http.delete<KycProfile>(`${this.url}/document`); }
}
