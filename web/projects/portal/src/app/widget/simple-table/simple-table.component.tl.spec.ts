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
 * SimpleTableComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — sortClicked: ASC → DESC → NONE cycle + onSort emit
 *   Group 2 [Risk 2] — getSortLabel: column-specific sort icon state
 *   Group 3 [Risk 2] — tableData setter: null guard + tableHeight
 *   Group 4 [Risk 2] — touchVScroll: scrollY clamping
 *
 * Direct instantiation avoids TouchScroll/NgbTooltip child init.
 */

import { ChangeDetectorRef, Renderer2 } from "@angular/core";
import { XConstants } from "../../common/util/xconstants";
import { ModelService } from "../services/model.service";
import { SimpleTableComponent } from "./simple-table.component";
import { BaseTableCellModel } from "../../vsobjects/model/base-table-cell-model";

function createTable() {
   return new SimpleTableComponent(
      {} as ModelService,
      { setProperty: vi.fn() } as unknown as Renderer2,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
   );
}

function cell(): BaseTableCellModel {
   return {} as BaseTableCellModel;
}

describe("SimpleTableComponent — sortClicked — cycle and emit [Group 1, Risk 3]", () => {

   it("should cycle sort ASC → DESC → NONE and emit onSort each click", () => {
      const comp = createTable();
      const emitSpy = vi.spyOn(comp.onSort, "emit");

      comp.sortClicked(2);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_DESC);
      expect(emitSpy).toHaveBeenLastCalledWith({ sortValue: XConstants.SORT_DESC, sortCol: 2 });

      comp.sortClicked(2);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_NONE);
      expect(emitSpy).toHaveBeenLastCalledWith({ sortValue: XConstants.SORT_NONE, sortCol: 2 });

      comp.sortClicked(2);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_ASC);
      expect(emitSpy).toHaveBeenLastCalledWith({ sortValue: XConstants.SORT_ASC, sortCol: 2 });
   });

   it("should initialize sortInfo to ASC on first sort click", () => {
      const comp = createTable();

      comp.sortClicked(0);

      expect(comp.sortInfo.col).toBe(0);
      expect(comp.sortInfo.sortValue).toBe(XConstants.SORT_DESC);
   });
});

describe("SimpleTableComponent — getSortLabel [Group 2, Risk 2]", () => {

   it("should return sort-ascending for active ascending column", () => {
      const comp = createTable();
      comp.sortInfo = { col: 1, sortValue: XConstants.SORT_ASC };

      expect(comp.getSortLabel(1)).toBe("sort-ascending");
      expect(comp.getSortLabel(0)).toBe("sort");
   });

   it("should return sort-descending for active descending column", () => {
      const comp = createTable();
      comp.sortInfo = { col: 3, sortValue: XConstants.SORT_DESC };

      expect(comp.getSortLabel(3)).toBe("sort-descending");
   });
});

describe("SimpleTableComponent — tableData setter [Group 3, Risk 2]", () => {

   it("should treat null tableData as empty array", () => {
      const comp = createTable();
      comp.tableData = null;

      expect(comp.tableData).toEqual([]);
   });

   it("should set tableHeight from row count and cellHeight", () => {
      const comp = createTable();
      comp.tableData = [[cell()], [cell()], [cell()]];

      expect(comp.tableHeight).toBe(84);
   });
});

describe("SimpleTableComponent — touchVScroll — clamping [Group 4, Risk 2]", () => {

   it("should clamp scrollY between 0 and max scroll range", () => {
      const comp = createTable();
      comp.tableContainer = {
         nativeElement: { scrollHeight: 500, clientHeight: 200 },
      } as typeof comp.tableContainer;

      comp.touchVScroll(-100);
      expect(comp.scrollY).toBe(100);

      comp.touchVScroll(500);
      expect(comp.scrollY).toBe(0);
   });
});
