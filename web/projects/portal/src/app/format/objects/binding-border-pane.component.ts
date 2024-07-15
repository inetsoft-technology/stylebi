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
import {
   AfterViewInit, Component, ElementRef, Input, Output, EventEmitter,
   ViewChild, OnChanges, SimpleChanges
} from "@angular/core";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { StyleConstants } from "../../common/util/style-constants";

const borderSelectState = {
   borderTop: false, borderLeft: false,
   borderBottom: false, borderRight: false
};

@Component({
   selector: "binding-border-pane",
   styleUrls: ["binding-border-pane.component.scss"],
   templateUrl: "binding-border-pane.component.html"
})
export class BindingBorderPane implements AfterViewInit, OnChanges {
   @Input() formatModel: FormatInfoModel;
   @Input() composerPane: boolean = false;
   @Output() onApply: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("myCanvas", {static: true}) myCanvas: ElementRef;
   context: CanvasRenderingContext2D;
   selectedBorder: any;
   currentColor: { colorString: string } = { colorString: ""};
   defaultColor = "#DADADA";

   constructor() {
      this.selectedBorder = borderSelectState;
   }

   ngOnChanges(changes: SimpleChanges): void {
      // set the initial color from existing border setting
      // this should only be done on initialization of a new border setting.
      if(!changes["formatModel"]) {
         return;
      }

      if(!this.formatModel || (this.isToggleAll() && !this.isSameBorderColor())) {
         this.currentColor.colorString = this.defaultColor;
      }
      else if(this.selectedBorder.borderTop) {
         this.currentColor.colorString = this.formatModel.borderTopColor;
      }
      else if(this.selectedBorder.borderLeft) {
         this.currentColor.colorString = this.formatModel.borderLeftColor;
      }
      else if(this.selectedBorder.borderBottom) {
         this.currentColor.colorString = this.formatModel.borderBottomColor;
      }
      else if(this.selectedBorder.borderRight) {
         this.currentColor.colorString = this.formatModel.borderRightColor;
      }
      else {
         this.currentColor.colorString = this.defaultColor;
      }

      this.populateCanvas();
   }

   ngAfterViewInit() {
      this.populateCanvas();
   }

   populateCanvas(): void {
      let canvas = this.myCanvas.nativeElement;
      this.context = canvas.getContext("2d");
      canvas.width = 150;
      canvas.height = 115;

      this.drawBorders();
   }

   drawBackground(): void {
      let imagePaper = new Image();
      let ctx = this.context;

      imagePaper.onload = function() {
         ctx.drawImage(imagePaper, 0, 0);
         ctx.font = "14px Roboto";

         // change the font color depending on the theme
         const bodyCssStyle = window.getComputedStyle(document.body);
         let fillStyle = "black";

         if(bodyCssStyle && bodyCssStyle.color) {
            fillStyle = `${bodyCssStyle.color}`;
         }

         ctx.fillStyle = fillStyle;
         ctx.fillText("_#(js:Select All)", 45, 60);
      };

      imagePaper.src = "assets/format_style.gif";
   }

   drawBorders(): void {
      this.context.clearRect(0, 0, 150, 115);
      this.drawTopBorder();
      this.drawLeftBorder();
      this.drawBottomBorder();
      this.drawRightBorder();
      this.drawBackground();
   }

   drawTopBorder(): void {
      let ctx = this.context;

      if(this.formatModel && this.formatModel.borderTopStyle &&
         this.formatModel.borderTopStyle != String(StyleConstants.NO_BORDER))
      {
         this.getCanvasSettings(this.formatModel.borderTopStyle, ctx);
         let btc = this.formatModel.borderTopColor;
         ctx.strokeStyle = btc ? btc : "black";
         ctx.beginPath();
         ctx.moveTo(15, 20);
         ctx.lineTo(135, 20);

         if(this.formatModel.borderTopStyle == String(StyleConstants.DOUBLE_LINE)) {
            ctx.moveTo(15, 23);
            ctx.lineTo(135, 23);
         }

         ctx.stroke();
      }
   }

