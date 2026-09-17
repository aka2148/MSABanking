import { Component, ElementRef, HostListener, Input, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface GlassSelectOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-glass-select',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './glass-select.html',
  styleUrl: './glass-select.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => GlassSelect), multi: true },
  ],
})
export class GlassSelect implements ControlValueAccessor {
  @Input() options: GlassSelectOption[] = [];
  @Input() placeholder = 'Select';

  isOpen = false;
  selectedValue: string | null = null;
  disabled = false;

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private elRef: ElementRef<HTMLElement>) {}

  get selectedLabel(): string {
    const found = this.options.find((o) => o.value === this.selectedValue);
    return found ? found.label : this.placeholder;
  }

  toggle(): void {
    if (this.disabled) return;
    this.isOpen = !this.isOpen;
    if (!this.isOpen) this.onTouched();
  }

  select(option: GlassSelectOption): void {
    this.selectedValue = option.value;
    this.onChange(option.value);
    this.isOpen = false;
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
