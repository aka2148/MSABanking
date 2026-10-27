import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component, ElementRef, HostBinding, HostListener, Input, ViewChild, OnDestroy, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { NavigationStart, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { overlayPlacement } from './overlay-placement';

export interface GlassSelectOption { label: string; value: string; disabled?: boolean; }
@Component({
  selector: 'app-glass-select', standalone: true,
  imports: [TranslatePipe, CommonModule],
  templateUrl: './glass-select.html', styleUrl: './glass-select.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => GlassSelect), multi: true }],
})
export class GlassSelect implements ControlValueAccessor, OnDestroy {
  @Input() options: GlassSelectOption[] = [];
  @Input() placeholder = 'Select';
  @Input() translateOptions = true;
  @ViewChild('trigger') trigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('menu') set menuElement(value:ElementRef<HTMLElement>|undefined) {
    if(!value)return;
    this.panel=value.nativeElement;
    // Moving the Angular view retains its event handlers, translations and CVA behavior.
    const style=getComputedStyle(this.elRef.nativeElement);
    for(let i=0;i<style.length;i++) { const key=style[i]; if(key.startsWith('--'))this.panel.style.setProperty(key,style.getPropertyValue(key)); }
    this.panel.style.direction=style.direction;
    document.body.appendChild(this.panel);
    this.reposition();
    queueMicrotask(()=>{if(this.isOpen)this.panel?.querySelector<HTMLInputElement>('input')?.focus();});
  }
  isOpen=false; selectedValue:string|null=null; disabled=false; searchTerm='';
  private panel?:HTMLElement;
  private onChange:(value:string)=>void=()=>{};
  private onTouched:()=>void=()=>{};
  private readonly navigation:Subscription;
  private readonly reposition=()=>{
    if(!this.panel||!this.trigger)return;
    const p=overlayPlacement(this.trigger.nativeElement.getBoundingClientRect(),window.innerWidth,window.innerHeight);
    if(!p.visible||p.maxHeight<40){this.close(false);return;}
    Object.assign(this.panel.style,{left:`${p.left}px`,top:`${p.top}px`,width:`${p.width}px`,maxHeight:`${p.maxHeight}px`});
  };
  constructor(private elRef:ElementRef<HTMLElement>,router:Router) {
    this.navigation=router.events.subscribe(event=>{if(event instanceof NavigationStart)this.close(false);});
  }
  @HostBinding('class.glass-select-host--open') get hostOpen(){return this.isOpen;}
  get selectedLabel(){return this.options.find(o=>o.value===this.selectedValue)?.label??this.placeholder;}
  get filteredOptions(){const q=this.searchTerm.trim().toLocaleLowerCase();return q?this.options.filter(o=>o.label.toLocaleLowerCase().includes(q)):this.options;}
  toggle():void {
    if(this.disabled)return;
    if(this.isOpen){this.close();return;}
    this.isOpen=true;this.searchTerm='';
    window.addEventListener('scroll',this.reposition,{capture:true,passive:true});
    window.addEventListener('resize',this.reposition,{passive:true});
  }
  close(restoreFocus=true):void {
    if(!this.isOpen)return;
    this.isOpen=false;this.searchTerm='';this.panel?.remove();this.panel=undefined;
    window.removeEventListener('scroll',this.reposition,true);
    window.removeEventListener('resize',this.reposition);
    this.onTouched();if(restoreFocus)this.trigger?.nativeElement.focus();
  }
  select(option:GlassSelectOption):void {if(option.disabled)return;this.selectedValue=option.value;this.onChange(option.value);this.close();}
  @HostListener('document:click',['$event']) onDocumentClick(event:MouseEvent):void {
    if(!this.elRef.nativeElement.contains(event.target as Node)&&!this.panel?.contains(event.target as Node))this.close();
  }
  @HostListener('document:keydown',['$event']) onKeydown(event:KeyboardEvent):void {
    if(!this.isOpen)return;
    if(event.key==='Escape'){event.preventDefault();this.close();return;}
    if(event.key==='Tab'){this.close();return;}
    if(event.key!=='ArrowDown'&&event.key!=='ArrowUp'&&event.key!=='Enter')return;
    const options=Array.from(this.panel?.querySelectorAll<HTMLButtonElement>('.glass-select__option:not(:disabled)')??[]);
    const index=options.indexOf(document.activeElement as HTMLButtonElement);
    event.preventDefault();
    if(event.key==='Enter'){options[index<0?0:index]?.click();return;}
    const next=event.key==='ArrowDown'?(index+1)%options.length:(index<=0?options.length-1:index-1);
    options[next]?.focus();options[next]?.scrollIntoView({block:'nearest'});
  }
  writeValue(value:string):void{this.selectedValue=value;}
  registerOnChange(fn:(value:string)=>void):void{this.onChange=fn;}
  registerOnTouched(fn:()=>void):void{this.onTouched=fn;}
  setDisabledState(value:boolean):void{this.disabled=value;if(value)this.close(false);}
  ngOnDestroy():void{this.close(false);this.navigation.unsubscribe();}
}