   drawLeftBorder(): void {
      let ctx = this.context;

      if(this.formatModel && this.formatModel.borderLeftStyle &&
         this.formatModel.borderLeftStyle != String(StyleConstants.NO_BORDER))
      {
         this.getCanvasSettings(this.formatModel.borderLeftStyle, ctx);
         let blc = this.formatModel.borderLeftColor;
         ctx.strokeStyle = blc ? blc : "black";
         ctx.beginPath();
         ctx.moveTo(15, 20);
         ctx.lineTo(15, 95);

         if(this.formatModel.borderLeftStyle == String(StyleConstants.DOUBLE_LINE)) {
            ctx.moveTo(18, 20);
            ctx.lineTo(18, 95);
         }

         ctx.stroke();
      }
   }

   drawBottomBorder(): void {
      let ctx = this.context;

      if(this.formatModel && this.formatModel.borderBottomStyle &&
         this.formatModel.borderBottomStyle != String(StyleConstants.NO_BORDER))
      {
         this.getCanvasSettings(this.formatModel.borderBottomStyle, ctx);
         let bbc = this.formatModel.borderBottomColor;
         ctx.strokeStyle = bbc ? bbc : "black";
         ctx.beginPath();
         ctx.moveTo(15, 95);
         ctx.lineTo(135, 95);

         if(this.formatModel.borderBottomStyle == String(StyleConstants.DOUBLE_LINE)) {
            ctx.moveTo(15, 92);
            ctx.lineTo(135, 92);
         }

         ctx.stroke();
      }
   }

   drawRightBorder(): void {
      let ctx = this.context;

      if(this.formatModel && this.formatModel.borderRightStyle &&
         this.formatModel.borderRightStyle != String(StyleConstants.NO_BORDER))
      {
         this.getCanvasSettings(this.formatModel.borderRightStyle, ctx);
         let brc = this.formatModel.borderRightColor;
         ctx.strokeStyle = brc ? brc : "black";
         ctx.beginPath();
         ctx.moveTo(135, 20);
         ctx.lineTo(135, 95);

         if(this.formatModel.borderRightStyle == String(StyleConstants.DOUBLE_LINE)) {
            ctx.moveTo(132, 20);
            ctx.lineTo(132, 95);
         }

         ctx.stroke();
      }
   }

   updateBorder(op: string): void {
      if(op == "top") {
         this.selectedBorder = {
            borderTop: true,
            borderLeft: false,
            borderBottom: false,
            borderRight: false
         };

         if(!this.formatModel.borderTopColor) {
            this.formatModel.borderTopColor = this.defaultColor;
         }

         this.currentColor.colorString = this.formatModel.borderTopColor;
      }
      else if(op == "left") {
         this.selectedBorder = {
            borderTop: false,
            borderLeft: true,
            borderBottom: false,
            borderRight: false
         };

         if(!this.formatModel.borderLeftColor) {
            this.formatModel.borderLeftColor = this.defaultColor;
         }

         this.currentColor.colorString = this.formatModel.borderLeftColor;
      }
      else if(op == "bottom") {
         this.selectedBorder = {
            borderTop: false,
            borderLeft: false,
            borderBottom: true,
            borderRight: false
         };

         if(!this.formatModel.borderBottomColor) {
            this.formatModel.borderBottomColor = this.defaultColor;
         }

         this.currentColor.colorString = this.formatModel.borderBottomColor;
      }
      else if(op == "right") {
         this.selectedBorder = {
            borderTop: false,
            borderLeft: false,
            borderBottom: false,
            borderRight: true
         };

         if(!this.formatModel.borderRightColor) {
            this.formatModel.borderRightColor = this.defaultColor;
         }

         this.currentColor.colorString = this.formatModel.borderRightColor;
      }
   }

   repaintBorder(color: string): void {
      this.currentColor.colorString = color;

      if(this.selectedBorder.borderTop) {
         this.formatModel.borderTopColor = color;
      }

      if(this.selectedBorder.borderLeft) {
         this.formatModel.borderLeftColor = color;
      }

      if(this.selectedBorder.borderBottom) {
         this.formatModel.borderBottomColor = color;
      }

      if(this.selectedBorder.borderRight) {
         this.formatModel.borderRightColor = color;
      }

      this.drawBorders();
   }

   setDefault(): void {
      this.setNullBorderStyle();
      this.setNullSelectedBorder();
      this.setDefaultBorderColor();
      this.drawBorders();
   }

