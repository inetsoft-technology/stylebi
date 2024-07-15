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
import { AfterViewInit, Component, ElementRef, Input, Renderer2 } from "@angular/core";

@Component({
   selector: "em-base-resizeable-dialog",
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
         let dialogContent: any = this.getDialogContent("mat-dialog-container");

         if(!dialogContent) {
            return;
         }

         this.renderer.appendChild(dialogContent, resizeHandle);
         this.renderer.setStyle(dialogContent, "position", "relative");
         this.renderer.setStyle(dialogContent, "overflow", "hidden");
         this.renderer.setStyle(dialogContent, "display", "flex");
         this.renderer.setStyle(this.element.nativeElement, "display", "flex");
         this.renderer.setStyle(this.element.nativeElement, "flex-direction", "column");
         this.renderer.setStyle(this.element.nativeElement, "flex", "auto");
         this.renderer.setStyle(this.element.nativeElement, "width", "100%");

         this.renderer.listen(resizeHandle, "mousedown",
            (evt: MouseEvent) => this.startResize(evt));
      }
   }

   startResize(event: MouseEvent) {
      this.resizeX = event.pageX;
      this.resizeY = event.pageY;
      let dialogContainer: any = this.getDialogContent("mat-dialog-container");
      let explicitWidth = dialogContainer.getBoundingClientRect().width;
      let explicitHeight = dialogContainer.getBoundingClientRect().height;
      this.resizeW = explicitWidth;
      this.resizeH = explicitHeight;
      let dialogContent: any = this.getDialogContent("cdk-overlay-pane");

      if(!this.resizeInited) {
         let overlayWrapper = this.getDialogContent("cdk-global-overlay-wrapper");
         this.maxWidth = overlayWrapper.getBoundingClientRect().width - 30;
         this.resizeInited = true;
      }

      this.changeSize(dialogContent, explicitWidth, explicitHeight);
      this.resizeListener = this.renderer.listen("document", "mousemove", (evt: MouseEvent) => {
         let dw = evt.pageX - this.resizeX;
         let dh = evt.pageY - this.resizeY;
         explicitWidth = this.resizeW + 2 * dw;
         explicitWidth = explicitWidth < this.minSize ? this.minSize : explicitWidth;
         explicitHeight = this.resizeH + 2 * dh;
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
