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
import { of as observableOf } from "rxjs";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { CreateCalcDialog } from "../calculate-dialog/create-calc-dialog.component";
import { VSBindingTreeActions } from "./vs-binding-tree-actions";

describe("vs binding tree action unit case", () => {
   let viewsheet: Viewsheet;
   let selectedNode: TreeNodeModel;
   let dialogService: any;
   let treeService: any;
   let modelService: any;

   beforeEach(() => {
      let wsDataTree = TestUtils.createMockWorksheetDataTree();
      viewsheet = new Viewsheet();
      selectedNode = wsDataTree.children[0].children[0].children[0];
      dialogService = { open: jest.fn() };
      treeService = {
         getTableName: jest.fn(),
         getParent: jest.fn()
      };
      modelService = { getModel: jest.fn() };

      treeService.getTableName.mockImplementation(() => null);
   });

   //Bug #18653 should pop up confirm dialog when remove calc field.
   //Bug #18682 should no pop up warning when new calc field
   //@TODO remove or modify in use calc field, no check
   it("should not pop up waring when new calc field", () => {
      let treeActions = new VSBindingTreeActions(viewsheet, selectedNode, [selectedNode],
         dialogService, treeService, modelService, "chart1", null, false);
      let showDialog = jest.spyOn(ComponentTool, "showDialog");
      showDialog.mockImplementation(() => new CreateCalcDialog(modelService));
      let col1 = TestUtils.createMockDataRef("state");
      let col2 = TestUtils.createMockDataRef("id");
      col2.dataType = "integer";
      modelService.getModel.mockImplementation(() => observableOf({
         aggregateFields: [],
         allcolumns: [col1, col2],
         calcFieldsGroup: [],
         columnFields: [col1, col2],
         sqlMergeable: false
      }));
      treeActions.actions[0].actions[0].action(new MouseEvent("click"));
      expect(showDialog).toHaveBeenCalled();

      let delConfirm = jest.spyOn(ComponentTool, "showConfirmDialog");
      delConfirm.mockImplementation(() => Promise.resolve("no"));
      treeActions.actions[0].actions[2].action(new MouseEvent("click"));
      expect(delConfirm).toHaveBeenCalled();
   });

   //Bug #18604 pysical table should support create calc field
   it("phycial table should create calcfield", () => {
      selectedNode = {
         children: [],
         data: {
            folder: true,
            indentifier: "0^1281^__NULL__^/baseWorksheet/CUSTOMERS",
            path: "/baseWorksheet/CUSTOMERS",
            type: AssetType.PHYSICAL_TABLE,
            properties: {}
         },
         leaf: false,
         expanded: true,
         label: "CUSTOMERS"
      };
      treeService.getParent.mockImplementation(() => null);
      let treeActions = new VSBindingTreeActions(viewsheet, selectedNode, [selectedNode],
         dialogService, treeService, modelService, "chart1", null, false);
      expect(treeActions.actions[0].actions[0].visible()).toBeTruthy();
   });
});