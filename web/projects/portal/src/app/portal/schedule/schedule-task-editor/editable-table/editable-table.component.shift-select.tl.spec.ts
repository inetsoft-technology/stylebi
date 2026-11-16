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
 * EditableTableComponent - shift/ctrl click selection (Bug #80366).
 * Real component, real DOM row clicks.
 */

import { fireEvent, render } from "@testing-library/angular";
import { EditableTableComponent } from "./editable-table.component";

async function renderTable() {
   const { fixture, container } = await render(EditableTableComponent, {
      componentInputs: { items: ["A", "B", "C", "D"], title: "Items", selectedItems: [] },
   });
   const comp = fixture.componentInstance;
   const rows = () => Array.from((container as HTMLElement).querySelectorAll("tbody tr"));
   const click = (i: number, init: any = {}) => {
      fireEvent.click(rows()[i], init);
      fixture.detectChanges();
   };

   return { comp, click };
}

describe("EditableTableComponent shift-click range", () => {
   it("selects the whole range between the anchor and the clicked row", async () => {
      const { comp, click } = await renderTable();
      click(0);
      click(2, { shiftKey: true });
      expect([...comp.selectedItems].sort()).toEqual([0, 1, 2]);
   });

   it("selects the range upwards", async () => {
      const { comp, click } = await renderTable();
      click(3);
      click(1, { shiftKey: true });
      expect([...comp.selectedItems].sort()).toEqual([1, 2, 3]);
   });

   it("shift-clicking the same row twice never duplicates an index", async () => {
      const { comp, click } = await renderTable();
      click(1);
      click(2, { shiftKey: true });
      click(2, { shiftKey: true });
      expect([...comp.selectedItems].sort()).toEqual([1, 2]);
   });

   it("shift-clicking the anchor row selects only that row", async () => {
      const { comp, click } = await renderTable();
      click(1);
      click(1, { shiftKey: true });
      expect(comp.selectedItems).toEqual([1]);
   });

   it("a second shift-click re-ranges from the same anchor", async () => {
      const { comp, click } = await renderTable();
      click(0);
      click(3, { shiftKey: true });
      click(1, { shiftKey: true });
      expect([...comp.selectedItems].sort()).toEqual([0, 1]);
   });

   it("ctrl-click toggles a row and plain click replaces the selection", async () => {
      const { comp, click } = await renderTable();
      click(0);
      click(2, { ctrlKey: true });
      expect([...comp.selectedItems].sort()).toEqual([0, 2]);
      click(2, { ctrlKey: true });
      expect(comp.selectedItems).toEqual([0]);
      click(3);
      expect(comp.selectedItems).toEqual([3]);
   });

   it("shift range starts from the last ctrl-clicked row", async () => {
      const { comp, click } = await renderTable();
      click(0);
      click(2, { ctrlKey: true });
      click(3, { shiftKey: true });
      expect([...comp.selectedItems].sort()).toEqual([2, 3]);
   });
});
