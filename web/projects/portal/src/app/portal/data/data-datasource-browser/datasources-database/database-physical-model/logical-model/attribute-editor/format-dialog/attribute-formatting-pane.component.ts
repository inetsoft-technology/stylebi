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
   Component,
   Input,
   Output,
   EventEmitter,
   OnChanges,
   SimpleChanges
} from "@angular/core";
import { AttributeFormatInfoModel } from "../../../../../../model/datasources/database/physical-model/logical-model/attribute-format-info-model";
import { Format } from "../../../../../../../../common/util/format";

@Component({
   selector: "attribute-formatting-pane",
   templateUrl: "attribute-formatting-pane.component.html",
   styleUrls: ["attribute-formatting-pane.component.scss"]
})
export class AttributeFormattingPane implements OnChanges {
   @Input() popup: boolean = true;
   @Input() formatModel: AttributeFormatInfoModel;
   @Output() onApply: EventEmitter<boolean> = new EventEmitter<boolean>();
   formats: any[];
   dateFormats: any[];
   dateFmts: any[];
   decimalFmts: any[];
   durationFmts: any[];
   dateFormat: string;
   private readonly numberFormats: Format[] = ["CurrencyFormat", "PercentFormat", "DecimalFormat"];

   public constructor() {
      this.formats = [
         { value: null, label: "_#(js:None)" },
         { value: "DateFormat", label: "_#(js:Date)" },
         { value: "CurrencyFormat", label: "_#(js:Currency)" },
         { value: "PercentFormat", label: "_#(js:Percent)" },
         { value: "DecimalFormat", label: "_#(js:Number)" },
         { value: "MessageFormat", label: "_#(js:Text)" },
         { value: "DurationFormat", label: "_#(js:Duration)" },
      ];
      this.dateFormats = [
         { value: "FULL", label: "_#(js:FULL)" },
         { value: "LONG", label: "_#(js:LONG)" },
         { value: "MEDIUM", label: "_#(js:MEDIUM)" },
         { value: "SHORT", label: "_#(js:SHORT)" },
         { value: "Custom", label: "_#(js:Custom)" }
      ];
      this.decimalFmts = ["#,##0.00", "#,##0.##", "#,##0.#K", "#,##0.#M", "#,##0.#B",
                          "#,##0.00;(#,##0.00)", "#,##0%", "##.00%", "##.##\"%\"", "##0.#####E0",
                          "\u00A4#,##0.00;(\u00A4#,##0.00)"];
      this.dateFmts = ["MM/dd/yyyy", "yyyy-MM-dd", "EEEEE, MMMMM dd, yyyy", "MMMM d, yyyy",
                       "MM/d/yy", "d-MMM-yy", "MM.d.yyyy", "MMM. d, yyyy", "d MMMMM yyyy",
                       "MMMMM yy", "MM-yy", "MM/dd/yyyy hh:mm a", "MM/dd/yyyy hh:mm:ss a",
                       "h:mm a", "h:mm:ss a", "h:mm:ss a, z"];
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

   ngOnChanges(changes: SimpleChanges): void {
      this.updateFormatSpec();
   }

   /**
    * Clear the spec if no longer using a custom type.
    */
   clearFormatSpec() {
      if(!this.formatModel) {
         return;
      }

      this.formatModel.formatSpec = null;

      // Commit after set format spec, for text format, no spec will not apply.
      if(this.formatModel.format == "MessageFormat") {
         return;
      }
   }

   /**
    * Whether or not to show the custom format input box.
    * @returns {boolean}
    */
   showFormatSpec(): boolean {
      if(!this.formatModel) {
         return false;
      }

      if(this.formatModel == null || this.formatModel.format == null) {
         return false;
      }

      let format = this.formatModel.format;

      if(format == "DecimalFormat" || format == "MessageFormat" ||
         format == "DateFormat" && this.dateFormat == "Custom" || format == "DurationFormat")
      {
         return true;
      }

      return false;
   }

   /**
    * Update the format spec.
    * @param evt
    */
   changeModel(evt: any) {
      if(!this.formatModel) {
         return ;
      }

      this.formatModel.formatSpec = <string> evt;
   }

   /**
    * Update format type when types changes.
    * @param formatType
    */
   typeChange(formatType: string) {
      if(!this.formatModel) {
         return ;
      }

      this.formatModel.format = formatType;
      this.clearFormatSpec();
      this.updateFormatSpec();
   }

   /**
    * Init the date format properly.
    */
   private updateFormatSpec() {
      if(this.formatModel?.format === "DateFormat" && !!this.formatModel.formatSpec) {
         this.dateFormat = this.formatModel.formatSpec;
         const custom: boolean =
            this.dateFormats
               .filter((format: any) => format.value === this.dateFormat).length == 0;

         if(custom) {
            this.dateFormat = "Custom";
         }
      }
      else if(this.formatModel?.format === "DecimalFormat" && !this.formatModel.formatSpec) {
         this.formatModel.formatSpec = this.decimalFmts[0];
      }
      else if(this.formatModel?.format === "DateFormat" && !this.formatModel.formatSpec) {
         this.dateFormat = "Custom";
         this.formatModel.formatSpec = this.dateFmts[0];
      }
      else if(this.formatModel?.format === "DurationFormat" && !this.formatModel.formatSpec) {
         this.formatModel.formatSpec = this.durationFmts[2];
      }
   }

   /**
    * Make sure to properly set the date format depending on if it is custom.
    * @param value
    */
   updateDateFormat(value: string): void {
      this.dateFormat = value;

      if(value === "Custom") {
         this.formatModel.formatSpec = this.dateFmts[0];
      }
      else {
         this.formatModel.formatSpec = value;
      }
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
