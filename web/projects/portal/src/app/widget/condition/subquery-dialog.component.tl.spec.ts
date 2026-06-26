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
 * SubqueryDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: default value construction; existing value table lookup
 *   Group 2 [Risk 3] — changeSelectedTable: resets attribute/subAttribute on table switch
 *   Group 3 [Risk 2] — isValid: paired sub/main attributes required together
 *   Group 4 [Risk 2] — getAvailableTables / getCurrentTableColumns filtering
 *   Group 5 [Risk 1] — ok/cancel emits; dataRefsEqual; getTooltip branching
 *
 * 🔁 Regression-sensitive: Bug #9968 — single currentTable with empty columns must not crash init
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { SubqueryValue } from "../../common/data/condition/subquery-value";
import { ColumnRef } from "../../binding/data/column-ref";
import { SubqueryDialog } from "./subquery-dialog.component";

function col(name: string, entity = "t1"): ColumnRef {
   return { entity, attribute: name, description: `${name} desc` } as ColumnRef;
}

function table(name: string, opts: { currentTable?: boolean; columns?: ColumnRef[] } = {}): SubqueryTable {
   return {
      name,
      description: name,
      currentTable: opts.currentTable ?? false,
      columns: opts.columns ?? [col("c1")],
   };
}

async function renderDialog(props: {
   subqueryTables?: SubqueryTable[];
   value?: SubqueryValue | null;
   showOriginalName?: boolean;
} = {}) {
   const result = await render(SubqueryDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         subqueryTables: props.subqueryTables ?? [table("q1"), table("q2")],
         value: props.value ?? null,
         showOriginalName: props.showOriginalName ?? false,
      },
   });
   return result.fixture.componentInstance;
}

