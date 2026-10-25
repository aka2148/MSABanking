import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component, ElementRef, HostBinding, HostListener, Input, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface GlassSelectOption {
  label: string;
  value: string;
  disabled?: boolean;
}

@Component({
  selector: 'app-glass-select',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  templateUrl: './glass-select.html',
  styleUrl: './glass-select.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => GlassSelect), multi: true },
  ],
})
export class GlassSelect implements ControlValueAccessor {
  @Input() options: GlassSelectOption[] = [];
  @Input() placeholder = 'Select';
  @Input() translateOptions = true;

  isOpen = false;
  selectedValue: string | null = null;
  disabled = false;
  searchTerm = '';

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};
  private search = '';
  private lastKeyAt = 0;

  @HostBinding('class.glass-select-host--open')
  get hostOpen(): boolean { return this.isOpen; }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (this.disabled) return;
    if ((event.target as HTMLElement).matches('.glass-select__search')) return;
    if (event.key === 'Escape') { this.isOpen = false; return; }
    if (event.key.length !== 1 || event.ctrlKey || event.altKey || event.metaKey || event.key === ' ') return;
    event.preventDefault();
    const now = Date.now();
    this.search = (now - this.lastKeyAt < 700 ? this.search : '') + event.key.toLocaleLowerCase();
    this.lastKeyAt = now;
    const index = this.options.findIndex(o => !o.disabled && o.label.toLocaleLowerCase().startsWith(this.search));
    if (index < 0) return;
    this.isOpen = true;
    setTimeout(() => {
      const option = this.elRef.nativeElement.querySelectorAll<HTMLButtonElement>('.glass-select__option')[index];
      option?.focus(); option?.scrollIntoView({ block: 'nearest' });
    });
  }

  constructor(private elRef: ElementRef<HTMLElement>) {}

  get selectedLabel(): string {
    const found = this.options.find((o) => o.value === this.selectedValue);
    return found ? found.label : this.placeholder;
  }

  get filteredOptions(): GlassSelectOption[] {
    const query = this.searchTerm.trim().toLocaleLowerCase();
    return query
      ? this.options.filter(option => option.label.toLocaleLowerCase().includes(query))
      : this.options;
  }

  toggle(): void {
    if (this.disabled) return;
    this.isOpen = !this.isOpen;
    this.searchTerm = '';
    if (!this.isOpen) this.onTouched();
  }

  select(option: GlassSelectOption): void {
    if (option.disabled) return;
    this.selectedValue = option.value;
    this.onChange(option.value);
    this.isOpen = false;
    this.searchTerm = '';
    this.onTouched();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elRef.nativeElement.contains(event.target as Node)) {
      this.isOpen = false;
    }
  }

  writeValue(value: string): void {
    this.selectedValue = value;
  }
  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }
}
