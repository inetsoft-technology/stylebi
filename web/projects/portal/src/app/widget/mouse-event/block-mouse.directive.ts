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
import { Input, Directive, NgZone, ElementRef, OnInit } from "@angular/core";

@Directive({
   selector: "[blockMouse]"
})
export class BlockMouseDirective implements OnInit {
   @Input() isBlockMouse: boolean = true;

   constructor(private _ngZone: NgZone, private el: ElementRef) {
   }

   ngOnInit(): void {
      const handler = (event: MouseEvent) => {
         if(this.isBlockMouse) {
            event.stopPropagation();
         }
      };
      this._ngZone.runOutsideAngular(() => {
         const nativeElement = this.el.nativeElement;
         nativeElement.addEventListener("click", handler, false);
         nativeElement.addEventListener("mousedown", handler, false);
         nativeElement.addEventListener("mouseup", handler, false);
         nativeElement.addEventListener("dblclick", handler, false);
         nativeElement.addEventListener("keydown", handler, false);
      });
   }
}
