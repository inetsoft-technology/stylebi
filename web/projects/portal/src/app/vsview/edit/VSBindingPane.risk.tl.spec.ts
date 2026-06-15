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
 * VSBindingPane — Pass 2: risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — isActionEnabled (merge-cells / split-cells): layout null guard, selectedCells null guard, cell count / span check
 *   Group 2  [Risk 3] — isActionEnabled (delete-row / delete-column): layout null guard, row/col count + selectedCells null guards
 *   Group 3  [Risk 2] — handleExpiredSheet: opens expiry dialog, sets dedup flag, blocks second dialog
 *   Group 4  [Risk 2] — cancelViewsheetLoading: dispatches cancel event with current runtimeId
 *   Group 5  [baseline] — processClearLoadingCommand: sets loading=false
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only): none
 *
 * Out of scope this pass:
 *   processVSTrapCommand / processVSBindingTrapCommand — async modal + confirm flow, not yet covered
 *   processProgress / processMessageCommand — modal + STOMP round-trip required
 */

import { waitFor } from "@testing-library/angular";

import { ClearLoadingCommand } from "../../vsobjects/command/clear-loading-command";
import { TestUtils } from "../../common/test/test-utils";
import {
   CLIENT_SERVICE_MOCK, CALC_TABLE_MOCK, MODAL_MOCK,
   renderComponent, resetMocks,
} from "./vs-binding-pane.test-fixtures";

beforeEach(() => resetMocks());

// ---------------------------------------------------------------------------
// Group 1 — isActionEnabled / isMergeCellsActionEnabled / isSplitCellsActionEnabled [Risk 3]
// ---------------------------------------------------------------------------

describe("VSBindingPane — isActionEnabled (merge/split/delete)", () => {
   // 🔁 Regression-sensitive: these methods control whether calc-table toolbar actions
   // are enabled. Wrong values silently enable destructive cell operations.

   it("should return true for any unrecognized action id (default allow)", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      const model = TestUtils.createMockVSCalcTableModel("Table1");

      expect((comp as any).isActionEnabled("some-unrelated-action", model)).toBe(true);
   });

   it("merge-cells: should return false when layoutModel is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue(null);

      expect((comp as any).isActionEnabled("calc-table merge-cells", {})).toBe(false);
   });

   it("merge-cells: should return false when selectedCells has only one cell", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({ selectedCells: [{}] });

      expect((comp as any).isActionEnabled("calc-table merge-cells", {})).toBe(false);
   });

   it("merge-cells: should return true when selectedCells has more than one cell", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({ selectedCells: [{}, {}] });

      expect((comp as any).isActionEnabled("calc-table merge-cells", {})).toBe(true);
   });

   it("merge-cells: should return false when selectedCells is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({ selectedCells: null });

      expect((comp as any).isActionEnabled("calc-table merge-cells", {})).toBe(false);
   });

   it("split-cells: should return false when layoutModel is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue(null);

      expect((comp as any).isActionEnabled("calc-table split-cells", {})).toBe(false);
   });

   it("split-cells: should return false when selectedCells is empty", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({ selectedCells: [] });

      expect((comp as any).isActionEnabled("calc-table split-cells", {})).toBe(false);
   });

   it("split-cells: should return true when the first selected cell has span width > 1", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         selectedCells: [{ span: { width: 2, height: 1 } }],
      });

      expect((comp as any).isActionEnabled("calc-table split-cells", {})).toBe(true);
   });

   it("split-cells: should return true when the first selected cell has span height > 1", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         selectedCells: [{ span: { width: 1, height: 3 } }],
      });

      expect((comp as any).isActionEnabled("calc-table split-cells", {})).toBe(true);
   });

   it("split-cells: should return false when span is 1×1", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         selectedCells: [{ span: { width: 1, height: 1 } }],
      });

      expect((comp as any).isActionEnabled("calc-table split-cells", {})).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — isDeleteRowActionEnabled / isDeleteColumnActionEnabled [Risk 3]
// ---------------------------------------------------------------------------

