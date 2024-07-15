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
   HostBinding,
   HostListener,
   OnInit,
   OnDestroy,
   Renderer2,
   ChangeDetectorRef,
   NgZone,
   Input,
   Output
} from "@angular/core";
import { GuiTool } from "../../common/util/gui-tool";

export enum ResizeOptions {
   TOP = 0b0001,
   RIGHT = 0b0010,
   BOTTOM = 0b0100,
   LEFT = 0b1000
}

export enum InteractArea {
   TOP_EDGE,
   RIGHT_EDGE,
   BOTTOM_EDGE,
   LEFT_EDGE,
   CENTER
}

export interface InteractInfo {
   x: number;
   y: number;
   area: InteractArea;
}

@Directive({
   selector: "[elementInteractable]"
})
export class ElementInteractDirective implements OnInit, OnDestroy {
   @Input() resizeOptions: number =
      ResizeOptions.TOP | ResizeOptions.RIGHT |
      ResizeOptions.BOTTOM | ResizeOptions.LEFT;

   @Input() isResizable: boolean = false;
   @Input() isMoveable: boolean = false;
   @Input() margin = GuiTool.isMobileDevice() ? 10 : 5;
   @Input() defaultCursor = "default";
   @Input() cursorAreaName = null;
   @Input() moveableAreaName = null;

   @Output() onStartResize = new EventEmitter<InteractInfo>();
   @Output() onStartMove = new EventEmitter<InteractInfo>();
   @Output() onEndResizeOrMove = new EventEmitter<InteractInfo>();

   private _clientRectangle: ClientRect;
   private mousemoveListener: () => void;

   constructor(private elementRef: ElementRef,
               private changeRef: ChangeDetectorRef,
               private renderer: Renderer2,
               private zone: NgZone) {
   }

   @HostBinding("style.cursor") cursor: string;

   ngOnInit() {
      this.zone.runOutsideAngular(() => {
         if(!this.isResizable && !this.isMoveable) {
            return;
         }

         this.mousemoveListener = this.renderer.listen(
            this.elementRef.nativeElement, "mousemove", (event) => this.onMove(event));
      });
   }

   ngOnDestroy() {
      if(!!this.mousemoveListener) {
         this.mousemoveListener();
         this.mousemoveListener = null;
      }
   }

   onMove(event: MouseEvent): void {
      // Change cursor if no mouse buttons are pressed
      if(event.buttons === 0) {
         const cursor = this.getCursor(this.getCurrentArea(event));

         if(cursor != this.cursor) {
            this.cursor = cursor;
            this.changeRef.detectChanges();
         }
      }
   }

   @HostListener("mousedown", ["$event"])
   onDown(event: MouseEvent): void {
      if(event.button != 0) {
         return;
      }

      this._clientRectangle = this.elementRef.nativeElement.getBoundingClientRect();
      const currentArea = this.getCurrentArea(event);
      const resizeInfo: InteractInfo = {
         x: event.clientX,
         y: event.clientY,
         area: currentArea
      };

      if(this.isMoveable && currentArea === InteractArea.CENTER) {
         this.onStartMove.emit(resizeInfo);
      }
      else if(this.isResizable && currentArea != InteractArea.CENTER && currentArea != null) {
         this.onStartResize.emit(resizeInfo);
      }

      // Since angular doesn't allow conditionally creating host listeners, register the
      // mouseup event listener on mousedown so we don't fire on every window mouseup
      // then remove immediately after callback
      let upListener = (evt: MouseEvent) => {
         this.onUp(evt, currentArea);
         window.removeEventListener("mouseup", upListener);
      };

      window.addEventListener("mouseup", upListener);
   }

   onUp(event: MouseEvent, currentArea: InteractArea): void {
      if(currentArea != null && (this.isResizable || this.isMoveable)) {
         let interactInfo: InteractInfo = {
            x: event.clientX,
            y: event.clientY,
            area: currentArea
         };

         this.onEndResizeOrMove.emit(interactInfo);
      }

      this.cursor = this.defaultCursor;
   }

   /**
    * Get the area that the mouse is on in the host element
    * @param event a mouse position given by a MouseEvent
    * @returns {InteractArea} a value from the InteractArea enum
    */
   private getCurrentArea(event: MouseEvent): InteractArea {
      let clientRectangle = this._clientRectangle != null ? this._clientRectangle :
         this.elementRef.nativeElement.getBoundingClientRect();
      let top = 0;
      let bottom = clientRectangle.height;
      let left = 0;
      let right = clientRectangle.width;
      let resizeEdge: InteractArea = this.isMoveable ? InteractArea.CENTER : null;

      if(this.isResizable) {
         if(this.resizeOptions & ResizeOptions.TOP && event.offsetY < top + this.margin) {
            resizeEdge = InteractArea.TOP_EDGE;
         }
         else if(this.resizeOptions & ResizeOptions.RIGHT && event.offsetX > right - this.margin) {
            resizeEdge = InteractArea.RIGHT_EDGE;
         }
         else if(this.resizeOptions & ResizeOptions.BOTTOM && event.offsetY > bottom - this.margin) {
            resizeEdge = InteractArea.BOTTOM_EDGE;
         }
         else if(this.resizeOptions & ResizeOptions.LEFT && event.offsetX < left + this.margin) {
            resizeEdge = InteractArea.LEFT_EDGE;
         }
      }

      return resizeEdge;
   }

   /**
    * Emit the correct cursor for a given edge
    */
   private getCursor(resizeEdge: InteractArea): string {
      let cursor: string;

      switch(resizeEdge) {
      case InteractArea.TOP_EDGE:
      case InteractArea.BOTTOM_EDGE:
         cursor = "row-resize";
         break;
      case InteractArea.RIGHT_EDGE:
      case InteractArea.LEFT_EDGE:
         cursor = "col-resize";
         break;
      default:
         cursor = this.isMoveable && this.cursorAreaName == this.moveableAreaName
            ? "move" : this.defaultCursor;
      }

      return cursor;
   }
}
