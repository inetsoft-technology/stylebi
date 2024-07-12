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
import { Component, Input, Output, OnInit, EventEmitter, ViewChild, ElementRef
       } from "@angular/core";
import { StyleConstants } from "../../common/util/style-constants";
import { ChartConfig } from "../../common/util/chart-config";

@Component({
   selector: "grid-line-dropdown",
   templateUrl: "grid-line-dropdown.component.html",
   styleUrls: ["grid-line-dropdown.component.scss"]
})
export class GridLineDropdown implements OnInit {
   @Input() lineStyle: number;
   @Input() disabled: boolean = false;
   @Input() supportDefault: boolean = false;
   @Output() lineStyleChange: EventEmitter<number> = new EventEmitter<number>();
   @ViewChild("dropdownBody") dropdownBody: ElementRef;
   private lineStyles0: number[] = [
      StyleConstants.NONE,
      StyleConstants.THIN_LINE,
      StyleConstants.MEDIUM_LINE,
      StyleConstants.THICK_LINE,
      StyleConstants.DOT_LINE,
      StyleConstants.DASH_LINE,
      StyleConstants.THIN_THIN_LINE,
      StyleConstants.ULTRA_THIN_LINE,
      StyleConstants.MEDIUM_DASH,
      StyleConstants.LARGE_DASH
   ];
   lineStyles: number[] = this.lineStyles0;

   ngOnInit() {
      if(this.supportDefault) {
         this.lineStyles = [-1].concat(this.lineStyles0);
      }
   }

   public choose(lineStyle: number): void {
      this.lineStyle = lineStyle;
      this.lineStyleChange.emit(lineStyle);
   }

   getLineStyleName(val: number) {
      return ChartConfig.getLineStyleName(val);
   }

   get dropdownMinWidth(): number {
      return this.dropdownBody && this.dropdownBody.nativeElement
         ? this.dropdownBody.nativeElement.offsetWidth : null;
   }
}