describe("VSBindingPane — isDeleteRowActionEnabled / isDeleteColumnActionEnabled", () => {
   it("delete-row: should return false when layoutModel is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue(null);

      expect((comp as any).isActionEnabled("calc-table delete-row", {})).toBe(false);
   });

   it("delete-row: should return false when only one row exists", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableRows: [{}],
         selectedCells: [{}],
         selectedRect: {},
      });

      expect((comp as any).isActionEnabled("calc-table delete-row", {})).toBe(false);
   });

   it("delete-row: should return true when multiple rows + selection + rect exist", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableRows: [{}, {}],
         selectedCells: [{}],
         selectedRect: {},
      });

      expect((comp as any).isActionEnabled("calc-table delete-row", {})).toBe(true);
   });

   it("delete-row: should return false when selectedCells is absent", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableRows: [{}, {}],
         selectedCells: null,
         selectedRect: {},
      });

      expect((comp as any).isActionEnabled("calc-table delete-row", {})).toBe(false);
   });

   it("delete-row: should return false when selectedRect is absent", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableRows: [{}, {}],
         selectedCells: [{}],
         selectedRect: null,
      });

      expect((comp as any).isActionEnabled("calc-table delete-row", {})).toBe(false);
   });

   it("delete-column: should return false when layoutModel is null", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue(null);

      expect((comp as any).isActionEnabled("calc-table delete-column", {})).toBe(false);
   });

   it("delete-column: should return true when multiple columns + selection + rect exist", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableColumns: [{}, {}],
         selectedCells: [{}],
         selectedRect: {},
      });

      expect((comp as any).isActionEnabled("calc-table delete-column", {})).toBe(true);
   });

   it("delete-column: should return false when only one column exists", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableColumns: [{}],
         selectedCells: [{}],
         selectedRect: {},
      });

      expect((comp as any).isActionEnabled("calc-table delete-column", {})).toBe(false);
   });

   it("delete-column: should return false when selectedRect is absent", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableColumns: [{}, {}],
         selectedCells: [{}],
         selectedRect: null,
      });

      expect((comp as any).isActionEnabled("calc-table delete-column", {})).toBe(false);
   });

   it("delete-column: should return false when selectedCells is absent", async () => {
      const { comp } = await renderComponent({ objectType: "VSCalcTable" });
      CALC_TABLE_MOCK.getTableLayout.mockReturnValue({
         tableColumns: [{}, {}],
         selectedCells: null,
         selectedRect: {},
      });

      expect((comp as any).isActionEnabled("calc-table delete-column", {})).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — handleExpiredSheet (dedup guard) [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — handleExpiredSheet", () => {
   // 🔁 Regression-sensitive: handleExpiredSheet guards with confirmExpiredDisplayed to prevent
   // stacking multiple "session expired" dialogs. If the guard is wrong, a second expired-sheet
   // command would open a second modal while the first is still pending.

   it("should open an expiry dialog on the first call", async () => {
      const { comp } = await renderComponent({ objectType: "VSChart" });

      (comp as any).handleExpiredSheet();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1));
   });

   it("should set confirmExpiredDisplayed=true to block duplicate dialogs", async () => {
      const { comp } = await renderComponent();

      (comp as any).handleExpiredSheet();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1));
      expect((comp as any).confirmExpiredDisplayed).toBe(true);
   });

   it("should not open a second dialog when confirmExpiredDisplayed is already true", async () => {
      const { comp } = await renderComponent();

      (comp as any).handleExpiredSheet();
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1));

      // Second call while the first dialog is still open must be a no-op.
      (comp as any).handleExpiredSheet();

      expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — cancelViewsheetLoading [Risk 2]
// ---------------------------------------------------------------------------

describe("VSBindingPane — cancelViewsheetLoading", () => {
   it("should fire a cancel-viewsheet event with the current runtimeId", async () => {
      const { comp } = await renderComponent();
      // ngOnInit sets runtimeId from MODEL_SERVICE_MOCK.getModel → "rt-test"
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.cancelViewsheetLoading();

      // CancelViewsheetLoadingEvent stores the id in private field runtimeViewsheetId, not runtimeId.
      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/cancelViewsheet",
         expect.objectContaining({ runtimeViewsheetId: "rt-test" }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 5 — processClearLoadingCommand [baseline]
// ---------------------------------------------------------------------------

describe("VSBindingPane — processClearLoadingCommand", () => {
   it("should set loading=false", async () => {
      const { comp } = await renderComponent();
      comp.processShowLoadingMaskCommand({ preparingData: false } as any);

      comp.processClearLoadingCommand({} as ClearLoadingCommand);

      expect(comp.loading).toBe(false);
   });
});
