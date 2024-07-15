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
   ChangeDetectorRef,
   Component,
   ElementRef,
   Inject,
   OnDestroy,
   OnInit,
   Renderer2
} from "@angular/core";
import { FixedDropdownComponent } from "./fixed-dropdown.component";
import { DropdownStackService } from "./dropdown-stack.service";

@Component({
   selector: "fixed-dropdown-contextmenu",
   templateUrl: "fixed-dropdown-contextmenu.component.html"
})
export class FixedDropdownContextmenuComponent
   extends FixedDropdownComponent implements OnInit, OnDestroy
{
   private readonly contextmenuFn = (e: Event) => this.closeContextmenuEvent(e);
   private readonly scrollFn = (e: any) => {
      if(e.detail) {
         this.closeContextmenuEvent(e);
      }
   };

   constructor(protected hostRef: ElementRef,
               protected renderer: Renderer2,
               protected dropdownService: DropdownStackService,
               @Inject(DOCUMENT) protected document: Document,
               public changeDetectorRef: ChangeDetectorRef)
   {
      super(hostRef, renderer, document, dropdownService);
   }

   ngOnInit() {
      super.ngOnInit();
      this.document.addEventListener("contextmenu", this.contextmenuFn, true);
      this.document.addEventListener("touchstart", this.contextmenuFn, {passive: false});
      this.document.addEventListener("scroll", this.scrollFn, true);
   }

   ngOnDestroy() {
      super.ngOnDestroy();
      this.document.removeEventListener("contextmenu", this.contextmenuFn, true);
      this.document.removeEventListener("touchstart", this.contextmenuFn, true);
      this.document.removeEventListener("scroll", this.scrollFn, true);
   }

   closeContextmenuEvent(event: Event) {
      // Bug #62941, when inside the shadow dom, event.target points to the shadow root instead of
      // the actual element that triggered the event so use event.composedPath instead
      let target = event.composedPath()[0] || event.target;

      if(!this.elementContainsTarget(target)) {
         this.tryClose();
      }
      else {
         if(event instanceof MouseEvent) {
            event.preventDefault();
         }
      }
   }
}
