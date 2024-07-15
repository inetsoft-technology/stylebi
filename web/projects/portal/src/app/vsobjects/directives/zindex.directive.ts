/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   Directive,
   HostListener,
   HostBinding,
   Input,
   OnInit,
} from "@angular/core";

/**
 * Directive that brings focused (mouse hover) elements to a given z-index value.
 */
@Directive({
   selector: "[ZIndexDirective]"
})
export class ZIndexDirective implements OnInit {
   // The non-focused z-index of the host component
   @Input() public zIndex: number;

   // The focused z-index of the host component
   @Input() public zIndexMax: number = 9999;

   // Binding to change the z-index
   @HostBinding("style.zIndex") zIndexValue: number;

   /**
    * On init copy the input z-index to the host binding property
    */
   ngOnInit(): void {
      this.zIndexValue = this.zIndex;
   }

   /**
    * When host element has mouseover event sets the zIndex to max value
    */
   @HostListener("mouseenter")
   onEnter(): void {
      this.zIndexValue = this.zIndexMax;
   }

   /**
    * When host element has mouseleave event sets the zIndex to back to its original value
    */
   @HostListener("mouseleave")
   onLeave(): void {
      this.zIndexValue = this.zIndex;
   }
}