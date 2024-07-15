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
   Component,
   OnInit,
   Input,
   Output,
   EventEmitter
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ViewsheetPrintLayoutDialogModel } from "../../data/vs/viewsheet-print-layout-dialog-model";
import { PrintLayoutMeasures } from "../../data/vs/vs-layout-model";

interface PaperSize {
   width: number;
   height: number;
}

@Component({
   selector: "viewsheet-print-layout-dialog",
   templateUrl: "viewsheet-print-layout-dialog.component.html",
   styleUrls: ["./viewsheet-print-layout-dialog.component.scss"]
})
export class ViewsheetPrintLayoutDialog implements OnInit {
   @Input() model: ViewsheetPrintLayoutDialogModel;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formPrint: UntypedFormGroup;
   measurements: string[] = ["inches", "mm", "points"];
   measurementsView: any = [
      {label: "_#(js:inches)", value: "inches"},
      {label: "_#(js:mm)", value: "mm"},
      {label: "_#(js:points)", value: "points"}
   ];
   unitRatios: number[] = [1, PrintLayoutMeasures.INCH_MM, PrintLayoutMeasures.INCH_POINT];
   paper: any[] = [
      {label: "_#(js:Letter [8.5x11 in])", value: "Letter [8.5x11 in]"},
      {label: "_#(js:Legal [8.5x14 in])", value: "Legal [8.5x14 in]"},
      {label: "_#(js:A2 [420x594 mm])", value: "A2 [420x594 mm]"},
      {label: "_#(js:A3 [297x420 mm])", value: "A3 [297x420 mm]"},
      {label: "_#(js:A4 [210x297 mm])", value: "A4 [210x297 mm]"},
      {label: "_#(js:A5 [148x210 mm])", value: "A5 [148x210 mm]"},
      {label: "_#(js:Executive [7.25x10.5 in])", value: "Executive [7.25x10.5 in]"},
      {label: "_#(js:Tabloid [11x17 in])", value: "Tabloid [11x17 in]"},
      {label: "_#(js:Ledger [17x11 in])", value: "Ledger [17x11 in]"},
      {label: "_#(js:Statement [5.5x8.5 in])", value: "Statement [5.5x8.5 in]"},
      {label: "_#(js:B4 [250x353 mm])", value: "B4 [250x353 mm]"},
      {label: "_#(js:B5 [182x257 mm])", value: "B5 [182x257 mm]"},
      {label: "_#(js:Folio [8.5x13 in])", value: "Folio [8.5x13 in]"},
      {label: "_#(js:Quarto [215x275 mm])", value: "Quarto [215x275 mm]"},
      {label: "(_#(js:Custom Size))", value: "(Custom Size)"}
   ];
   private paperSizes: PaperSize[] = [
      {width: 8.5, height: 11},
      {width: 8.5, height: 14},
      {width: 16.5354, height: 23.3858}, // 420x594mm
      {width: 11.6929, height: 16.5354}, // 297x420mm
      {width: 8.26772, height: 11.6929}, // 210x297mm
      {width: 5.82677, height: 8.26772}, // 148x210mm
      {width: 7.25, height: 10.5},
      {width: 11, height: 17},
      {width: 17, height: 11},
      {width: 5.5, height: 8.5},
      {width: 9.84252, height: 13.8976}, // 250x353mm
      {width: 7.16535, height: 10.1181}, // 182x257mm
      {width: 8.5, height: 13},
      {width: 8.46457, height: 10.8268}, // 215x275mm
      null
   ];
   scaleOptions: number[] = [
      0.5, 1, 1.5, 2, 2.5, 3
   ];
   formValid = () => this.model && this.formPrint && this.formPrint.valid &&
      !this.isHorizontalMarginTooLarge() && !this.isHorizontalMarginTooLarge() &&
      !this.isHeaderFromEdgeTooLarge() && !this.isFooterFromEdgeTooLarge();

   ngOnInit() {
      if(!this.model) {
         this.model = {
            paperSize: "Letter [8.5x11 in]",
            marginTop: 1,
            marginBottom: 1,
            marginRight: 1,
            marginLeft: 1,
            footerFromEdge: 0.75,
            headerFromEdge: 0.5,
            landscape: false,
            scaleFont: 1,
            numberingStart: 0,
            customWidth: 0,
            customHeight: 0,
            units: "inches"
         };
      }

      this.initForm();
   }

