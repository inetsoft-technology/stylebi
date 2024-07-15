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
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComboBoxEditorModel } from "../../../vsobjects/model/combo-box-editor-model";
import { VariableListDialog } from "../../../widget/dialog/variable-list-dialog/variable-list-dialog.component";
import { Tool } from "../../../../../../shared/util/tool";
import { XSchema } from "../../../common/data/xschema";
import { ComponentTool } from "../../../common/util/component-tool";

@Injectable({
   providedIn: "root"
})
export class ComboBoxEditorValidationService {
   constructor(private modalService: NgbModal) {
   }

   public validateEmbeddedValues(comboBoxEditorModel: ComboBoxEditorModel): boolean {
      const embeddedDataType = comboBoxEditorModel.variableListDialogModel.dataType;

      if(comboBoxEditorModel.embedded) {
         let dataRegex: RegExp = VariableListDialog.getDataRegex(embeddedDataType);

         if(dataRegex) {
            for(let value of comboBoxEditorModel.variableListDialogModel.values) {
               if(!dataRegex.test(value)) {
                  comboBoxEditorModel.valid = false;
                  return false;
               }
            }
         }
         else if(embeddedDataType == XSchema.BOOLEAN) {
            for(let value of comboBoxEditorModel.variableListDialogModel.values) {
               if(value == null) {
                  comboBoxEditorModel.valid = false;
                  return false;
               }
            }
         }
      }

      return true;
   }

   public validateQueryValues(comboBoxEditorModel: ComboBoxEditorModel): Promise<boolean> {
      const embeddedDataType = comboBoxEditorModel.variableListDialogModel.dataType;
      const queryDataType =
         comboBoxEditorModel.selectionListDialogModel.selectionListEditorModel.dataType;

      if(comboBoxEditorModel.query && comboBoxEditorModel.embedded &&
         embeddedDataType !== queryDataType)
      {
         return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
            "_#(js:input.mergeDatatype.notMatch)",
            {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then((result: string) => {
               return result === "yes";
            });
      }

      return Promise.resolve(true);
   }
}
