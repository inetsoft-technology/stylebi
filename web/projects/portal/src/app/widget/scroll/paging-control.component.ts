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
   AfterViewInit,
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   Output
} from "@angular/core";
import { PagingControlService } from "../../common/services/paging-control.service";

@Component({
   selector: "paging-control",
   templateUrl: "paging-control.component.html",
   styleUrls: ["paging-control.component.scss"]
})
export class PagingControlComponent implements AfterViewInit, OnDestroy {
   @Input() assemblyName: string;
   @Input() abc: number;
   @Input() viewportWidth: number;
   @Input() viewportHeight: number;
   @Input() contentWidth: number;
   @Input() contentHeight: number;
   @Input() scrollTop: number;
   @Input() scrollLeft: number;
   @Input() enabled: boolean;
   @Input() startX: number;
   @Input() startY: number;
   @Output() scrollTopChange = new EventEmitter<number>();
   @Output() scrollLeftChange = new EventEmitter<number>();
   moved: boolean = false;
   movedX: number;
   movedY: number;
   moveListener = (event: TouchEvent) => {
      event.preventDefault();
      event.stopPropagation();

      if(event.targetTouches.length === 1) {
         const touch = event.targetTouches[0];
         this.moved = true;

         if(this.pagingControlService.inViewport(touch, true)) {
            this.movedX = touch.pageX - 40;
            this.movedY = touch.pageY - 40;
         }
      }
   };

   constructor(private pagingControlService: PagingControlService)
   {
   }

   ngAfterViewInit() {
      document.addEventListener("touchmove", this.moveListener);
   }

   ngOnDestroy() {
      document.removeEventListener("touchmove", this.moveListener);
   }

   get startXPosition(): number {
      return this.moved ? this.movedX : this.startX;
   }

   get startYPosition(): number {
      return this.moved ? this.movedY : this.startY;
   }

   get downScrollEnabled(): boolean {
      return this.scrollTop + this.viewportHeight < this.contentHeight;
   }

   get upScrollEnabled(): boolean {
      return this.scrollTop > 0;
   }

   get rightScrollEnabled(): boolean {
      return this.scrollLeft + this.viewportWidth < this.contentWidth;
   }

   get leftScrollEnabled(): boolean {
      return this.scrollLeft > 0;
   }

   pageDown() {
      this.scrollTopChange.emit(Math.min(this.scrollTop + this.viewportHeight - 5,
                                         this.contentHeight - this.viewportHeight));
   }

   pageRight() {
      this.scrollLeftChange.emit(Math.min(this.scrollLeft + this.viewportWidth - 5,
                                          this.contentWidth - this.viewportWidth));
   }

   pageUp() {
      this.scrollTopChange.emit(Math.max(this.scrollTop - this.viewportHeight + 5, 0));
   }

   pageLeft() {
      this.scrollLeftChange.emit(Math.max(this.scrollLeft - this.viewportWidth + 5, 0));
   }
}
