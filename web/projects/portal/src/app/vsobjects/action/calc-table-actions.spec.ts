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
import { TableDataPathTypes } from "../../common/data/table-data-path-types";
import { TestUtils } from "../../common/test/test-utils";
import {
   BindingContextProviderFactory, ComposerContextProviderFactory, ViewerContextProviderFactory
} from "../context-provider.service";
import { VSCalcTableModel } from "../model/vs-calctable-model";
import { CalcTableActions } from "./calc-table-actions";

describe("CalcTableActions", () => {
   const createModel: () => VSCalcTableModel = () => {
      return TestUtils.createMockVSCalcTableModel("Table1");
   };

   let model: VSCalcTableModel;

   beforeEach(() => {
      model = createModel();
   });

   const selectCell: (model: VSCalcTableModel) => void = () => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["Cell [1,3]"];
      region.type = TableDataPathTypes.DETAIL;

      model.titleSelected = false;
      model.firstSelectedRow = 1;
      model.firstSelectedColumn = 3;
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [3]);
      model.selectedRegions = [region];
   };

   const selectTitle: (model: VSCalcTableModel) => void = () => {
      let region = TestUtils.createMockselectedRegion();
      region.path = null;
      region.type = TableDataPathTypes.TITLE;

      model.selectedData = null;
      model.selectedHeaders = null;
      model.selectedRegions = [region];
   };

   const selectHeader: (model: VSCalcTableModel) => void = () => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["Cell [0,0]"];
      region.type = TableDataPathTypes.HEADER;

      model.titleSelected = false;
      model.firstSelectedRow = 0;
      model.firstSelectedColumn = 0;
      model.selectedHeaders = null;
      model.selectedData =  new Map<number, number[]>();
      model.selectedData.set(0, [0]);
      model.selectedRegions = [region];
   };

   it("check status of menu actions and toolbar actions in composer", () => {
      const expectedMenu = [
         [
            { id: "calc-table properties", visible: true },
            { id: "calc-table show-format-pane", visible: true },
            { id: "calc-table conditions", visible: true },
            { id: "calc-table reset-table-layout", visible: false},
            { id: "calc-table annotate", visible: false },
            { id: "calc-table filter", visible: false }
         ],
         [
            { id: "calc-table sorting", visible: true }
         ],
         [
            { id: "calc-table hyperlink", visible: false },
            { id: "calc-table highlight", visible: false }
         ],
         [
            { id: "calc-table merge-cells", visible: false },
            { id: "calc-table split-cells", visible: false },
            { id: "calc-table insert-rows-columns", visible: false }
         ],
         [
            { id: "calc-table insert-row", visible: false },
            { id: "calc-table append-row", visible: false },
            { id: "calc-table delete-row", visible: false }
         ],
         [
            { id: "calc-table insert-column", visible: false },
            { id: "calc-table append-column", visible: false },
            { id: "calc-table delete-column", visible: false }
         ],
         [
            { id: "calc-table copy-cell", visible: false },
            { id: "calc-table cut-cell", visible: false },
            { id: "calc-table paste-cell", visible: false },
            { id: "calc-table remove-cell", visible: false }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "calc-table open-max-mode", visible: false },
            { id: "calc-table close-max-mode", visible: false },
            { id: "calc-table show-details", visible: false },
            { id: "calc-table export", visible: true },
            { id: "calc-table multi-select", visible: false },
            { id: "calc-table edit", visible: true }
         ]
      ];

      const actions = new CalcTableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions in binding", () => {
      const expectedMenu = [
         [
            { id: "calc-table properties", visible: true },
            { id: "calc-table show-format-pane", visible: true },
            { id: "calc-table conditions", visible: false },
            { id: "calc-table reset-table-layout", visible: false},
            { id: "calc-table annotate", visible: false },
            { id: "calc-table filter", visible: false }
         ],
         [
            { id: "calc-table sorting", visible: false }
         ],
         [
            { id: "calc-table hyperlink", visible: false },
            { id: "calc-table highlight", visible: false }
         ],
         [
            { id: "calc-table merge-cells", visible: true },
            { id: "calc-table split-cells", visible: true },
            { id: "calc-table insert-rows-columns", visible: true }
         ],
         [
            { id: "calc-table insert-row", visible: true },
            { id: "calc-table append-row", visible: true },
            { id: "calc-table delete-row", visible: true }
         ],
         [
            { id: "calc-table insert-column", visible: true },
            { id: "calc-table append-column", visible: true },
            { id: "calc-table delete-column", visible: true }
         ],
         [
            { id: "calc-table copy-cell", visible: true },
            { id: "calc-table cut-cell", visible: true },
            { id: "calc-table paste-cell", visible: true },
            { id: "calc-table remove-cell", visible: true }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "calc-table open-max-mode", visible: false },
            { id: "calc-table close-max-mode", visible: false },
            { id: "calc-table show-details", visible: false },
            { id: "calc-table export", visible: true },
            { id: "calc-table multi-select", visible: false },
            { id: "calc-table edit", visible: false }
         ]
      ];

      const actions = new CalcTableActions(model, BindingContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //select text cell
      model.firstSelectedColumn = 0;
      model.firstSelectedRow = 0;
      model.selectedData = new Map();
      model.selectedData.set(0, [0]);
      model.selectedRegions = [{
         level: 0,
         col: false,
         row: false,
         type: TableDataPathTypes.DETAIL,
         dataType: "string",
         path: ["Cell[1,1]"],
         index: 0,
         colIndex: 0,
         bindingType: 1
      }];

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   xit("check status of menu actions and toolbar actions in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "calc-table properties", visible: false },
            { id: "calc-table show-format-pane", visible: false },
            { id: "calc-table conditions", visible: false },
            { id: "calc-table reset-table-layout", visible: false},
            { id: "calc-table annotate", visible: false },
            { id: "calc-table filter", visible: false }
         ],
         [
            { id: "calc-table sorting", visible: false }
         ],
         [
            { id: "calc-table hyperlink", visible: false },
            { id: "calc-table highlight", visible: false }
         ],
         [
            { id: "calc-table merge-cells", visible: false },
            { id: "calc-table split-cells", visible: false },
            { id: "calc-table insert-rows-columns", visible: false }
         ],
         [
            { id: "calc-table insert-row", visible: false },
            { id: "calc-table append-row", visible: false },
            { id: "calc-table delete-row", visible: false }
         ],
         [
            { id: "calc-table insert-column", visible: false },
            { id: "calc-table append-column", visible: false },
            { id: "calc-table delete-column", visible: false }
         ],
         [
            { id: "calc-table copy-cell", visible: false },
            { id: "calc-table cut-cell", visible: false },
            { id: "calc-table paste-cell", visible: false },
            { id: "calc-table remove-cell", visible: false }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "calc-table open-max-mode", visible: true },
            { id: "calc-table close-max-mode", visible: false },
            { id: "calc-table show-details", visible: false },
            { id: "calc-table export", visible: true },
            { id: "calc-table multi-select", visible: false },
            { id: "calc-table edit", visible: false }
         ]
      ];

      //check status in viewer
      const actions1 = new CalcTableActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new CalcTableActions(model2, BindingContextProviderFactory(false));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();

      //Bug #17514 check script apply
      model.enableAdvancedFeatures = true;
      selectTitle(model);
      expect(menuActions1[0].actions[1].visible()).toBeTruthy();

      model.actionNames = ["Condition", "Highlight"];
      expect(menuActions1[0].actions[1].visible()).toBeFalsy();

      model2.enableAdvancedFeatures = true;
      model2.firstSelectedColumn = 0;
      model2.firstSelectedRow = 1;
      model2.selectedData = new Map();
      model2.selectedData.set(1, [0]);
      model2.selectedRegions = [{
         level: 0,
         col: false,
         row: false,
         type: TableDataPathTypes.GROUP_HEADER,
         dataType: "string",
         path: ["Cell[1,0]"],
         index: 0,
         colIndex: 0,
         bindingType: 2
      }];
      expect(menuActions2[2].actions[1].visible()).toBeTruthy();

      model2.actionNames = ["Condition", "Highlight"];
      expect(menuActions2[2].actions[1].visible()).toBeFalsy();
   });

   it("check status of menu actions and toolbar actions in composer when select group cell", () => {
      const expectedMenu = [
         [
            { id: "calc-table properties", visible: true },
            { id: "calc-table show-format-pane", visible: true },
            { id: "calc-table conditions", visible: false },
            { id: "calc-table reset-table-layout", visible: false},
            { id: "calc-table annotate", visible: false },
            { id: "calc-table filter", visible: true }
         ],
         [
            { id: "calc-table sorting", visible: false }
         ],
         [
            { id: "calc-table hyperlink", visible: true },
            { id: "calc-table highlight", visible: true }
         ],
         [
            { id: "calc-table merge-cells", visible: false },
            { id: "calc-table split-cells", visible: false },
            { id: "calc-table insert-rows-columns", visible: false }
         ],
         [
            { id: "calc-table insert-row", visible: false },
            { id: "calc-table append-row", visible: false },
            { id: "calc-table delete-row", visible: false }
         ],
         [
            { id: "calc-table insert-column", visible: false },
            { id: "calc-table append-column", visible: false },
            { id: "calc-table delete-column", visible: false }
         ],
         [
            { id: "calc-table copy-cell", visible: false },
            { id: "calc-table cut-cell", visible: false },
            { id: "calc-table paste-cell", visible: false },
            { id: "calc-table remove-cell", visible: false }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "calc-table open-max-mode", visible: false },
            { id: "calc-table close-max-mode", visible: false },
            { id: "calc-table show-details", visible: true },
            { id: "calc-table export", visible: true },
            { id: "calc-table multi-select", visible: false },
            { id: "calc-table edit", visible: true }
         ]
      ];

      const actions = new CalcTableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      model.firstSelectedColumn = 0;
      model.firstSelectedRow = 1;
      model.selectedData = new Map();
      model.selectedData.set(1, [0]);
      model.selectedRegions = [{
         level: 0,
         col: false,
         row: false,
         type: TableDataPathTypes.GROUP_HEADER,
         dataType: "string",
         path: ["Cell[1,0]"],
         index: 0,
         colIndex: 0,
         bindingType: 2
      }];

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions in viewer when select summary cell", () => {
      const expectedMenu = [
         [
            { id: "calc-table properties", visible: false },
            { id: "calc-table show-format-pane", visible: false },
            { id: "calc-table conditions", visible: false },
            { id: "calc-table reset-table-layout", visible: false},
            { id: "calc-table annotate", visible: false },
            { id: "calc-table filter", visible: true }
         ],
         [
            { id: "calc-table sorting", visible: false }
         ],
         [
            { id: "calc-table hyperlink", visible: false },
            { id: "calc-table highlight", visible: false }
         ],
         [
            { id: "calc-table merge-cells", visible: false },
            { id: "calc-table split-cells", visible: false },
            { id: "calc-table insert-rows-columns", visible: false }
         ],
         [
            { id: "calc-table insert-row", visible: false },
            { id: "calc-table append-row", visible: false },
            { id: "calc-table delete-row", visible: false }
         ],
         [
            { id: "calc-table insert-column", visible: false },
            { id: "calc-table append-column", visible: false },
            { id: "calc-table delete-column", visible: false }
         ],
         [
            { id: "calc-table copy-cell", visible: false },
            { id: "calc-table cut-cell", visible: false },
            { id: "calc-table paste-cell", visible: false },
            { id: "calc-table remove-cell", visible: false }
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
         ],
         [
            { id: "annotation edit", visible: false },
            { id: "annotation format", visible: false },
            { id: "annotation remove", visible: false }
         ]
      ];

      const expectedToolbar = [
         [
            { id: "calc-table open-max-mode", visible: true },
            { id: "calc-table close-max-mode", visible: false },
            { id: "calc-table show-details", visible: true },
            { id: "calc-table export", visible: true },
            { id: "calc-table multi-select", visible: false },
            { id: "calc-table edit", visible: false }
         ]
      ];

      const actions1 = new CalcTableActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;
      model.firstSelectedColumn = 1;
      model.firstSelectedRow = 1;
      model.selectedData = new Map();
      model.selectedData.set(1, [1]);
      model.selectedRegions = [{
         level: 0,
         col: false,
         row: false,
         type: TableDataPathTypes.SUMMARY,
         dataType: "string",
         path: ["Cell[1,1]"],
         index: 0,
         colIndex: 0,
         bindingType: 2
      }];
      model.adhocFilterEnabled = true;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();
   });

   // Bug #17157
   it("check filter button visible on text cell or binding cell", () => {
      const actions = new CalcTableActions(model, ViewerContextProviderFactory(false));
      const action = actions.menuActions[0].actions[5];

      selectCell(model);
      model.adhocFilterEnabled = true;
      model.selectedRegions[0].bindingType = 2;
      expect(action.id()).toBe("calc-table filter");
      expect(action.visible()).toBe(true);

      model.selectedRegions[0].bindingType = 1;
      expect(action.id()).toBe("calc-table filter");
      expect(action.visible()).toBe(false);
   });

   // Bug #17264
   it("should show show details button when non-text cell is selected in the composer", () => {
      const actions = new CalcTableActions(model, ComposerContextProviderFactory());
      const action = actions.toolbarActions[1].actions[2];

      selectTitle(model);
      expect(action).toBeTruthy();
      expect(action.id()).toBe("calc-table show-details");
      expect(action.visible()).toBe(false);

      selectCell(model);
      model.selectedRegions[0].bindingType = 1;
      expect(action.visible()).toBe(false);

      selectCell(model);
      model.selectedRegions[0].bindingType = 2;
      expect(action.visible()).toBe(true);
   });

   // Bug #17187 should display annotation action when in viewer and has security
   it("should display annotation action when in viewer and has security", () => {
      const actions = new CalcTableActions(model, ViewerContextProviderFactory(false), true);
      const menuActions = actions.menuActions;

      selectHeader(model);
      expect(TestUtils.toString(menuActions[0].actions[4].label())).toBe("Annotate Cell");
      expect(menuActions[0].actions[4].visible()).toBeTruthy();

      selectTitle(model);
      expect(TestUtils.toString(menuActions[0].actions[4].label())).toBe("Annotate Component");
      expect(menuActions[0].actions[4].visible()).toBeTruthy();

      selectCell(model);
      expect(TestUtils.toString(menuActions[0].actions[4].label())).toBe("Annotate Cell");
      expect(menuActions[0].actions[4].visible()).toBeTruthy();

      // Bug #21151 should not display annotate action when assembly is not enabled
      model.enabled = false;
      expect(menuActions[0].actions[3].visible()).toBeFalsy();
   });

   //Bug #17624 should hide condition and sort item when click calc table cell
   it("should hide condition and sort item when click calc table cell", () => {
      const actions = new CalcTableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      let condition = menuActions[0].actions[3];
      let sort = menuActions[1].actions[0];

      selectCell(model);
      expect(condition.visible()).toBeFalsy();
      expect(sort.visible()).toBeFalsy();
   });

   //Bug #17658 should hide filter item on viewer when no container included
   it("should hide filter item on viewer when no container included", () => {
      const actions = new CalcTableActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;
      let filter = menuActions[0].actions[5];

      selectCell(model);
      model.adhocFilterEnabled = false;
      expect(filter.visible()).toBeFalsy();
   });

   it("should only show reset layout in default state", () => {
      const testModel = createModel();
      const actions = new CalcTableActions(testModel, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      testModel.selectedHeaders = new Map<number, number[]>();
      testModel.selectedHeaders.set(0, [0, 1]);
      testModel.explicitTableWidth = true;
      expect(menuActions[0].actions[3].visible())
         .toBeFalsy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const actions = new CalcTableActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});
