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
import { Component, Input } from "@angular/core";
import { VSFormatPaneModel } from "../model/vs-format-pane-model";

@Component({
   selector: "vs-format-pane",
   templateUrl: "vs-format-pane.component.html"
})
export class VSFormatPane {
   @Input() presenter: string[];
   @Input() model: VSFormatPaneModel;

   decimalFmts: string[] = ["", "#,##0.00", "#,##0.##", "#,##0.#K", "#,##0.#M", "#,##0.#B",
      "#,##0.00;(#,##0.00)", "#,##0%", "##.00%", "##0.#####E0",
      "\u00A4#,##0.00;(\u00A4#,##0.00)"];

   dateFmts: string[] = ["", "MM/dd/yyyy", "yyyy-MM-dd",
      "EEEEE, MMMMM dd, yyyy", "MMMM d, yyyy", "MM/d/yy", "d-MMM-yy", "MM.d.yyyy",
      "MMM. d, yyyy", "d MMMMM yyyy", "MMMMM yy", "MM-yy", "MM/dd/yyyy hh:mm a",
      "MM/dd/yyyy hh:mm:ss a", "h:mm a", "h:mm:ss a", "h:mm:ss a, z"];

   dateOptions: string[] = ["Full", "Long", "Medium", "Short", "Custom"];

   openPresenter() {
      //TODO - Complete Presenter Modal based on which presenter is selected.
   }
}