   initForm(): void {
      const customValidators = this.model.paperSize == "(Custom Size)" ?
         [ Validators.required, FormValidators.positiveIntegerInRange ] : [];

      this.formPrint = new UntypedFormGroup({
         customWidth: new UntypedFormControl(this.model.customWidth, customValidators),
         customHeight: new UntypedFormControl(this.model.customHeight, customValidators),
         marginTop: new UntypedFormControl(this.model.marginTop, [
            Validators.required,
            FormValidators.positiveIntegerInRange
         ]),
         marginLeft: new UntypedFormControl(this.model.marginLeft, [
            Validators.required,
            FormValidators.positiveIntegerInRange
         ]),
         marginBottom: new UntypedFormControl(this.model.marginBottom, [
            Validators.required,
            FormValidators.positiveIntegerInRange
         ]),
         marginRight: new UntypedFormControl(this.model.marginRight, [
            Validators.required,
            FormValidators.positiveIntegerInRange
         ]),
         headerFromEdge: new UntypedFormControl(this.model.headerFromEdge, [
            Validators.required,
            FormValidators.positiveIntegerInRange
         ]),
         footerFromEdge: new UntypedFormControl(this.model.footerFromEdge, [
            Validators.required,
            FormValidators.positiveIntegerInRange
         ]),
         scaleFont: new UntypedFormControl(this.model.scaleFont, [
            Validators.required,
            FormValidators.positiveNonZeroInRange
         ]),
         numberingStart: new UntypedFormControl(this.model.numberingStart, [
            FormValidators.isInteger()
         ])
      });
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      if(!this.model.numberingStart) {
         this.model.numberingStart = 0;
      }

      this.onCommit.emit(this.model);
   }

   private get paperSize(): PaperSize {
      let i: number = -1;

      for(let j = 0; j < this.paper.length; j++) {
         if(this.model.paperSize == this.paper[j].value) {
            i = j;
         }
      }

      let size: PaperSize = this.paperSizes[i];

      if(size == null) {
         size = {
            width: this.toInch(this.model.customWidth, this.model.units),
            height: this.toInch(this.model.customHeight, this.model.units)
         };
      }

      return this.model.landscape ? {width: size.height, height: size.width} : size;
   }

   private toInch(v: number, units: string): number {
      switch(units) {
      case "mm":
         return v / 25.4;
      case "points":
         return v / 72;
      default:
         return v;
      }
   }

   isVerticalMarginTooLarge(): boolean {
      const margins = this.toInch(Number(this.model.marginTop) + Number(this.model.marginBottom), this.model.units);
      return margins >= this.paperSize.height;
   }

   isHorizontalMarginTooLarge(): boolean {
      const margins = this.toInch(Number(this.model.marginLeft) + Number(this.model.marginRight), this.model.units);
      return margins >= this.paperSize.width;
   }

   isHeaderFromEdgeTooLarge(): boolean {
      return this.model.headerFromEdge > this.model.marginTop;
   }

   isFooterFromEdgeTooLarge(): boolean {
      return this.model.footerFromEdge > this.model.marginBottom;
   }

   selectScaleFont(value: number): void {
      this.model.scaleFont = value;
   }

   unitChanged(units: string) {
      const currUnit: number = this.measurements.indexOf(this.model.units);
      const newUnit: number = this.measurements.indexOf(units);

      if(newUnit !== currUnit) {
         let width: number = !this.model.customWidth ? 0 : this.model.customWidth;
         let height: number = !this.model.customHeight ? 0 : this.model.customHeight;
         let unitRatio = this.unitRatios[newUnit] / this.unitRatios[currUnit];

         width = width * unitRatio;
         height = height * unitRatio;
         let top = this.model.marginTop * unitRatio;
         let bottom = this.model.marginBottom * unitRatio;
         let right = this.model.marginRight * unitRatio;
         let left = this.model.marginLeft * unitRatio;
         let header = this.model.headerFromEdge * unitRatio;
         let footer = this.model.footerFromEdge * unitRatio;

         // if in mm/points, display integer
         if(newUnit > 0) {
            width = Math.round(width);
            height = Math.round(height);
            top = Math.round(top);
            bottom = Math.round(bottom);
            right = Math.round(right);
            left = Math.round(left);
            header = Math.round(header);
            footer = Math.round(footer);
         }

         this.model.customWidth = width;
         this.model.customHeight = height;
         this.model.marginTop = top;
         this.model.marginBottom = bottom;
         this.model.marginRight = right;
         this.model.marginLeft = left;
         this.model.headerFromEdge = header;
         this.model.footerFromEdge = footer;
         this.model.units = units;
      }
   }
}
