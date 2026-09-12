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
 * ObjectWizardPane — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — aiAssistantPermission setter: correctly forwards to
 *                        aiAssistantService.aiAssistantVisible; null/undefined safe
 *   Group 2  [Risk 3] — processRemoveVSObjectCommand: isLatestTempAssembly time-based path
 *                        (newer current, older command → no-op; same time → clears)
 *   Group 3  [Risk 3] — onEditSecondColumn: when checkAggTrap returns false (no trap) →
 *                        sendRefreshWizardBindingEvent called; when trap confirmed → sends;
 *                        when trap cancelled → does NOT send + nulls out secondaryColumn
 *   Group 4  [Risk 2] — processClearRecommendLoadingCommand / processShowRecommendLoadingCommand:
 *                        eventLoading reflects loadingEventCount; double increment / decrement
 *   Group 5  [Risk 2] — processSetVSBindingModelCommand: only updates tableBindingModel when
 *                        binding.type == "table" AND current tableBindingModel differs; chart
 *                        type bindings are silently ignored (confirmed suspicion A from P1)
 *
 * Out of scope this pass:
 *   showProgressDialog — modal interaction requires real ComponentTool flow; deferred
 *   processProgress with checkMv=true — same modal flow
 *   splitPaneDragEnd — calls wizardPreviewPane.setPreviewPaneSize() only if ref set; trivial
 */

import { waitFor } from "@testing-library/angular";
import { of } from "rxjs";
import { ComponentTool } from "../../../common/util/component-tool";

