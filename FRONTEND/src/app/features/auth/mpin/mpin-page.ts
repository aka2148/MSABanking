import { Component,OnDestroy,OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { MpinProofService, weakMpin } from '../../../core/services/mpin-proof.service';
import { MpinInput } from '../../../shared/components/mpin-input';
import { WorkspaceShell } from '../../../shared/components/workspace-shell/workspace-shell';
import { AuthService } from '../../../core/services/auth.service';

@Component({selector:'app-mpin-page',standalone:true,imports:[CommonModule,FormsModule,RouterLink,TranslatePipe,MpinInput,WorkspaceShell],template:`<app-workspace-shell><main class="glass-card"><h1>{{'MPIN'|translate}}</h1><p *ngIf="lockedUntil">{{'MPIN is locked until {0}.'|translate:[lockedUntil|date:'medium']}}</p><form (ngSubmit)="submit()"><label class="glass-field" *ngIf="!isSet"><span>{{'Account password'|translate}}</span><input type="password" autocomplete="current-password" [(ngModel)]="password" name="password" /></label><label class="glass-field" *ngIf="isSet"><span>{{'Current MPIN'|translate}}</span><app-mpin-input [(ngModel)]="current" name="current" /></label><label class="glass-field"><span>{{(isSet?'New MPIN':'MPIN')|translate}}</span><app-mpin-input [(ngModel)]="next" name="next" /></label><label class="glass-field"><span>{{'Confirm MPIN'|translate}}</span><app-mpin-input [(ngModel)]="confirm" name="confirm" /></label><p class="glass-field__error" *ngIf="error">{{error|translate}}</p><p role="status" *ngIf="message">{{message|translate}}</p><button class="glass-btn" [disabled]="busy">{{(isSet?'Change MPIN':'Set up MPIN')|translate}}</button></form></main></app-workspace-shell>`})
export class MpinPage implements OnInit,OnDestroy {
  isSet=false;lockedUntil:string|null=null;password='';current='';next='';confirm='';error='';message='';busy=false;
  constructor(private api:MpinProofService,private auth:AuthService){}
  ngOnInit():void{if(this.auth.currentRole()!=='ROLE_CUSTOMER')return;this.refresh();}
  refresh():void{this.api.status().subscribe(s=>{this.isSet=s.set;this.lockedUntil=s.locked?s.lockedUntil:null;});}
  submit():void{this.error='';this.message='';if(weakMpin(this.next)){this.error='Choose a less predictable MPIN.';this.clearPin();return;}if(this.next!==this.confirm){this.error='MPIN entries do not match.';this.clearPin();return;}this.busy=true;const call=this.isSet?this.api.change(this.current,this.next):this.api.setup(this.password,this.next);call.subscribe({next:()=>{this.busy=false;this.message=this.isSet?'MPIN changed.':'MPIN set up.';this.isSet=true;this.clearPin();this.refresh();},error:e=>{this.busy=false;this.error=e?.error?.code??e?.error?.message??'MPIN update failed.';this.clearPin();}});}
  private clearPin():void{this.current='';this.next='';this.confirm='';this.password='';}
  ngOnDestroy():void{this.clearPin();}
}
