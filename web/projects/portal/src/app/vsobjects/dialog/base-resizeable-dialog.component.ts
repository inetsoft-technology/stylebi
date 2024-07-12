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
import { AfterViewInit, Component, ElementRef, Input, Renderer2 } from "@angular/core";

@Component({
   selector: "base-resizeable-dialog",
   templateUrl: "./base-resizeable-dialog.component.html"
})
export class BaseResizeableDialogComponent implements AfterViewInit {
   @Input() resizeable: boolean = true;
   @Input() inSlide: boolean = false;
   resizeX: number = null;
   resizeY: number = null;
   resizeW: number = null;
   resizeH: number = null;
   protected resizeInited: boolean;
   private resizeListener: () => void;
   private resizeEndListener: () => void;
   private minSize: number = 200;
   private maxWidth: number = -1;

   constructor(protected renderer: Renderer2, protected element: ElementRef) { }

   ngAfterViewInit(): void {
      if(!this.inSlide && this.resizeable && !!this.renderer && !!this.element) {
         const resizeHandle = this.renderer.createElement("div");
         this.renderer.addClass(resizeHandle, "dialog-resize-handle");
         this.renderer.addClass(resizeHandle, "resize-bottom-right-icon");
         this.renderer.appendChild(this.element.nativeElement, resizeHandle);
         this.renderer.listen(resizeHandle, "mousedown",
            (evt: MouseEvent) => this.startResize(evt));

         if(!!this.element) {
            let nativeEle: any = this.element.nativeElement;
            this.renderer.setStyle(nativeEle, "display", "flex");
            this.renderer.setStyle(nativeEle, "flex-direction", "column");
            this.renderer.setStyle(nativeEle, "flex", "auto");
            this.renderer.setStyle(nativeEle, "overflow", "hidden");
         }
      }
   }

   startResize(event: MouseEvent) {
      this.resizeX = event.pageX;
      this.resizeY = event.pageY;
      let explicitWidth = this.element.nativeElement.getBoundingClientRect().width;
      let explicitHeight = this.element.nativeElement.getBoundingClientRect().height;
      this.resizeW = explicitWidth;
      this.resizeH = explicitHeight;
      let dialogContent: any = this.getDialogContent("modal-dialog");

      if(!this.resizeInited) {
         let modalContent: any = this.getDialogContent("modal-content");
         let modal = this.getDialogContent("modal");
         let modalBody = this.element.nativeElement.querySelector(".modal-body");

         if(!modalContent || !modal || !modalBody) {
            return;
         }
         else {
            this.renderer.setStyle(modalContent, "height", "100%");
            this.renderer.setStyle(modalContent, "max-height", "100%");
            this.renderer.setStyle(modalContent, "width", "100%");
            this.renderer.setStyle(modalContent, "max-width", "100%");
            this.renderer.setStyle(modalBody, "overflow", "auto");
            this.maxWidth = modal.getBoundingClientRect().width - 30;
            this.resizeInited = true;
         }
      }

      this.changeSize(dialogContent, explicitWidth, explicitHeight);
      this.resizeListener = this.renderer.listen("document", "mousemove", (evt: MouseEvent) => {
         let dw = evt.pageX - this.resizeX;
         let dh = evt.pageY - this.resizeY;
         explicitWidth = this.resizeW + 2 * dw;
         explicitWidth = explicitWidth < this.minSize ? this.minSize : explicitWidth;
         explicitHeight = this.resizeH + dh;
         explicitHeight = explicitHeight < this.minSize ? this.minSize : explicitHeight;
         this.changeSize(dialogContent, explicitWidth, explicitHeight);
         evt.preventDefault();
         evt.stopPropagation();
      });
      this.resizeEndListener = this.renderer.listen("document",
         "mouseup", () => {
         this.resizeListener();
         this.resizeEndListener();
      });
   }

   private changeSize(ref: any, width: number, height: number) {
      if(!!ref && width <= this.maxWidth) {
         this.renderer.setStyle(ref, "max-width", width + "px");
         this.renderer.setStyle(ref, "max-height", "93vh");
         this.renderer.setStyle(ref, "width", width + "px");
         this.renderer.setStyle(ref, "height", height + "px");
      }
   }

   getDialogContent(classId: string): Element {
      if(!this.element) {
         return null;
      }

      let ele = this.element.nativeElement;

      while(!!ele && !ele.classList.contains(classId)) {
         ele = ele.parentElement;
      }

      return ele;
   }

   get canResize() {
      return this.resizeable && !this.inSlide;
   }

   get resized(): boolean {
      return this.resizeInited;
   }
}
