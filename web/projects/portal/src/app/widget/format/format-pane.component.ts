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
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators, } from "@angular/forms";
import { FormatPaneModel } from "./format-pane-model";

@Component({
   selector: "format-pane",
   templateUrl: "format-pane.component.html"
})
export class FormatPane implements OnInit {
   @Input() model: FormatPaneModel;
   @Input() form: UntypedFormGroup;

   initForm(): void {
      this.form.addControl("format", new UntypedFormControl(this.model.format, [
         Validators.required
      ]));
   }

   ngOnInit(): void {
      this.initForm();

      if(this.model && this.model.format == "date" &&
         this.model.dateValue != this.dateOptions[0] &&
         this.model.dateValue != this.dateOptions[1] &&
         this.model.dateValue != this.dateOptions[2] &&
         this.model.dateValue != this.dateOptions[3])
      {
         this.model.value = this.model.dateValue;
         this.model.dateValue = "Custom";
      }
   }

   decimalFmts: string[] = ["", "#,##0.00", "#,##0.##", "#,##0.#K", "#,##0.#M", "#,##0.#B",
      "#,##0.00;(#,##0.00)", "#,##0%", "##.00%", "##0.#####E0",
      "\u00A4#,##0.00;(\u00A4#,##0.00)"];

   dateFmts: string[] = ["", "MM/dd/yyyy", "yyyy-MM-dd",
      "EEEEE, MMMMM dd, yyyy", "MMMM d, yyyy", "MM/d/yy", "d-MMM-yy", "MM.d.yyyy",
      "MMM. d, yyyy", "d MMMMM yyyy", "MMMMM yy", "MM-yy", "MM/dd/yyyy hh:mm a",
      "MM/dd/yyyy hh:mm:ss a", "h:mm a", "h:mm:ss a", "h:mm:ss a, z"];

   dateOptions: string[] = ["Full", "Long", "Medium", "Short", "Custom"];

   resetValue() {
      this.model.value = null;
   }
}
