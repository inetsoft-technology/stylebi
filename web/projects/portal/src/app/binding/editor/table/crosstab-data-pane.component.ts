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
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";

@Component({
   selector: "crosstab-data-pane",
   templateUrl: "crosstab-data-pane.component.html",
   styleUrls: ["../data-pane.component.scss"]
})
export class CrosstabDataPane {
   @Input() bindingModel: CrosstabBindingModel;
   @Input() grayedOutValues: string[] = [];

   getGroupNum(): number {
      if(this.bindingModel == null) {
         return 0;
      }

      let rowL = this.bindingModel.rows == null ? 0 : this.bindingModel.rows.length;
      let colL = this.bindingModel.cols == null ? 0 : this.bindingModel.cols.length;
      return rowL > colL ? rowL : colL;
   }
}