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
import { VariableInputDialogModel } from "./variable-input-dialog-model";
import { XSchema } from "../../../common/data/xschema";
import { VariableInputDialog } from "./variable-input-dialog.component";

describe("Variable Input Dialog Test", () => {
   const createModel: () => VariableInputDialogModel = () => {
      return {
         varInfos: [
            {
               type: XSchema.BOOLEAN,
               value: null,
               values: []
            }
         ]
      };
   };

   let variableInputDialog: VariableInputDialog;
   let renderer: any;
   let element: any;

   beforeEach(() => {
      renderer = { };
      element = { };

      variableInputDialog = new VariableInputDialog();
      variableInputDialog.model = createModel();
   });

   // Bug #16824 Make sure the default value of a boolean type is false
   it("should set the default boolean value to false", () => {
      variableInputDialog.ngOnInit();
      expect(variableInputDialog.model.varInfos[0].value).toBeDefined();
      expect(variableInputDialog.model.varInfos[0].value[0]).toBeFalsy();
   });
});