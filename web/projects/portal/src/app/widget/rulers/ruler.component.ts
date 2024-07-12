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
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   ElementRef,
   Input,
   ViewChild
} from "@angular/core";

declare const window;

@Component({
   selector: "w-ruler",
   templateUrl: "ruler.component.html",
   styleUrls: ["ruler.component.scss"]
})
export class Ruler implements AfterViewInit {
   @Input() horizontal: boolean = true;
   @Input() showGuides: boolean = false;
   @Input() guideTop: number = 0;
   @Input() guideLeft: number = 0;
   @Input() guideWidth: number = 0;
   @Input() guideHeight: number = 0;
   @Input() top: number = 0;
   @Input() left: number = 0;
   @Input() bottom: number = 0;
   @Input() right: number = 0;
   @Input() set scale(value: number) {
      this._scale = Number(value.toFixed(2));
      this.updateRulerSize();
   }

   @ViewChild("ruler", {static: true}) ruler: ElementRef;
   @ViewChild("canvas", {static: true}) canvas: ElementRef;

   private _scale: number = 1;

   get guideTopStyle(): number {
      return this.horizontal ? 0 : this.guideTop;
   }

   get guideLeftStyle(): number {
      return this.horizontal ? this.guideLeft : 0;
   }

   get guideWidthStyle(): string {
      return this.horizontal ? `${this.guideWidth}px` : "100%";
   }

   get guideHeightStyle(): string {
      return this.horizontal ? "100%" : `${this.guideHeight}px`;
   }

   get rulerTopStyle(): number {
      return this.horizontal ? this.top : this.top + 18;
   }

   get rulerLeftStyle(): number {
      return this.horizontal ? this.left + 18 : this.left;
   }

   get rulerBottomStyle(): string {
      return this.horizontal ? "auto" : `${this.bottom}px`;
   }

   get rulerRightStyle(): string {
      return this.horizontal ? `${this.right}px` : "auto";
   }

   constructor(private changeDetector: ChangeDetectorRef) {
   }

   ngAfterViewInit(): void {
      this.updateRulerSize();
   }

   preventMouseEvents(event: any) {
      event.preventDefault();
   }

   updateRulerSize(): void {
      if(this.ruler.nativeElement.offsetParent === null) {
         // If offsetParent is null, this element, or one of its ancestors, has the CSS display
         // property set to none. In this case, the clientWidth and clientHeight properties will be
         // 0 and the ticks will be lost, so bail out.
         return;
      }

      const rulerWidth = this.ruler.nativeElement.clientWidth;
      const rulerHeight = this.ruler.nativeElement.clientHeight;
      const flip = !this.horizontal;

      this.canvas.nativeElement.setAttribute("width", rulerWidth);
      this.canvas.nativeElement.setAttribute("height", rulerHeight);

      const cssStyle = window.getComputedStyle(this.ruler.nativeElement);
      const context = this.canvas.nativeElement.getContext("2d");

      context.save();
      context.font = `${cssStyle.fontSize} ${cssStyle.fontFamily}`;
      context.textBaseline = flip ? "bottom" : "top";
      context.fillStyle = `${cssStyle.color}`;
      context.strokeStyle = `${cssStyle.color}`;
      context.lineWidth = 1;

      let size: number;
      let offset: number;
      let tickPosition = 0;
      let tickLabel = 0;

      if(flip) {
         size = rulerHeight;
         offset = rulerWidth;
         context.rotate(Math.PI / 2);
         context.translate(0, -offset);
      }
      else {
         size = rulerWidth;
         offset = rulerHeight;
      }

      context.clearRect(0, 0, size, offset);
      const adv = this._scale > 0.6 ? 5 : (this._scale > 0.4 ? 10 : (this._scale > 0.2 ? 20 : 200));

      while(tickPosition <= size) {
         if((tickPosition % (50 * this._scale)) === 0) { // label
            const y1 = flip ? 0 : offset;
            const y2 = flip ? offset - 1 : 1;

            if(tickPosition > 0) {
               context.beginPath();
               context.moveTo(tickPosition, y1);
               context.lineTo(tickPosition, y2);
               context.stroke();
            }

            context.fillText(`${tickLabel}`, tickPosition + 1, y2);
         }
         else if((tickPosition % (10 * this._scale)) == 0) { // major
            const y1 = flip ? 0 : offset;
            const y2 = flip ? offset / 3 : offset - offset / 3;
            context.beginPath();
            context.moveTo(tickPosition, y1);
            context.lineTo(tickPosition, y2);
            context.stroke();
         }
         else if((tickPosition % (5 * this._scale)) == 0) { // minor
            const y1 = flip ? 0 : offset;
            const y2 = flip ? offset / 4 : offset - offset / 4;
            context.beginPath();
            context.moveTo(tickPosition, y1);
            context.lineTo(tickPosition, y2);
            context.stroke();
         }

         tickPosition += adv * this._scale;
         tickLabel += adv;
      }

      context.restore();
      this.changeDetector.detectChanges();
   }
}
