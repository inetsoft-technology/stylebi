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
 * VSBindingPane — Pass 3: display
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — formatPaneDisabled: chart VO selection guard, VSCalcTable row/column null check
 *   Group 2  [Risk 2] — processSetCurrentFormatCommand: JSON-diff dedup guard, origFormat clone
 *   Group 3  [baseline] — processShowLoadingMaskCommand: sets loading=true
 *   Group 4  [baseline] — isAggregateTextFormat: delegates to chartService.textFormat for charts only
 *   Group 5  [baseline] — updateFormat: dispatches format / reset-format event
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only): none
 *
 * Out of scope this pass:
 *   All interaction/command-routing methods — covered in VSBindingPane.interaction.tl.spec.ts
 *   All async-modal / trap methods — covered in VSBindingPane.risk.tl.spec.ts
 */

import { ShowLoadingMaskCommand } from "../../vsobjects/command/show-loading-mask-command";
import { SetCurrentFormatCommand } from "../../vsobjects/command/set-current-format-command";
import { ChartTool } from "../../graph/model/chart-tool";
import { TestUtils } from "../../common/test/test-utils";
import {
   CLIENT_SERVICE_MOCK, CHART_EDITOR_MOCK,
   renderComponent, resetMocks,
} from "./vs-binding-pane.test-fixtures";

beforeEach(() => resetMocks());

// ---------------------------------------------------------------------------
// Group 1 — formatPaneDisabled [Risk 3]
// ---------------------------------------------------------------------------

