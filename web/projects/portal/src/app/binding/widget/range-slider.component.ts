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
import { Component, EventEmitter, Input, Output, Renderer2, ViewChild, ElementRef,
         HostListener, OnInit } from "@angular/core";
import { RangeSliderOptions } from "./range-slider-options";

interface SliderTick {
   left: string;
   label: String;
}

enum Handle { Left, Middle, Right, None }

@Component({
   selector: "range-slider",
   templateUrl: "range-slider.component.html",
   styleUrls: ["range-slider.component.scss"]
})
export class RangeSlider implements OnInit {
   @Input() model: RangeSliderOptions;
   @Output() sliderChanged = new EventEmitter();
   @ViewChild("rangeSlider") sliderDiv: ElementRef;
   @ViewChild("leftHandle") leftHandle: ElementRef;
   handleType = Handle; // for template
   ticks: SliderTick[] = [];
   private mouseDownX: number = NaN; // down position (pageX)
   private mouseOffset: number = 0; // distance to the left handle
   private mouseHandle: Handle = Handle.None;
   private size: number;

   constructor(private renderer: Renderer2) {
   }

   ngOnInit(): void {
      this.size = this.model.max - this.model.min;
      this.ticks = this.getTicks();
   }

   // get the range x (css left) position
   getRangeX(): number {
      return this.model.width * this.model.selectStart / this.size;
   }

   // get the range width in pixels
   getRangeWidth(): number {
      return this.model.width * (this.model.selectEnd - this.model.selectStart) / this.size;
   }

   // get the label for the current range
   getCurrentLabel(): string {
      return this.model.selectStart + ".." + this.model.selectEnd;
   }

   // get the tick positions (css left)
   getTicks(): SliderTick[] {
      let ticks: SliderTick[] = [];

      for(let i = 0; i <= this.size; i = i + 10) {
         let tick: SliderTick = {left: "0px", label: ""};
         let leftOffset: number = this.model.width * i / this.size;
         tick.left = leftOffset - 2 + "px";
         ticks.push(tick);
      }

      return ticks;
   }

   mouseDown(event: MouseEvent, handle: Handle) {
      if(event.button != 0) {
         return;
      }

      this.mouseDownX = event.pageX;
      this.mouseHandle = handle;

      if(handle == Handle.Middle) {
         this.mouseOffset = event.pageX -
            this.leftHandle.nativeElement.getBoundingClientRect().left;
      }

      const mouseMoveListener: Function = this.renderer.listen(
         "document", "mousemove", (evt: MouseEvent) => {
            this.mouseMove(evt);
         });

      const cancelMouseUp: Function = this.renderer.listen("document", "mouseup", () => {
         this.mouseHandle = Handle.None;
         mouseMoveListener();
         cancelMouseUp();
      });
   }

   private mouseMove(event: MouseEvent) {
      switch(this.mouseHandle) {
      case Handle.Left:
         this.model.selectStart = Math.min(this.model.selectEnd,
                                           Math.max(0, this.getIndex(event.pageX, true)));
         break;
      case Handle.Right:
         this.model.selectEnd = Math.max(this.model.selectStart,
                                         this.getIndex(event.pageX, false));
         break;
      case Handle.Middle:
         let start = this.getIndex(event.pageX - this.mouseOffset, true);
         const range = this.model.selectEnd - this.model.selectStart;
         start = Math.max(0, start);
         start = Math.min(start, this.size - range);
         const diff = start - this.model.selectStart;
         this.model.selectStart += diff;
         this.model.selectEnd += diff;
         break;
      default:
      }

      this.valueChanged();
   }

   // get the index (in selection list) of the mouse position
   private getIndex(pageX: number, left: boolean): number {
      const offset: number = pageX -
         this.sliderDiv.nativeElement.getBoundingClientRect().left;

      if(left) {
         return this.model.min +
            Math.max(0, Math.round(offset * (this.size - 2) / this.model.width));
      }
      else {
         return Math.min(this.model.max,
                         this.model.min +
                         Math.max(Math.round(offset * (this.size - 1) / this.model.width)));
      }
   }

   private valueChanged() {
      if(this.mouseHandle != Handle.None) {
         this.sliderChanged.emit([this.model.selectStart, this.model.selectEnd]);
      }
   }
}
