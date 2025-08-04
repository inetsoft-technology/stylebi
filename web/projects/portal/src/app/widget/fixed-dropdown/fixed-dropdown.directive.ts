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
import {
   ChangeDetectorRef,
   Directive,
   ElementRef,
   EventEmitter,
   HostListener,
   Input, NgZone,
   OnDestroy,
   Output,
   TemplateRef
} from "@angular/core";
import { Point } from "../../common/data/point";
import { DropdownOptions } from "./dropdown-options";
import { DropdownRef } from "./fixed-dropdown-ref";
import { FixedDropdownService } from "./fixed-dropdown.service";

@Directive({
   selector: "[fixedDropdown]"
})
export class FixedDropdownDirective implements OnDestroy {
   @Input() fixedDropdown: TemplateRef<any>;
   @Input() dropdownPlacement: "mouse" | "bottom" | "right" = "mouse";
   @Input() disabled: boolean = false;
   @Input() id: string;
   @Input() disabledSelfAction: boolean = false;
   @Output() openChange = new EventEmitter<boolean>();
   private _autoClose: boolean = true;
   private _closeOnOutsideClick: boolean = true;
   private _closeOnWindowResize: boolean = true;
   private _closeOnEsc: boolean = true;
   private _zIndex: number = null;
   private dropdown: DropdownRef = null;
   private isDropdownOpenOnMousedown: boolean;

   constructor(private dropdownService: FixedDropdownService,
               private elementRef: ElementRef,
               private changeRef: ChangeDetectorRef,
               private zone: NgZone)
   {
   }

   ngOnDestroy() {
      this.close();
   }

   @Input()
   set autoClose(flag: boolean) {
      this._autoClose = flag;

      if(this.dropdown) {
         this.dropdown.dropdownInstance.autoClose = this.autoClose;
      }
   }

   get autoClose(): boolean {
      return this._autoClose;
   }

   @Input()
   set zIndex(flag: number) {
      this._zIndex = flag;

      if(this.dropdown) {
         this.dropdown.dropdownInstance.zIndex = this.zIndex;
      }
   }

   get zIndex(): number {
      return this._zIndex;
   }

   @Input()
   set closeOnOutsideClick(flag: boolean) {
      this._closeOnOutsideClick = flag;

      if(this.dropdown) {
         this.dropdown.dropdownInstance.closeOnOutsideClick = this.closeOnOutsideClick;
      }
   }

   get closeOnOutsideClick(): boolean {
      return this._closeOnOutsideClick;
   }

   @Input()
   set closeOnWindowResize(flag: boolean) {
      this._closeOnWindowResize = flag;

      if(this.dropdown) {
         this.dropdown.dropdownInstance.closeOnWindowResize = this.closeOnWindowResize;
      }
   }

   get closeOnWindowResize(): boolean {
      return this._closeOnWindowResize;
   }

   @Input()
   set closeOnEsc(flag: boolean) {
      this._closeOnEsc = flag;

      if(this.dropdown) {
         this.dropdown.dropdownInstance.closeOnEsc = this._closeOnEsc;
      }
   }

   get closeOnEsc(): boolean {
      return this._closeOnEsc;
   }

   get closed(): boolean {
      return !this.dropdown || this.dropdown?.closed;
   }

   @HostListener("mousedown")
   public mousedown() {
      if(this.disabledSelfAction) {
         return;
      }

      // If dropdown is open on mousedown, don't open it on click.
      this.isDropdownOpenOnMousedown = this.dropdown && !this.dropdown.closed;
   }

   @HostListener("click", ["$event"])
   private clicked(event: MouseEvent) {
      if(this.disabledSelfAction) {
         return;
      }

      this.toggleDropdown(event);
   }

   public toggleDropdown(event: MouseEvent) {
      if(!this.disabled && (!this.dropdown || this.dropdown.closed) && !!this.fixedDropdown &&
         !this.isDropdownOpenOnMousedown)
      {
         const position = this.getDropdownPosition(event);
         const options: DropdownOptions = {
            position,
            contextmenu: false,
            autoClose: this.autoClose,
            closeOnOutsideClick: this.closeOnOutsideClick,
            closeOnWindowResize: this.closeOnWindowResize,
            zIndex: this.zIndex,
         };

         this.openChange.emit(true);
         this.dropdown = this.dropdownService.open(this.fixedDropdown, options);

         this.zone.run(() => {
            this.changeRef.detectChanges();
         });

         const sub = this.dropdown.closeEvent.subscribe(() => {
            this.openChange.emit(false);
            sub.unsubscribe();
         });
      }
      else if(this.dropdown) {
         this.dropdown.close();
      }
   }

   public open(event: MouseEvent) {
      if(this.dropdown == null || this.dropdown.closed) {
         this.isDropdownOpenOnMousedown = false;
         this.toggleDropdown(event);
      }
   }

   public close(): void {
      if(this.dropdown) {
         this.dropdown.close();
         this.dropdown = null;
      }
   }

   private getDropdownPosition(event: MouseEvent): Point {
      const box = this.elementRef.nativeElement.getBoundingClientRect();
      let position: Point;

      switch(this.dropdownPlacement) {
      case "mouse":
         position = {x: event.clientX, y: event.clientY};
         break;
      case "bottom":
         position = {x: box.left, y: box.bottom};
         break;
      case "right":
         position = {x: box.right, y: box.top};
         break;
      default:
         position = {x: 0, y: 0};
      }

      return position;
   }
}
