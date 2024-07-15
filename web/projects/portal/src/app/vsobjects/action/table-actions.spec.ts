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
import { TableDataPathTypes } from "../../common/data/table-data-path-types";
import { TestUtils } from "../../common/test/test-utils";
import {
   BindingContextProviderFactory, ComposerContextProviderFactory, ContextProvider, ViewerContextProviderFactory
} from "../context-provider.service";
import { VSTableModel } from "../model/vs-table-model";
import { TableActions } from "./table-actions";

describe("TableActions", () => {
   const createModel: () => VSTableModel = () => {
      return TestUtils.createMockVSTableModel("Table1");
   };

   const selectTitle: (model: VSTableModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = null;
      region.type = TableDataPathTypes.TITLE;

      model.selectedData = null;
      model.selectedHeaders = null;
      model.selectedRegions = [region];
      model.titleSelected = true;
   };

   const selectHeaderCell: (model: VSTableModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["State"];
      region.type = TableDataPathTypes.HEADER;

      model.titleSelected = false;
      model.firstSelectedRow = 0;
      model.firstSelectedColumn = 0;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0]);
      model.selectedData = null;
      model.selectedRegions = [region];
   };

   const selectDetailCell: (model: VSTableModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["City"];
      region.type = TableDataPathTypes.DETAIL;

      model.titleSelected = false;
      model.firstSelectedRow = 1;
      model.firstSelectedColumn = 3;
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [3]);
      model.selectedRegions = [region];
   };

   const selectSummaryDetailCell: (model: VSTableModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["customer_id"];
      region.dataType = "double";
      region.type = TableDataPathTypes.DETAIL;

      model.summary = true;
      model.titleSelected = false;
      model.firstSelectedRow = 1;
      model.firstSelectedColumn = 3;
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [3]);
      model.selectedRegions = [region];
   };

   //bug #18337, remove sort menu
   it("check status of menu actions and toolbar actions of common table in composer", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: true },
            { id: "table show-format-pane", visible: true }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: true },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: true },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: false },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: false },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: true }
         ],
         [
            { id: "table selection-reset", visible: false },
            { id: "table selection-apply", visible: false }
         ]
      ];

      const model = createModel();
      model.titleSelected = true;
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug#17897 reset table layout action
      model.explicitTableWidth = true;
      expect(menuActions[2].actions[1].visible()).toBeTruthy();

      //Bug #19287 copy/paste highlight
      model.highlightedCells = [{
               row: true,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: ["City"],
               type: TableDataPathTypes.DETAIL
            }];
      selectDetailCell(model);
      expect(menuActions[1].actions[2].visible()).toBeFalsy();
   });

   it("check status of menu actions and toolbar actions of common table in binding", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: true },
            { id: "table show-format-pane", visible: true },
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: false },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: false },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: false },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: false }
         ],
         [
            { id: "table selection-reset", visible: false },
            { id: "table selection-apply", visible: false }
         ]
      ];

      const model = createModel();
      model.titleSelected = true;
      const actions = new TableActions(model, BindingContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug#17897 reset table layout action
      model.explicitTableWidth = true;
      expect(menuActions[2].actions[1].visible()).toBeTruthy();
   });

   it("check status of menu actions and toolbar actions of common table in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: false },
            { id: "table show-format-pane", visible: false }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: false },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: false },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: true },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: false }
         ],
         [
            { id: "table selection-reset", visible: false },
            { id: "table selection-apply", visible: false }
         ]
      ];

      //check status in viewer
      const model = createModel();
      model.titleSelected = true;
      const actions1 = new TableActions(model, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      model2.titleSelected = true;
      const actions2 = new TableActions(model2, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();

      //Bug #17514 check script apply
      model.enableAdvancedFeatures = true;
      selectTitle(model);
      expect(menuActions1[2].actions[0].visible()).toBeTruthy();

      model.actionNames = ["Condition", "Highlight"];
      expect(menuActions1[2].actions[0].visible()).toBeFalsy();

      model2.enableAdvancedFeatures = true;
      selectDetailCell(model2);
      // TODO fix spec
      // expect(menuActions2[1].actions[1].visible()).toBeTruthy();

      model2.actionNames = ["Condition", "Highlight"];
      expect(menuActions2[1].actions[1].visible()).toBeFalsy();
   });

   it("check status of menu actions and toolbar actions of common table in binding when select table header", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: true },
            { id: "table show-format-pane", visible: true }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: false },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: true },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: false },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: false }
         ],
         [
            { id: "table selection-reset", visible: false },
            { id: "table selection-apply", visible: false }
         ]
      ];

      const model = createModel();
      selectHeaderCell(model);
      const actions = new TableActions(model, BindingContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions of common table in preview when select detail cell", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: false },
            { id: "table show-format-pane", visible: false }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: false },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: true },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: false },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: true },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: false }
         ],
         [
            { id: "table selection-reset", visible: false },
            { id: "table selection-apply", visible: false }
         ]
      ];

      const model = createModel();
      selectDetailCell(model);
      const actions = new TableActions(model, BindingContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions of form table in composer when select table header", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: true },
            { id: "table show-format-pane", visible: true }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: true },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: false },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: true },
            { id: "table column-options", visible: true }
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
            { id: "table open-max-mode", visible: false },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: true }
         ],
         [
            {id: "table selection-reset", visible: false},
            { id: "table selection-apply", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      model.form = true;
      selectHeaderCell(model);
      model.columnEditorEnabled[0] = true;
      model.sortInfo = {
         sortable: true
      };

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
      expect(menuActions[1].actions[0].visible()).toBeFalsy();

      //Bug #19287 copy/paste highlight
      model.highlightedCells = [{
               row: true,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: [],
               type: TableDataPathTypes.DETAIL
            }];
      selectDetailCell(model);
      expect(menuActions[1].actions[2].visible()).toBeFalsy();
   });

   it("check status of menu actions and toolbar actions of form table in viewer when select detail cell", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: false },
            { id: "table show-format-pane", visible: false }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: false },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: true },
            { id: "table append-row", visible: true },
            { id: "table delete-rows", visible: true },
            { id: "table delete-columns", visible: false },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: true },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: false }
         ],
         [
            {id: "table selection-reset", visible: false},
            { id: "table selection-apply", visible: false }
         ]
      ];

      const model = createModel();
      model.form = true;
      model.insert = true;
      model.del = true;
      selectDetailCell(model);
      const actions = new TableActions(model, ViewerContextProviderFactory(false), true);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions of embedded table in composer when select table title", () => {
      const expectedMenu = [
         [
            { id: "table properties", visible: true },
            { id: "table show-format-pane", visible: true }
         ],
         [
            { id: "table hyperlink", visible: false },
            { id: "table highlight", visible: false },
            { id: "table copy-highlight", visible: false },
            { id: "table paste-highlight", visible: false }
         ],
         [
            { id: "table conditions", visible: false },
            { id: "table reset-table-layout", visible: false },
            { id: "table convert-to-freehand-table", visible: true },
            { id: "table annotate title", visible: false },
            { id: "table annotate cell", visible: false },
            { id: "table filter", visible: false }
         ],
         [
            { id: "table insert-row", visible: false },
            { id: "table append-row", visible: false },
            { id: "table delete-rows", visible: false },
            { id: "table delete-columns", visible: false },
            { id: "table column-options", visible: false }
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
            { id: "table open-max-mode", visible: false },
            { id: "table close-max-mode", visible: false },
            { id: "table show-details", visible: false },
            { id: "table export", visible: true },
            { id: "table multi-select", visible: false },
            { id: "table edit", visible: true }
         ],
         [
            {id: "table selection-reset", visible: true},
            { id: "table selection-apply", visible: true }
         ]
      ];

      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      model.embedded = true;
      selectTitle(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //should not display apply and reset when embedded table is submit on change
      model.submitOnChange = true;
      expect(toolbarActions[1].actions[0].visible()).toBeFalsy();
      expect(toolbarActions[1].actions[1].visible()).toBeFalsy();
      model.submitOnChange = false;

      //Bug #19287 copy/paste highlight
      model.highlightedCells = [{
               row: true,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: [],
               type: TableDataPathTypes.DETAIL
            }];
      selectDetailCell(model);
      expect(menuActions[1].actions[2].visible()).toBeFalsy();
   });

   // Bug #9934 should show Condition option when no cells are selected.
   it("should show condition option in composer", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectTitle(model);
      expect(TestUtils.toString(menuActions[2].actions[0].label())).toBe("Conditions");
      expect(menuActions[2].actions[0].visible()).toBeTruthy();
   });

   // Bug #10272 should not show hyperlink action if form table
   // Bug #17141 should display Delete Column(s) and Column Option when select header of form table
   it("should hide hyperlink action and show delete column and column options if form table", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      model.form = true;
      model.selectedHeaders = new Map();
      expect(TestUtils.toString(menuActions[1].actions[0].label())).toBe("Hyperlink");
      expect(menuActions[1].actions[0].visible()).toBeFalsy();
      expect(TestUtils.toString(menuActions[3].actions[3].label())).toBe("Delete Columns");
      expect(menuActions[3].actions[3].visible()).toBeTruthy();
      expect(TestUtils.toString(menuActions[3].actions[4].label())).toBe("Column Options");
      expect(menuActions[3].actions[4].visible()).toBeTruthy();
   });

   // Bug #10359 show detail should be visible when table is summary
   it("should see show details if table type is summary", () => {
      const model = createModel();
      const actions = new TableActions(model, ViewerContextProviderFactory(false));
      const toolbarActions = actions.toolbarActions;

      selectDetailCell(model);
      model.summary = true;
      expect(toolbarActions[1].actions[0].visible()).toBeTruthy();
   });

   // Bug #10271 should not show default actions when cell is selected.
   it("should hide default options when cell selected", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectDetailCell(model);
      expect(TestUtils.toString(menuActions[4].actions[0].label())).toBe("Copy");
      expect(menuActions[4].actions[0].visible()).toBeFalsy();
   });

   // Bug #10819 do no show conditions option when embedded table.
   it("should hide conditions option when the table is embedded", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectTitle(model);
      model.embedded = true;
      expect(TestUtils.toString(menuActions[2].actions[0].label())).toBe("Conditions");
      expect(menuActions[2].actions[0].visible()).toBeFalsy();
   });

   // Bug #10824 disabled convert to freehand table when table is form.
   it("should hide convert to freehand table when table is form", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectTitle(model);
      model.form = true;
      expect(TestUtils.toString(menuActions[2].actions[2].label())).toBe("Convert to Freehand Table");
      expect(menuActions[2].actions[2].enabled()).toBeFalsy();
   });

   // Bug #10856/10857/10858/10859 filter and hyperlink should not show for embedded and form tables.
   it("should hide hyperlink option when table column is editable", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectHeaderCell(model);
      model.form = true;
      model.columnEditorEnabled = [true];

      expect(TestUtils.toString(menuActions[1].actions[0].label())).toBe("Hyperlink");
      expect(menuActions[1].actions[0].visible())
         .toBeFalsy();
   });

   // Bug #15997
   it("should show hyperlink option when table column is not editable", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectHeaderCell(model);
      model.selectedData = null;
      model.form = true;
      model.columnEditorEnabled = [false];

      expect(menuActions[1].actions[0].visible())
         .toBeFalsy();
   });

   it("should hide hyperlink option when table is embedded", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      selectHeaderCell(model);
      model.embedded = true;

      expect(TestUtils.toString(menuActions[1].actions[0].label())).toBe("Hyperlink");
      expect(menuActions[1].actions[0].visible())
         .toBeFalsy();
   });

   // Bug #16875, prevent regression, the edit toolbar button should be visible in the
   // viewer, but not in the composer preview
   it("edit toolbar action visible in viewer", () => {
      const model = createModel();
      const actions = new TableActions(model, ViewerContextProviderFactory(false));
      model.enableAdhoc = true;
      const action = actions.toolbarActions[1].actions[5];
      expect(action.id()).toBe("table edit");
      expect(action.visible()).toBe(true);
   });

   // Bug #16875, prevent regression, the edit toolbar button should be visible in the
   // viewer, but not in the composer preview
   it("edit toolbar action should not be visible in preview", () => {
      const model = createModel();
      const actions = new TableActions(model, new ContextProvider(true, false, true, false, false, false, false, false, false, false, false));
      model.enableAdhoc = true;
      const action = actions.toolbarActions[1].actions[5];
      expect(action.id()).toBe("table edit");
      // TODO fix spec
      // expect(action.visible()).toBe(false);
   });

   // Bug #10075 column option should be visible for form tables.
   it("should show column option in binding", () => {
      const model = createModel();
      const actions = new TableActions(model, BindingContextProviderFactory(false));
      const menuActions = actions.menuActions;

      selectHeaderCell(model);
      model.form = true;
      expect(menuActions[3].actions[4].id()).toBe("table column-options");
      // TODO fix spec
      // expect(menuActions[3].actions[4].visible()).toBeTruthy();
   });

   // Bug #17128
   it("should show ungroup menu when no cell is selected", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());

      selectTitle(model);
      expect(actions.menuActions[4].actions[4].id()).toBe("vs-object ungroup");
      expect(actions.menuActions[4].actions[4].visible()).toBe(true);
      expect(actions.menuActions[4].actions[3].id()).toBe("vs-object group");
      expect(actions.menuActions[4].actions[3].visible()).toBe(true);
   });

   // Bug #17264
   // Bug #21293 should not display show details for form table
   it("should show show details toolbar button for non-editable table when summary cell is selected in the composer", () => {
      const model = createModel();
      const actions = new TableActions(model, ComposerContextProviderFactory());
      const action = actions.toolbarActions[1].actions[2];

      selectTitle(model);
      expect(action).toBeTruthy();
      expect(action.id()).toBe("table show-details");
      expect(action.visible()).toBe(false);

      selectDetailCell(model);
      expect(action.visible()).toBe(false);

      selectSummaryDetailCell(model);
      expect(action.visible()).toBe(true);

      model.form = true;
      expect(action.visible()).toBe(false);
   });

   // Bug #17187 should display annotation action when in viewer and has security
   it("should display annotation action when in viewer and has security", () => {
      const model = createModel();
      const actions = new TableActions(model, ViewerContextProviderFactory(false), true);
      const menuActions = actions.menuActions;

      selectHeaderCell(model);
      expect(TestUtils.toString(menuActions[2].actions[4].label())).toBe("Annotate Cell");
      expect(menuActions[2].actions[4].visible()).toBeTruthy();

      selectTitle(model);
      expect(TestUtils.toString(menuActions[2].actions[3].label())).toBe("Annotate Component");
      expect(menuActions[2].actions[3].visible()).toBeTruthy();

      selectDetailCell(model);
      expect(TestUtils.toString(menuActions[2].actions[4].label())).toBe("Annotate Cell");
      expect(menuActions[2].actions[4].visible()).toBeTruthy();

      // Bug #21151 should not display annotate action when assembly is not enabled
      model.enabled = false;
      //temp ignore for bug is not fixed correctly
      //expect(menuActions[2].actions[4].visible()).toBeFalsy(
      //   "Annotate Cell should not be visible when assembly is not enabled");
      selectTitle(model);
      expect(menuActions[2].actions[3].visible()).toBeFalsy();
   });

   // Bug #17382 should hide delete column and column options in composer preview
   it("should hide delete column and column options in composer preview", () => {
      const model = createModel();
      const actions = new TableActions(model, new ContextProvider(false, true, true, false, false, false, false, false, false, false, false));
      const menuActions = actions.menuActions;

      selectHeaderCell(model);

      expect(TestUtils.toString(menuActions[3].actions[3].label())).toBe("Delete Columns");
      // TODO fix spec
      // expect(menuActions[3].actions[3].visible()).toBeFalsy();
      expect(TestUtils.toString(menuActions[3].actions[4].label())).toBe("Column Options");
      expect(menuActions[3].actions[4].visible()).toBeFalsy();
   });

   //Bug #16082 should show condition and highlight item on viewer when advancedFeatures is true"
   it("should show condition and highlight item on viewer when advancedFeatures is true", () => {
      const model = createModel();
      const actions = new TableActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;
      let condition = menuActions[2].actions[0];
      let hightlight = menuActions[1].actions[1];

      model.enableAdvancedFeatures = true;
      selectTitle(model);
      expect(condition.visible()).toBeTruthy();
      expect(hightlight.visible()).toBeFalsy();

      selectHeaderCell(model);
      expect(hightlight.visible()).toBeTruthy();

      selectDetailCell(model);
      expect(hightlight.visible()).toBeTruthy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new TableActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });
});