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
import { Directive, ElementRef, EventEmitter, OnInit, Output, Renderer2 } from "@angular/core";

@Directive({
   selector: "[vsToolbarButton]"
})
export class VsToolbarButtonDirective implements OnInit {
   @Output()
   vsToolbarButton = new EventEmitter<VsToolbarButtonDirective>();

   constructor(private element: ElementRef, private renderer: Renderer2) {
   }

   ngOnInit(): void {
      this.renderer.listen(
         this.element.nativeElement, "focus", () => this.vsToolbarButton.emit(this));
   }

   focus(): void {
      setTimeout(() => this.element.nativeElement.focus(), 0);
   }

   get disabled(): boolean {
      return this.element.nativeElement.disabled;
   }
}