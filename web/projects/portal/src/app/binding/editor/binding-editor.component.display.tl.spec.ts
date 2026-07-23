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
 * BindingEditor — P3 Display (pure getters and ViewChild delegation)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — showHighLowPane(): returns true for stock/candle/geo/tree/network/
 *                         circular/gantt/supportsPathField; false for plain chart or non-chart
 *   Group 2  [baseline] — formatsInactive: true when selectedTab !== FORMAT_PANE
 *   Group 3  [baseline] — formatsDisabled: OR of hideFormatPane and formatPaneDisabled
 *   Group 4  [baseline] — formatPaneVisible: true only when selectedTab === FORMAT_PANE
 *   Group 6  [baseline] — bindingType: delegates to bindingModel.type
 *   Group 7  [baseline] — isVS: delegates to UIContextService.isVS()
 *   Group 8  [Risk 2]   — hideDcTip(): sets showDcAppliedTip=false
 *   Group 9  [baseline] — tableBindingModel / crosstabBindingModel: pass-through type casts
 *   Group 10 [Risk 2]   — popUpWarning(): routes type="info"/"warning"/"danger" to correct
 *                          notifications ViewChild method with the warning message
 *   Group 11 [Risk 2]   — sizeChanged(): calls notifications.info when wordCloud is true
 */

import {
   UI_CONTEXT_MOCK,
   resetMocks,
   renderComponent,
} from "./binding-editor.component.test-fixtures";
import { SidebarTab } from "../widget/binding-tree/data-editor-tab-pane.component";
import { GraphTypes } from "../../common/graph-types";

import type { BindingModel } from "../data/binding-model";
import type { ChartBindingModel } from "../data/chart/chart-binding-model";
import type { TableBindingModel } from "../data/table/table-binding-model";
import type { CrosstabBindingModel } from "../data/table/crosstab-binding-model";

