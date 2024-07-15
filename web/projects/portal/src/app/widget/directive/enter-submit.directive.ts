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
   AfterContentInit,
   Directive,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnDestroy,
   Output,
   Renderer2
} from "@angular/core";

@Directive({
   selector: "[enterSubmit]"
})
export class EnterSubmitDirective implements AfterContentInit, OnDestroy {
   @Input() enterSubmit: (() => boolean) | boolean;
   @Input() enterSubmitEnable = false;
   @Output() onEnter = new EventEmitter<void>();
   @Output() onEsc = new EventEmitter<void>();
   private keydownListener: () => void;

   constructor(private element: ElementRef, private renderer: Renderer2, private zone: NgZone) {
   }

   ngAfterContentInit(): void {
      if(!!this.element.nativeElement && !this.element.nativeElement.hasAttribute("tabindex")) {
         this.element.nativeElement.setAttribute("tabindex", -1);
      }

      if(!this.element.nativeElement.querySelector("[autofocus], [defaultFocus]")) {
         this.element.nativeElement.focus();
      }

      if(this.element.nativeElement) {
         this.zone.runOutsideAngular(() => {
            this.keydownListener = this.renderer.listen(
               this.element.nativeElement, "keydown", ($event) => this.onKeyDown($event));
         });
      }
   }

   ngOnDestroy(): void {
      if(this.keydownListener) {
         this.keydownListener();
      }
   }

   public onKeyDown(event: KeyboardEvent): void {
      if(this.onEsc && event.keyCode === 27) {
         event.stopPropagation();
         event.preventDefault();
         this.onEsc.emit();
         return;
      }

      if((this.enterSubmitEnable || !!this.enterSubmit &&
         this.enterSubmit === true || this.enterSubmit instanceof Function && this.enterSubmit())
         && (event.keyCode === 13 || event.which === 13))
      {
         // If the target is a link or button, it is unlikely the user is intending to close the dialog
         if(event.target instanceof HTMLButtonElement ||
            event.target instanceof HTMLAnchorElement ||
            (event.target instanceof HTMLTextAreaElement && !(event.ctrlKey || event.metaKey)))
         {
            return;
         }

         event.preventDefault();
         this.onEnter.emit();
      }
   }
}
