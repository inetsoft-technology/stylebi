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
 * EmCSVConfigPaneComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — addAssembly(): null selectedAssemblies init, dedup guard, fireConfigChange
 *   Group 2 [Risk 2]  — removeAssembly(): null guard, splice, fireConfigChange
 *   Group 3 [Risk 2]  — selectAllChanged() + isSelectedAllTables(): null vs empty-array semantics
 *
 * KEY contracts:
 *   - isSelectedAllTables() returns true iff selectedAssemblies === null (null = ALL selected).
 *   - isSelectedAllTables() returns false for [] (empty array = NONE explicitly selected).
 *   - addAssembly() initializes selectedAssemblies to [] before pushing when it is null.
 *   - addAssembly() is a no-op for duplicates (same assembly already present).
 *   - removeAssembly() is a no-op when selectedAssemblies is null/undefined.
 *   - selectAllChanged(true)  → selectedAssemblies = null  (all tables included implicitly).
 *   - selectAllChanged(false) → selectedAssemblies = []    (no tables selected).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { EmCSVConfigPaneComponent } from "./em-csv-config-pane.component";
import { CSVConfigModel } from "../../../../../../shared/schedule/model/csv-config-model";

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComp() {
   const result = await render(EmCSVConfigPaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
   });
   result.fixture.detectChanges();
   return { comp: result.fixture.componentInstance as EmCSVConfigPaneComponent };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 2] — addAssembly(): null init, dedup, emit
// ════════════════════════════════════════════════════════════════════════════

describe("EmCSVConfigPaneComponent — addAssembly(): initialization and deduplication", () => {

   // 🔁 Regression-sensitive: when selectedAssemblies starts as null (all-selected state),
   // addAssembly() must initialize it to [] before pushing, converting it to an explicit
   // selection. Without this init, push() on null throws TypeError.
   it("should initialize selectedAssemblies to [] and push the assembly when it is null", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = new CSVConfigModel(); // selectedAssemblies defaults to null
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.addAssembly("TableA");

      expect(comp.csvConfigModel.selectedAssemblies).toEqual(["TableA"]);
      expect(emitSpy).toHaveBeenCalledTimes(1);
   });

   // 🔁 Regression-sensitive: adding the same assembly twice must not create a duplicate entry.
   // Duplicate assemblies in selectedAssemblies cause the same table to be exported twice.
   it("should not add a duplicate assembly and should not fire a change event for it", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = Object.assign(new CSVConfigModel(), { selectedAssemblies: ["TableA"] });
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.addAssembly("TableA");

      expect(comp.csvConfigModel.selectedAssemblies).toEqual(["TableA"]);
      expect(emitSpy).not.toHaveBeenCalled();
   });

   // Risk Point/Contract: adding an empty string is a no-op (falsy guard).
   it("should not add or emit when assembly name is empty", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = Object.assign(new CSVConfigModel(), { selectedAssemblies: [] });
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.addAssembly("");

      expect(comp.csvConfigModel.selectedAssemblies).toEqual([]);
      expect(emitSpy).not.toHaveBeenCalled();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — removeAssembly(): null guard, splice, emit
// ════════════════════════════════════════════════════════════════════════════

describe("EmCSVConfigPaneComponent — removeAssembly(): null guard and splice", () => {

   // Risk Point/Contract: removeAssembly() must be a no-op when selectedAssemblies is null
   // (all-tables mode). Attempting to call indexOf on null would throw a TypeError.
   it("should not throw and should not emit when selectedAssemblies is null", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = new CSVConfigModel(); // selectedAssemblies = null
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      expect(() => comp.removeAssembly("TableA")).not.toThrow();
      expect(emitSpy).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: removeAssembly() must splice the exact matching item
   // and fire the change event so the parent can update its state.
   it("should remove the assembly and emit when it exists in selectedAssemblies", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = Object.assign(new CSVConfigModel(), {
         selectedAssemblies: ["TableA", "TableB"]
      });
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.removeAssembly("TableA");

      expect(comp.csvConfigModel.selectedAssemblies).toEqual(["TableB"]);
      expect(emitSpy).toHaveBeenCalledTimes(1);
   });

   // Boundary: removing an assembly that is not in the list is a no-op.
   it("should not modify selectedAssemblies when the assembly is not present", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = Object.assign(new CSVConfigModel(), {
         selectedAssemblies: ["TableA"]
      });
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.removeAssembly("TableX");

      expect(comp.csvConfigModel.selectedAssemblies).toEqual(["TableA"]);
      expect(emitSpy).not.toHaveBeenCalled();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — selectAllChanged() + isSelectedAllTables(): null vs []
// ════════════════════════════════════════════════════════════════════════════

describe("EmCSVConfigPaneComponent — selectAllChanged() and isSelectedAllTables(): null vs empty array", () => {

   // 🔁 Regression-sensitive: selectAllChanged(true) must set selectedAssemblies to null,
   // not [], to mean "all tables implicitly included." If set to [] instead, no tables
   // are exported despite the "select all" intent.
   it("should set selectedAssemblies to null and emit when selectAllChanged(true)", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = Object.assign(new CSVConfigModel(), { selectedAssemblies: ["TableA"] });
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.selectAllChanged(true);

      expect(comp.csvConfigModel.selectedAssemblies).toBeNull();
      expect(emitSpy).toHaveBeenCalledTimes(1);
   });

   // 🔁 Regression-sensitive: selectAllChanged(false) must set selectedAssemblies to [],
   // not null — null means "all selected," [] means "none explicitly selected."
   it("should set selectedAssemblies to [] and emit when selectAllChanged(false)", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = new CSVConfigModel(); // null
      const emitSpy = jest.spyOn(comp.csvConfigChanged, "emit");

      comp.selectAllChanged(false);

      expect(comp.csvConfigModel.selectedAssemblies).toEqual([]);
      expect(emitSpy).toHaveBeenCalledTimes(1);
   });

   // Risk Point/Contract: null means all selected; [] means none — these two states must be
   // distinguishable at the UI layer via isSelectedAllTables().
   it("should return true for isSelectedAllTables() when selectedAssemblies is null", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = new CSVConfigModel(); // selectedAssemblies = null

      expect(comp.isSelectedAllTables()).toBe(true);
   });

   it("should return false for isSelectedAllTables() when selectedAssemblies is []", async () => {
      const { comp } = await renderComp();
      comp.csvConfigModel = Object.assign(new CSVConfigModel(), { selectedAssemblies: [] });

      expect(comp.isSelectedAllTables()).toBe(false);
   });

});
