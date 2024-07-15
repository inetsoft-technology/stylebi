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
import { Directive, Input, Output, EventEmitter, ElementRef, AfterViewInit } from "@angular/core";

@Directive({
   selector: "[touchScroll]"
})
export class TouchScrollDirective implements AfterViewInit {
   @Input() touchScrollEnabled: boolean = true;
   @Output() vScroll: EventEmitter<number> = new EventEmitter<number>();
   @Output() hScroll: EventEmitter<number> = new EventEmitter<number>();
   @Output() scrollEnd: EventEmitter<any> = new EventEmitter<any>();

   touchmove = (event) => this.touchMove(event);
   touchend = (event) => this.touchEnd(event);
   downX: number;
   downY: number;

   constructor(private host: ElementRef) {
   }

   ngAfterViewInit() {
      const elem = this.host.nativeElement;
      elem.addEventListener("touchstart", (event) => this.touchStart(event), true);
   }

   touchStart(event: TouchEvent) {
      if(!this.touchScrollEnabled) {
         return;
      }

      const elem = this.host.nativeElement;
      this.downX = event.touches[0].pageX;
      this.downY = event.touches[0].pageY;
      elem.addEventListener("touchmove", this.touchmove, true);
      elem.addEventListener("touchend", this.touchend, true);
   }

   touchMove(event: TouchEvent) {
      const elem = this.host.nativeElement;
      const deltaX = event.touches[0].pageX - this.downX;
      const deltaY = event.touches[0].pageY - this.downY;
      this.downX = event.touches[0].pageX;
      this.downY = event.touches[0].pageY;

      this.vScroll.emit(deltaY);
      this.hScroll.emit(deltaX);
   }

   touchEnd(event: TouchEvent) {
      const elem = this.host.nativeElement;
      elem.removeEventListener("touchmove", this.touchmove, true);
      elem.removeEventListener("touchend", this.touchend, true);
   }
}