// ---------------------------------------------------------------------------
// Global setup
// ---------------------------------------------------------------------------

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — showHighLowPane() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — showHighLowPane()", () => {
   // 🔁 Regression-sensitive: this gates visibility of the high/low pane which is required
   // for stock and candlestick charts. If false when it should be true, the pane is hidden
   // and the user cannot configure the required fields.

   it("should return falsy when bindingModel is null", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = null;
      // Source returns null (not false) when chartBinding is null — test the behavioral intent
      expect(comp.showHighLowPane()).toBeFalsy();
   });

   it("should return falsy when bindingModel has no chartType property (non-chart binding)", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "table", groups: [], details: [], aggregates: [] } as unknown as BindingModel;
      // Source returns null when chartType is absent — test the behavioral intent
      expect(comp.showHighLowPane()).toBeFalsy();
   });

   it("should return true when chartType is CHART_STOCK", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "chart", chartType: GraphTypes.CHART_STOCK } as unknown as BindingModel;
      expect(comp.showHighLowPane()).toBe(true);
   });

   it("should return true when chartType is CHART_CANDLE", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "chart", chartType: GraphTypes.CHART_CANDLE } as unknown as BindingModel;
      expect(comp.showHighLowPane()).toBe(true);
   });

   it("should return true when chartType is CHART_TREE", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "chart", chartType: GraphTypes.CHART_TREE } as unknown as BindingModel;
      expect(comp.showHighLowPane()).toBe(true);
   });

   it("should return true when supportsPathField is true", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = {
         type: "chart",
         chartType: 0x00,
         supportsPathField: true,
      } as unknown as BindingModel;
      expect(comp.showHighLowPane()).toBe(true);
   });

   it("should return false when chartType is a plain bar chart (0x00) with no special flags", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = {
         type: "chart",
         chartType: 0x00,
         supportsPathField: false,
      } as unknown as BindingModel;
      expect(comp.showHighLowPane()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — formatsInactive getter [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — formatsInactive getter", () => {
   it("should return true when selectedTab is BINDING_TREE (not FORMAT_PANE)", async () => {
      const { comp } = await renderComponent();
      comp.switchTab(SidebarTab.BINDING_TREE);
      expect(comp.formatsInactive).toBe(true);
   });

   it("should return false when selectedTab is FORMAT_PANE", async () => {
      const { comp } = await renderComponent();
      comp.switchTab(SidebarTab.FORMAT_PANE);
      expect(comp.formatsInactive).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — formatsDisabled getter [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — formatsDisabled getter", () => {
   it("should return true when hideFormatPane is true", async () => {
      const { comp } = await renderComponent();
      comp.hideFormatPane = true;
      expect(comp.formatsDisabled).toBe(true);
   });

   it("should return true when formatPaneDisabled input is true", async () => {
      const { fixture } = await renderComponent();
      fixture.componentRef.setInput("formatPaneDisabled", true);
      const comp = fixture.componentInstance;
      comp.hideFormatPane = false;
      expect(comp.formatsDisabled).toBe(true);
   });

   it("should return false when both hideFormatPane and formatPaneDisabled are false", async () => {
      const { fixture } = await renderComponent();
      fixture.componentRef.setInput("formatPaneDisabled", false);
      const comp = fixture.componentInstance;
      comp.hideFormatPane = false;
      expect(comp.formatsDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — formatPaneVisible getter [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — formatPaneVisible getter", () => {
   it("should return false when selectedTab is BINDING_TREE", async () => {
      const { comp } = await renderComponent();
      comp.switchTab(SidebarTab.BINDING_TREE);
      expect(comp.formatPaneVisible).toBe(false);
   });

   it("should return true when selectedTab is FORMAT_PANE", async () => {
      const { comp } = await renderComponent();
      comp.switchTab(SidebarTab.FORMAT_PANE);
      expect(comp.formatPaneVisible).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — bindingType getter [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — bindingType getter", () => {
   it("should return the type from the current bindingModel", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "crosstab" } as unknown as BindingModel;
      expect(comp.bindingType).toBe("crosstab");
   });
});

// ---------------------------------------------------------------------------
// Group 7 — isVS getter [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — isVS getter", () => {
   it("should return true when UIContextService.isVS() returns true", async () => {
      UI_CONTEXT_MOCK.isVS.mockReturnValue(true);
      const { comp } = await renderComponent();
      expect(comp.isVS).toBe(true);
   });

   it("should return false when UIContextService.isVS() returns false", async () => {
      UI_CONTEXT_MOCK.isVS.mockReturnValue(false);
      const { comp } = await renderComponent();
      expect(comp.isVS).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — hideDcTip() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — hideDcTip()", () => {
   // 🔁 Regression-sensitive: if showDcAppliedTip is not cleared, the date-comparison tip
   // persists across binding changes and confuses the user after DC is removed.

   it("should set showDcAppliedTip to false", async () => {
      const model = {
         type: "chart", hasDateComparison: true,
         xfields: [], yfields: [], geoFields: [], groupFields: [], geoCols: [],
         colorField: null, shapeField: null, sizeField: null, textField: null,
         pathField: null, openField: null, closeField: null, highField: null, lowField: null,
         sourceField: null, targetField: null, startField: null, endField: null,
         milestoneField: null,
      } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      comp.hideDcTip();

      expect(comp.showDcAppliedTip).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — tableBindingModel / crosstabBindingModel getters [baseline]
// ---------------------------------------------------------------------------

describe("BindingEditor — tableBindingModel / crosstabBindingModel getters", () => {
   it("tableBindingModel should return the current bindingModel cast as TableBindingModel", async () => {
      const model = { type: "table", groups: [], details: [], aggregates: [] } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      expect(comp.tableBindingModel).toBe(model as unknown as TableBindingModel);
   });

   it("crosstabBindingModel should return the current bindingModel cast as CrosstabBindingModel", async () => {
      const model = { type: "crosstab", rows: [], cols: [], aggregates: [] } as unknown as BindingModel;
      const { comp } = await renderComponent({ bindingModel: model });
      expect(comp.crosstabBindingModel).toBe(model as unknown as CrosstabBindingModel);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — popUpWarning() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — popUpWarning()", () => {
   // 🔁 Regression-sensitive: routing the wrong notification type (e.g., calling info when
   // the message should be danger) changes the visual severity shown to the user.

   it("should call notifications.info with the message when type is 'info'", async () => {
      const { comp } = await renderComponent();
      comp.popUpWarning({ type: "info", msg: "Information message" });
      expect((comp.notifications as any).info).toHaveBeenCalledWith("Information message");
   });

   it("should call notifications.warning with the message when type is 'warning'", async () => {
      const { comp } = await renderComponent();
      comp.popUpWarning({ type: "warning", msg: "Warning message" });
      expect((comp.notifications as any).warning).toHaveBeenCalledWith("Warning message");
   });

   it("should call notifications.danger with the message when type is 'danger'", async () => {
      const { comp } = await renderComponent();
      comp.popUpWarning({ type: "danger", msg: "Danger message" });
      expect((comp.notifications as any).danger).toHaveBeenCalledWith("Danger message");
   });

   it("should not call any notification method for unknown warning type", async () => {
      const { comp } = await renderComponent();
      comp.popUpWarning({ type: "unknown", msg: "msg" });
      expect((comp.notifications as any).info).not.toHaveBeenCalled();
      expect((comp.notifications as any).warning).not.toHaveBeenCalled();
      expect((comp.notifications as any).danger).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 11 — sizeChanged() [Risk 2]
// ---------------------------------------------------------------------------

describe("BindingEditor — sizeChanged()", () => {
   // 🔁 Regression-sensitive: the font-scale hint should only appear for word-cloud charts.
   // Showing it for non-word-cloud charts confuses users.

   it("should call notifications.info when bindingModel.wordCloud is true", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "chart", wordCloud: true } as unknown as BindingModel;

      comp.sizeChanged();

      expect((comp.notifications as any).info).toHaveBeenCalledOnce();
   });

   it("should NOT call notifications.info when bindingModel.wordCloud is false", async () => {
      const { comp } = await renderComponent();
      comp.bindingModel = { type: "chart", wordCloud: false } as unknown as BindingModel;

      comp.sizeChanged();

      expect((comp.notifications as any).info).not.toHaveBeenCalled();
   });
});
