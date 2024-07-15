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
   ElementRef,
   HostListener,
   Input,
   Output,
   OnDestroy,
   Directive,
   NgZone,
   ChangeDetectorRef,
   AfterViewInit,
   EventEmitter
} from "@angular/core";
import { DragEvent } from "../../common/data/drag-event";

@Directive({
   selector: "[dropHighlight]"
})
export class DropHighlightDirective implements AfterViewInit, OnDestroy {
   @Input() disabled: boolean = false;
   elem: HTMLElement;
   oldBorder: string;
   private dragOverFn = (event) => this.dragOver(event);

   constructor(element: ElementRef,
               private changeRef: ChangeDetectorRef,
               private zone: NgZone) {
      this.elem = element.nativeElement;
   }

   ngAfterViewInit() {
      this.oldBorder = this.elem.style.border;

      if(!this.oldBorder) {
         this.elem.style.border = this.oldBorder = "2px solid transparent";
      }

      this.zone.runOutsideAngular(() => {
         this.elem.addEventListener("dragover", this.dragOverFn);
      });
   }

   ngOnDestroy() {
      this.elem.removeEventListener("dragover", this.dragOverFn);
   }

   addActiveStyle() {
      if(!this.disabled) {
         const border = "2px solid #66DD66";

         if(this.elem.style.border != border) {
            this.elem.style.border = border;
            this.changeRef.detectChanges();
         }
      }
   }

   removeActiveStyle() {
      this.elem.style.border = this.oldBorder;
   }

   @HostListener("dragleave", ["$event"])
   dragLeave(event: DragEvent) {
      this.removeActiveStyle();
   }

   dragOver(event: DragEvent) {
      this.addActiveStyle();
   }

   @HostListener("drop", ["$event"])
   drop(event: DragEvent) {
      event.preventDefault();
      this.removeActiveStyle();
   }
}
