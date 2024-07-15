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
import { Component, EventEmitter, Input, Output, OnInit, OnChanges, SimpleChanges, ViewChild } from "@angular/core";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { StyleConstants } from "../../common/util/style-constants";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "border-style-pane",
   styleUrls: ["binding-border-pane.component.scss"],
   templateUrl: "border-style-pane.component.html"
})

export class BorderStylePane implements OnInit, OnChanges {
   @Input() formatModel: FormatInfoModel;
   @Input() selectedBorder: any;
   @Input() composerPane: boolean = false;
   @Output() onClick: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   styles: Array<{label: string, value: string, cssClass: string}> = [];
   label: string;
   cssClass: string;
   currentBorderStyle: string;

   constructor() {
   }

   ngOnInit(): void {
      if(!this.composerPane) {
         this.styles = [
            { label: "_#(js:None)", value: String(StyleConstants.NO_BORDER), cssClass: null },
            { label: "_#(js:Mixed)", value: String(StyleConstants.MIXED_BORDER), cssClass: null },
            { label: null, value: String(StyleConstants.THIN_LINE), cssClass: "line-style-THIN_LINE"},
            { label: null, value: String(StyleConstants.MEDIUM_LINE), cssClass: "line-style-MEDIUM_LINE"},
            { label: null, value: String(StyleConstants.THICK_LINE), cssClass: "line-style-THICK_LINE"},
            { label: null, value: String(StyleConstants.DOUBLE_LINE), cssClass: "line-style-DOUBLE_LINE"},
            { label: null, value: String(StyleConstants.DOT_LINE), cssClass: "line-style-DOT_LINE"},
            { label: null, value: String(StyleConstants.DASH_LINE), cssClass: "line-style-DASH_LINE"},
            { label: "_#(js:THIN THIN LINE)", value: String(StyleConstants.THIN_THIN_LINE), cssClass: null },
            { label: "_#(js:ULTRA THIN LINE)", value: String(StyleConstants.ULTRA_THIN_LINE), cssClass: null },
            { label: null, value: String(StyleConstants.MEDIUM_DASH), cssClass: "line-style-MEDIUM_DASH"},
            { label: null, value: String(StyleConstants.LARGE_DASH), cssClass: "line-style-LARGE_DASH"},
            { label: null, value: "24578", cssClass: "line-style-RAISED_3D"},
            { label: null, value: "40962", cssClass: "line-style-LOWERED_3D"},
            { label: null, value: "24579", cssClass: "line-style-DOUBLE_3D_RAISED"},
            { label: null, value: "40963", cssClass: "line-style-DOUBLE_3D_LOWERED"}
         ];
      }
      else {
         this.styles = [
            { label: "_#(js:None)", value: String(StyleConstants.NO_BORDER), cssClass: null },
            { label: "_#(js:Mixed)", value: String(StyleConstants.MIXED_BORDER), cssClass: null },
            { label: null, value: String(StyleConstants.THIN_LINE), cssClass: "line-style-THIN_LINE"},
            { label: null, value: String(StyleConstants.MEDIUM_LINE), cssClass: "line-style-MEDIUM_LINE"},
            { label: null, value: String(StyleConstants.THICK_LINE), cssClass: "line-style-THICK_LINE"},
            { label: null, value: String(StyleConstants.DOUBLE_LINE), cssClass: "line-style-DOUBLE_LINE"},
            { label: null, value: String(StyleConstants.DOT_LINE), cssClass: "line-style-DOT_LINE"},
            { label: null, value: String(StyleConstants.DASH_LINE), cssClass: "line-style-DASH_LINE"},
            { label: null, value: String(StyleConstants.MEDIUM_DASH), cssClass: "line-style-MEDIUM_DASH"},
            { label: null, value: String(StyleConstants.LARGE_DASH), cssClass: "line-style-LARGE_DASH"},
         ];
      }

      this.updateParameters();
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(!!this.styles && this.styles.length > 0) {
         this.updateParameters();
      }
   }

   updateParameters(): void {
      if(!!this.formatModel) {
         this.currentBorderStyle = this.getCurrentBorderStyle();
         this.cssClass = this.getStyleCssClass();
         this.label = this.getStyleLabel();
      }
   }

   getCurrentBorderStyle(): string {
      let style = String(StyleConstants.NO_BORDER);

      if(this.isToggleAll() && !this.isSameBorderStyle()) {
         style = String(StyleConstants.MIXED_BORDER);
      }
      else if(this.selectedBorder.borderTop) {
         style = this.formatModel.borderTopStyle;
      }
      else if(this.selectedBorder.borderLeft) {
         style = this.formatModel.borderLeftStyle;
      }
      else if(this.selectedBorder.borderBottom) {
         style = this.formatModel.borderBottomStyle;
      }
      else if(this.selectedBorder.borderRight) {
         style = this.formatModel.borderRightStyle;
      }

      return style;
   }

   setBorderStyle(event: any, style: string): void {
      event.stopPropagation();

      if(this.isNullSelectedBorder() || style == String(StyleConstants.MIXED_BORDER)) {
         return;
      }

      if(this.selectedBorder.borderTop) {
         this.formatModel.borderTopStyle = style;
      }

      if(this.selectedBorder.borderLeft) {
         this.formatModel.borderLeftStyle = style;
      }

      if(this.selectedBorder.borderBottom) {
         this.formatModel.borderBottomStyle = style;
      }

      if(this.selectedBorder.borderRight) {
         this.formatModel.borderRightStyle = style;
      }

      this.onClick.emit(this.selectedBorder);
      this.updateParameters();

      if(this.dropdown) {
         this.dropdown.close();
      }
   }

   isNullSelectedBorder(): boolean {
      return !this.selectedBorder.borderTop && !this.selectedBorder.borderLeft &&
         !this.selectedBorder.borderBottom && !this.selectedBorder.borderRight;
   }

   isToggleAll(): boolean {
      return this.selectedBorder.borderTop && this.selectedBorder.borderLeft &&
         this.selectedBorder.borderBottom && this.selectedBorder.borderRight;
   }

   getStyleCssClass(): string {
      let style = this.currentBorderStyle;

      for(let i = 0; i < this.styles.length; i++) {
         if(this.styles[i].value == style) {
            return this.styles[i].cssClass;
         }
      }

      return this.styles[0].cssClass;
   }

   getStyleLabel(): string {
      let style = this.currentBorderStyle;

      for(let i = 0; i < this.styles.length; i++) {
         if(this.styles[i].value == style) {
            return this.styles[i].label;
         }
      }

      return this.styles[0].label;
   }

   isSameBorderStyle(): boolean {
      return this.formatModel.borderTopStyle == this.formatModel.borderLeftStyle &&
         this.formatModel.borderTopStyle == this.formatModel.borderBottomStyle &&
         this.formatModel.borderTopStyle == this.formatModel.borderRightStyle;
   }
}
