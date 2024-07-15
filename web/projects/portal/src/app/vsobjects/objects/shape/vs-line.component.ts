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
   AfterViewChecked,
   Component,
   ElementRef,
   Input,
   NgZone,
   OnChanges,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Line } from "../../../common/data/line";
import { Point } from "../../../common/data/point";
import { StyleConstants } from "../../../common/util/style-constants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { VSLineModel } from "../../model/vs-line-model";
import { VSShape } from "./vs-shape";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-line",
   templateUrl: "vs-line.component.html",
   styleUrls: ["vs-line.component.scss"]
})
export class VSLine extends VSShape<VSLineModel> implements AfterViewChecked, OnChanges {
   @Input() selected: boolean = false;
   @ViewChild("line1") line1: ElementRef;
   @ViewChild("line2") line2: ElementRef;
   arrowStartUrl: string;
   arrowEndUrl: string;
   lineLength: number;

   // used to create unique id's because of a webkit bug
   // see (https://bugs.chromium.org/p/chromium/issues/detail?id=109212)
   private runtimeId = "";

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected modalService: NgbModal,
               zone: NgZone,
               contextProvider: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, modalService, zone, contextProvider, dataTipService);
      this.runtimeId = this.viewsheetClient.runtimeId;
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.updateLineStyle();
         this.arrowStartUrl = this.getArrowURL(true);
         this.arrowEndUrl = this.getArrowURL(false);
         this.lineLength = this.getLineLength();
      }
   }

   ngAfterViewChecked() {
      // Do this drawing here because line1 and line2 may not be created yet
      // in OnInit/DoCheck/etc. This will only work because there is no modifying
      // any bindings, just the native element; otherwise this would throw a
      // changed after checked error.
      if(this.line1 && this.line2 && this.isDouble()) {
         this.drawParallelLines();
      }
   }

   isDouble(): boolean {
      return this.model && this.model.lineStyle == StyleConstants.DOUBLE_LINE;
   }

   drawParallelLines(): void {
      let x1: number = this.model.startLeft;
      let y1: number = this.model.startTop;
      let x2: number = this.model.endLeft;
      let y2: number = this.model.endTop;

      // If the total perpendicular distance between the parallel lines is
      // 3 pixels, this value should be 1.5
      let halfOffset: number = 1.5;

      // Address cases where the line is horizontal of vertical.
      if(x2 - x1 == 0) {
         this.line1.nativeElement.x1.baseVal.value = x1 + halfOffset;
         this.line1.nativeElement.y1.baseVal.value = y1;
         this.line1.nativeElement.x2.baseVal.value = x2 + halfOffset;
         this.line1.nativeElement.y2.baseVal.value = y2;
         this.line2.nativeElement.x1.baseVal.value = x1 - halfOffset;
         this.line2.nativeElement.y1.baseVal.value = y1;
         this.line2.nativeElement.x2.baseVal.value = x2 - halfOffset;
         this.line2.nativeElement.y2.baseVal.value = y2;
         return;
      }
      else if(y2 - y1 == 0) {
         this.line1.nativeElement.x1.baseVal.value = x1;
         this.line1.nativeElement.y1.baseVal.value = y1 + halfOffset;
         this.line1.nativeElement.x2.baseVal.value = x2;
         this.line1.nativeElement.y2.baseVal.value = y2 + halfOffset;
         this.line2.nativeElement.x1.baseVal.value = x1;
         this.line2.nativeElement.y1.baseVal.value = y1 - halfOffset;
         this.line2.nativeElement.x2.baseVal.value = x2;
         this.line2.nativeElement.y2.baseVal.value = y2 - halfOffset;
         return;
      }

      // This is the slope of the line original line--the parallel
      // lines will have the same slope
      let m: number = (y2 - y1) / (x2 - x1);

      // This is the y-intercept of the line--we will
      // be calculating the offset for this value to get our desired
      // parallel lines
      let b: number = y1 - m * x1;

      // this calculates the offset for the y-intercept of the parallel
      // lines
      let c: number = halfOffset / Math.cos(Math.atan(m));

      // so now, the equations of our parallel lines are:
      // y = mx + (b + c), y = mx + (b - c)
      // now we need to calculate the endpoints.
      // Figure out the endpoints based on intersection of parallel and perpendicular lines
      // Perpendicular lines being y - y1 = -1/m * (x - x1) and y - y2 = -1/m * (x - x2)
      let tx1: number = (x1 + m * (y1 - b + c)) / ((m * m) + 1);
      let ty1: number = (m * tx1) + b - c;

      let tx2: number = (x2 + m * (y2 - b + c)) / ((m * m) + 1);
      let ty2: number = (m * tx2) + b - c;

      let bx1: number = (x1 + m * (y1 - b - c)) / ((m * m) + 1);
      let by1: number = (m * bx1) + b + c;

      let bx2: number = (x2 + m * (y2 - b - c)) / ((m * m) + 1);
      let by2: number = (m * bx2) + b + c;

      this.line1.nativeElement.x1.baseVal.value = tx1;
      this.line1.nativeElement.y1.baseVal.value = ty1;
      this.line1.nativeElement.x2.baseVal.value = tx2;
      this.line1.nativeElement.y2.baseVal.value = ty2;
      this.line2.nativeElement.x1.baseVal.value = bx1;
      this.line2.nativeElement.y1.baseVal.value = by1;
      this.line2.nativeElement.x2.baseVal.value = bx2;
      this.line2.nativeElement.y2.baseVal.value = by2;
   }

   get lineRotationAngle(): number {
      const start = new Point(this.model.startLeft, this.model.startTop);
      const end = new Point(this.model.endLeft, this.model.endTop);
      const line = new Line(start, end);
      return line.getAngle();
   }

   private getLineLength(): number {
      const start: Point = new Point(this.model.startLeft, this.model.startTop);
      const end: Point = new Point(this.model.endLeft, this.model.endTop);
      const line: Line = new Line(start, end);
      return line.getLength();
   }

   private getArrowURL(start: boolean): string {
      let arrow: string = "";
      let prefix = "";

      if(start) {
         switch(this.model.startAnchorStyle) {
            case StyleConstants.EMPTY_ARROW:
               arrow = "empty-arrow";
               break;
            case StyleConstants.WHITE_ARROW:
               arrow = "white-arrow";
               break;
            case StyleConstants.FILLED_ARROW:
               arrow = "filled-arrow";
               break;
            default:
               arrow = "";
         }

         prefix = "-start";
      }
      else {
         switch(this.model.endAnchorStyle) {
            case StyleConstants.EMPTY_ARROW:
               arrow = "empty-arrow";
               break;
            case StyleConstants.WHITE_ARROW:
               arrow = "white-arrow";
               break;
            case StyleConstants.FILLED_ARROW:
               arrow = "filled-arrow";
               break;
            default:
               arrow = "";
         }

         prefix = "-end";
      }

      if(arrow === "") {
         return "";
      }

      arrow += prefix + this.markerSuffix;

      return `url(#${arrow})`;
   }

   get markerSuffix(): string {
      return (this.runtimeId + this.getAssemblyName()).replace(/[\W_]+/g, "_");
   }
}
