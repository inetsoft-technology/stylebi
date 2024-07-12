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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild,
         ElementRef } from "@angular/core";
import { LineStyle } from "../../common/data/line-style";

@Component({
   selector: "style-dropdown",
   templateUrl: "style-dropdown.component.html",
   styleUrls: ["style-dropdown.component.scss"]
})
export class StyleDropdown implements OnInit {
   @Input() style: string;
   @Input() color: string;
   @Input() noneAvailable: boolean = true;
   @Output() styleChange: EventEmitter<string> = new EventEmitter<string>();
   @Input() isPresenter: boolean = false;
   @ViewChild("dropdownBody") dropdownBody: ElementRef;
   public LineStyle = LineStyle;
   public lineStyles: string[];

   ngOnInit(): void {
      const lineStyles = this.noneAvailable ? [LineStyle.NONE] : [];

      if(this.isPresenter) {
         this.lineStyles = lineStyles.concat([
            LineStyle.THIN_LINE,
            LineStyle.MEDIUM_LINE,
            LineStyle.THICK_LINE,
            LineStyle.DOUBLE_LINE,
            LineStyle.DOT_LINE,
            LineStyle.DASH_LINE,
            LineStyle.THIN_THIN_LINE,
            LineStyle.ULTRA_THIN_LINE,
            LineStyle.MEDIUM_DASH,
            LineStyle.LARGE_DASH,
            LineStyle.RAISED_3D,
            LineStyle.LOWERED_3D,
            LineStyle.DOUBLE_3D_RAISED,
            LineStyle.DOUBLE_3D_LOWERED
         ]);
      }
      else {
         this.lineStyles = lineStyles.concat([
            LineStyle.THIN_LINE,
            LineStyle.MEDIUM_LINE,
            LineStyle.THICK_LINE,
            LineStyle.DOUBLE_LINE,
            LineStyle.DOT_LINE,
            LineStyle.DASH_LINE,
            LineStyle.MEDIUM_DASH,
            LineStyle.LARGE_DASH
         ]);
      }
   }

   choose(style: string): void {
      this.style = style;
      this.styleChange.emit(style);
   }

   get dropdownMinWidth(): number {
      return this.dropdownBody && this.dropdownBody.nativeElement
         ? this.dropdownBody.nativeElement.offsetWidth : null;
   }
}
