/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Directive, HostListener, Input, Renderer2, ElementRef } from "@angular/core";
import { DefaultValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";

@Directive({
   selector : "input[trim]",
})
export class InputTrimDirective extends DefaultValueAccessor {
   constructor(private renderer: Renderer2, private elementRef: ElementRef) {
      super(renderer, elementRef, false);
   }

   // set a new value to the field and model.
   private set value(val: any) {
      // update element
      this.writeValue(val);
      // update model
      this.onChange(val);
   }

   /**
    * Updates the value on the blur event.
    */
   @HostListener("blur", ["$event.type", "$event.target.value"])
   onBlur(event: string, value: string): void {
      this.updateValue(event, value);
   }

   /**
    * Trims an input value, and sets it to the model and element.
    *
    * @param {string} event - input event
    * @param {string} value - input value
    */
   private updateValue(event: string, value: string): void {
      this.value = value.trim();

      // this is necessary for reactive forms
      let evt: Event = document.createEvent("Event");
      evt.initEvent("input", true, true);
      Object.defineProperty(evt, "target", {
         value: this.elementRef.nativeElement,
         enumerable: true
      });
      this.elementRef.nativeElement.dispatchEvent(evt);
   }
}
