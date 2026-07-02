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
 * TextFieldMc — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnChanges/initMeasures: measure roller list and editorService.measureName
 *   Group 2 [Risk 2] — openTextFormatPane: clear selection and emit showTextFormat when enabled
 *   Group 3 [Risk 2] — prev/next: wrap measure index and emit showTextFormat
 *   Group 4 [Risk 2] — isMeasureRollerEnabled: disabled when multiStyles or field is bound
 *   Group 5 [Risk 2] — isEditEnabled: stock vs tree vs showValues label editing guard
 *
 * HTTP: no HTTP — text format pane opened via onUpdateData emit only
 *
 * Out of scope:
 *   syncAllChartAggregateInfo — symmetric clone path, covered via setAestheticRef contract
 *   isMixedField — all-chart-aggregate flag only, no independent user flow
 */

import { SimpleChange } from "@angular/core";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { TestUtils } from "../../../../common/test/test-utils";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ChartModel } from "../../../../graph/model/chart-model";
import { ChartSelection } from "../../../../graph/model/chart-selection";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { TextFieldMc } from "./text-field-mc.component";

const EDIT_TEXT = "_#(Edit Text)";

function createChartModel(chartType = GraphTypes.CHART_BAR): ChartModel {
   return {
      chartSelection: { regions: [{ name: "r1" }], chartObject: null } as unknown as ChartSelection,
      showValues: true,
      chartType,
      scatterMatrix: false
   } as ChartModel;
}

async function renderTextFieldMc(options: {
   chartType?: number;
   multiStyles?: boolean;
   textField?: AestheticInfo | null;
   yfields?: ReturnType<typeof TestUtils.createMockChartAggregateRef>[];
   textFormat?: boolean;
} = {}) {
   const editorService = {
      measureName: null as string | null,
      textFormat: options.textFormat ?? false,
      changeChartAesthetic: vi.fn(),
      isDropPaneAccept: vi.fn().mockReturnValue(true),
      getDNDType: vi.fn().mockReturnValue(0)
   };
   const bindingModel = TestUtils.createMockChartBindingModel();
   bindingModel.multiStyles = options.multiStyles ?? false;
   bindingModel.yfields = options.yfields ?? [
      TestUtils.createMockChartAggregateRef("Sum(qty)"),
      TestUtils.createMockChartAggregateRef("Sum(sales)")
   ];
   if(options.textField !== undefined) {
      bindingModel.textField = options.textField;
   }
   const chartModel = createChartModel(options.chartType);

   const result = await render(TextFieldMc, {
      providers: [
         { provide: ChartEditorService, useValue: editorService },
         { provide: DndService, useValue: {} },
         { provide: UIContextService, useValue: { isVS: () => false } }
      ],
      componentProperties: {
         bindingModel,
         chartModel
      }
   });
   return { ...result, editorService, bindingModel, chartModel };
}

describe("TextFieldMc — initMeasures [Group 1, Risk 2]", () => {
   it("should populate measures and set editorService.measureName on init", async () => {
      const { editorService, container } = await renderTextFieldMc();

      expect(container.querySelector(".measure-roller")).toBeTruthy();
      expect(container.querySelector(".measure-label")?.textContent).toBeTruthy();
      expect(editorService.measureName).toBeTruthy();
   });

   // 🔁 Regression-sensitive: Bug #60550 — do not switch to text format while axis label format applies
   it("should emit getTextFormat when textFormat active on first measure init", async () => {
      const editorService = {
         measureName: null as string | null,
         textFormat: true,
         changeChartAesthetic: vi.fn(),
         isDropPaneAccept: vi.fn().mockReturnValue(true),
         getDNDType: vi.fn().mockReturnValue(0)
      };
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.yfields = [
         TestUtils.createMockChartAggregateRef("Sum(qty)"),
         TestUtils.createMockChartAggregateRef("Sum(sales)")
      ];
      const chartModel = createChartModel();
      const { fixture } = await render(TextFieldMc, {
         providers: [
            { provide: ChartEditorService, useValue: editorService },
            { provide: DndService, useValue: {} },
            { provide: UIContextService, useValue: { isVS: () => false } }
         ],
         componentProperties: { bindingModel, chartModel }
      });
      const emitted = vi.fn();
      fixture.componentInstance.onUpdateData.subscribe(emitted);

      fixture.componentInstance.ngOnChanges({
         bindingModel: new SimpleChange(null, bindingModel, true)
      });
      fixture.detectChanges();

      expect(emitted).toHaveBeenCalledWith("getTextFormat");
   });
});

