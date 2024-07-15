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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AddParameterDialog } from "../add-parameter-dialog/add-parameter-dialog.component";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ValueTypes } from "../../../../vsobjects/model/dynamic-value-model";
import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";

@Component({
   selector: "parameter-table",
   templateUrl: "parameter-table.component.html",
   styleUrls: ["./parameter-table.component.scss"]
})
export class ParameterTable {
   @Input() parameters: AddParameterDialogModel[];
   @Input() parameterNames: string[] = [];
   @Input() containsSheet: boolean;
   @Output() parametersChange = new EventEmitter<AddParameterDialogModel[]>();
   @ViewChild("addParameterDialog") addParameterDialog: AddParameterDialog;
   public editIndex: number = -1;

   constructor(private modalService: NgbModal) {
   }

   public openParameterDialog(index?: number): void {
      this.editIndex = index >= 0 ? index : -1;

      this.modalService.open(this.addParameterDialog, {size: "sm", backdrop: "static", windowClass: "parameter-table-dialog"})
         .result
         .then((mdls: AddParameterDialogModel[]) => {
            this.parameters = mdls;
            this.parametersChange.emit(this.parameters);
         },
         () => {
            //cancel
         });
   }

   public removeParameter(index: number): void {
      const name: string = this.parameters[index].name;
      const msg: string = "_#(js:Delete Parameter): " + name + "?";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg)
         .then((buttonClicked: string) => {
            if(buttonClicked === "ok") {
               this.parameters.splice(index, 1);
            }
         });
   }

   public clearAllParameters(): void {
      const msg: string = "_#(js:em.common.clearAll)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg)
         .then((buttonClicked: string) => {
            if(buttonClicked === "ok") {
               this.parametersChange.emit([]);
            }
         });
   }

   getParamValue(param: AddParameterDialogModel): string {
      return param.type === "timeInstant" && param.value.type == ValueTypes.VALUE ?
         param.value.value.replace(/T/gm, " ") : param.value.value;
   }
}
