import { Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({selector:'app-mpin-input',standalone:true,template:`<input type="password" inputmode="numeric" maxlength="6" autocomplete="off" pattern="[0-9]{6}" [disabled]="disabled" [value]="value" (input)="onInput($event)" (blur)="touched()" />`,providers:[{provide:NG_VALUE_ACCESSOR,useExisting:forwardRef(()=>MpinInput),multi:true}]})
export class MpinInput implements ControlValueAccessor {
  value='';disabled=false;private change:(v:string)=>void=()=>{};private touch:()=>void=()=>{};
  writeValue(value:string|null):void{this.value=value??'';}registerOnChange(fn:(v:string)=>void):void{this.change=fn;}registerOnTouched(fn:()=>void):void{this.touch=fn;}setDisabledState(value:boolean):void{this.disabled=value;}
  onInput(event:Event):void{const input=event.target as HTMLInputElement;this.value=input.value.replace(/\D/g,'').slice(0,6);input.value=this.value;this.change(this.value);}touched():void{this.touch();}
}