import {
   CLIENT_SERVICE_MOCK,
   BINDING_TREE_MOCK,
   AI_ASSISTANT_MOCK,
   resetMocks,
   tempName,
   renderComponent,
} from "./object-wizard-pane.test-fixtures";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — aiAssistantPermission setter [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — aiAssistantPermission setter", () => {
   // 🔁 Regression-sensitive: aiAssistantService.aiAssistantVisible is the sole flag that
   // controls whether the AI assistant panel renders. If the setter doesn't propagate the
   // value (or treats null as true), the panel shows when it should be hidden.
   it("should set aiAssistantService.aiAssistantVisible=true when true is passed", async () => {
      const { comp } = await renderComponent();
      comp.aiAssistantPermission = true;
      expect(AI_ASSISTANT_MOCK.aiAssistantVisible).toBe(true);
   });

   it("should set aiAssistantService.aiAssistantVisible=false when false is passed", async () => {
      const { comp } = await renderComponent();
      comp.aiAssistantPermission = true; // start as true
      comp.aiAssistantPermission = false;
      expect(AI_ASSISTANT_MOCK.aiAssistantVisible).toBe(false);
   });

   it("should treat null as false (null-coalesce behavior)", async () => {
      const { comp } = await renderComponent();
      comp.aiAssistantPermission = null;
      expect(AI_ASSISTANT_MOCK.aiAssistantVisible).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — processRemoveVSObjectCommand: time-based guard [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processRemoveVSObjectCommand time-based guard", () => {
   // 🔁 Regression-sensitive: when a stale remove command arrives (for an older temp object
   // than the current one), vsObject must NOT be cleared. Clearing it would remove the new
   // object from the preview before the server has processed it.
   it("should NOT clear vsObject when the removed name is an older temp than current", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = { absoluteName: tempName(2000) } as any;

      (comp as any)["processRemoveVSObjectCommand"]({ name: tempName(1000) });

      // isLatestTempAssembly(currentName="Recommender__2000", newName="Recommender__1000")
      // → newTime(1000) < oldTime(2000) → returns false → vsObject unchanged
      expect(comp.vsObject?.absoluteName).toBe(tempName(2000));
   });

   it("should clear vsObject when removed name is same timestamp as current", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = { absoluteName: tempName(1000) } as any;

      (comp as any)["processRemoveVSObjectCommand"]({ name: tempName(1000) });

      // same time → isLatestTempAssembly returns true → clears vsObject
      expect(comp.vsObject).toBeNull();
   });

   it("should clear vsObject when removed name is a newer temp than current", async () => {
      const { comp } = await renderComponent();
      comp.vsObject = { absoluteName: tempName(1000) } as any;

      (comp as any)["processRemoveVSObjectCommand"]({ name: tempName(2000) });

      expect(comp.vsObject).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — onEditSecondColumn: checkAggTrap flow [Risk 3]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — onEditSecondColumn", () => {
   // 🔁 Regression-sensitive: if the trap check is skipped or the cancel path sends the event,
   // the server may apply an illegal aggregation that corrupts the binding model. The user
   // expects that "cancel" on the trap dialog reverts the secondaryColumn selection.
   it("should call sendRefreshWizardBindingEvent when no trap is detected", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = { chartBindingModel: { xfields: [], yfields: [] } } as any;
      BINDING_TREE_MOCK.checkAggTrap.mockReturnValue(of(false));
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.onEditSecondColumn(0);

      await waitFor(() => {
         expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vswizard/binding/refresh",
            expect.anything()
         );
      });
   });

   it("should send event when trap is confirmed (user clicks 'continue')", async () => {
      const { comp } = await renderComponent();
      (comp as any)._bindingModel = { chartBindingModel: { xfields: [], yfields: [{ secondaryColumn: "test", secondaryColumnValue: "test" }] } } as any;
      BINDING_TREE_MOCK.checkAggTrap.mockReturnValue(of(true));
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      // Mock the trap alert to immediately resolve with "continue"
      const spy = vi.spyOn(ComponentTool, "showTrapAlert")
         .mockResolvedValue("continue");

      try {
         comp.onEditSecondColumn(0);

         await waitFor(() => {
            expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
               "/events/vswizard/binding/refresh",
               expect.anything()
            );
         });
      } finally {
         spy.mockRestore();
      }
   });

   it("should NOT send event and should null out secondaryColumn when trap is cancelled", async () => {
      const { comp } = await renderComponent();
      const measures = [{ secondaryColumn: "test", secondaryColumnValue: "testVal" }];
      (comp as any)._bindingModel = {
         chartBindingModel: { xfields: [], yfields: measures },
      } as any;
      BINDING_TREE_MOCK.checkAggTrap.mockReturnValue(of(true));
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      const spy = vi.spyOn(ComponentTool, "showTrapAlert")
         .mockResolvedValue("cancel");

      try {
         comp.onEditSecondColumn(0);

         await waitFor(() => {
            // secondaryColumn should be nulled out
            expect(measures[0].secondaryColumn).toBeNull();
            expect(measures[0].secondaryColumnValue).toBeNull();
         });
         expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processClearRecommendLoadingCommand / processShowRecommendLoadingCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — eventLoading via loading commands", () => {
   // 🔁 Regression-sensitive: eventLoading controls the spinner overlay. If counts get out of
   // sync (double-decrement or missed increment), the overlay either freezes or flickers.
   it("should be false initially", async () => {
      const { comp } = await renderComponent();
      expect(comp.eventLoading).toBe(false);
   });

   it("should be true after one showLoading command", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processShowRecommendLoadingCommand"]({});
      expect(comp.eventLoading).toBe(true);
   });

   it("should be false after matching clear command", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processShowRecommendLoadingCommand"]({});
      (comp as any)["processClearRecommendLoadingCommand"]({});
      expect(comp.eventLoading).toBe(false);
   });

   it("should remain non-zero after two shows and one clear", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processShowRecommendLoadingCommand"]({});
      (comp as any)["processShowRecommendLoadingCommand"]({});
      (comp as any)["processClearRecommendLoadingCommand"]({});
      expect(comp.eventLoading).toBe(true);
   });

   it("should reach false after two shows and two clears", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processShowRecommendLoadingCommand"]({});
      (comp as any)["processShowRecommendLoadingCommand"]({});
      (comp as any)["processClearRecommendLoadingCommand"]({});
      (comp as any)["processClearRecommendLoadingCommand"]({});
      expect(comp.eventLoading).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — processSetVSBindingModelCommand: chart binding silently ignored [Risk 2]
// ---------------------------------------------------------------------------

describe("ObjectWizardPane — processSetVSBindingModelCommand", () => {
   // 🔁 Suspicion A confirmed: command.binding.type == 'table' is required to update
   // tableBindingModel. Chart type bindings arrive via processRefreshVsWizardBindingCommand
   // instead, so this silent drop is intentional — but if the type strings ever change,
   // chart binding updates would silently fail.
   it("should update tableBindingModel when binding.type is 'table' and model differs", async () => {
      const { comp } = await renderComponent();
      const originalTable = { type: "table", details: [], availableFields: [] } as any;
      const newTable = { type: "table", details: [{ name: "col1" }], availableFields: [] } as any;
      (comp as any)._bindingModel = {
         tableBindingModel: originalTable,
         chartBindingModel: null,
      } as any;

      (comp as any)["processSetVSBindingModelCommand"]({ binding: newTable });

      expect(comp._bindingModel.tableBindingModel).toBe(newTable);
   });

   it("should NOT update tableBindingModel when binding type is not 'table'", async () => {
      const { comp } = await renderComponent();
      const originalTable = { type: "table", details: [] } as any;
      const chartBinding = { type: "chart", xfields: [], yfields: [] } as any;
      (comp as any)._bindingModel = {
         tableBindingModel: originalTable,
         chartBindingModel: null,
      } as any;

      (comp as any)["processSetVSBindingModelCommand"]({ binding: chartBinding });

      // chart type: silently ignored, tableBindingModel unchanged
      expect(comp._bindingModel.tableBindingModel).toBe(originalTable);
   });

   it("should NOT update when _bindingModel is null", async () => {
      const { comp } = await renderComponent();
      // _bindingModel starts null; command should be a no-op without throwing
      expect(() => {
         (comp as any)["processSetVSBindingModelCommand"]({
            binding: { type: "table", details: [] },
         });
      }).not.toThrow();
   });
});
