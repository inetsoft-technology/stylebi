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
import { ComponentTool } from "../../../common/util/component-tool";
import { ListValuesPaneModel } from "../../data/vs/list-values-pane-model";
import { RadioButtonPropertyDialogModel } from "../../data/vs/radiobutton-property-dialog-model";
import { ComboBoxEditorValidationService } from "./combo-box-editor-validation.service";
import { RadioButtonPropertyDialog } from "./radiobutton-property-dialog.component";
import { of as observableOf } from "rxjs";

let createModel: () => RadioButtonPropertyDialogModel = () => {
   return {
      radioButtonGeneralPaneModel: {
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
            noDefault: true,
            query: true,
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
   let trapService: any;
   let contextService: any;
   let comboBoxEditorValidationService: any;
   let comboxServiceTmp: any;
   let modalService: any;
   let radioProDialog: RadioButtonPropertyDialog;
   let dialogService: any;

   beforeEach(() => {
      trapService = { checkTrap: jest.fn() };
      contextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         getObjectChange: jest.fn(() => observableOf({}))
      };
      modalService = { open: jest.fn() };
      comboxServiceTmp = {
         validateEmbeddedValues: jest.fn(),
         validateQueryValues: jest.fn()
      };
      dialogService = { checkScript: jest.fn((id, scripts, ok: Function, cancel) => ok()) };

      comboBoxEditorValidationService = new ComboBoxEditorValidationService(modalService);
      radioProDialog = new RadioButtonPropertyDialog(contextService, comboBoxEditorValidationService, trapService, dialogService);
      radioProDialog.model = createModel();
   });

   //Bug #18850 should pop up confirm dialog
   it("should pop up confirm dialog when query type unmatch embedded type ", () => {
      radioProDialog.model.radioButtonGeneralPaneModel.listValuesPaneModel = createListModel();
      const showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("true"));
      radioProDialog.ok();
      expect(showConfirmDialog).toHaveBeenCalled();
   });

    //Bug #18966 check trap
   it("check trap", () => {
      let radioDialog = new RadioButtonPropertyDialog(contextService, comboxServiceTmp, trapService,  dialogService);
      radioDialog.model = createModel();
      radioDialog.model.radioButtonGeneralPaneModel.listValuesPaneModel = createListModel();
      comboxServiceTmp.validateEmbeddedValues.mockImplementation(() => true);
      comboxServiceTmp.validateQueryValues.mockImplementation(() => Promise.resolve(true));
      const checkTrap = jest.spyOn(radioDialog, "checkTrap");
      radioDialog.ok();
      expect(checkTrap).not.toHaveBeenCalled();
   });
});
