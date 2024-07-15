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
import { Renderer2 } from "@angular/core";
import { Point } from "../../../../common/data/point";
import { GuiTool } from "../../../../common/util/gui-tool";

interface DragElement {
   el: HTMLElement;
   lastDragPosition?: Point;
}

export class AssemblyDragScrollHandler {
   public container: HTMLElement;
   public renderer: Renderer2;
   private dragElements: DragElement[];
   private scrollCallbacks: Function[];
   private dragMousePosition: Point | null;
   private dragScrollAnimationFrameId: number | null = null;
   private dragScrollLeft: number | null = null;
   private dragScrollTop: number | null = null;
   private dragScrollXFactor = 0;
   private dragScrollYFactor = 0;
   private ignoreNextScroll: boolean;

   constructor(public readonly dragScrollMargin: number, public readonly dragScrollRate: number) {
      this.reset();
   }

   public reset(): void {
      this.clearAnimationFrame();
      this.dragElements = [];
      this.scrollCallbacks = [];
      this.dragMousePosition = null;
      this.dragScrollLeft = null;
      this.dragScrollTop = null;
      this.ignoreNextScroll = false;
   }

   public addElement(dragElement: DragElement): void {
      this.dragElements.push(dragElement);
   }

   public addScrollCallback(callback: Function) {
      this.scrollCallbacks.push(callback);
   }

   public onDrag(event: MouseEvent): void {
      if(this.dragMousePosition == null) {
         this.dragMousePosition = new Point(event.clientX, event.clientY);
      }
      else {
         this.dragMousePosition.x = event.clientX;
         this.dragMousePosition.y = event.clientY;
      }

      if(this.dragElements.length > 0 && this.dragElements[0].lastDragPosition != null) {
         this.dragElements.forEach((dragEl) => dragEl.lastDragPosition = null);
      }

      const bounds = this.container.getBoundingClientRect();
      this.dragScrollLeft = this.container.scrollLeft;
      this.dragScrollTop = this.container.scrollTop;

      const leftBound = bounds.left + this.dragScrollMargin;
      const topBound = bounds.top + this.dragScrollMargin;
      const rightBound = bounds.right - this.dragScrollMargin - GuiTool.measureScrollbars();
      const bottomBound = bounds.bottom - this.dragScrollMargin - GuiTool.measureScrollbars();

      this.dragScrollXFactor = 0;
      this.dragScrollYFactor = 0;

      if(leftBound >= this.dragMousePosition.x && this.dragScrollLeft > 0) {
         this.dragScrollXFactor = -1;
      }
      else if(rightBound <= this.dragMousePosition.x) {
         this.dragScrollXFactor = 1;
      }

      if(topBound >= this.dragMousePosition.y && this.dragScrollTop > 0) {
         this.dragScrollYFactor = -1;
      }
      else if(bottomBound <= this.dragMousePosition.y) {
         this.dragScrollYFactor = 1;
      }

      const dragScrollCallback = () => {
         if(this.dragMousePosition != null) {
            let rate = this.dragScrollRate;

            // When corner scrolling adjust rate so that the net speed is constant
            if(this.dragScrollXFactor !== 0 && this.dragScrollYFactor !== 0) {
               rate = Math.round(rate / Math.SQRT2);
            }

            let currentScrollLeft: number;
            let currentScrollTop: number;

            if(this.dragScrollXFactor !== 0) {
               currentScrollLeft = this.container.scrollLeft;
            }

            if(this.dragScrollYFactor !== 0) {
               currentScrollTop = this.container.scrollTop;
            }

            if(this.dragScrollXFactor !== 0) {
               const scrollLeft = currentScrollLeft + this.dragScrollXFactor * rate;
               this.renderer.setProperty(this.container, "scrollLeft", scrollLeft);
            }

            if(this.dragScrollYFactor !== 0) {
               const scrollTop = currentScrollTop + this.dragScrollYFactor * rate;
               this.renderer.setProperty(this.container, "scrollTop", scrollTop);
            }

            if(this.dragScrollXFactor !== 0 || this.dragScrollYFactor !== 0) {
               this.onScroll();
               this.ignoreNextScroll = true;
            }

            this.requestAnimationFrame(dragScrollCallback);
         }
         else {
            this.clearAnimationFrame();
         }
      };

      if(this.dragScrollXFactor === 0 && this.dragScrollYFactor === 0) {
         this.clearAnimationFrame();
      }
      else if(!this.isAnimationFrameRequested()) {
         this.requestAnimationFrame(dragScrollCallback);
      }
   }

   public onScroll(): void {
      if(this.ignoreNextScroll) {
         this.ignoreNextScroll = false;
      }
      else if(this.dragScrollTop != null) {
         const currentLeft = this.container.scrollLeft;
         const currentTop = this.container.scrollTop;
         const diffLeft = currentLeft - this.dragScrollLeft;
         const diffTop = currentTop - this.dragScrollTop;

         if(diffLeft !== 0 || diffTop !== 0) {
            for(const dragEl of this.dragElements) {
               if(dragEl.lastDragPosition == null) {
                  const currentElLeft = parseFloat(dragEl.el.style.left);
                  const currentElTop = parseFloat(dragEl.el.style.top);
                  dragEl.lastDragPosition = new Point(currentElLeft, currentElTop);
               }

               const newElLeft = dragEl.lastDragPosition.x + diffLeft;
               const newElTop = dragEl.lastDragPosition.y + diffTop;

               if(diffLeft !== 0) {
                  this.renderer.setStyle(dragEl.el, "left", newElLeft + "px");
               }

               if(diffTop !== 0) {
                  this.renderer.setStyle(dragEl.el, "top", newElTop + "px");
               }
            }

            this.scrollCallbacks.forEach((cb) => cb());
         }
      }
   }

   private isAnimationFrameRequested(): boolean {
      return this.dragScrollAnimationFrameId != null;
   }

   private requestAnimationFrame(callback: FrameRequestCallback): void {
      this.dragScrollAnimationFrameId = window.requestAnimationFrame(callback);
   }

   private clearAnimationFrame(): void {
      window.cancelAnimationFrame(this.dragScrollAnimationFrameId);
      this.dragScrollAnimationFrameId = null;
   }
}
