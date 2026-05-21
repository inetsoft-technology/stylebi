/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { DOCUMENT } from "@angular/common";
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   forwardRef,
   HostBinding,
   Inject,
   Input,
   Output,
   ViewChild,
} from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import { Tool } from "../../../../../shared/util/tool";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";

export interface CustomSelectOption<T = any> {
   label: string;
   value: T;
   title?: string;
   disabled?: boolean;
}

let nextListboxId = 0;

@Component({
   selector: "custom-select",
   templateUrl: "./custom-select.component.html",
   styleUrls: ["./custom-select.component.scss"],
   providers: [{
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CustomSelectComponent),
      multi: true
   }]
})
export class CustomSelectComponent implements ControlValueAccessor, AfterViewInit {
   @Input() options: CustomSelectOption[] = [];
   @Input() placeholder: string = "";
   @Input() id: string;
   @Input() dataTest: string;
   @Input() ariaLabel: string;
   @Input() ariaLabelledby: string;
   @Input() invalid: boolean = false;
   @Input() closeOnSelect: boolean = true;
   @Input()
   set disabled(disabled: boolean) {
      this.explicitlyDisabled = disabled;
   }
   @Output() selectionChange = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   @ViewChild("trigger", {read: ElementRef}) triggerRef: ElementRef<HTMLButtonElement>;

   @HostBinding("class.custom-select-host") readonly hostClass = true;
   @HostBinding("class.custom-select") readonly customSelectClass = true;
   @HostBinding("class.has-value") get hasValueClass(): boolean {
      return this.selectedOption != null;
   }
   @HostBinding("class.is-open") open: boolean = false;
   @HostBinding("class.is-focused") focused: boolean = false;
   @HostBinding("class.is-disabled") get disabledClass(): boolean {
      return this.isDisabled;
   }
   @HostBinding("class.is-invalid") get invalidClass(): boolean {
      return this.invalid;
   }

   value: any = null;
   activeIndex: number = -1;
   readonly listboxId = `custom-select-listbox-${nextListboxId++}`;
   suppressTriggerSelfAction: boolean = false;
   private explicitlyDisabled: boolean = false;
   private formDisabled: boolean = false;
   private onChange: (value: any) => void = () => {};
   private onTouched: () => void = () => {};

   constructor(@Inject(DOCUMENT) private document: Document) {
   }

   ngAfterViewInit(): void {
      this.syncActiveIndex();
   }

   writeValue(value: any): void {
      this.value = value;
      this.syncActiveIndex();
   }