describe("SubqueryDialog — ngOnChanges — table cache refresh [Group 1, Risk 3]", () => {

   it("should refresh availableTables when subqueryTables input changes", async () => {
      const comp = await renderDialog({
         subqueryTables: [table("q1"), table("q2")],
      });
      expect(comp.getAvailableTables().map(t => t.name)).toEqual(["q1", "q2"]);

      comp.subqueryTables = [table("main", { currentTable: true }), table("subOnly")];
      comp.ngOnChanges({
         subqueryTables: {
            previousValue: null,
            currentValue: comp.subqueryTables,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(comp.getAvailableTables().map(t => t.name)).toEqual(["subOnly"]);
      expect(comp.getCurrentTableColumns()).toEqual([col("c1")]);
   });
});

describe("SubqueryDialog — ngOnInit — value initialization [Group 1, Risk 3]", () => {

   it("should build default value from first available table when value is null", async () => {
      const comp = await renderDialog({
         subqueryTables: [table("queryA", { columns: [col("colA")] }), table("queryB")],
      });

      expect(comp.value.query).toBe("queryA");
      expect(comp.value.attribute).toEqual(col("colA"));
      expect(comp.selectedTable.name).toBe("queryA");
   });

   // 🔁 Regression-sensitive: Bug #9968 — empty columns on currentTable must not crash
   it("should not crash when only currentTable exists with empty columns", async () => {
      const comp = await renderDialog({
         subqueryTables: [table("current", { currentTable: true, columns: [] })],
      });

      expect(comp.availableTables).toEqual([]);
      expect(comp.currentTableColumns).toEqual([]);
      expect(comp.value.query).toBeNull();
      expect(comp.value.attribute).toBeNull();
   });

   it("should select matching table when value is pre-populated", async () => {
      const existing: SubqueryValue = {
         query: "q2",
         attribute: col("x"),
         subAttribute: null,
         mainAttribute: null,
      };
      const comp = await renderDialog({
         subqueryTables: [table("q1"), table("q2")],
         value: existing,
      });

      expect(comp.selectedTable.name).toBe("q2");
      expect(comp.value).toBe(existing);
   });

   it("should leave selectedTable undefined when pre-populated query is not in available tables", async () => {
      const existing: SubqueryValue = {
         query: "missing",
         attribute: col("x"),
         subAttribute: null,
         mainAttribute: null,
      };
      const comp = await renderDialog({
         subqueryTables: [table("q1"), table("q2")],
         value: existing,
      });

      expect(comp.selectedTable).toBeUndefined();
      expect(comp.value).toBe(existing);
   });
});

describe("SubqueryDialog — changeSelectedTable — attribute reset [Group 2, Risk 3]", () => {

   it("should reset attribute to first column and clear subAttribute on table change", async () => {
      const comp = await renderDialog({
         subqueryTables: [
            table("q1", { columns: [col("a1")] }),
            table("q2", { columns: [col("b1"), col("b2")] }),
         ],
      });
      comp.value.subAttribute = col("oldSub");
      comp.value.mainAttribute = col("oldMain");

      comp.changeSelectedTable("q2");

      expect(comp.value.query).toBe("q2");
      expect(comp.value.attribute).toEqual(col("b1"));
      expect(comp.value.subAttribute).toBeNull();
      expect(comp.selectedTable.name).toBe("q2");
   });

   // 🔁 Regression-sensitive: documents current behavior — mainAttribute is NOT cleared on table switch
   it("should preserve mainAttribute when switching tables (current contract)", async () => {
      const comp = await renderDialog({
         subqueryTables: [
            table("q1", { columns: [col("a1")] }),
            table("q2", { columns: [col("b1")] }),
         ],
      });
      const main = col("mainCol");
      comp.value.mainAttribute = main;

      comp.changeSelectedTable("q2");

      expect(comp.value.mainAttribute).toBe(main);
   });

   it("should set attribute to null when switched table has no columns", async () => {
      const comp = await renderDialog({
         subqueryTables: [
            table("q1", { columns: [col("a1")] }),
            table("q2", { columns: [] }),
         ],
      });

      comp.changeSelectedTable("q2");

      expect(comp.value.attribute).toBeNull();
   });
});

describe("SubqueryDialog — isValid — paired attribute contract [Group 3, Risk 2]", () => {

   it("should be valid when both sub and main attributes are null", async () => {
      const comp = await renderDialog();
      comp.value.subAttribute = null;
      comp.value.mainAttribute = null;

      expect(comp.isValid()).toBe(true);
   });

   it("should be valid when both sub and main attributes are set", async () => {
      const comp = await renderDialog();
      comp.value.subAttribute = col("sub");
      comp.value.mainAttribute = col("main");

      expect(comp.isValid()).toBe(true);
   });

   it("should be invalid when only one of sub/main attributes is set", async () => {
      const comp = await renderDialog();
      comp.value.subAttribute = col("sub");
      comp.value.mainAttribute = null;

      expect(comp.isValid()).toBe(false);
   });
});

describe("SubqueryDialog — table filtering [Group 4, Risk 2]", () => {

   it("should exclude currentTable from availableTables", async () => {
      const comp = await renderDialog({
         subqueryTables: [
            table("main", { currentTable: true }),
            table("sub1"),
            table("sub2"),
         ],
      });

      expect(comp.getAvailableTables().map(t => t.name)).toEqual(["sub1", "sub2"]);
   });

   it("should return columns from currentTable via getCurrentTableColumns", async () => {
      const columns = [col("x"), col("y")];
      const comp = await renderDialog({
         subqueryTables: [table("main", { currentTable: true, columns })],
      });

      expect(comp.getCurrentTableColumns()).toEqual(columns);
   });
});

describe("SubqueryDialog — emits and helpers [Group 5, Risk 1]", () => {

   it("should emit onCommit with current value on ok", async () => {
      const comp = await renderDialog();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(emitSpy).toHaveBeenCalledWith(comp.value);
   });

   it("should emit cancel on cancel", async () => {
      const comp = await renderDialog();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });

   it("should compare dataRefs by entity and attribute in dataRefsEqual", async () => {
      const comp = await renderDialog();
      const ref1 = col("a", "e1");
      const ref2 = col("a", "e1");
      const ref3 = col("b", "e1");

      expect(comp.dataRefsEqual(ref1, ref2)).toBe(true);
      expect(comp.dataRefsEqual(ref1, ref3)).toBe(false);
      expect(comp.dataRefsEqual(null, null)).toBe(true);
      expect(comp.dataRefsEqual(ref1, null)).toBe(false);
   });

   it("should use ColumnRef tooltip when showOriginalName is true", async () => {
      const ref = col("field1", "Orders");
      vi.spyOn(ColumnRef, "getTooltip").mockReturnValue("Orders.field1");
      const comp = await renderDialog({ showOriginalName: true });

      expect(comp.getTooltip(ref)).toBe("Orders.field1");
   });

   it("should use description when showOriginalName is false", async () => {
      const ref = col("field1");
      const comp = await renderDialog({ showOriginalName: false });

      expect(comp.getTooltip(ref)).toBe("field1 desc");
   });
});
