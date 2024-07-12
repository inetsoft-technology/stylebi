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
import { Component } from "@angular/core";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { CalcTableBindingModel } from "../../data/table/calc-table-binding-model";
import { CellBindingInfo } from "../../data/table/cell-binding-info";

@Component({
   selector: "calc-option-pane",
   templateUrl: "calc-option-pane.component.html",
   styleUrls: ["calc-option-pane.component.scss"]
})
export class CalcOptionPane {
   cells: any[] = [];

   public constructor(private editorService: VSCalcTableEditorService) {
   }

   get cellBinding(): CellBindingInfo {
      return <CellBindingInfo> this.editorService.getCellBinding();
   }

   getCellNames() {
      const cells: any[] = this.editorService.getCellNamesWithDefaults();

      if(cells == null) {
         return this.cells;
      }

      this.cells = cells;
      return this.cells;
   }

   setCellBinding(evt: any) {
      if(this.cellBinding.mergeRowGroup == "null" ||
         typeof this.cellBinding.mergeRowGroup != "string")
      {
         this.cellBinding.mergeRowGroup = null;
      }

      if(this.cellBinding.mergeColGroup == "null" ||
         typeof this.cellBinding.mergeColGroup != "string")
      {
         this.cellBinding.mergeColGroup = null;
      }

      this.editorService.setCellBinding();
   }
}