   setNullBorderStyle(): void {
      this.formatModel.borderTopStyle = null;
      this.formatModel.borderLeftStyle = null;
      this.formatModel.borderBottomStyle = null;
      this.formatModel.borderRightStyle = null;
   }

   setDefaultBorderStyle(): void {
      this.formatModel.borderTopStyle = StyleConstants.THIN_LINE + "";
      this.formatModel.borderLeftStyle = StyleConstants.THIN_LINE + "";
      this.formatModel.borderBottomStyle = StyleConstants.THIN_LINE + "";
      this.formatModel.borderRightStyle = StyleConstants.THIN_LINE + "";
   }

   setDefaultBorderColor(): void {
      this.formatModel.borderTopColor = this.defaultColor;
      this.formatModel.borderLeftColor = this.defaultColor;
      this.formatModel.borderBottomColor = this.defaultColor;
      this.formatModel.borderRightColor = this.defaultColor;
      this.currentColor.colorString = this.defaultColor;
   }

   setNullSelectedBorder(): void {
      this.selectedBorder = {
         borderTop: false,
         borderLeft: false,
         borderBottom: false,
         borderRight: false
      };

      this.currentColor.colorString = this.defaultColor;
   }

   setAllSelectedBorder(): void {
      this.selectedBorder = {
         borderTop: true,
         borderLeft: true,
         borderBottom: true,
         borderRight: true
      };

      if(this.isSameBorderColor()) {
         this.currentColor.colorString = this.formatModel.borderTopColor;
      }
      else {
         this.currentColor.colorString = this.defaultColor;
      }
   }

   isDefaultBorder(): boolean {
      return this.formatModel.borderTopStyle == StyleConstants.THIN_LINE + "" &&
         this.formatModel.borderLeftStyle == StyleConstants.THIN_LINE + "" &&
         this.formatModel.borderBottomStyle == StyleConstants.THIN_LINE + "" &&
         this.formatModel.borderRightStyle == StyleConstants.THIN_LINE + "";
   }

   selectAll(): void {
      if(this.isToggleAll()) {
         this.setNullSelectedBorder();
      }
      else {
         this.setAllSelectedBorder();
      }
   }

   isSameBorderStyle(): boolean {
      return this.formatModel.borderTopStyle == this.formatModel.borderLeftStyle &&
         this.formatModel.borderTopStyle == this.formatModel.borderBottomStyle &&
         this.formatModel.borderTopStyle == this.formatModel.borderRightStyle;
   }

   isSameBorderColor(): boolean {
      if(!this.formatModel) {
         return true;
      }

      return this.formatModel.borderTopColor == this.formatModel.borderLeftColor &&
         this.formatModel.borderTopColor == this.formatModel.borderBottomColor &&
         this.formatModel.borderTopColor == this.formatModel.borderRightColor;
   }

   isToggleAll(): boolean {
      return this.selectedBorder.borderTop && this.selectedBorder.borderLeft &&
         this.selectedBorder.borderBottom && this.selectedBorder.borderRight;
   }


   isNullSelectedBorder(): boolean {
      return !this.selectedBorder.borderTop && !this.selectedBorder.borderLeft &&
         !this.selectedBorder.borderBottom && !this.selectedBorder.borderRight;
   }

   getCanvasSettings(style: string, context): void {
      context.lineWidth = 1;
      context.setLineDash([1, 0]);

      if(style == String(StyleConstants.MEDIUM_LINE)) {
         context.lineWidth = 2;
      }
      else if(style == String(StyleConstants.THICK_LINE)) {
         context.lineWidth = 3;
      }

      if(style == String(StyleConstants.DOT_LINE)) {
         context.setLineDash([2, 1]);
      }
      else if(style == String(StyleConstants.DASH_LINE)) {
         context.setLineDash([4, 3]);
      }
      else if(style == String(StyleConstants.MEDIUM_DASH)) {
         context.setLineDash([7, 7]);
      }
      else if(style == String(StyleConstants.LARGE_DASH)) {
         context.setLineDash([11, 11]);
      }
   }

   selectBorderStyle(borderStyle: any): void {
      this.selectedBorder = borderStyle;
      this.repaintBorder(this.currentColor.colorString);
   }
}
