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
 * TaskActionPane - shift-click range selection followed by Delete (Bug #80366).
 *
 * The selection is produced by the real EditableTableComponent.selectItem() (the pane binds
 * [(selectedItems)] two-way to selectedActions) and then deleted with the real deleteAction().
 */

import { Subject } from "rxjs";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { EditableTableComponent } from "../editable-table/editable-table.component";
import {
   lastRenderedFixture, makeAction, makeModel, MODAL_MOCK, renderTaskActionPane, resetMocks
} from "./task-action-pane.test-helpers";

beforeEach(() => {
   resetMocks();
   // ComponentTool suppresses a repeated identical message shown within a short time
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});
afterEach(() => { lastRenderedFixture?.destroy(); });

const plain = { ctrlKey: false, shiftKey: false } as MouseEvent;
const shift = { ctrlKey: false, shiftKey: true } as MouseEvent;

async function setup() {
   const model = makeModel({
      actions: ["A", "B", "C", "D"].map((label) => makeAction({ label }))
   });
   const { comp } = await renderTaskActionPane(model);
   const table = new EditableTableComponent();
   table.items = ["A", "B", "C", "D"];
   table.selectedItems = [];
   table.selectedItemsChange.subscribe((v: number[]) => comp.selectedActions = v);

   const click = (i: number, event: MouseEvent = plain) => table.selectItem(event, i);
   const del = async (expected: string[]) => {
      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("ok"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      comp.deleteAction();
      // let the confirm dialog promise resolve (no waitFor: its MutationObserver loops with
      // the pane's per-check array getters)
      await new Promise((resolve) => setTimeout(resolve));
      expect(comp.model.actions.map((a) => a.label)).toEqual(expected);
   };

   return { comp, click, del };
}

describe("TaskActionPane shift-click range then Delete", () => {
   it("B, shift-C, shift-C, Delete leaves A and D", async () => {
      const { click, del } = await setup();
      click(1);
      click(2, shift);
      click(2, shift);
      await del(["A", "D"]);
   });

   it("B, shift-B, Delete leaves A, C and D", async () => {
      const { click, del } = await setup();
      click(1);
      click(1, shift);
      await del(["A", "C", "D"]);
   });

   it("A, shift-C, Delete leaves D", async () => {
      const { click, del } = await setup();
      click(0);
      click(2, shift);
      await del(["D"]);
   });

   it("deleteAction ignores duplicate selected indexes", async () => {
      const { comp, del } = await setup();
      comp.selectedActions = [1, 2, 2];
      await del(["A", "D"]);
   });
});
