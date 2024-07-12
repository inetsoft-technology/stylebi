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
import { TestUtils } from "../../common/test/test-utils";
import { ComposerContextProviderFactory, ViewerContextProviderFactory } from "../context-provider.service";
import { VSSelectionTreeModel } from "../model/vs-selection-tree-model";
import { SelectionTreeActions } from "./selection-tree-actions";

describe("SelectionTreeActions", () => {
   const createModel: () => VSSelectionTreeModel = () => {
      return Object.assign({
         root: null,
         mode: 1,
         expandAll: false,
         levels: 1
      }, TestUtils.createMockVSSelectionBaseModel("VSSelectionTree", "name"));
   };

   //bug #17886, selection tree should have select subtree and clear subtree menus
   //bug #18179, should subtree action only when select cell
   it("check status of menu actions and toolbar actions of selection tree in composer", () => {
      const expectedMenu = [
         [
            { id: "selection-tree properties", visible: true },
            { id: "selection-tree show-format-pane", visible: true }
         ],
         [
            { id: "selection-tree select-all", visible: false },
            { id: "selection-tree select-subtree", visible: false },
            { id: "selection-tree clear-subtree", visible: false }
         ],
         [
            { id: "vs-object copy", visible: true },
            { id: "vs-object cut", visible: true },
            { id: "vs-object remove", visible: true },
            { id: "vs-object group", visible: true },
            { id: "vs-object ungroup", visible: true }
         ],
         [
            { id: "vs-object bring-forward", visible: true },
            { id: "vs-object bring-to-front", visible: true },
            { id: "vs-object send-backward", visible: true },
            { id: "vs-object send-to-back", visible: true }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "selection-tree search", visible: true },
            { id: "selection-tree sort", visible: true },
            { id: "selection-tree sort-asc", visible: false },
            { id: "selection-tree sort-desc", visible: false },
            { id: "selection-tree unselect", visible: true },
            { id: "selection-tree apply", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new SelectionTreeActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //Bug #21436
      expect(TestUtils.toString(toolbarActions[1].actions[3].label())).toBe("Sort Ascending");
      expect(TestUtils.toString(toolbarActions[1].actions[4].label())).toBe("Sort Descending");
      expect(TestUtils.toString(toolbarActions[1].actions[5].label())).toBe("Sort Hide Others");

      model.contextMenuCell = TestUtils.createMockSelectionValues();
      model.selectedRegions = [{
         col: false,
         colIndex: -1,
         dataType: "string",
         index: 0,
         level: 0,
         path: [],
         row: true,
         type: 2048}];
      expect(menuActions[1].actions[1].visible()).toBeTruthy();
      expect(menuActions[1].actions[2].visible()).toBeTruthy();
      // will hidden `select/clear subtree` action when contextMenu on leaf cell.
      expect(menuActions[1].actions[1].enabled()).toBeFalsy();
      expect(menuActions[1].actions[2].enabled()).toBeFalsy();
   });

   it("check status of menu actions and toolbar actions of selection tree in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "selection-tree properties", visible: false },
            { id: "selection-tree show-format-pane", visible: false }
         ],
         [
            { id: "selection-tree select-all", visible: false },
            { id: "selection-tree select-subtree", visible: false },
            { id: "selection-tree clear-subtree", visible: false }
         ],
         [
            { id: "vs-object copy", visible: false },
            { id: "vs-object cut", visible: false },
            { id: "vs-object remove", visible: false },
            { id: "vs-object group", visible: false },
            { id: "vs-object ungroup", visible: false }
         ],
         [
            { id: "vs-object bring-forward", visible: false },
            { id: "vs-object bring-to-front", visible: false },
            { id: "vs-object send-backward", visible: false },
            { id: "vs-object send-to-back", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "selection-tree search", visible: true },
            { id: "selection-tree sort", visible: true },
            { id: "selection-tree sort-asc", visible: false },
            { id: "selection-tree sort-desc", visible: false },
            { id: "selection-tree unselect", visible: true },
            { id: "selection-tree apply", visible: false }
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new SelectionTreeActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new SelectionTreeActions(model2, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions of selection tree in viewer when single selection", () => {
      const expectedToolbar = [
         [
            { id: "selection-tree search", visible: true },
            { id: "selection-tree sort", visible: true },
            { id: "selection-tree sort-asc", visible: false },
            { id: "selection-tree sort-desc", visible: false },
            { id: "selection-tree unselect", visible: false },
            { id: "selection-tree apply", visible: false }
         ]
      ];

      const model = createModel();
      model.singleSelection = true;
      const actions = new SelectionTreeActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(toolbarActions).toMatchSnapshot();

      //Bug #21010 should disable select/clear subtree when is single selection
      model.contextMenuCell = TestUtils.createMockSelectionValues();
      expect(menuActions[1].actions[1].enabled()).toBeFalsy();
      expect(menuActions[1].actions[2].enabled()).toBeFalsy();
   });

   it("check status of toolbar actions of selection tree in composer when is dropdown", () => {
      const expectedToolbar = [
         [
            { id: "selection-tree search", visible: false },
            { id: "selection-tree sort", visible: false },
            { id: "selection-tree sort-asc", visible: false },
            { id: "selection-tree sort-desc", visible: false },
            { id: "selection-tree unselect", visible: true },
            { id: "selection-tree apply", visible: false }
         ]
      ];

      const model = createModel();
      model.dropdown = true;
      model.hidden = true;
      const actions = new SelectionTreeActions(model, ComposerContextProviderFactory());
      const toolbarActions = actions.toolbarActions;

      expect(toolbarActions).toMatchSnapshot();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new SelectionTreeActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});