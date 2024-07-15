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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { LabelValueTuple } from "../../../../../shared/util/label-value-tuple";
import { Format } from "../../common/util/format";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";

@Component({
   selector: "formatting-pane",
   templateUrl: "formatting-pane.component.html",
   styleUrls: ["formatting-pane.component.scss"]
})
export class FormattingPane {
   @Input() formatModel: FormatInfoModel;
   @Input() dynamic: boolean = false;
   @Input() vsId: string = null;
   @Input() variableValues: string[] = [];
   @Output() onApply = new EventEmitter<boolean>();
   formats: LabelValueTuple<Format | null>[];
   dateFormats: LabelValueTuple<string>[];
   dateFmts: string[];
   decimalFmts: string[];
   durationFmts: string[];
   private readonly numberFormats: Format[] = ["CurrencyFormat", "PercentFormat", "DecimalFormat"];

   constructor() {
      this.formats = [
         {value: null, label: "_#(js:None)"},
         {value: "DateFormat", label: "_#(js:Date)"},
         {value: "CurrencyFormat", label: "_#(js:Currency)"},
         {value: "PercentFormat", label: "_#(js:Percent)"},
         {value: "DecimalFormat", label: "_#(js:Number)"},
         {value: "MessageFormat", label: "_#(js:Text)"},
         {value: "DurationFormat", label: "_#(js:Duration)"}
      ];

      this.dateFormats = [
         {value: "FULL", label: "_#(js:Full)"},
         {value: "LONG", label: "_#(js:Long)"},
         {value: "MEDIUM", label: "_#(js:Medium)"},
         {value: "SHORT", label: "_#(js:Short)"},
         {value: "Custom", label: "_#(js:Custom)"}
      ];

      this.dateFmts = ["", "MM/dd/yyyy", "yyyy-MM-dd", "EEEEE, MMMMM dd, yyyy",
         "MMMM d, yyyy", "MM/d/yy", "d-MMM-yy", "MM.d.yyyy", "MMM. d, yyyy",
         "d MMMMM yyyy", "MMMMM yy", "MM-yy", "MM/dd/yyyy hh:mm a",
         "MM/dd/yyyy hh:mm:ss a", "h:mm a", "h:mm:ss a", "h:mm:ss a, z"
      ];

      this.decimalFmts = ["", "#,##0.00", "#,##0.##", "#,##0.#K", "#,##0.#M",
         "#,##0.#B", "#,##0.00;(#,##0.00)", "#,##0%", "##.00%", "##.##\"%\"", "##0.#####E0",
         "\u00A4#,##0.00;(\u00A4#,##0.00)"];

      this.durationFmts = ["", "dd HH:mm", "dd HH:mm:ss", "HH:mm", "HH:mm:ss", "mm", "mm:ss", "ss"];
   }

   getDecimalFormats(): string[] {
      let ufmt: string;

      if(this.formatModel && this.formatModel.decimalFmts) {
         for(let i = 0; i < this.formatModel.decimalFmts.length; i++) {
            ufmt = "#,###" + this.formatModel.decimalFmts[i];

            if(this.decimalFmts.indexOf(ufmt) == -1) {
               this.decimalFmts.push(ufmt);
            }
         }
      }

      return this.decimalFmts;
   }

   clearFormatSpec() {
      if(!this.formatModel) {
         return ;
      }

      this.formatModel.formatSpec = null;

      // Commit after set format spec, for text format, no spec will not apply.
      if(this.formatModel.format === "MessageFormat") {
         return;
      }

      if(this.formatModel.format === "DateFormat") {
         this.formatModel.dateSpec = "Custom";
      }

      if(this.formatModel.format === "DurationFormat" && this.durationFmts) {
         this.formatModel.formatSpec = this.durationFmts[2];
      }
   }

