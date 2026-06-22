/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { of } from "rxjs";
import { FontService } from "../../../../widget/services/font.service";
import { TextLayoutDesignerComponent } from "./text-layout-designer.component";

describe("TextLayoutDesignerComponent Unit Test", () => {
   let fixture: ComponentFixture<TextLayoutDesignerComponent>;
   let component: TextLayoutDesignerComponent;

   const fontService = {
      getAllFonts: vi.fn(() => of([])),
      defaultFont: ""
   };

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [FormsModule, TextLayoutDesignerComponent],
         providers: [
            { provide: FontService, useValue: fontService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TextLayoutDesignerComponent);
      component = fixture.componentInstance;
   });

   const dropEvent = (name: string): any => ({
      preventDefault: () => {}, stopPropagation: () => {},
      dataTransfer: { getData: () => JSON.stringify({ column: [{ name }] }) }
   });

   it("inserts an optimistic FIELD item at the drop position and emits onAddField", () => {
      const spy = vi.fn();
      component.onAddField.subscribe(spy);
      component.textFields = [];               // no bound fields resolved yet
      component.workingRows = [{ items: [] }];

      component.onBindingTreeDrop(dropEvent("state"), 0, 0);

      // host is told to append the real (aggregated) binding
      expect(spy).toHaveBeenCalledWith(
         expect.objectContaining({ insertRow: 0, insertIndex: 0 }));
      // and the item is visible immediately, referencing the index the backend appends at
      expect(component.workingRows[0].items.length).toBe(1);
      expect(component.workingRows[0].items[0])
         .toEqual({ type: component.FIELD, fieldIndex: 0 });
   });

   it("re-emits a chip aesthetic edit (e.g. change aggregate) to the host via onFieldChange", () => {
      const spy = vi.fn();
      component.onFieldChange.subscribe(spy);
      component.onFieldChipChanged();
      expect(spy).toHaveBeenCalled();
   });

   it("assigns sequential provisional indices for rapid multi-drops", () => {
      component.onAddField.subscribe(() => {});
      component.textFields = [];
      component.workingRows = [{ items: [] }];

      component.onBindingTreeDrop(dropEvent("a"), 0, 0);
      component.onBindingTreeDrop(dropEvent("b"), 0, 1);

      expect(component.workingRows[0].items.map((i: any) => i.fieldIndex)).toEqual([0, 1]);
   });

   it("removing a FIELD chip drops the orphaned textField and compacts later indices", () => {
      const removed = vi.fn();
      component.onRemoveField.subscribe(removed);
      component.textFields = [{ fullName: "a" }, { fullName: "b" }, { fullName: "c" }] as any;
      component.workingRows = [{ items: [
         { type: component.FIELD, fieldIndex: 0 },
         { type: component.FIELD, fieldIndex: 1 },
         { type: component.FIELD, fieldIndex: 2 }
      ] }];

      // remove the middle FIELD (index 1)
      component.removeFieldItem(0, 1);

      // host told to drop entry 1; orphan removed locally; index 2 compacted to 1
      expect(removed).toHaveBeenCalledWith(1);
      expect(component.textFields.map((f: any) => f.fullName)).toEqual(["a", "c"]);
      expect(component.workingRows[0].items.map((i: any) => i.fieldIndex)).toEqual([0, 1]);
   });

   it("sets the drop indicator when a divider is entered and clears on dragend", () => {
      component.workingRows = [{ items: [{ type: component.STATIC, text: "a" }] }];

      component.onDividerDragEnter(0, 1);
      expect(component.isDropTarget(0, 1)).toBe(true);
      expect(component.isDropTarget(0, 0)).toBe(false);

      component.onInternalDragEnd();
      expect(component.isDropTarget(0, 1)).toBe(false);
   });

   it("isDraggingItem matches only the active item drag", () => {
      (component as any).internalDrag = { kind: "item", rowIndex: 0, itemIndex: 0 };

      expect(component.isDraggingItem(0, 0)).toBe(true);
      expect(component.isDraggingItem(0, 1)).toBe(false);
      expect(component.isDraggingItem(1, 0)).toBe(false);
   });

   it("moves an item within a row, adjusting the target for the removal shift", () => {
      component.workingRows = [{ items: [
         { type: component.STATIC, text: "a" },
         { type: component.STATIC, text: "b" },
         { type: component.STATIC, text: "c" }
      ] }];

      // drag item 0 ("a") to the divider after "c" (insert-before index 3)
      (component as any).moveItem(0, 0, 0, 3);

      expect(component.workingRows[0].items.map((i: any) => i.text)).toEqual(["b", "c", "a"]);
   });

   it("moves an item to another row and drops the emptied source row", () => {
      component.workingRows = [
         { items: [{ type: component.STATIC, text: "a" }] },
         { items: [{ type: component.STATIC, text: "b" }] }
      ];

      // drag the only item of row 0 to the front of row 1
      (component as any).moveItem(0, 0, 1, 0);

      // source row removed; surviving row has both items in order
      expect(component.workingRows.length).toBe(1);
      expect(component.workingRows[0].items.map((i: any) => i.text)).toEqual(["a", "b"]);
   });

   it("inserts a palette item at the indicated position", () => {
      component.workingRows = [{ items: [
         { type: component.STATIC, text: "a" },
         { type: component.STATIC, text: "b" }
      ] }];

      (component as any).insertPaletteItem(component.SPACING, 0, 1);

      const types = component.workingRows[0].items.map((i: any) => i.type);
      expect(types).toEqual([component.STATIC, component.SPACING, component.STATIC]);
   });

   it("does not drop the textField when another item still references it", () => {
      const removed = vi.fn();
      component.onRemoveField.subscribe(removed);
      component.textFields = [{ fullName: "a" }] as any;
      component.workingRows = [{ items: [
         { type: component.FIELD, fieldIndex: 0 },
         { type: component.FIELD, fieldIndex: 0 }
      ] }];

      component.removeFieldItem(0, 0);

      // still referenced by the remaining item — keep the entry, no emit, no reindex
      expect(removed).not.toHaveBeenCalled();
      expect(component.textFields.length).toBe(1);
      expect(component.workingRows[0].items[0].fieldIndex).toBe(0);
   });

   it("removeRow deletes the whole row, drops its orphaned fields (highest first) and compacts survivors", () => {
      const removed = vi.fn();
      component.onRemoveField.subscribe(removed);
      component.textFields = [{ fullName: "a" }, { fullName: "b" }, { fullName: "c" }] as any;
      component.workingRows = [
         { items: [{ type: component.FIELD, fieldIndex: 0 }, { type: component.FIELD, fieldIndex: 1 }] },
         { items: [{ type: component.FIELD, fieldIndex: 2 }] }
      ];

      // delete the first row (holds fields 0 and 1)
      component.removeRow(0);

      // both orphaned fields dropped, emitted highest index first so host splices stay valid
      expect(removed.mock.calls.map(c => c[0])).toEqual([1, 0]);
      expect(component.textFields.map((f: any) => f.fullName)).toEqual(["c"]);
      // surviving row shifted up; its field index compacted 2 -> 0
      expect(component.workingRows.length).toBe(1);
      expect(component.workingRows[0].items[0].fieldIndex).toBe(0);
   });

   it("removeRow with only non-field items removes the row without touching textFields", () => {
      const removed = vi.fn();
      component.onRemoveField.subscribe(removed);
      component.textFields = [{ fullName: "a" }] as any;
      component.workingRows = [
         { items: [{ type: component.STATIC, text: "hello" }] },
         { items: [{ type: component.FIELD, fieldIndex: 0 }] }
      ];

      component.removeRow(0);

      expect(removed).not.toHaveBeenCalled();
      expect(component.textFields.length).toBe(1);
      expect(component.workingRows.length).toBe(1);
   });

   it("removeRow on the last row empties workingRows and drops all fields", () => {
      const removed = vi.fn();
      component.onRemoveField.subscribe(removed);
      component.textFields = [{ fullName: "a" }, { fullName: "b" }] as any;
      component.workingRows = [
         { items: [{ type: component.FIELD, fieldIndex: 0 }, { type: component.FIELD, fieldIndex: 1 }] }
      ];

      component.removeRow(0);

      expect(component.workingRows.length).toBe(0);
      expect(component.textFields.length).toBe(0);
      expect(removed.mock.calls.map((c: any) => c[0])).toEqual([1, 0]);
   });

   it("removeRow keeps a field still referenced by a surviving row", () => {
      const removed = vi.fn();
      component.onRemoveField.subscribe(removed);
      component.textFields = [{ fullName: "a" }] as any;
      component.workingRows = [
         { items: [{ type: component.FIELD, fieldIndex: 0 }] },
         { items: [{ type: component.FIELD, fieldIndex: 0 }] }
      ];

      component.removeRow(0);

      // field 0 still used by the surviving row — keep it, no emit
      expect(removed).not.toHaveBeenCalled();
      expect(component.textFields.length).toBe(1);
      expect(component.workingRows.length).toBe(1);
      expect(component.workingRows[0].items[0].fieldIndex).toBe(0);
   });
});
