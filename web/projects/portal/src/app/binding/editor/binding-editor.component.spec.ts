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
import { BindingEditor } from "./binding-editor.component";

// These tests exercise the Text Layout Designer host logic directly (no DOM/TestBed needed):
// the designer must be transactional — mid-session field add/remove must NOT mutate the live
// binding model; only Commit applies, Cancel discards.
describe("BindingEditor — Text Layout Designer transactional textFields", () => {
   function makeEditor(bindingModel: any): any {
      // Bypass the Angular constructor; these methods only touch plain component fields.
      // Set the backing field directly — the `bindingModel` setter has a side effect
      // (this.bindingService.bindingModel = ...) that needs an injected service we don't have here.
      const comp: any = Object.create(BindingEditor.prototype);
      comp._bindingModel = bindingModel;
      comp.layoutDesignerTextFields = [];
      comp.layoutDesignerLayout = null;
      comp.layoutDesignerAggregateName = null;
      comp.showLayoutDesigner = false;
      return comp;
   }

   function fullLayoutModel(): any {
      const A = { fullName: "A" }, B = { fullName: "B" }, C = { fullName: "C" };
      return {
         model: {
            textFields: [A, B, C],
            textLayout: { rows: [{ items: [
               { type: 0, fieldIndex: 0 },
               { type: 2, spacingAmount: 5 },
               { type: 0, fieldIndex: 1 },
               { type: 1, text: "-" },
               { type: 0, fieldIndex: 2 }
            ] }] }
         },
         A, B, C
      };
   }

   it("Cancel after removing a field leaves the live binding model's textFields unchanged", () => {
      const { model, A, B, C } = fullLayoutModel();
      const comp = makeEditor(model);

      comp.openLayoutDesignerOverlay(null);   // open: working copy = [A,B,C]
      comp.onLayoutRemoveField(1);             // remove B (index 1)
      comp.onLayoutDesignerCancel();           // cancel — must discard

      expect(model.textFields).toEqual([A, B, C]);
      expect(model.textFields.length).toBe(3);
   });

   it("Cancel after adding a field leaves the live binding model's textFields unchanged", () => {
      const { model, A, B, C } = fullLayoutModel();
      const comp = makeEditor(model);

      comp.openLayoutDesignerOverlay(null);
      comp.onLayoutAddField({ field: { fullName: "D" }, insertRow: 0, insertIndex: 0 });
      comp.onLayoutDesignerCancel();

      expect(model.textFields).toEqual([A, B, C]);
      expect(model.textFields.length).toBe(3);
   });

   it("remove updates the working copy so the designer reflects the change mid-session", () => {
      const { model, A, C } = fullLayoutModel();
      const comp = makeEditor(model);

      comp.openLayoutDesignerOverlay(null);
      comp.onLayoutRemoveField(1);

      // working copy compacted to [A, C]; live model still intact until commit
      expect(comp.layoutDesignerTextFields).toEqual([A, C]);
      expect(model.textFields.length).toBe(3);
   });
});
