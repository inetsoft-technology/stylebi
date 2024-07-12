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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { TableEditorService } from "../../services/table/table-editor.service";
import { TableOptionInfo } from "../../data/table/table-option-info";
import { TableFormatInfo } from "../../../common/data/tablelayout/table-format-info";

@Component({
   selector: "table-option",
   templateUrl: "table-option.component.html",
})
export class TableOption {
   @Input() option: TableOptionInfo;
   @Input() groupNum: number;
   @Input() format: TableFormatInfo;
   @Input() formatOptionVisible: boolean;
   @Output() onChangeFormat = new EventEmitter<TableFormatInfo>();

   public constructor(private editorService: TableEditorService) {
   }

   updateDisOption(evt: any) {
      this.option.distinct = evt.target.checked;
      this.editorService.setBindingModel();
   }

   updateGraOption(evt: any) {
      this.option.grandTotal = evt.target.checked;
      this.editorService.setBindingModel();
   }
}
