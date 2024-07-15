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
import { Directive, ElementRef, HostListener, Input } from "@angular/core";

@Directive({
   selector : "[enterClick]",
})
export class EnterClickDirective {
   @Input() hasKeys: boolean = false;

   constructor(private elementRef: ElementRef) {
   }

   /**
    * Trigger click event on the element if user clicks enter.
    */
   @HostListener("keydown", ["$event"])
   onEnter(event: KeyboardEvent): void {
      if(event.keyCode == 13) {
         if(this.hasKeys) {
            const init: MouseEventInit = {
               shiftKey: event.shiftKey,
               ctrlKey: event.ctrlKey,
               metaKey: event.metaKey
            };
            const click: Event = new MouseEvent("click", init);
            this.elementRef.nativeElement.dispatchEvent(click);
         }
         else {
            this.elementRef.nativeElement.click();
         }
      }
   }
}