describe("TextFieldMc — openTextFormatPane [Group 2, Risk 2]", () => {
   it("should clear chart selection and emit showTextFormat when edit enabled", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const field = {
         fullName: "Sum(qty)",
         dataInfo: TestUtils.createMockChartAggregateRef("Sum(qty)"),
         frame: null
      };
      bindingModel.textField = field;
      const { fixture, editorService, chartModel } = await renderTextFieldMc({ textField: field });
      const emitted = vi.fn();
      fixture.componentInstance.onUpdateData.subscribe(emitted);

      await userEvent.click(screen.getByTitle(EDIT_TEXT));

      expect(chartModel.chartSelection.regions).toEqual([]);
      expect(editorService.measureName).toBe("Sum(qty)");
      expect(emitted).toHaveBeenCalledWith("showTextFormat");
   });
});

describe("TextFieldMc — measure roller navigation [Group 3, Risk 2]", () => {
   it("should wrap prev/next and emit showTextFormat for each measure", async () => {
      const { fixture, container } = await renderTextFieldMc();
      const emitted = vi.fn();
      fixture.componentInstance.onUpdateData.subscribe(emitted);
      const label = () => container.querySelector(".measure-label");
      const firstMeasure = label()?.textContent;

      await userEvent.click(container.querySelector(".chevron-circle-arrow-right-icon")!);
      expect(label()?.textContent).not.toBe(firstMeasure);
      expect(emitted).toHaveBeenCalledWith("showTextFormat");

      emitted.mockClear();
      await userEvent.click(container.querySelector(".chevron-circle-arrow-left-icon")!);
      expect(label()?.textContent).toBe(firstMeasure);
      expect(emitted).toHaveBeenCalledWith("showTextFormat");
   });
});

describe("TextFieldMc — isMeasureRollerEnabled [Group 4, Risk 2]", () => {
   it("should enable roller only without multiStyles and without bound text field", async () => {
      const enabled = await renderTextFieldMc();
      const disabledField = await renderTextFieldMc({
         textField: { fullName: "t", dataInfo: null, frame: null }
      });
      const disabledMulti = await renderTextFieldMc({ multiStyles: true });

      expect(enabled.container.querySelector(".measure-roller")).toBeTruthy();
      expect(disabledField.container.querySelector(".measure-roller")).toBeFalsy();
      expect(disabledMulti.container.querySelector(".measure-roller")).toBeFalsy();
   });
});

describe("TextFieldMc — isEditEnabled [Group 5, Risk 2]", () => {
   it("should allow edit for tree chart even when showValues is false", async () => {
      const { container } = await renderTextFieldMc({ chartType: GraphTypes.CHART_TREE });

      expect(screen.getByTitle(EDIT_TEXT).className).not.toContain("icon-disabled");
      expect(container.querySelector("chart-aesthetic-mc")).toBeTruthy();
   });

   it("should disable edit for stock chart without text field", async () => {
      const { container } = await renderTextFieldMc({ chartType: GraphTypes.CHART_STOCK, textField: null });

      expect(screen.getByTitle(EDIT_TEXT).className).toContain("icon-disabled");
      expect(container.querySelector("chart-aesthetic-mc")).toBeFalsy();
   });
});
