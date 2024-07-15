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
import { GraphTypes } from "../../common/graph-types";
import { TestUtils } from "../../common/test/test-utils";
import { Axis } from "../../graph/model/axis";
import { ChartRegion } from "../../graph/model/chart-region";
import { Legend } from "../../graph/model/legend";
import { Plot } from "../../graph/model/plot";
import { Title } from "../../graph/model/title";
import {
   BindingContextProviderFactory, ComposerContextProviderFactory, ContextProvider, ViewerContextProviderFactory
} from "../context-provider.service";
import { VSChartModel } from "../model/vs-chart-model";
import { ChartActions } from "./chart-actions";

describe("ChartActions", () => {
   const createModel: () => VSChartModel = () => {
      return TestUtils.createMockVSChartModel("Chart1");
   };

   const popService: any = { getPopComponent: jest.fn() };
   const composerContext = ComposerContextProviderFactory();
   const bindingContext = BindingContextProviderFactory(false);
   const viewerContext = ViewerContextProviderFactory(false);
   popService.getPopComponent.mockImplementation(() => "");

   const createRegion: () => ChartRegion = () => {
      return {
         segTypes: [],
         pts: [],
         centroid: null,
         index: -1,
         tipIdx: -1,
         metaIdx: 0,
         rowIdx: 0,
         valIdx: -1,
         hyperlinks: [],
         noselect: false,
         grouped: false,
         boundaryIdx: -1,
         vertical: false,
      };
   };

   const createRegionWithMeasure: () => ChartRegion = () => {
      return {
         segTypes: [],
         pts: [],
         centroid: null,
         index: -1,
         tipIdx: -1,
         metaIdx: 0,
         rowIdx: 0,
         valIdx: -1,
         hyperlinks: [],
         noselect: false,
         grouped: false,
         boundaryIdx: -1,
         vertical: false,
      };
   };

   const selectMeasureBar: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Plot> {
            areaName: "plot_area",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [],
            secondary: false,
            xboundaries: [],
            yboundaries: [],
            showReferenceLine: false,
            showPlotResizers: false,
         },
         regions: [ createRegionWithMeasure() ]
      };
      model.regionMetaDictionary = [{meaIdx: 0, hasMeasure: true}];
   };

   const selectXAxis: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "bottom_x_axis",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [ createRegion() ],
            secondary: false
         },
         regions: [
            createRegion()]
      };
      model.stringDictionary = ["Label"];
   };

   const selectMultiXAxis: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "bottom_x_axis",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [ createRegion() ],
            secondary: false
         },
         regions: [
            createRegion(),
            createRegion() ]
      };
      model.stringDictionary = ["Label"];
   };

   const selectLegendContent: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Legend> {
            areaName: "legend_content",
            titleLabel: "legend",
            aestheticType: ""
         },
         regions: [ createRegion() ]
      };
      model.stringDictionary = ["Label"];
   };

   const selectXTitle: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Title> {
            areaName: "x_title"
         },
         regions: [ createRegion() ]
      };
   };

   const selectPlot: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Plot> {
            areaName: "plot_area",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [],
            secondary: false,
            xboundaries: [],
            yboundaries: [],
            showReferenceLine: false,
            showPlotResizers: false,
         },
          regions: [ ]
      };
   };

   const selectYAxis: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "left_y_axis",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [ ],
            secondary: false
         },
         regions: [
            createRegionWithMeasure() ]
      };
      model.regionMetaDictionary[0].hasMeasure = true;
   };

   const selectVO: (model: VSChartModel) => void = (model) => {
      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "plot_area",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [],
            secondary: false
         },
         regions: []
      };
   };

   //Bug #10833 do not show hyperlink for measure axis
   //Bug #17156, group should be visible when select multi x axis label.
   it("should show hyperlink for dimension axes", () => {
      const model: VSChartModel = createModel();
      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "bottom_x_axis",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [ createRegion() ],
            secondary: false
         },
         regions: [
            createRegion(),
            createRegion()
         ]
      };
      model.stringDictionary = ["Label"];
      model.regionMetaDictionary = [{dimIdx: 0, meaIdx: -1, colIdx: 0}];
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      expect(menuActions[1].actions[0].id()).toBe("chart axis-hyperlink");
      expect(menuActions[1].actions[0].visible()).toBeTruthy();

      // for Bug #17156, group should be visible when select multi x axis label.
      expect(menuActions[3].actions[0].id()).toBe("chart group");
      expect(menuActions[3].actions[0].visible()).toBeTruthy();
   });

   //Bug #16468 do not show hyperlink/highlight option if there is no measure field
   //Bug #20927
   it("should not show hyperlink/highlight on plot region not containing measure", () => {
      const model: VSChartModel = createModel();
      model.chartSelection = {
         chartObject: <Plot> {
            areaName: "plot_area",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [],
            secondary: false,
            xboundaries: [],
            yboundaries: [],
            showReferenceLine: false,
            showPlotResizers: false,
         },
         regions: [ createRegion() ]
      };
      model.stringDictionary = ["Label"];
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      expect(menuActions[1].actions[1].visible()).toBeFalsy();
      // expect(menuActions[1].actions[2].visible()).toBeFalsy();
   });

   // Bug #17179
   it("should have visible title property action when axis title is selected", () => {
      const model = createModel();
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      expect(menuActions).toBeTruthy();

      const action = menuActions[0].actions[3];
      expect(action.id()).toBe("chart title-properties");
      expect(action.visible()).toBe(false);

      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "x2_title",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [ createRegion() ],
            secondary: false
         },
         regions: [
            createRegion(),
            createRegion()
         ]
      };

      expect(action.visible()).toBe(true);
   });

   // Bug #17201
   it("should not show the filter menu action on a stock, candle, or map chart when vo is selected", () => {
      const model = createModel();
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      expect(menuActions).toBeTruthy();

      const action = menuActions[2].actions[1];
      expect(action.id()).toBe("chart filter");
      expect(action.visible()).toBe(false);

      model.chartType = GraphTypes.CHART_BAR;
      let region = createRegionWithMeasure();
      model.regionMetaDictionary = [{areaType: "vo", hasMeasure: true}];
      selectVO(model);
      model.chartSelection.regions = [region];

      model.adhocFilterEnabled = true;
      expect(action.visible()).toBe(true);

      model.chartType = GraphTypes.CHART_STOCK;
      model.plotHighlightEnabled = false;
      expect(action.visible()).toBe(false);

      model.chartType = GraphTypes.CHART_CANDLE;
      expect(action.visible()).toBe(false);
      //bug #18147, candle chart hyperlink menu
      expect(menuActions[1].actions[0].visible()).toBeFalsy();
      // unsupport now by milk
      // expect(menuActions[1].actions[1].visible()).toBeTruthy();

      model.chartType = GraphTypes.CHART_MAP;
      expect(action.visible()).toBe(false);
   });

   // Bug #17187 should display annotation action when in viewer and has security
   it("should display annotation action when in viewer with has security and not maximize mode", () => {
      const model = createModel();
      const actions = new ChartActions(model, popService, viewerContext, true);
      const menuActions = actions.menuActions;
      expect(menuActions).toBeTruthy();

      expect(TestUtils.toString(menuActions[5].actions[0].label())).toBe("Annotate Component");
      expect(menuActions[5].actions[0].visible()).toBeTruthy();

      model.chartType = GraphTypes.CHART_BAR;
      selectMeasureBar(model);
      expect(TestUtils.toString(menuActions[5].actions[0].label())).toBe("Annotate Point");
      expect(menuActions[5].actions[0].visible()).toBeTruthy();

      // Bug #18082 should not dispaly annotation action when in maximize mode
      model.maxMode = true;
      expect(menuActions[5].actions[0].visible()).toBeFalsy();

      // bug #18125 should display annotate component when select vo text
      model.maxMode = false;
      model.showValues = true;
      model.regionMetaDictionary = [{colIdx: 0, areaType: "text", hasMeasure: true}],
      model.chartSelection.regions = [{
         segTypes: [],
         pts: [],
         centroid: null,
         index: -1,
         tipIdx: -1,
         metaIdx: 0,
         rowIdx: 0,
         valIdx: -1,
         hyperlinks: [],
         noselect: false,
         grouped: false,
         boundaryIdx: -1,
      }];
      expect(TestUtils.toString(menuActions[5].actions[0].label())).toBe("Annotate Component");

      // Bug #21151 should not display annotate action when assembly is not enabled
      model.enabled = false;
      expect(menuActions[5].actions[0].visible()).toBeFalsy();
   });

   // Bug #16823
   it("should not show the filter menu action on a viewer when no container", () => {
      const model = createModel();
      const actions = new ChartActions(model, popService, viewerContext);
      const menuActions = actions.menuActions;
      const action = menuActions[2].actions[1];

      model.adhocFilterEnabled = false;
      selectPlot(model);
      expect(action.visible()).toBe(false);
   });

   // Bug #17718 Don't show filter if plot selection does not contain a measure
   it("should not show the filter menu action plot does not contain measure", () => {
      const model = createModel();
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      expect(menuActions).toBeTruthy();

      const action = menuActions[2].actions[1];
      expect(action.id()).toBe("chart filter");
      expect(action.visible()).toBe(false);

      model.chartType = GraphTypes.CHART_BAR;
      model.chartSelection = {
         chartObject: <Axis> {
            areaName: "plot_area",
            bounds: null,
            layoutBounds: null,
            tiles: null,
            regions: [ createRegionWithMeasure() ],
            secondary: false
         },
         regions: [ createRegionWithMeasure() ]
      };

      model.regionMetaDictionary = [{dimIdx: -1, meaIdx: 1, colIdx: 1, hasMeasure: true}];
      model.adhocFilterEnabled = true;
      expect(action.visible()).toBe(true);

      model.regionMetaDictionary[0].hasMeasure = false;

      expect(action.visible()).toBe(false);
   });

   // broken chart menus test temporarily, the date comparison feature is doing.
   // will update this test after finish the feature
   xit("check status of menu actions and toolbar actions in composer", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: true },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: true }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      model.regionMetaDictionary = [{dimIdx: 0, meaIdx: -1, colIdx: 0}];

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug #17528, #17902, should display filter and show details in composer when select xAxis
      selectXAxis(model);
      model.adhocFilterEnabled = true;
      expect(menuActions[2].actions[1].visible()).toBe(true);
      expect(toolbarActions[1].actions[6].visible()).toBe(true);

      //bug #18149, should not display hyperlink and highlight when select legend of map chart
      model.chartType = GraphTypes.CHART_MAP;
      selectLegendContent(model);
      expect(menuActions[1].actions[1].visible()).toBe(false);
      expect(menuActions[1].actions[2].visible()).toBe(false);
   });

   xit("check status of menu actions and toolbar actions in binding", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: true },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: true },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, bindingContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug #20958
      model.maxMode = true;
      expect(toolbarActions[1].actions[10].visible()).toBeFalsy();
      expect(toolbarActions[1].actions[11].visible()).toBeTruthy();
   });

   xit("check status of menu actions and toolbar actions in viewer and preview", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: true },
            { id: "chart show-format-pane", visible: true },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: true },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      //check status in viewer
      const model1 = createModel();
      const actions1 = new ChartActions(model1, popService, viewerContext);
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //bug #18563, chart Edit should support in viewer
      model1.enableAdhoc = true;
      const editAction =
         toolbarActions1[0].actions.find((action) => action.id() === "chart edit");
      expect(editAction.visible()).toBeTruthy();

      const expectedMenu2 = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: true },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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

      //check status in preview
      const model = createModel();
      const actions2 = new ChartActions(model, popService, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();

      //bug #18019, should display filter in preview when select bar
      model.adhocFilterEnabled = true;
      model.chartType = GraphTypes.CHART_BAR;
      selectMeasureBar(model);
      expect(menuActions2[2].actions[1].visible()).toBe(true);

      //Bug #19149, should not display filter when in maxmize mode
      model.maxMode = true;
      expect(menuActions2[2].actions[1].visible()).toBe(false);
   });

   xit("check status of menu actions and toolbar actions in composer when select measure bar", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: true },
            { id: "chart highlight", visible: true },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: true },
            { id: "chart filter", visible: true }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: true },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: true },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: true },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: true },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: true }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      model.chartType = GraphTypes.CHART_BAR;
      selectMeasureBar(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();

      //bug ##18553, setActionVisible function
      model.actionNames =
         ["Properties", "Filter", "Brush", "Zoom Chart", "Exclude Data", "Show Data", "Show Details"];
      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
      model.actionNames = [];

      //Bug #19225 add clear brush/zoom to right menu
      model.brushed = true;
      expect(menuActions[1].actions[4].visible()).toBe(true);
      model.brushed = false;
      model.zoomed = true;
      expect(menuActions[1].actions[3].visible()).toBe(true);
      model.zoomed = false;
   });

   //bug #17587, group should be visible when select multiple axis
   xit("check status of menu actions and toolbar actions in viewer when select multiple bottom xAxis", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: true },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: true },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: true },
            { id: "chart show-format-pane", visible: true },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: true },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: true },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: true },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: true },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: true },
            { id: "chart open-max-mode", visible: true },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, viewerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectMultiXAxis(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   xit("check status of menu actions and toolbar actions in preview when select xTitle", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: true },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: true },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: true },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: true },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, ViewerContextProviderFactory(true));
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectXTitle(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   xit("check status of menu actions and toolbar actions in binding when select legend content", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: true },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: true },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: true },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: true },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: true },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: true },
            { id: "chart open-max-mode", visible: true },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: true },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, bindingContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectLegendContent(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   xit("check status of menu actions and toolbar actions in viewer when select plot area", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: true },
            { id: "chart show-format-pane", visible: true },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: true },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, viewerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      selectPlot(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   xit("check status of menu actions and toolbar actions in composer when select yAxis", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: true },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: true },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: true },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: true }
         ]
      ];

      const model = createModel();
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;
      model.chartType = GraphTypes.CHART_BAR;
      selectYAxis(model);

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   //Bug #17710，#18532 should not show filter when click text measure
   it("should not show filter item when click text measure", () => {
      const model = createModel();
      const actions = new ChartActions(model, popService, ViewerContextProviderFactory(true));
      const menuActions = actions.menuActions;
      let filter = menuActions[2].actions[1];

      let region = createRegionWithMeasure();
      model.regionMetaDictionary = [{areaType: "text", hasMeasure: true}];
      selectVO(model);
      model.chartSelection.regions = [region];
      expect(TestUtils.toString(filter.label())).toEqual("Filter");
      expect(filter.visible()).toBeFalsy();
   });

   //bug #18553, setActionVisible function
   xit("check status of menu actions and toolbar actions in viewer and preview when use setActionVisible", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: false },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: false },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: false },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      model.enableAdhoc = true;
      model.chartType = GraphTypes.CHART_BAR;
      selectMeasureBar(model);
      model.actionNames = ["Properties", "Save Image As", "Format", "Filter", "Brush",
         "Zoom Chart", "Exclude Data", "Show Data", "Show Details", "Open Max Mode", "Edit"];

      //check in viewer
      const actions1 = new ChartActions(model, popService, viewerContext);
      const menuActions1 = actions1.menuActions;
      const toolbarActions1 = actions1.toolbarActions;

      expect(menuActions1).toMatchSnapshot();
      expect(toolbarActions1).toMatchSnapshot();

      //check status in preview
      const actions2 = new ChartActions(model, popService, ViewerContextProviderFactory(true));
      const menuActions2 = actions2.menuActions;
      const toolbarActions2 = actions2.toolbarActions;

      expect(menuActions2).toMatchSnapshot();
      expect(toolbarActions2).toMatchSnapshot();
   });

   //bug #18521, should display highlight when select plot area in composer
   xit("check status of menu actions and toolbar actions in composer when select plot area", () => {
      const expectedMenu = [
         [
            { id: "chart axis-properties", visible: false },
            { id: "chart legend-properties", visible: false },
            { id: "chart properties", visible: true },
            { id: "chart title-properties", visible: false },
            { id: "chart show-format-pane", visible: true },
            { id: "chart hide-title", visible: false },
            { id: "chart show-title", visible: false },
            { id: "chart hide-axis", visible: false },
            { id: "chart hide-legend", visible: false },
            { id: "chart save-image-as", visible: false },
            { id: "chart show-format-pane", visible: false },
            { id: "chart resize-plot", visible: false },
            { id: "chart reset-size", visible: false },
         ],
         [
            { id: "chart axis-hyperlink", visible: false },
            { id: "chart plot-hyperlink", visible: false },
            { id: "chart highlight", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart clear-brush", visible: false }
         ],
         [
            { id: "chart conditions", visible: true },
            { id: "chart filter", visible: false }
         ],
         [
            { id: "chart group", visible: false },
            { id: "chart rename", visible: false },
            { id: "chart ungroup", visible: false }
         ],
         [
            { id: "chart show-titles", visible: false },
            { id: "chart show-axes", visible: false },
            { id: "chart show-legends", visible: false }
         ],
         [
            { id: "chart annotate", visible: false }
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
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: false },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: true }
         ]
      ];

      const model = createModel();
      selectPlot(model);
      const actions = new ChartActions(model, popService, composerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });

   //Bug #19986 should not display menu action when as data tip component
   //Bug #20068 should not display some toolbar when as data tip component
   it("should not display menu action and some toolbar action when as data tip component", () => {
      const expectedToolbar = [
         [
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: true },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => true);
      const model = createModel();
      selectMeasureBar(model);
      const actions = new ChartActions(model, popService, viewerContext, false, null, dataTipService);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions.length).toBe(0);
      expect(toolbarActions).toMatchSnapshot();
   });

   //Bug #20223 should not display Reset Size when size not changed
   //Bug #20764 should display Reset Size for facet pie chart
   //Bug #20611 should not display Reset Size for pie chart
   //Bug #20271
   it("check Reset Size menu action", () => {
      const dataTipService: any = { isDataTip: jest.fn() };
      dataTipService.isDataTip.mockImplementation(() => false);
      const model: VSChartModel = Object.assign({
         notAuto: false,
         maxMode: false,
         showValues: false,
         brushed: false,
         zoomed: false,
         hasFlyovers: false,
         flyOnClick: false,
         axes: [],
         facets: [],
         legends: [],
         plot: null,
         titles: [],
         stringDictionary: [],
         regionMetaDictionary: [{colIdx: 0}],
         axisHidden: false,
         titleHidden: false,
         legendHidden: false,
         legendOption: null,
         chartSelection: null,
         initialWidthRatio: 1,
         widthRatio: 3,
         initialHeightRatio: 1,
         heightRatio: 2,
         resized: true,
         verticallyResizable: false,
         horizontallyResizable: false,
         plotHighlightEnabled: true,
         maxHorizontalResize: 20,
         maxVerticalResize: 20,
         mapInfo: false,
         hyperlinks: null,
         legendsBounds: null,
         contentBounds: null,
         chartType: 0,
         multiStyles: false,
         adhocContainerPresent: false,
         enableAdhoc: false,
         showPlotResizers: false,
         showResizePreview: false,
         titleFormat: TestUtils.createMockVSFormatModel(),
         title: "",
         titleVisible: false,
         titleSelected: false,
         clearCanvasSubject: null,
         editedByWizard: false,
         filterFields: [],
         axisFields: [],
         aestheticFields: [],
         trendComparisonAggrNames: [],
         wordCloud: false,
         customPeriod: false,
         dateComparisonEnabled: false,
         dateComparisonDefined: false,
         appliedDateComparison: false,
         dateComparisonDescription: ""
      }, TestUtils.createMockVSObjectModel("VSChart", "chart1"));

      const actions = new ChartActions(model, popService, composerContext, false, null, dataTipService);
      const menuActions = actions.menuActions;

      expect(TestUtils.toString(menuActions[0].actions[11].label())).toBe("Reset Size");
      expect(menuActions[0].actions[11].visible()).toBe(false);

      //Bug #20611, Bug #20764
      model.chartType = 3;
      expect(menuActions[0].actions[11].visible()).toBe(false);

      model.facets = [
      {areaName: "facetTL", bounds: {x: 17, y: 16, width: 0, height: 73}, tiles: [], regions: [],
         layoutBounds: {x: 17, y: 16, width: 0, height: 73}},
      {areaName: "facetTR", bounds: {x: 349, y: 16, width: 0, height: 73}, tiles: [], regions: [],
         layoutBounds: {x: 349, y: 16, width: 0, height: 73}}];

      model.horizontallyResizable = true;
      expect(menuActions[0].actions[11].visible()).toBe(true);
   });

   //Bug #21381 disable some actions when chart is pop component
   it("disable all menu actions when chart is pop component", () => {
      const expectedMenu = [];

      const expectedToolbar = [
         [
            { id: "chart brush", visible: false },
            { id: "chart clear-brush", visible: false },
            { id: "chart zoom", visible: false },
            { id: "chart clear-zoom", visible: false },
            { id: "chart exclude-data", visible: false },
            { id: "chart show-data", visible: true },
            { id: "chart show-details", visible: true },
            { id: "chart open-max-mode", visible: false },
            { id: "chart close-max-mode", visible: false },
            { id: "chart manual-refresh", visible: false },
            { id: "chart auto-refresh", visible: false },
            { id: "chart refresh", visible: false },
            { id: "chart multi-select", visible: false },
            { id: "chart edit", visible: false }
         ]
      ];

      const model = createModel();
      popService.getPopComponent.mockImplementation(() => "Chart1");
      selectMeasureBar(model);
      const actions = new ChartActions(model, popService, viewerContext);
      const menuActions = actions.menuActions;
      const toolbarActions = actions.toolbarActions;

      expect(menuActions).toMatchSnapshot();
      expect(toolbarActions).toMatchSnapshot();
   });
});
