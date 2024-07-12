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
import { SelectionListActions } from "./selection-list-actions";

describe("SelectionListActions", () => {
   const createModel = () => TestUtils.createMockVSSelectionListModel("SelectionList1");

   it("check status of menu actions and toolbar actions of selection list in composer", () => {
      const expectedMenu = [
         [
            { id: "selection-list properties", visible: true },
            { id: "selection-list show-format-pane", visible: true },
            { id: "selection-list convert-to-range-slider", visible: false }
         ],
         [
            { id: "selection-list select-all", visible: false }
         ],
         [
            { id: "vs-object remove", visible: false },
            { id: "selection-list viewer-remove-from-container", visible: false }
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
            { id: "selection-list search", visible: true },
            { id: "selection-list sort", visible: true },
            { id: "selection-list sort-asc", visible: false },
            { id: "selection-list sort-desc", visible: false },
            { id: "selection-list reverse", visible: true },
            { id: "selection-list unselect", visible: true },
            { id: "selection-list apply", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new SelectionListActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //Bug #21436
      expect(TestUtils.toString(toolbarActions[1].actions[3].label())).toBe("Sort Ascending");
      expect(TestUtils.toString(toolbarActions[1].actions[4].label())).toBe("Sort Descending");
      expect(TestUtils.toString(toolbarActions[1].actions[5].label())).toBe("Sort Hide Others");

      //bug #18449, selection list in group container
      model.container = "group1";
      model.containerType = "VSGroupContainer";
      const actions2 = new SelectionListActions(model, ComposerContextProviderFactory());
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;
      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions of selection list in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "selection-list properties", visible: false },
            { id: "selection-list show-format-pane", visible: false },
            { id: "selection-list convert-to-range-slider", visible: false },
         ],
         [
            { id: "selection-list select-all", visible: false }
         ],
         [
            { id: "vs-object remove", visible: false },
            { id: "selection-list viewer-remove-from-container", visible: false }
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
            { id: "selection-list search", visible: true },
            { id: "selection-list sort", visible: true },
            { id: "selection-list sort-asc", visible: false },
            { id: "selection-list sort-desc", visible: false },
            { id: "selection-list reverse", visible: true },
            { id: "selection-list unselect", visible: true },
            { id: "selection-list apply", visible: false }
         ]
      ];

      //check status in viewer
      const model = createModel();
      const actions1 = new SelectionListActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new SelectionListActions(model2, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();
   });

   it("check status of toolbar actions of selection list in viewer when single selection", () => {
      const expectedToolbar = [
         [
            { id: "selection-list open-max-mode", visible: true },
            { id: "selection-list search", visible: true },
            { id: "selection-list sort", visible: true },
            { id: "selection-list sort-asc", visible: false },
            { id: "selection-list sort-desc", visible: false },
            { id: "selection-list reverse", visible: false },
            { id: "selection-list unselect", visible: false },
            { id: "selection-list apply", visible: false }
         ]
      ];

      const model = createModel();
      model.singleSelection = true;
      const actions = new SelectionListActions(model, ViewerContextProviderFactory(false));
      const toolbarActions = actions.toolbarActions;

      expect(toolbarActions).toMatchSnapshot();
   });

   //bug#18022, bug#18105
   it("check status of menu actions and toolbar actions of selection list in composer when in selection container", () => {
      const expectedMenu = [
         [
            { id: "selection-list properties", visible: true },
            { id: "selection-list convert-to-range-slider", visible: true },
         ],
         [
            { id: "selection-list select-all", visible: false }
         ],
         [
            { id: "vs-object remove", visible: true },
            { id: "selection-list viewer-remove-from-container", visible: false }
         ],
      ];

      const expectedToolbar = [
         [
            { id: "selection-list search", visible: true },
            { id: "selection-list sort", visible: true },
            { id: "selection-list sort-asc", visible: false },
            { id: "selection-list sort-desc", visible: false },
            { id: "selection-list reverse", visible: true },
            { id: "selection-list unselect", visible: true },
            { id: "selection-list apply", visible: false }
         ]
      ];
      const model = createModel();
      model.container = "aa";
      model.containerType = "VSSelectionContainer";
      const actions = new SelectionListActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug#18046, viewer remove action
      const actions2 = new SelectionListActions(model, ViewerContextProviderFactory(false));
      const menuActions2 = actions2.menuActions;
      expect(menuActions2[2].actions[0].visible()).toBeFalsy();
      expect(menuActions2[2].actions[1].visible()).toBeTruthy();
   });

   //bug#18064
   it("check status of menu actions and toolbar actions of selection list in composer when" +
      " triggered by filter", () => {
      const expectedMenu = [
         [
            { id: "selection-list properties", visible: false },
            { id: "selection-list show-format-pane", visible: false },
            { id: "selection-list convert-to-range-slider", visible: false },
         ],
         [
            { id: "selection-list select-all", visible: false }
         ],
         [
            { id: "vs-object remove", visible: false },
            { id: "selection-list viewer-remove-from-container", visible: false }
         ],
      ];

      const expectedToolbar = [
         [
            { id: "selection-list search", visible: true },
            { id: "selection-list sort", visible: true },
            { id: "selection-list sort-asc", visible: false },
            { id: "selection-list sort-desc", visible: false },
            { id: "selection-list reverse", visible: true },
            { id: "selection-list unselect", visible: true },
            { id: "selection-list apply", visible: false }
         ]
      ];

      const model = createModel();
      model.adhocFilter = true;
      const actions = new SelectionListActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("unselect action should not be present when Single Selection is true", () => {
      const model = createModel();
      model.singleSelection = true;
      const actions = new SelectionListActions(model, ViewerContextProviderFactory(false));
      const toolbarActions = actions.toolbarActions;
      const group = toolbarActions[1];
      const unselectAction = group.actions.find((action) => action.label().indexOf("Unselect") >= 0);

      expect(unselectAction.visible()).toBeFalsy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new SelectionListActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});