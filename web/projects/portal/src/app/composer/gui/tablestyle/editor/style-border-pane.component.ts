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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from "@angular/core";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import {StyleConstants} from "../../../../common/util/style-constants";

@Component({
   selector: "style-border-pane",
   templateUrl: "style-border-pane.component.html",
   styleUrls: ["table-style-format-pane.component.scss"],
})
export class StyleBorderPaneComponent implements OnInit, OnChanges {
   @Input() borderStyle: number = StyleConstants.THIN_LINE;
   @Output() borderChange: EventEmitter<number> = new EventEmitter<number>();
   styles: Array<{ label: string, value: number, cssClass: string }> = [];
   cssClass: string;

   ngOnInit() {
      this.styles = TableStyleUtil.STYLE_BORDER_STYLES;
      this.updateParameters();
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(!!this.styles && this.styles.length > 0) {
         this.updateParameters();
      }
   }

   updateParameters(): void {
      this.cssClass = this.getStyleCssClass();
   }

   getStyleLabel(): string {
      if(this.borderStyle == StyleConstants.THIN_LINE) {
         return this.styles[0].label;
      }

      for(let i = 0; i < this.styles.length; i++) {
         if(this.styles[i].value == this.borderStyle) {
            return this.styles[i].label;
         }
      }

      return this.styles[0].label;
   }

   getStyleCssClass(): string {
      for(let i = 0; i < this.styles.length; i++) {
         if(this.styles[i].value == this.borderStyle) {
            return this.styles[i].cssClass;
         }
      }

      return this.styles[0].cssClass;
   }

   setBorderStyle(style: number, cssClass: string) {
      this.borderStyle = style;
      this.borderChange.emit(style);
      this.cssClass = cssClass;
   }
}

