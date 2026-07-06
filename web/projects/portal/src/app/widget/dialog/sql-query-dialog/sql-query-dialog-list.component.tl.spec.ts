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
 * SQLQueryDialogListComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — indexSelected / deleteItem: selection state + index adjustment
 *   Group 2 [Risk 3] — drop + reorderItems: reorder emit contract
 *   Group 3 [Risk 2] — insertPositionDragOverItem: insert-above vs below midpoint
 *   Group 4 [Risk 1] — selectAll / clearSelection / getLabel
 */

import { DragService } from "../../services/drag.service";
import { SQLQueryDialogListComponent } from "./sql-query-dialog-list.component";

function createList(items: string[] = ["a", "b", "c"]) {
   const dragService = {
      get: vi.fn(),
      put: vi.fn(),
      getDragData: vi.fn(() => "external"),
   } as unknown as DragService;
   const comp = new SQLQueryDialogListComponent(dragService);
   comp.items = items;
   comp.reorderName = "reorder";
   comp.dragNames = ["externalDrag"];
   return { comp, dragService };
}

describe("SQLQueryDialogListComponent — selection and delete [Group 1, Risk 3]", () => {

   it("should replace selection in single-select mode", () => {
      const { comp } = createList();
      const emitSpy = vi.spyOn(comp.itemSelected, "emit");

      comp.indexSelected(0);
      comp.indexSelected(2);

      expect(comp.selectedIndexes).toEqual([2]);
      expect(emitSpy).toHaveBeenCalledTimes(2);
      expect(emitSpy).toHaveBeenLastCalledWith("c");
   });

   it("should accumulate selection in multiple-select mode", () => {
      const { comp } = createList();
      comp.multipleSelection = true;

      comp.indexSelected(0);
      comp.indexSelected(2);

      expect(comp.selectedIndexes).toEqual([0, 2]);
      expect(comp.getSelectedItems()).toEqual(["a", "c"]);
   });

   it("should not re-select an already selected index", () => {
      const { comp } = createList();
      const emitSpy = vi.spyOn(comp.itemSelected, "emit");

      comp.indexSelected(1);
      comp.indexSelected(1);

      expect(comp.selectedIndexes).toEqual([1]);
      expect(emitSpy).toHaveBeenCalledTimes(1);
   });

   it("should emit itemDeleted and shift selected indexes down", () => {
      const { comp } = createList();
      comp.selectedIndexes = [0, 2];
      const emitSpy = vi.spyOn(comp.itemDeleted, "emit");

      comp.deleteItem(1);

      expect(emitSpy).toHaveBeenCalledWith(1);
      expect(comp.selectedIndexes).toEqual([0, 1]);
   });
});

describe("SQLQueryDialogListComponent — drop reorder [Group 2, Risk 3]", () => {

   it("should emit reordered items when reorder drag data is present", () => {
      const { comp, dragService } = createList(["x", "y", "z"]);
      vi.mocked(dragService.get).mockImplementation((name: string) => {
         if(name === "reorder") {
            return [2];
         }
         return undefined;
      });
      const emitSpy = vi.spyOn(comp.itemsChange, "emit");
      const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() };

      comp.drop(event as never, 0);

      expect(emitSpy).toHaveBeenCalledWith(["z", "x", "y"]);
      expect(comp.insertPosition).toBeNull();
   });

   it("should emit itemsDropped for external drag data", () => {
      const { comp, dragService } = createList();
      vi.mocked(dragService.get).mockReturnValue(undefined);
      const emitSpy = vi.spyOn(comp.itemsDropped, "emit");
      const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() };

      comp.drop(event as never, 1);

      expect(emitSpy).toHaveBeenCalledWith(["external", 1]);
   });
});

describe("SQLQueryDialogListComponent — insert position [Group 3, Risk 2]", () => {

   it("should insert below item when mouse is in lower half", () => {
      const { comp } = createList();
      const event = {
         target: { offsetHeight: 20 },
         offsetY: 15,
      };

      expect(comp.insertPositionDragOverItem(event as never, 2)).toBe(3);
   });

   it("should insert above item when mouse is in upper half", () => {
      const { comp } = createList();

      expect(comp.insertPositionDragOverItem({
         target: { offsetHeight: 20 },
         offsetY: 5,
      } as never, 2)).toBe(2);
   });
});

describe("SQLQueryDialogListComponent — helpers [Group 4, Risk 1]", () => {

   it("should select all item indexes", () => {
      const { comp } = createList(["p", "q"]);

      comp.selectAll();

      expect(comp.selectedIndexes).toEqual([0, 1]);
   });

   it("should clear selection", () => {
      const { comp } = createList();
      comp.selectedIndexes = [0, 1];
      comp.clearSelection();
      expect(comp.selectedIndexes).toEqual([]);
   });

   it("should use labelFunction when provided", () => {
      const { comp } = createList();
      comp.labelFunction = (item: string) => `label:${item}`;

      expect(comp.getLabel("x")).toBe("label:x");
      expect(comp.getLabel("y")).toBe("label:y");
   });
});
