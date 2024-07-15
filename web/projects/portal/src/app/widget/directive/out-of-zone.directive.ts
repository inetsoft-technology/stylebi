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
import {
   Directive,
   ElementRef,
   EventEmitter,
   NgZone,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";

@Directive({
   selector: "[outOfZone]"
})
export class OutOfZoneDirective implements OnInit, OnDestroy {
   @Output() onDragover = new EventEmitter<any>();
   @Output() onDragleave = new EventEmitter<any>();
   @Output() onMouseenter = new EventEmitter<any>();
   @Output() onMouseleave = new EventEmitter<any>();
   @Output() onMousedown = new EventEmitter<any>();
   @Output() onMouseup = new EventEmitter<any>();
   @Output() onDocMousemove = new EventEmitter<any>();
   @Output() onMousemove = new EventEmitter<any>();
   @Output() onMouseout = new EventEmitter<any>();
   @Output() onMouseover = new EventEmitter<any>();
   @Output() onScroll = new EventEmitter<any>();
   @Output() onDocKeydown = new EventEmitter<any>();
   @Output() onKeydown = new EventEmitter<any>();
   @Output() onClick = new EventEmitter<any>();
   @Output() onWheel = new EventEmitter<WheelEvent>();

   docKeydown: (any) => void;
   docMousemove: (any) => void;

   constructor(private _ngZone: NgZone, private el: ElementRef<HTMLElement>) {
   }

   ngOnInit(): void {
      const handlers = [
         {event: "dragover", handler: this.onDragover},
         {event: "dragleave", handler: this.onDragleave},
         {event: "mouseenter", handler: this.onMouseenter},
         {event: "mouseleave", handler: this.onMouseleave},
         {event: "mousedown", handler: this.onMousedown},
         {event: "mouseup", handler: this.onMouseup},
         {event: "mousemove", handler: this.onMousemove},
         {event: "mouseout", handler: this.onMouseout},
         {event: "mouseover", handler: this.onMouseover},
         {event: "scroll", handler: this.onScroll},
         {event: "keydown", handler: this.onKeydown},
         {event: "click", handler: this.onClick},
      ];

      this._ngZone.runOutsideAngular(() => {
         const nativeElement = this.el.nativeElement;

         for(let i = 0; i < handlers.length; i++) {
            const h = handlers[i];

            if(h.handler.observers.length > 0) {
               const _handler = $event => {
                  h.handler.emit($event);
               };

               nativeElement.addEventListener(h.event, _handler, false);
            }
         }

         if(this.onDocKeydown.observers.length > 0) {
            this.docKeydown = ($event) => this.onDocKeydown.emit($event);
            document.addEventListener("keydown", this.docKeydown, false);
         }

         if(this.onDocMousemove.observers.length > 0) {
            this.docMousemove = ($event) => this.onDocMousemove.emit($event);
            window.addEventListener("mousemove", this.docMousemove, false);
         }

         if(this.onWheel.observers.length > 0) {
            // Passive false so that the default event behavior can be prevented.
            nativeElement.addEventListener("wheel", (e) => this.onWheel.emit(e), {passive: false});
         }
      });
   }

   ngOnDestroy(): void {
      if(this.docKeydown) {
         document.removeEventListener("keydown", this.docKeydown);
      }

      if(this.docMousemove) {
         window.removeEventListener("mousemove", this.docMousemove);
      }
   }
}
