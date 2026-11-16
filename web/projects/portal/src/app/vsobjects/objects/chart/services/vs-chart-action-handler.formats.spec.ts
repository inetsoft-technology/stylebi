/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * The handler hands the title and legend format dialogs the SAME axis / legend index it sends the
 * server (bug #80459, PR F): the pairing name is built from these values, so a name that pointed
 * at another axis or legend than the one being edited would attribute the draft to the wrong
 * property. Pinned here against the request the handler itself makes.
 */
import { of } from "rxjs";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { TestUtils } from "../../../../common/test/test-utils";
import { VSChartModel } from "../../../model/vs-chart-model";
import { VSChartActionHandler } from "./vs-chart-action-handler";

describe("VSChartActionHandler title / legend format dialogs", () => {
   let modelService: any;
   let viewsheetClient: any;
   let handler: VSChartActionHandler;
   let dialog: any;

   beforeEach(() => {
      modelService = { getModel: vi.fn(() => of({})) };
      viewsheetClient = { sendEvent: vi.fn(), runtimeId: "rt-1" };
      handler = new VSChartActionHandler(
         modelService, viewsheetClient, <any> { open: vi.fn() }, <any> { get: vi.fn() }, false,
         <any> {}, null);
      dialog = {};
      handler["showDialog"] = <any> vi.fn(() => dialog);
   });

   function open(id: string, model: VSChartModel): Promise<void> {
      handler.handleEvent(new AssemblyActionEvent<VSChartModel>(id, model), []);
      return new Promise(resolve => setTimeout(resolve));
   }

   function titleModel(areaName: string): VSChartModel {
      const model = TestUtils.createMockVSChartModel("Chart1");
      model.absoluteName = "Viewsheet1.Chart1";
      model.chartSelection = <any> { chartObject: { areaName }, regions: [] };
      return model;
   }

   it.each(["x_title", "x2_title", "y_title", "y2_title"])(
      "%s: the dialog gets the axis the request names, and the chart's own name",
      async (areaName: string) => {
         const axis = areaName.replace("_title", "");
         await open("chart title-properties", titleModel(areaName));

         expect(modelService.getModel.mock.calls[0][1].get("axisType")).toBe(axis);
         expect(dialog.axisTitleType).toBe(axis);
         expect(dialog.assemblyName).toBe("Viewsheet1.Chart1");
         expect(dialog.vsId).toBe("rt-1");
      });

   it("legend: the dialog gets the legend's own index, not its position in the list", async () => {
      const model = TestUtils.createMockVSChartModel("Chart1");
      model.absoluteName = "Viewsheet1.Chart1";
      const legend = (legendIndex: number, field: string, areaName: string) => ({
         legendIndex, field, targetFields: [], aestheticType: "color", nodeAesthetic: false,
         legendObjects: [{ areaName }]
      });
      // list order is the reverse of the index order; the selected one is listed first
      model.legends = <any> [legend(2, "Region", "legend_a"), legend(0, "Year", "legend_b")];
      model.chartSelection = <any> {
         chartObject: { areaName: "legend_a", field: "Region", targetFields: [],
                        aestheticType: "color", nodeAesthetic: false },
         regions: []
      };
      await open("chart legend-properties", model);

      expect(modelService.getModel.mock.calls[0][1].get("index")).toBe("2");
      expect(dialog.legendIndex).toBe(2);
      expect(dialog.assemblyName).toBe("Viewsheet1.Chart1");
      expect(dialog.vsId).toBe("rt-1");
   });
});