   registerOnChange(fn: (value: any) => void): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: () => void): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      this.formDisabled = isDisabled;
   }

   get selectedOption(): CustomSelectOption {
      return this.options?.find((option) => Tool.isEquals(option.value, this.value)) ?? null;
   }

   get selectedLabel(): string {
      return this.selectedOption?.label ?? this.placeholder ?? "";
   }

   get selectedTitle(): string {
      return this.selectedOption?.title ?? this.selectedLabel;
   }

   get dropdownMinWidth(): number {
      return this.triggerRef?.nativeElement?.offsetWidth ?? null;
   }

   get activeOptionId(): string | null {
      return this.activeIndex >= 0 ? this.getOptionId(this.activeIndex) : null;
   }

   get triggerInvalid(): boolean {
      return this.invalid;
   }

   get isDisabled(): boolean {
      return this.explicitlyDisabled || this.formDisabled;
   }

   handleOpenChange(open: boolean): void {
      this.open = open;
      this.onTouched();

      if(open) {
         this.syncActiveIndex();
         setTimeout(() => {
            this.focusActiveOption();
         });
      }
   }

   onTriggerFocus(): void {
      this.focused = true;
   }

   onTriggerBlur(): void {
      this.focused = false;
      this.onTouched();
   }

   onTriggerKeydown(event: KeyboardEvent): void {
      if(this.isDisabled) {
         return;
      }

      switch(event.key) {
      case "ArrowDown":
         event.preventDefault();
         if(!this.open) {
            this.openDropdown();
         }
         else {
            this.stepActiveIndex(1);
         }
         break;
      case "ArrowUp":
         event.preventDefault();
         if(!this.open) {
            this.openDropdown();
         }
         else {
            this.stepActiveIndex(-1);
         }
         break;
      case "Enter":
      case " ":
         event.preventDefault();
         if(!this.open) {
            this.openDropdown();
         }
         else if(this.activeIndex >= 0) {
            this.selectOption(this.options[this.activeIndex]);
         }
         break;
      case "Escape":
         if(this.open) {
            event.preventDefault();
            this.closeDropdown();
         }
         break;
      }
   }

   onOptionKeydown(event: KeyboardEvent, index: number): void {
      switch(event.key) {
      case "ArrowDown":
         event.preventDefault();
         this.stepActiveIndex(1);
         break;
      case "ArrowUp":
         event.preventDefault();
         this.stepActiveIndex(-1);
         break;
      case "Home":
         event.preventDefault();
         this.setActiveIndex(this.getFirstEnabledIndex());
         this.focusActiveOption();
         break;
      case "End":
         event.preventDefault();
         this.setActiveIndex(this.getLastEnabledIndex());
         this.focusActiveOption();
         break;
      case "Enter":
      case " ":
         event.preventDefault();
         this.selectOption(this.options[index]);
         break;
      case "Escape":
         event.preventDefault();
         this.closeDropdown();
         this.focusTrigger();
         break;
      case "Tab":
         this.closeDropdown();
         break;
      }
   }

   onMenuMouseDown(event: MouseEvent): void {
      const target = event.target as HTMLElement;
      const optionButton = this.findOptionButton(event);
      const optionIndex = Number(optionButton?.dataset?.optionIndex);

      if(optionButton == null || Number.isNaN(optionIndex)) {
         return;
      }

      this.selectOption(this.options[optionIndex], event);
   }

   selectOption(option: CustomSelectOption, event?: Event): void {
      if(option == null || option.disabled) {
         return;
      }

      event?.stopPropagation();
      event?.preventDefault();

      this.value = option.value;
      this.onChange(option.value);
      this.onTouched();
      this.selectionChange.emit(option.value);
      this.syncActiveIndex();

      if(this.closeOnSelect) {
         this.suppressTriggerSelfAction = true;
         this.closeDropdown();
         setTimeout(() => {
            this.suppressTriggerSelfAction = false;
            this.focusTrigger();
         });
      }
   }

   setActiveIndex(index: number): void {
      this.activeIndex = index;
   }

   isSelected(option: CustomSelectOption): boolean {
      return Tool.isEquals(option.value, this.value);
   }

   isActive(index: number): boolean {
      return index === this.activeIndex;
   }

   getOptionId(index: number): string {
      return `${this.listboxId}-option-${index}`;
   }

   private openDropdown(): void {
      this.dropdown?.toggleDropdown(new MouseEvent("click"));
   }

   private closeDropdown(): void {
      this.dropdown?.close();
   }

   private syncActiveIndex(): void {
      const selectedIndex = this.options?.findIndex((option) => Tool.isEquals(option.value, this.value)) ?? -1;
      this.activeIndex = selectedIndex >= 0 ? selectedIndex : this.getFirstEnabledIndex();
   }

   private stepActiveIndex(direction: 1 | -1): void {
      if(!this.options?.length) {
         return;
      }

      let nextIndex = this.activeIndex;

      for(let i = 0; i < this.options.length; i++) {
         nextIndex = (nextIndex + direction + this.options.length) % this.options.length;

         if(!this.options[nextIndex]?.disabled) {
            this.activeIndex = nextIndex;
            this.focusActiveOption();
            return;
         }
      }
   }

   private focusActiveOption(): void {
      const optionButtons = this.getOptionButtons();
      optionButtons.item(this.activeIndex)?.focus();
   }

   private focusTrigger(): void {
      this.triggerRef?.nativeElement?.focus();
   }

   private findOptionButton(event: MouseEvent): HTMLButtonElement | null {
      const path = typeof event.composedPath === "function" ? event.composedPath() : [];

      for(const entry of path) {
         if(entry instanceof HTMLButtonElement && entry.classList.contains("custom-select-option")) {
            return entry;
         }
      }

      const target = event.target as HTMLElement;
      const closestButton = target?.closest<HTMLButtonElement>(".custom-select-option");

      if(closestButton != null) {
         return closestButton;
      }

      const optionButtons = this.getOptionButtons();

      for(let i = 0; i < optionButtons.length; i++) {
         const optionButton = optionButtons.item(i);
         const rect = optionButton.getBoundingClientRect();

         if(event.clientX >= rect.left && event.clientX <= rect.right &&
            event.clientY >= rect.top && event.clientY <= rect.bottom)
         {
            return optionButton;
         }
      }

      return null;
   }

   private getOptionButtons(): NodeListOf<HTMLButtonElement> {
      return this.document.querySelectorAll<HTMLButtonElement>(
         `#${this.listboxId} .custom-select-option`
      );
   }

   private getFirstEnabledIndex(): number {
      return this.options?.findIndex((option) => !option.disabled) ?? -1;
   }

   private getLastEnabledIndex(): number {
      if(!this.options?.length) {
         return -1;
      }

      for(let i = this.options.length - 1; i >= 0; i--) {
         if(!this.options[i].disabled) {
            return i;
         }
      }

      return -1;
   }
}