describe("VSBindingPane — formatPaneDisabled", () => {
   // 🔁 Regression-sensitive: formatPaneDisabled gates format editing in the binding pane.
   // Wrong return values silently block or expose format controls at incorrect times.

   it("should return false when no objectModel is set", async () => {
      const { comp } = await renderComponent();
      expect(comp.formatPaneDisabled).toBe(false);
   });

   it("should return false for a chart with no chartSelection", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.chartSelection = null;
      comp.objectModel = chart;
      expect(comp.formatPaneDisabled).toBe(false);
   });

   it("should return false for a chart with chartSelection but no chartObject", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.chartSelection = { chartObject: null, regions: [] };
      comp.objectModel = chart;
      expect(comp.formatPaneDisabled).toBe(false);
   });

   it("should return true when a non-editable chart VO is selected with regions", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.chartSelection = {
         chartObject: TestUtils.createMockChartObject("plot_area"),
         regions: [TestUtils.createMockChartRegion()],
      };
      comp.objectModel = chart;

      const spy = vi.spyOn(ChartTool, "isNonEditableVOSelected").mockReturnValue(true);
      try {
         expect(comp.formatPaneDisabled).toBe(true);
      } finally {
         spy.mockRestore();
      }
   });

   it("should return false when non-editable VO is selected but regions array is empty", async () => {
      const { comp } = await renderComponent();
      const chart = TestUtils.createMockVSChartModel("Chart1");
      chart.chartSelection = {
         chartObject: TestUtils.createMockChartObject("plot_area"),
         regions: [],
      };
      comp.objectModel = chart;

      const spy = vi.spyOn(ChartTool, "isNonEditableVOSelected").mockReturnValue(true);
      try {
         expect(comp.formatPaneDisabled).toBe(false);
      } finally {
         spy.mockRestore();
      }
   });

   it("should return true for VSCalcTable when firstSelectedColumn is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      const calc = TestUtils.createMockVSCalcTableModel("Table1");
      // createMockBaseTableModel hardcodes objectType "VSTable"; override so the guard matches.
      calc.objectType = "VSCalcTable";
      calc.firstSelectedColumn = null;
      comp.objectModel = calc;
      expect(comp.formatPaneDisabled).toBe(true);
   });

   it("should return true for VSCalcTable when firstSelectedRow is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      const calc = TestUtils.createMockVSCalcTableModel("Table1");
      calc.objectType = "VSCalcTable";
      calc.firstSelectedRow = null;
      comp.objectModel = calc;
      expect(comp.formatPaneDisabled).toBe(true);
   });

   it("should return false for VSCalcTable when both row and column are set", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      const calc = TestUtils.createMockVSCalcTableModel("Table1");
      // createMockBaseTableModel hardcodes objectType "VSTable"; override so the guard matches.
      // createMockBaseTableModel defaults firstSelectedRow=0, firstSelectedColumn=0.
      calc.objectType = "VSCalcTable";
      comp.objectModel = calc;
      expect(comp.formatPaneDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — processSetCurrentFormatCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processSetCurrentFormatCommand", () => {
   // 🔁 Regression-sensitive: processSetCurrentFormatCommand decides when to update the
   // live format panel. Replacing currentFormat unconditionally would cause a render
   // flicker on every STOMP tick; the JSON diff guard prevents that.

   it("should update currentFormat when the incoming model differs from the current one", async () => {
      const { comp } = await renderComponent();
      const fmt = { fontStyle: 1 } as any;
      const cmd: SetCurrentFormatCommand = { model: fmt };

      // Bypass: processSetCurrentFormatCommand is private; called via STOMP dispatch in production.
      (comp as any).processSetCurrentFormatCommand(cmd);

      expect(comp.currentFormat).toBe(fmt);
   });

   it("should not replace currentFormat when the incoming model equals the current one", async () => {
      const { comp } = await renderComponent();
      const fmt = { fontStyle: 1 } as any;
      comp.currentFormat = fmt;
      const ref = comp.currentFormat;
      // Use a distinct reference with the same JSON value so the dedup guard is
      // exercised: if the guard breaks and replaces, currentFormat becomes sameFmt
      // (different reference) and toBe(ref) fails.
      const sameFmt = JSON.parse(JSON.stringify(fmt)) as any;

      (comp as any).processSetCurrentFormatCommand({ model: sameFmt });

      // Reference must be the same object — not replaced.
      expect(comp.currentFormat).toBe(ref);
   });

   it("should always set origFormat to a clone of the incoming model", async () => {
      const { comp } = await renderComponent();
      const fmt = { fontStyle: 2 } as any;
      const cmd: SetCurrentFormatCommand = { model: fmt };

      (comp as any).processSetCurrentFormatCommand(cmd);

      // origFormat is a deep clone (Tool.clone) — same value but not same reference.
      expect(comp.origFormat).toEqual(fmt);
      expect(comp.origFormat).not.toBe(fmt);
   });

   it("should set origFormat to null-equivalent when model is null", async () => {
      const { comp } = await renderComponent();
      const cmd: SetCurrentFormatCommand = { model: null };

      (comp as any).processSetCurrentFormatCommand(cmd);

      // Tool.clone(null) returns null.
      expect(comp.origFormat).toBeNull();
   });

   it("should call getCurrentFormat when model is null and resetFormat=true", async () => {
      const { comp } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      // Bypass setter — resetFormat is a private field with no public setter; setting it
      // directly avoids triggering getCurrentFormat prematurely before the command fires.
      (comp as any).resetFormat = true;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      (comp as any).processSetCurrentFormatCommand({ model: null });

      // getCurrentFormat dispatches a getFormat STOMP event.
      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/getFormat",
         expect.objectContaining({ binding: true }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3 — processShowLoadingMaskCommand [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processShowLoadingMaskCommand", () => {
   it("should set loading=true", async () => {
      const { comp } = await renderComponent();
      comp.processShowLoadingMaskCommand({ preparingData: false } as ShowLoadingMaskCommand);
      expect(comp.loading).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — isAggregateTextFormat [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — isAggregateTextFormat", () => {
   it("should return false when objectModel is not a chart", async () => {
      const { comp } = await renderComponent({ objectType: "VSTable" });
      comp.objectModel = TestUtils.createMockVSTableModel("Table1");
      CHART_EDITOR_MOCK.textFormat = true; // even if textFormat is set, non-chart → false

      expect((comp as any).isAggregateTextFormat()).toBe(false);
   });

   it("should return false when objectModel is a chart but chartService.textFormat is false", async () => {
      const { comp } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      CHART_EDITOR_MOCK.textFormat = false;

      expect((comp as any).isAggregateTextFormat()).toBe(false);
   });

   it("should return true when objectModel is a chart and chartService.textFormat is true", async () => {
      const { comp } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      CHART_EDITOR_MOCK.textFormat = true;

      expect((comp as any).isAggregateTextFormat()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — updateFormat (via private call) [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — updateFormat", () => {
   it("should dispatch a format event with the provided format model", async () => {
      const { comp } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();
      const fmt = { fontStyle: 0 } as any;

      // Bypass: updateFormat is private; exposed via updateData("updateFormat") in production.
      (comp as any).updateFormat(fmt);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/format",
         expect.objectContaining({ format: fmt }),
      );
   });

   it("should dispatch a reset format event when fmt is null", async () => {
      const { comp } = await renderComponent();
      comp.objectModel = TestUtils.createMockVSChartModel("Chart1");
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      (comp as any).updateFormat(null);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/format",
         expect.objectContaining({ reset: true }),
      );
   });
});