   showFormatSpec(): boolean {
      if(this.formatModel == null || this.formatModel.format == null) {
         return false;
      }

      const format = this.formatModel.format;

      return format === "DecimalFormat" ||
          format === "MessageFormat" ||
          format === "DateFormat" && this.formatModel.dateSpec === "Custom" ||
          format == "DurationFormat" ;
   }

   changeModel(formatSpec: string): void {
      if(!this.formatModel) {
         return;
      }

      this.formatModel.formatSpec = formatSpec;
   }

   typeChange(formatType: Format): void {
      if(!this.formatModel) {
         return;
      }

      this.formatModel.format = formatType;
      this.clearFormatSpec();
   }

   increaseDecimal(): void {
      const oldFormat = <Format> this.formatModel.format;

      if(!this.numberFormats.includes(oldFormat)) {
         return;
      }

      const oldSpec = this.formatModel.formatSpec;
      let newSpec: string;

      if(oldFormat === "CurrencyFormat") {
         newSpec = "\u00A4#.000";
      }
      else if(oldFormat === "PercentFormat") {
         newSpec = "#,##0.0%";
      }
      else if(oldSpec == null || !oldSpec.trim()) {
         newSpec = "###0.0";
      }
      else {
         const specs = oldSpec.split(";");
         newSpec = "";

         for(let i = 0; i < specs.length; i++) {
            if(i > 0) {
               newSpec += ";";
            }

            const spec = specs[i];
            const dotIdx = spec.indexOf(".");

            if(dotIdx >= 0) {
               newSpec += spec.substr(0, dotIdx) + ".0" + spec.substr(dotIdx + 1);
            }
            else if(spec[spec.length - 1] === "%") {
               newSpec += spec.substr(0, spec.length - 1) + ".0%";
            }
            else {
               const lastIntegerIndex = Math.max(spec.lastIndexOf("0"), spec.lastIndexOf("#"));
               newSpec += spec.substr(0, lastIntegerIndex + 1) + ".0" +
                   spec.substr(lastIntegerIndex + 1);
            }
         }
      }

      this.formatModel.format = "DecimalFormat";
      this.formatModel.formatSpec = newSpec;
   }

   decreaseDecimal(): void {
      const oldFormat = <Format> this.formatModel.format;

      if(!this.numberFormats.includes(oldFormat)) {
         return;
      }

      const oldSpec = this.formatModel.formatSpec;
      let newSpec = oldSpec;

      if(oldFormat === "CurrencyFormat") {
         newSpec = "\u00A4#.0";
      }
      else if(oldFormat === "PercentFormat" || oldSpec == null || !oldSpec.trim()) {
         return;
      }
      else {
         const specs = oldSpec.split(";");
         newSpec = "";

         for(let i = 0; i < specs.length; i++) {
            if(i > 0) {
               newSpec += ";";
            }

            const spec = specs[i];
            const dotIdx = spec.indexOf(".");

            if(dotIdx === -1) {
               newSpec += spec;
            }
            else if(spec[dotIdx + 1] !== "0" && spec[dotIdx + 1] !== "#") {
               newSpec += spec.substr(0, dotIdx) + spec.substr(dotIdx + 1);
            }
            else if(spec[dotIdx + 2] !== "0" && spec[dotIdx + 2] !== "#") {
               newSpec += spec.substr(0, dotIdx) + spec.substr(dotIdx + 2);
            }
            else {
               const start = spec.substr(0, dotIdx);
               const end = spec.substr(dotIdx + 2);
               newSpec += end.length === 0 ? start : `${start}.${end}`;
            }
         }
      }

      this.formatModel.format = "DecimalFormat";
      this.formatModel.formatSpec = newSpec;
   }

   increaseDecimalDisabled(): boolean {
      return !this.numberFormats.includes(<Format> this.formatModel?.format);
   }

   decreaseDecimalDisabled(): boolean {
      return this.increaseDecimalDisabled() || this.formatModel?.format === "PercentFormat" ||
          this.formatModel.format === "DecimalFormat" && !this.formatModel?.formatSpec?.includes(".");
   }
}
