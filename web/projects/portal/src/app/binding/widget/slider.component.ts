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
import { Component, EventEmitter, Input, OnInit, Output, Renderer2,
         ChangeDetectorRef } from "@angular/core";
import { SliderOptions } from "./slider-options";
import { GuiTool } from "../../common/util/gui-tool";

interface SliderTick {
   left: string;
   label: String;
}

@Component({
   selector: "slider", // eslint-disable-line @angular-eslint/component-selector
   templateUrl: "slider.component.html",
   styleUrls: ["slider.component.scss"]
})
export class Slider implements OnInit {
   @Input() model: SliderOptions;
   @Input() enabled: boolean = true;
   @Output() sliderChanged = new EventEmitter();
   @Output() changeCompleted = new EventEmitter();
   ticks: SliderTick[] = [];
   private mouseDownX: number = NaN;
   private mouseDelta: number = 0;
   public sliderWidth: number = 270;

   constructor(private renderer: Renderer2,
               private changeRef: ChangeDetectorRef) {
   }

   public ngOnInit(): void {
      this.ticks = this.getTicks();
   }

   // get the width of the slider line, used to calculate the various positions.
   getLineWidth(): number {
      return this.sliderWidth;
   }

   // get the current (handle) position
   getValueX(): number {
      let curr = (this.model.value - this.model.min) /
         (this.model.max - this.model.min) * this.getLineWidth();

      if(!isNaN(this.mouseDownX)) {
         curr += this.mouseDelta;
      }

      return Math.max(0, Math.min(curr, this.getLineWidth()));
   }

   // get the current value label
   getLabel(): string {
      if(!isNaN(this.mouseDownX)) {
         const x = this.getValueX();
         return this.toLabel(this.model.min + (x / this.getLineWidth()) *
            (this.model.max - this.model.min));
      }

      return this.toLabel(this.model.value);
   }

   get isDragging(): boolean {
      return !isNaN(this.mouseDownX);
   }

   // css left for value thumb
   getValueLeft(): string {
      return this.getValueX() + "px";
   }

   // css left for value label — CSS uses transform: translateX(-50%) to centre on this position
   getLabelLeft(): string {
      return this.getValueX() + "px";
   }

   getTicks(): SliderTick[] {
      const lineWidth = this.getLineWidth();
      const numLong: number = this.model.max - this.model.min;
      const incCount: number = Math.ceil(numLong / this.model.increment);
      const incWidth = (this.model.increment / numLong) * lineWidth;
      const jump = this.getJump(this.getValueLabels());
      const tickjump = (incCount / jump < 4) ? Math.floor(jump / 2) : jump;

      // Max tick is only shown when it lands exactly on a tickjump boundary so
      // all gaps are identical. Otherwise it is dropped rather than squished.
      const maxFitsEvenly = tickjump === 0 || incCount % tickjump === 0;
      const ticks: SliderTick[] = [];

      for(let i = 0; i <= incCount; i++) {
         const isFirst = i === 0;
         const isLast = i === incCount;
         const isRegular = !isFirst && !isLast && (tickjump === 0 || i % tickjump === 0);

         if((isFirst && this.model.minVisible) ||
            (isLast && this.model.maxVisible && maxFitsEvenly) ||
            isRegular)
         {
            // Position is derived from the value index so each tick lands on
            // the exact pixel that corresponds to its whole-number value.
            const x = Math.min(i * incWidth, lineWidth);
            const value = this.model.min + i * this.model.increment;
            ticks.push({label: this.toLabel(value), left: x + "px"});
         }
      }

      return ticks;
   }

   // get the tick labels
   private getValueLabels(): Array<string> {
      const nticks = Math.floor((this.model.max - this.model.min) /
         this.model.increment);
      const values: Array<string> = [];
      const labels = this.model.labels;

      for(let i = 0; i < nticks; i++) {
         values.push((labels && labels[i]) ? labels[i]
            : this.toLabel(this.model.min + i * this.model.increment));
      }

      return values;
   }

   // get the number of tick labels to skip
   private getJump(values: Array<string>): number {
      for(let i = 1; i < values.length; i++) {
         if(!this.isValuesOverlapped(values, i)) {
            return i;
         }
      }

      return values.length;
   }

   // check if the labels will overlap if we skip number (jump - 1) of items in between
   private isValuesOverlapped(values: Array<string>, jump: number) {
      if(jump >= values.length) {
         return true;
      }

      const incWidthDelta = this.getLineWidth() / values.length;
      const VALUE_GAP = 5;

      for(let i = 0; i + jump < values.length; i += jump) {
         const length1 = this.getDefaultLabelWidth(values[i]);
         const length2 = this.getDefaultLabelWidth(values[i + jump]);

         if((length1 + length2) / 2 + VALUE_GAP > incWidthDelta * jump) {
            return true;
         }
      }

      return false;
   }

   // get the width for the label
   private getDefaultLabelWidth(value: string) {
      return GuiTool.measureText(value, "10px arial") + 4;
   }

   // nice number string
   private toLabel(v: number): string {
      return isNaN(v) ? "" : this.fixNum(v);
   }

   // round number to the number of significant digits
   private fixNum(num: number): string {
      return num.toFixed(this.getFractionDigits());
   }

   // find the number of digits after decimal point
   private getFractionDigits(): number {
      let incstr = this.model.increment + "";
      let incidx = incstr.indexOf(".") < 0 ? 0 :
      incstr.length - incstr.indexOf(".") - 1;

      if(incidx == 1 && incstr.charAt(incstr.indexOf(".") + 1) == "0") {
         return 0;
      }

      return incidx;
   }

   private getIncrementPrecision() {
      let incPrecision = 2;
      const incStr = this.model.increment + "";
      const index = incStr.indexOf(".");

      if(index != -1) {
         incPrecision = Math.max(incStr.substring(index).length, 2);
      }

      return incPrecision;
   }

   moveHandleHere(event: MouseEvent): void {
      const x = Math.max(0, Math.min(event.offsetX, this.getLineWidth()));
      this.model.value = this.model.min + (x / this.getLineWidth()) * (this.model.max - this.model.min);
      this.sliderChanged.emit(parseInt(this.toLabel(this.model.value), 10));
      this.changeCompleted.emit(true);
      this.changeRef.detectChanges();
   }

   mouseDown(event: MouseEvent) {
      if(event.button != 0 || !this.enabled) {
         return;
      }

      this.mouseDelta = 0;
      this.mouseDownX = event.pageX;

      // Register global listeners so dragging outside the component still works.
      // Without this, mousemove stops firing when the cursor exits the container,
      // making it impossible to reach the min and max values.
      const cancelMouseMove: Function =
         this.renderer.listen("document", "mousemove", (e: MouseEvent) => {
            this.mouseDelta = e.pageX - this.mouseDownX;
            const newValue = parseInt(this.getLabel(), 10);
            this.sliderChanged.emit(newValue);
            this.changeRef.detectChanges();
         });

      const cancelMouseUp: Function =
         this.renderer.listen("document", "mouseup", () => {
            this.model.value = parseFloat(this.getLabel());
            this.mouseDownX = NaN;
            cancelMouseMove();
            cancelMouseUp();
            this.changeCompleted.emit(true);
         });
   }

   mouseMove(event: MouseEvent) {
      // Handled by the document-level listener registered in mouseDown.
   }
}
