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
import { ElementRef, Directive, HostBinding } from "@angular/core";

@Directive({
   selector: "[bindingDraggable]"
})
export class DraggableDirective {
   selector: HTMLElement;

   //Store numeric values of styles
   top: number = 0;
   left: number = 0;

   constructor(private element: ElementRef) {

   }

   createSelector(): HTMLElement {
      return this.selector || document.createElement("div");
   }

   initSelector() {
      let elem: HTMLElement = this.element.nativeElement;
      this.selector = this.createSelector();
      this.selector.style.position = "absolute";
      this.selector.style.width = elem.clientWidth + "px";
      this.selector.style.height = elem.clientHeight + "px";
      this.selector.style.border = "1px solid red";

      //init position
      this.top = elem.offsetTop;
      this.left = elem.offsetLeft;
      this.setSelectorPosition();
   }

   setSelectorPosition() {
      this.selector.style.top = this.top + "px";
      this.selector.style.left = this.left + "px";
   }
}
