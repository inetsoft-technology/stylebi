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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { CrosstabOptionInfo } from "../../data/table/crosstab-option-info";
import { TableEditorService } from "../../services/table/table-editor.service";
import { Tool } from "../../../../../../shared/util/tool";
import { TableFormatInfo } from "../../../common/data/tablelayout/table-format-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { BindingService } from "../../services/binding.service";

@Component({
   selector: "crosstab-option",
   templateUrl: "crosstab-option.component.html",
})
export class CrosstabOption {
   @Input() option: CrosstabOptionInfo;
   @Input() formatOptionVisible: boolean;
   @Input() format: TableFormatInfo;
   @Input() vsId: string;
   @Input() variables: string[];
   @Output() onChangeFormat = new EventEmitter<TableFormatInfo>();
   rtotals: any[];
   ctotals: any[];

   public constructor(private tableEditorService: TableEditorService,
                      private uiContextService: UIContextService,
                      private bindingService: BindingService)
   {
      this.rtotals = [{ label: "_#(js:Hide)", value: "false" }, { label: "_#(js:Show)", value: "true" }];
      this.ctotals = [{ label: "_#(js:Hide)", value: "false" }, { label: "_#(js:Show)", value: "true" }];
   }

   get isVS(): boolean {
      return this.uiContextService.isVS();
   }

   updateOption() {
      this.tableEditorService.setBindingModel();
   }

   getRowTotalLabel(): string {
      if(this.option.rowTotalVisibleValue == "true") {
         return "_#(js:Show)";
      }
      else if(this.option.rowTotalVisibleValue == "false") {
         return "_#(js:Hide)";
      }

      return this.option.rowTotalVisibleValue;
   }

   getColTotalLabel(): string {
      if(this.option.colTotalVisibleValue == "true") {
         return "_#(js:Show)";
      }
      else if(this.option.colTotalVisibleValue == "false") {
         return "_#(js:Hide)";
      }

      return this.option.colTotalVisibleValue;
   }

   get assemblyName(): string {
      return this.bindingService.assemblyName;
   }
}
