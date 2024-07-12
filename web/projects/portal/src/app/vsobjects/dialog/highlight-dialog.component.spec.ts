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
import { VSConditionDialogModel } from "../../common/data/condition/vs-condition-dialog-model";
import { HighlightDialogModel } from "../../widget/highlight/highlight-dialog-model";
import { HighlightModel } from "../../widget/highlight/highlight-model";
import { HighlightDialog } from "./highlight-dialog.component";

describe("Highlight Dialog Test", () => {
   const createModel: () => HighlightDialogModel = () => {
      return {
         imageObj: false,
         tableAssembly: false,
         chartAssembly: false,
         showRow: false,
         showFont: false,
         highlights: [],
         tableName: "table1",
         fields: [],
         usedHighlightNames: [],
         confirmChanges: false
      };
   };

   const createHighlightModel: (highlightName: string) => HighlightModel = (highlightName) => {
      return {
         name: highlightName,
         foreground: "",
         background: "",
         fontInfo: null,
         vsConditionDialogModel: null,
         applyRow: false
      };
   };

   let createVSConditionDialogMode: (name: string) => VSConditionDialogModel = (name: string) => {
      return {
         conditionList: null,
         fields: null,
         tableName: name
      };
   };

   let highlightDialog: HighlightDialog;
   let modalService: any;

   beforeEach(() => {
      modalService = { open: jest.fn() };
      let trapService: any = { checkTrap: jest.fn() };

      highlightDialog = new HighlightDialog(modalService, trapService);
      highlightDialog.model = createModel();
   });

   // Bug #10849 make sure highlight dialog pop up warning if highlight is not complete.
   it("should validate highlights before emit", () => {
      const validateHighlights = jest.spyOn(highlightDialog, "validateHighlights");
      highlightDialog.ok();
      expect(validateHighlights).toHaveBeenCalled();
   });

   // Bug #18500 when deleting selected item, the last item should be highlighted
//   it("should select the last item when deleting highlight items", () => {
//      let highlight1 = createHighlightModel("highlight1");
//      let highlight2 = createHighlightModel("highlight2");
//      let highlight3 = createHighlightModel("highlight3");
//
//      highlightDialog.model.highlights = [highlight1, highlight2, highlight3];
//      highlightDialog.selectHighlight(highlight1);
//      highlightDialog.deleteHighlight();
//      expect(highlightDialog.selectedHighlight.name).toContain(
//         "highlight2", "the last item is not selected");
//   });

   //Bug #18478 should pop up warning when edit form table on viewer side
//   it("should pop up warning when edit form table on viewer side", () => {
//      highlightDialog.model.confirmChanges = true;
//      let highlight1 = createHighlightModel("highlight1");
//      let conditionModel1 = createVSConditionDialogMode("table1");
//      let conlist1 = TestUtils.createMockCondition();
//      conlist1.field.attribute = "id";
//      conlist1.field.dataType = "integer";
//      conlist1.values[0].value = "1";
//      conditionModel1.conditionList = [conlist1];
//      highlight1.vsConditionDialogModel = conditionModel1;
//
//      highlightDialog.model.highlights = [highlight1];
//      highlightDialog.selectHighlight(highlight1);
//      highlightDialog.deleteHighlight();
//
//      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
//      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
//      highlightDialog.ok();
//      expect(showConfirmDialog).toHaveBeenCalled();
//   });
});
