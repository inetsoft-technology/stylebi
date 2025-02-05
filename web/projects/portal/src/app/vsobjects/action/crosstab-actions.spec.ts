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
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { CrosstabActions } from "./crosstab-actions";

describe("CrosstabActions", () => {
   const createModel: () => VSCrosstabModel = () => {
      return TestUtils.createMockVSCrosstabModel("Table1");
   };

   //SummaryCell: summary cell
   const selectSummaryCell: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["city", "Year(orderdate)", "product_name", "Max(customer_id)"];
      region.type = TableDataPathTypes.SUMMARY;

      model.firstSelectedRow = 4;
      model.firstSelectedColumn = 16;
      model.selectedHeaders = null;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(4, [16]);
      model.selectedRegions = [region];
   };

   //GrandTotalCell: grand total header
   const selectGrandTotoalHeaderCell: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["ROW_GRAND_TOTAL"];
      region.type = TableDataPathTypes.GRAND_TOTAL;

      model.firstSelectedRow = 10;
      model.firstSelectedColumn = 0;
      model.selectedData = null;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(10, [0]);
      model.selectedRegions = [region];
   };

   //GrandTotalCell: grand total summary cell
   const selectGrandTotoalSummaryCell: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["ROW_GRAND_TOTAL", "Year(Date)", "Sum(Discount)"];
      region.type = TableDataPathTypes.GRAND_TOTAL;

      model.selectedRegions = [region];
      model.selectedHeaders = null;
      model.firstSelectedColumn = 5;
      model.firstSelectedRow = 10;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(10, [5]);
   };

   //HeaderCell: text header, summary header
   const selectHeaderCell: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["Cell [0,0]"];
      region.type = TableDataPathTypes.HEADER;

      model.firstSelectedRow = 0;
      model.firstSelectedColumn = 0;
      model.selectedData = null;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0]);
      model.selectedRegions = [region];
   };

   //GroupHeaderCell: col,row group header
   const selectGroupHeader: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["City"];
      region.type = TableDataPathTypes.GROUP_HEADER;

      model.selectedData = null;
      model.runtimeColHeaderCount = 1;
      model.runtimeRowHeaderCount = 1;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(5, [1]);
      model.selectedRegions = [region];
      model.cells = [
         [{
            cellData: "Cell1",
            cellLabel: null,
            row: 5,
            col: 1,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               row: false,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: ["City"],
               type: TableDataPathTypes.GROUP_HEADER
            },
            presenter: null,
            isImage: false
         }]
      ];
   };

   const selectMultGroupHeader: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["City"];
      region.type = TableDataPathTypes.GROUP_HEADER;

      model.selectedRegions = [region, region];
      model.selectedData = null;
      model.runtimeColHeaderCount = 1;
      model.runtimeRowHeaderCount = 1;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0, 1]);
      model.cells = [
         [{
            cellData: "Cell1",
            cellLabel: null,
            row: 0,
            col: 0,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               row: false,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: ["City"],
               type: TableDataPathTypes.GROUP_HEADER
            },
            presenter: null,
            isImage: false
         }, {
            cellData: "Cell2",
            cellLabel: null,
            row: 0,
            col: 1,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               row: false,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: ["City"],
               type: TableDataPathTypes.GROUP_HEADER
            },
            presenter: null,
            isImage: false
         }]
      ];
   };

   const selectFakeHeader: (model: VSCrosstabModel) => void = (model) => {
      let region = TestUtils.createMockselectedRegion();
      region.path = ["Aggregate"];
      region.type = TableDataPathTypes.GROUP_HEADER;

      model.selectedData = null;
      model.runtimeColHeaderCount = 0;
      model.runtimeRowHeaderCount = 1;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [1]);
      model.selectedRegions = [region];
      model.cells = [
         [{
            cellData: "Aggregate",
            cellLabel: null,
            row: 0,
            col: 1,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               row: false,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: ["Aggregate"],
               type: TableDataPathTypes.GROUP_HEADER
            },
            presenter: null,
            isImage: false
         }]
      ];
   };

   // broken chart menus test temporarily, the date comparison feature is doing.
   // will update this test after finish the feature
   it("check status of menu actions and toolbar actions in composer", () => {
      const expectedMenu = [
         [
            { id: "crosstab properties", visible: true },
            { id: "crosstab show-format-pane", visible: true }
         ],
         [
            { id: "crosstab hyperlink", visible: false },
            { id: "crosstab highlight", visible: false },
            { id: "crosstab copy-highlight", visible: false },
            { id: "crosstab paste-highlight", visible: false }
         ],
         [
            { id: "crosstab conditions", visible: true },
            { id: "crosstab reset-table-layout", visible: false },
            { id: "crosstab convert-to-freehand-table", visible: true },
            { id: "crosstab annotate", visible: false },
            { id: "crosstab filter", visible: false }
         ],
         [
            { id: "crosstab group", visible: false },
            { id: "crosstab rename", visible: false },
            { id: "crosstab ungroup", visible: false }
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
            { id: "crosstab open-max-mode", visible: false },
            { id: "crosstab close-max-mode", visible: false },
            { id: "crosstab show-details", visible: false },
            { id: "crosstab export", visible: true },
            { id: "crosstab multi-select", visible: false },
            { id: "crosstab edit", visible: true }
         ]
      ];
      const model = createModel();
      let contextProvider = ComposerContextProviderFactory();
      const actions = new CrosstabActions(model, contextProvider);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug#17897 reset table layout action
      model.explicitTableWidth = true;
      expect(menuActions[3].actions[1].visible()).toBeTruthy();

      //Bug #19287 copy/paste highlight
      model.highlightedCells = [{
               row: true,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: [],
               type: TableDataPathTypes.GROUP_HEADER
            }];
      selectGroupHeader(model);
      expect(menuActions[1].actions[2].visible()).toBeFalsy();
   });

   it("check status of menu actions and toolbar actions in binding", () => {
      const expectedMenu = [
         [
            { id: "crosstab properties", visible: true },
            { id: "crosstab show-format-pane", visible: true }
         ],
         [
            { id: "crosstab hyperlink", visible: false },
            { id: "crosstab highlight", visible: false },
            { id: "crosstab copy-highlight", visible: false },
            { id: "crosstab paste-highlight", visible: false }
         ],
         [
            { id: "crosstab conditions", visible: false },
            { id: "crosstab reset-table-layout", visible: false },
            { id: "crosstab convert-to-freehand-table", visible: false },
            { id: "crosstab annotate", visible: false },
            { id: "crosstab filter", visible: false }
         ],
         [
            { id: "crosstab group", visible: false },
            { id: "crosstab rename", visible: false },
            { id: "crosstab ungroup", visible: false }
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
            { id: "crosstab open-max-mode", visible: false },
            { id: "crosstab close-max-mode", visible: false },
            { id: "crosstab show-details", visible: false },
            { id: "crosstab export", visible: true },
            { id: "crosstab multi-select", visible: false },
            { id: "crosstab edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new CrosstabActions(model, BindingContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug#17897 reset table layout action
      model.explicitTableWidth = true;
      expect(menuActions[3].actions[1].visible()).toBeTruthy();
   });

   it("check status of menu actions and toolbar actions in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "crosstab properties", visible: false },
            { id: "crosstab show-format-pane", visible: false },
         ],
         [
            { id: "crosstab hyperlink", visible: false },
            { id: "crosstab highlight", visible: false },
            { id: "crosstab copy-highlight", visible: false },
            { id: "crosstab paste-highlight", visible: false }
         ],
         [
            { id: "crosstab conditions", visible: false },
            { id: "crosstab reset-table-layout", visible: false },
            { id: "crosstab convert-to-freehand-table", visible: false },
            { id: "crosstab annotate", visible: false },
            { id: "crosstab filter", visible: false }
         ],
         [
            { id: "crosstab group", visible: false },
            { id: "crosstab rename", visible: false },
            { id: "crosstab ungroup", visible: false }
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
            { id: "crosstab open-max-mode", visible: true },
            { id: "crosstab close-max-mode", visible: false },
            { id: "crosstab show-details", visible: false },
            { id: "crosstab export", visible: true },
            { id: "crosstab multi-select", visible: false },
            { id: "crosstab edit", visible: false }
         ]
      ];

      //check status in viewer
      const model1 = createModel();
      const actions1 = new CrosstabActions(model1, ViewerContextProviderFactory(false));
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const model2 = createModel();
      const actions2 = new CrosstabActions(model2, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();

      //bug #18112
      selectGroupHeader(model2);
      model2.firstSelectedRow = 5;
      model2.firstSelectedColumn = 1;
      model2.cells = [
         [{
            cellData: "Cell1",
            cellLabel: null,
            row: 5,
            col: 1,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: true,
            dataPath: {
               row: false,
               col: false,
               colIndex: -1,
               index: 0,
               level: -1,
               dataType: "string",
               path: ["City"],
               type: TableDataPathTypes.GROUP_HEADER
            },
            presenter: null,
            isImage: false
         }]
      ];

      expect(menuActions2[4].actions[1].visible()).toBeTruthy();
      expect(menuActions2[4].actions[2].visible()).toBeTruthy();
   });

   it("check status of menu actions and toolbar actions in binding when select multiple group header", () => {
      const expectedMenu = [
         [
            { id: "crosstab properties", visible: true },
            { id: "crosstab show-format-pane", visible: true }
         ],
         [
            { id: "crosstab hyperlink", visible: false },
            { id: "crosstab highlight", visible: false },
            { id: "crosstab copy-highlight", visible: false },
            { id: "crosstab paste-highlight", visible: false }
         ],
         [
            { id: "crosstab conditions", visible: false },
            { id: "crosstab reset-table-layout", visible: false },
            { id: "crosstab convert-to-freehand-table", visible: false },
            { id: "crosstab annotate", visible: false },
            { id: "crosstab filter", visible: false }
         ],
         [
            { id: "crosstab group", visible: true },
            { id: "crosstab rename", visible: false },
            { id: "crosstab ungroup", visible: false }
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
            { id: "crosstab open-max-mode", visible: false },
            { id: "crosstab close-max-mode", visible: false },
            { id: "crosstab show-details", visible: true },
            { id: "crosstab export", visible: true },
            { id: "crosstab multi-select", visible: false },
            { id: "crosstab edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new CrosstabActions(model, BindingContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectMultGroupHeader(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   //Bug #18673 no sort action
   it("check status of menu actions and toolbar actions in composer when select header cell", () => {
      const expectedMenu = [
         [
            { id: "crosstab properties", visible: true },
            { id: "crosstab show-format-pane", visible: true }
         ],
         [
            { id: "crosstab hyperlink", visible: true },
            { id: "crosstab highlight", visible: true },
            { id: "crosstab copy-highlight", visible: false },
            { id: "crosstab paste-highlight", visible: false }
         ],
         [
            { id: "crosstab conditions", visible: false },
            { id: "crosstab reset-table-layout", visible: false },
            { id: "crosstab convert-to-freehand-table", visible: false },
            { id: "crosstab annotate", visible: false },
            { id: "crosstab filter", visible: true }
         ],
         [
            { id: "crosstab group", visible: false },
            { id: "crosstab rename", visible: false },
            { id: "crosstab ungroup", visible: false }
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
            { id: "crosstab open-max-mode", visible: false },
            { id: "crosstab close-max-mode", visible: false },
            { id: "crosstab show-details", visible: true },
            { id: "crosstab export", visible: true },
            { id: "crosstab multi-select", visible: false },
            { id: "crosstab edit", visible: true }
         ]
      ];

      const model = createModel();
      const actions = new CrosstabActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectGroupHeader(model);
      model.sortInfo = {
         sortable: true
      };

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   it("check status of menu actions and toolbar actions in viewer when select summary cell", () => {
      const expectedMenu = [
         [
            { id: "crosstab properties", visible: false },
            { id: "crosstab show-format-pane", visible: false }
         ],
         [
            { id: "crosstab hyperlink", visible: false },
            { id: "crosstab highlight", visible: false },
            { id: "crosstab copy-highlight", visible: false },
            { id: "crosstab paste-highlight", visible: false }
         ],
         [
            { id: "crosstab conditions", visible: false },
            { id: "crosstab reset-table-layout", visible: false },
            { id: "crosstab convert-to-freehand-table", visible: false },
            { id: "crosstab annotate", visible: false },
            { id: "crosstab filter", visible: true }
         ],
         [
            { id: "crosstab group", visible: false },
            { id: "crosstab rename", visible: false },
            { id: "crosstab ungroup", visible: false }
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
            { id: "crosstab open-max-mode", visible: true },
            { id: "crosstab close-max-mode", visible: false },
            { id: "crosstab show-details", visible: true },
            { id: "crosstab export", visible: true },
            { id: "crosstab multi-select", visible: false },
            { id: "crosstab edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectSummaryCell(model);
      model.sortInfo = {
         sortable: true
      };
      model.adhocFilterEnabled = true;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   // Bug #10402 do not show reset layout when it is in default state
   it("should only show reset layout in default state", () => {
      const model = createModel();
      const actions = new CrosstabActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0, 1]);
      model.explicitTableWidth = true;
      expect(menuActions[3].actions[1].visible())
         .toBeFalsy();
   });

   // Bug #16875, prevent regression, the edit toolbar button should be visible in the
   // viewer, but not in the composer preview
   it("edit toolbar action should be visible in the viewer", () => {
      const model = createModel();
      model.enableAdhoc = true;
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false));
      const action = actions.toolbarActions[1].actions[7];
      expect(action.id()).toBe("crosstab edit");
      expect(action.visible()).toBe(true);
   });

   // Bug #16875, prevent regression, the edit toolbar button should be visible in the
   // viewer, but not in the composer preview
   it("edit toolbar action should not be visible in preview", () => {
      const model = createModel();
      model.enableAdhoc = true;
      const actions = new CrosstabActions(createModel(),
         new ContextProvider(true, false, true, false, false, false, false, false, false, false, false));
      const action = actions.toolbarActions[1].actions[7];
      expect(action.id()).toBe("crosstab edit");
      expect(action.visible()).toBe(false);
   });

   // Bug #10084 group should be visible for when 2 header cell selected.
   it("should show group", () => {
      const model = createModel();
      const actions = new CrosstabActions(model, BindingContextProviderFactory(false));
      const menuActions = actions.menuActions;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0, 1]);
      model.cells = [
         [{
            cellData: "Cell1",
            cellLabel: null,
            row: 0,
            col: 0,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               level: 0,
               col: false,
               row: false,
               type: 2048,
               dataType: "string",
               path: [],
               index: 0,
               colIndex: 0
            },
            presenter: null,
            isImage: false
         }, {
            cellData: "Cell2",
            cellLabel: null,
            row: 0,
            col: 0,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               level: 0,
               col: false,
               row: false,
               type: 2048,
               dataType: "string",
               path: [],
               index: 0,
               colIndex: 0
            },
            presenter: null,
            isImage: false
         }]
      ];

      expect(menuActions[4].actions[0].visible()).toBeTruthy();
   });

   // Bug #10406 do not show group for date types.
   // Bug #19905 do not show group for boolean types.
   // Bug #19939 do not show group for date range column
   it("should not show group for date, boolean types and date range columns", () => {
      const model = createModel();
      const actions = new CrosstabActions(model, BindingContextProviderFactory(false));
      const menuActions = actions.menuActions;
      model.selectedHeaders = new Map<number, number[]>();
      model.selectedHeaders.set(0, [0, 1]);
      model.cells = [
         [{
            cellData: "Cell1",
            cellLabel: null,
            row: 0,
            col: 0,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               level: 0,
               col: false,
               row: false,
               type: 2048,
               dataType: "date",
               path: [],
               index: 0,
               colIndex: 0
            },
            presenter: null,
            isImage: false
         }, {
            cellData: "Cell2",
            cellLabel: null,
            row: 0,
            col: 0,
            vsFormatModel: TestUtils.createMockVSFormatModel(),
            hyperlinks: [],
            grouped: false,
            dataPath: {
               level: 0,
               col: false,
               row: false,
               type: 2048,
               dataType: "date",
               path: [],
               index: 0,
               colIndex: 0
            },
            presenter: null,
            isImage: false
         }]
      ];

      expect(menuActions[4].actions[0].visible()).toBeFalsy();

      model.cells[0][0].dataPath.dataType = "boolean";
      model.cells[0][1].dataPath.dataType = "boolean";
      expect(menuActions[4].actions[0].visible()).toBeFalsy();

      model.cells[0][0].dataPath.dataType = "integer";
      model.cells[0][1].dataPath.dataType = "integer";
      model.cells[0][0].dataPath.path = ["Year(orderdate)", "QuarterOfYear(orderdate)"];
      model.cells[0][1].dataPath.path = ["Year(orderdate)", "QuarterOfYear(orderdate)"];
      expect(menuActions[4].actions[0].visible()).toBeFalsy();
   });

   // Bug #17095, Bug #17186
   it("should only show filter menu when group header or summary cell is selected", () => {
      const model = createVSCrosstabModel();
      model.adhocFilterEnabled = true;
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false));
      const action = actions.menuActions[3].actions[4];

      expect(action.id()).toBe("crosstab filter");
      expect(action.visible()).toBe(false);

      selectHeaderCell(model);
      expect(action.visible()).toBe(false);

      selectGrandTotoalHeaderCell(model);
      expect(action.visible()).toBe(false);

      selectGroupHeader(model);
      expect(action.visible()).toBe(true);

      selectGrandTotoalHeaderCell(model);
      expect(action.visible()).toBe(false);

      selectSummaryCell(model);
      expect(action.visible()).toBe(true);

      selectGrandTotoalSummaryCell(model);
      expect(action.visible()).toBe(true);
   });

   // Bug #17264
   it("should show show details toolbar button when non-static header cell selected in composer", () => {
      const model = createModel();
      model.firstSelectedColumn = -1;
      model.firstSelectedRow = -1;
      model.selectedHeaders = null;
      model.runtimeColHeaderCount = 1;
      model.runtimeRowHeaderCount = 1;
      model.headerColCount = 1;
      model.headerRowCount = 1;

      const actions = new CrosstabActions(model, ComposerContextProviderFactory());
      const action = actions.toolbarActions[1].actions[4];

      expect(action).toBeTruthy();
      expect(action.id()).toBe("crosstab show-details");
      expect(action.visible()).toBe(false);

      model.firstSelectedColumn = 2;
      model.firstSelectedRow = 1;
      model.selectedData = new Map<number, number[]>();
      model.selectedData.set(1, [2]);
      expect(action.visible()).toBe(true);
   });

   // Bug #17187 should display annotation action when in viewer and has security
   it("should display annotation action when in viewer and has security", () => {
      const model = createModel();
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false), true);
      const menuActions = actions.menuActions;

      model.selectedHeaders = new Map();
      model.selectedHeaders.set(0, [0]);
      expect(TestUtils.toString(menuActions[3].actions[3].label())).toBe("Annotate Cell");
      expect(menuActions[3].actions[3].visible()).toBeTruthy();

      model.selectedHeaders = null;
      expect(TestUtils.toString(menuActions[3].actions[3].label())).toBe("Annotate Component");
      expect(menuActions[3].actions[3].visible()).toBeTruthy();

      model.selectedData = new Map();
      model.selectedData.set(1, [1]);
      expect(TestUtils.toString(menuActions[3].actions[3].label())).toBe("Annotate Cell");
      expect(menuActions[3].actions[3].visible()).toBeTruthy();

      //bug #18109, #18017, should display annotate cell when select multiple header
      selectMultGroupHeader(model);
      expect(TestUtils.toString(menuActions[3].actions[3].label())).toBe("Annotate Cell");
      expect(menuActions[3].actions[3].visible()).toBeTruthy();

      // Bug #21151 should not display annotate action when assembly is not enabled
      model.enabled = false;
      expect(menuActions[3].actions[3].visible()).toBeFalsy();
   });

   // Bug #17305 should display show details when summay cell is selected in binding panel
   it("should create binding toolbar actions", () => {
      const model = createModel();
      const actions = new CrosstabActions(model, BindingContextProviderFactory(false));
      const toolbarActions = actions.toolbarActions;
      expect(toolbarActions).toBeTruthy();
      expect(toolbarActions.length).toBe(3);

      expect(toolbarActions[1].actions).toBeTruthy();
      expect(toolbarActions[1].actions.length).toBe(8);

      selectSummaryCell(model);
      expect(toolbarActions[1].actions[4].id()).toBe("crosstab show-details");
      expect(toolbarActions[1].actions[4].visible()).toBeTruthy();
   });

   // highlight visiable on diff data path
   it("highlight menu visible on different data path of composer", () => {
      const model = createVSCrosstabModel();
      const actions = new CrosstabActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      let highlight = menuActions[1].actions[1];
      let region = TestUtils.createMockselectedRegion();

      //title
      model.titleSelected = true;
      expect(highlight.visible()).toBeFalsy();
      model.titleSelected = false;

       //HeaderCell: text header, summary header
      selectHeaderCell(model);
      expect(highlight.visible()).toBeFalsy();

      //GrandTotalHeaderCell: grand total header
      selectGrandTotoalHeaderCell(model);
      expect(highlight.visible()).toBeFalsy();

      //SummaryCell: group total cell, summary cell,
      selectSummaryCell(model);
      expect(highlight.visible()).toBeTruthy();

      //GroupHeaderCell: row, col group header cell
      //existed Bug
      selectGroupHeader(model);
      expect(highlight.visible()).toBeTruthy();

      //GrandTotalCell: grand total summary cell
      selectGrandTotoalSummaryCell(model);
      expect(highlight.visible()).toBeTruthy();
   });

   //Bug #17518 and Bug #16082, bug #18412
   it("highlight menu visible on different data path in viewer", () => {
      const model = createVSCrosstabModel();
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;
      let highlight = menuActions[1].actions[1];
      let condition = menuActions[3].actions[0];
      let region = TestUtils.createMockselectedRegion();
      model.enableAdvancedFeatures = true;

      //title
      model.titleSelected = true;
      expect(highlight.visible()).toBeFalsy();
      expect(condition.visible()).toBeTruthy();
      model.titleSelected = false;

       //HeaderCell: text header, summary header
      selectHeaderCell(model);
      expect(highlight.visible()).toBeFalsy();

      //GrandTotalHeaderCell: grand total header
      selectGrandTotoalHeaderCell(model);
      expect(highlight.visible()).toBeFalsy();

      //SummaryCell: group total cell, summary cell,
      selectSummaryCell(model);
      expect(highlight.visible()).toBeTruthy();

      //GroupHeaderCell: row, col group header cell
      selectGroupHeader(model);
      expect(highlight.visible()).toBeTruthy();

      //GrandTotalCell: grand total summary cell
      selectGrandTotoalSummaryCell(model);
      expect(highlight.visible()).toBeTruthy();
   });

   //Bug #17514 check script apply
   it("check script apply in viewer", () => {
      const model = createVSCrosstabModel();
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;
      let highlight = menuActions[2].actions[1];
      let condition = menuActions[3].actions[0];
      let region = TestUtils.createMockselectedRegion();
      model.enableAdvancedFeatures = true;
      model.actionNames = ["Condition", "Highlight"];

      //title
      model.titleSelected = true;
      expect(condition.visible()).toBeFalsy();
      model.titleSelected = false;

       //HeaderCell: text header, summary header
      selectHeaderCell(model);
      expect(highlight.visible()).toBeFalsy();

      //GrandTotalHeaderCell: grand total header
      selectGrandTotoalHeaderCell(model);
      expect(highlight.visible()).toBeFalsy();

      //SummaryCell: group total cell, summary cell,
      selectSummaryCell(model);
      expect(highlight.visible()).toBeFalsy();

      //GroupHeaderCell: row, col group header cell
      selectGroupHeader(model);
      expect(highlight.visible()).toBeFalsy();

      //GrandTotalCell: grand total summary cell
      selectGrandTotoalSummaryCell(model);
      expect(highlight.visible()).toBeFalsy();
   });

   //Bug #17596 should not show adhoc filter when no container existed on viewer side
   it("should not show adhoc filter when no container existed on viewer", () => {
      const model = createVSCrosstabModel();
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(true));
      const menuActions = actions.menuActions;
      let filter = menuActions[3].actions[4];
      let region = TestUtils.createMockselectedRegion();

      region.path = ["City"];
      region.type = TableDataPathTypes.SUMMARY;
      model.selectedRegions = [region];
      model.adhocFilterEnabled = false;
      expect(filter.visible()).toBeFalsy();
   });

   //Bug #17763 should show group item when select multi group header cell
   it("should show group item when select multi group header cell", () => {
      const model = createModel();
      const actions = new CrosstabActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      let group = menuActions[4].actions[0];

      selectMultGroupHeader(model);
      expect(group.visible()).toBeTruthy();
   });

   //Bug #19986 should not display menu action when as data tip component
   it("should not display menu action when as data tip component", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false), false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(menuActions.length).toBe(0);
   });

   //Bug #17885 filter should not be visible when select fake aggregate
   it("filter should not be visible when select fake aggregate", () => {
      const model = Object.assign({
         headerHeights: [],
         cellHeight: 0,
         rowNames: [],
         colNames: [],
         aggrNames: [],
         sortTypeMap: {},
         dataTypeMap: {},
         sortOnHeader: false,
         sortAggregate: false,
         sortDimension: false,
         containsFakeAggregate: true,
         dateRangeNames: [],
         cells: [],
         adhocFilterEnabled: true,
         timeSeriesNames: [],
         hasHiddenColumn: false,
         trendComparisonAggrNames: [],
         customPeriod: false,
         dateComparisonEnabled: false,
         dateComparisonDefined: false,
         appliedDateComparison: false,
         dcMergedColumn: null
         }, TestUtils.createMockBaseTableModel("name"));
      selectFakeHeader(model);
      const actions = new CrosstabActions(model, ViewerContextProviderFactory(false));
      const menuActions = actions.menuActions;

      expect(menuActions[3].actions[4].visible()).toBeFalsy();
   });

   //Bug #20919 should disable convert to freehand when bind cube source
   it("should disable convert to freehand when bind cube source", () => {
      const model = createVSCrosstabModel();
      model.advancedStatus = "[Query1]";
      const actions = new CrosstabActions(model, ComposerContextProviderFactory());
      const menuActions = actions.menuActions;
      let convert = menuActions[3].actions[2];
      expect(convert.enabled()).toBeTruthy();
      model.cubeType = "SqlServer";
      expect(convert.enabled()).toBeFalsy();
   });

   function createVSCrosstabModel(): VSCrosstabModel {
      let name = "Table1";

      return Object.assign({
         headerHeights: [],
         cellHeight: 0,
         rowNames: ["City"],
         colNames: [],
         aggrNames: ["Sum(Discount)"],
         sortTypeMap: {},
         dataTypeMap: {},
         sortOnHeader: false,
         sortAggregate: false,
         sortDimension: false,
         containsFakeAggregate: false,
         dateRangeNames: ["Year(orderdate)", "QuarterOfYear(orderdate)",
            "MonthOfYear(orderdate)", "DayOfMonth(orderdate)", "HourOfDay(orderdate)"],
         cells: [],
         timeSeriesNames: [],
         hasHiddenColumn: false,
         trendComparisonAggrNames: [],
         customPeriod: false,
         dateComparisonEnabled: false,
         dateComparisonDefined: false,
         appliedDateComparison: false,
         dcMergedColumn: null
         }, TestUtils.createMockBaseTableModel(name));
   }
});
