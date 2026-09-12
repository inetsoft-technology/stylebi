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
 * SQLQueryJoinDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — validate: duplicate / repeated tables / incompatible columns / full outer
 *   Group 2 [Risk 3] — ok / close emit; changeOperator clears outer-join flags
 *   Group 3 [Risk 3] — ngOnDestroy unsubscribes columnCache subscriptions
 *
 * Direct instantiation — modal template not rendered.
 */

import { of, Subject } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { JoinItem } from "../../../composer/data/ws/join-item";
import { SQLQueryJoinDialog } from "./sql-query-join-dialog.component";

function entry(attribute: string, dtype: string): AssetEntry {
   return { properties: { attribute, dtype } } as unknown as AssetEntry;
}

function joinItem(overrides: Partial<JoinItem> = {}): JoinItem {
   return {
      table1: "T1",
      table2: "T2",
      column1: "id",
      column2: "id",
      operator: "=",
      all1: false,
      all2: false,
      ...overrides,
   };
}

function createDialog() {
   const comp = new SQLQueryJoinDialog();
   const idCol = entry("id", "integer");
   const nameCol = entry("name", "string");
   comp.tables = { T1: {} as AssetEntry, T2: {} as AssetEntry };
   comp.joins = [];
   comp.supportsFullOuterJoin = true;
   comp.columnCache = {
      T1: of([idCol]),
      T2: of([idCol, nameCol]),
   };
   comp.join = joinItem();
   comp.tempColumn1 = idCol;
   comp.tempColumn2 = idCol;
   return { comp, idCol, nameCol };
}

describe("SQLQueryJoinDialog — validate [Group 1, Risk 3]", () => {

   it("should fail validation when join duplicates an existing entry", () => {
      const { comp } = createDialog();
      const existing = joinItem();
      comp.joins = [existing];
      comp.join = joinItem();
      comp.editIndex = -1;

      expect(comp.validate()).toBe(false);
      expect(comp.error).toBe("_#(js:common.sqlquery.joinDuplicate)");
   });

   it("should allow saving when editing the same join at editIndex", () => {
      const { comp } = createDialog();
      const existing = joinItem();
      comp.joins = [existing];
      comp.join = joinItem();
      comp.editIndex = 0;

      expect(comp.validate()).toBe(true);
      expect(comp.error).toBeNull();
   });

   it("should fail validation when both sides reference the same table", () => {
      const { comp } = createDialog();
      comp.join = joinItem({ table1: "T1", table2: "T1" });

      expect(comp.validate()).toBe(false);
      expect(comp.error).toBe("_#(js:common.sqlquery.joinTableRepeat)");
   });

   it("should fail validation when column types are incompatible", () => {
      const { comp, idCol, nameCol } = createDialog();
      comp.tempColumn1 = idCol;
      comp.tempColumn2 = nameCol;

      expect(comp.validate()).toBe(false);
      expect(comp.error).toBe("Columns have incompatible types.");
   });

   it("should fail validation for full outer join when datasource does not support it", () => {
      const { comp } = createDialog();
      comp.supportsFullOuterJoin = false;
      comp.join = joinItem({ all1: true, all2: true });

      expect(comp.validate()).toBe(false);
      expect(comp.error).toContain("Full Outer Join");
   });
});

describe("SQLQueryJoinDialog — commit and operator [Group 2, Risk 3]", () => {

   it("should emit join on ok after copying temp column attributes", () => {
      const { comp, idCol } = createDialog();
      comp.tempColumn1 = idCol;
      comp.tempColumn2 = idCol;
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(comp.join.column1).toBe("id");
      expect(comp.join.column2).toBe("id");
      expect(emitSpy).toHaveBeenCalledWith(comp.join);
   });

   it("should emit cancel on close", () => {
      const { comp } = createDialog();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.close();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });

   it("should clear outer-join flags when operator is not equals", () => {
      const { comp } = createDialog();
      comp.join = joinItem({ all1: true, all2: true });

      comp.changeOperator(">");

      expect(comp.join.operator).toBe(">");
      expect(comp.join.all1).toBe(false);
      expect(comp.join.all2).toBe(false);
   });
});

describe("SQLQueryJoinDialog — subscriptions [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: leaked columnCache subscriptions refetch on every dialog open
   it("should unsubscribe column subscriptions on destroy", () => {
      const comp = new SQLQueryJoinDialog();
      const subject1 = new Subject<AssetEntry[]>();
      const subject2 = new Subject<AssetEntry[]>();
      comp.joins = [];
      comp.tables = { T1: {} as AssetEntry };
      comp.columnCache = { T1: subject1, T2: subject2 };
      comp.join = joinItem({ table1: null, table2: null });
      comp.ngOnInit();

      comp.ngOnDestroy();

      expect(comp.columnSub1.closed).toBe(true);
      expect(comp.columnSub2.closed).toBe(true);
   });
});
