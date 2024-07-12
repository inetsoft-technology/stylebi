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
import { ComponentTool } from "../../../common/util/component-tool";
import { CheckboxPropertyDialogModel } from "../../data/vs/checkbox-property-dialog-model";
import { ListValuesPaneModel } from "../../data/vs/list-values-pane-model";
import { CheckboxPropertyDialog } from "./checkbox-property-dialog.component";
import { ComboBoxEditorValidationService } from "./combo-box-editor-validation.service";
import { of as observableOf } from "rxjs";

let createModel: () => CheckboxPropertyDialogModel = () => {
   return {
      checkboxGeneralPaneModel: {
         generalPropPaneModel: null,
         listValuesPaneModel: null,
         sizePositionPaneModel: null,
         titlePropPaneModel: null
      },
      dataInputPaneModel: null,
      vsAssemblyScriptPaneModel: {}
   };
};

let createListModel: () => ListValuesPaneModel = () => {
   return {
         comboBoxEditorModel: {
            calendar: false,
            dataType: "string",
            embedded: true,
            query: true,
            noDefault: true,
            type: "ComboBox",
            valid: true,
            defaultValue: null,
            selectionListDialogModel: {
               selectionListEditorModel: {
                  column: "reseller",
                  dataType: "boolean",
                  form: false,
                  localizedTables: ["model"],
                  ltableDescriptions: [""],
                  table: "model",
                  tables: ["model"],
                  value: "reseller"
               },
            },
            variableListDialogModel: {
               dataType: "string",
               labels: ["A", "B"],
               values: ["A", "B"]
            }
         },
         embeddedDataDown: false,
         sortType: 1
      };
};

describe("radiobutton property dialog componnet unit case", () => {
   let comboBoxEditorValidationService: any;
   let trapService: any;
   let contextService: any;
   let comboxServiceTmp: any;
   let modalService: any;
   let radioProDialog: CheckboxPropertyDialog;
   let dialogService: any;

   beforeEach(() => {
      modalService = { open: jest.fn() };
      contextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         getObjectChange: jest.fn(() => observableOf({}))
      };
      comboxServiceTmp = {
         validateEmbeddedValues: jest.fn(),
         validateQueryValues: jest.fn()
      };
      dialogService = { checkScript: jest.fn((id, scripts, ok: Function, cancel) => ok()) };
      trapService = { checkTrap: jest.fn() };
      comboBoxEditorValidationService = new ComboBoxEditorValidationService(modalService);
      radioProDialog = new CheckboxPropertyDialog(comboBoxEditorValidationService, trapService, contextService, dialogService);
      radioProDialog.model = createModel();
   });

   //Bug #18850 should pop up confirm dialog
   it("should pop up confirm dialog when query type unmatch embedded type ", () => {
      radioProDialog.model.checkboxGeneralPaneModel.listValuesPaneModel = createListModel();
      const showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("true"));
      radioProDialog.ok();
      expect(showConfirmDialog).toHaveBeenCalled();
   });

   //Bug #19604 check trap
   it("check trap", () => {
      let checkboBoxDialog = new CheckboxPropertyDialog(comboxServiceTmp, trapService, contextService, dialogService);
      checkboBoxDialog.model = createModel();
      checkboBoxDialog.model.checkboxGeneralPaneModel.listValuesPaneModel = createListModel();
      comboxServiceTmp.validateEmbeddedValues.mockImplementation(() => true);
      comboxServiceTmp.validateQueryValues.mockImplementation(() => Promise.resolve(true));
      const checkTrap = jest.spyOn(checkboBoxDialog, "checkTrap");
      checkboBoxDialog.ok();
      expect(checkTrap).not.toHaveBeenCalled();
   });
});
