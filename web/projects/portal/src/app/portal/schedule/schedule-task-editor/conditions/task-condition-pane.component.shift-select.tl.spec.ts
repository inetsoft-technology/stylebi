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
 * TaskConditionPane - shift-click range selection followed by Delete (Bug #80366).
 *
 * The selection is produced by the real EditableTableComponent.selectItem() (the pane binds
 * [(selectedItems)] two-way to selectedConditions) and then deleted with the real
 * deleteCondition().
 */

import { Subject } from "rxjs";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { EditableTableComponent } from "../editable-table/editable-table.component";
import {
   makeDailyCondition, makeModel, modalMock, renderTaskConditionPane, resetMocks
} from "./task-condition-pane.test-helpers";

let fixtureToDestroy: { destroy(): void } | null = null;

beforeEach(() => {
   resetMocks();
   // ComponentTool suppresses a repeated identical message shown within a short time
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});
afterEach(() => { fixtureToDestroy?.destroy(); fixtureToDestroy = null; });

const plain = { ctrlKey: false, shiftKey: false } as MouseEvent;
const shift = { ctrlKey: false, shiftKey: true } as MouseEvent;

async function setup() {
   const model = makeModel({
      conditions: ["A", "B", "C", "D"].map((label) => makeDailyCondition({ label }))
   });
   const { comp, fixture } = await renderTaskConditionPane({ model });
   fixtureToDestroy = fixture;
   const table = new EditableTableComponent();
   table.items = ["A", "B", "C", "D"];
   table.selectedItems = [];
   table.selectedItemsChange.subscribe((v: number[]) => comp.selectedConditions = v);

   const click = (i: number, event: MouseEvent = plain) => table.selectItem(event, i);
   const del = async (expected: string[]) => {
      modalMock.open.mockImplementationOnce(() => ({
         result: Promise.resolve("ok"),
         componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      comp.deleteCondition();
      // let the confirm dialog promise resolve (no waitFor: its MutationObserver loops with
      // the pane's per-check array getters)
      await new Promise((resolve) => setTimeout(resolve));
      expect(comp.model.conditions.map((c) => c.label)).toEqual(expected);
   };

   return { comp, click, del };
}

describe("TaskConditionPane shift-click range then Delete", () => {
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

   it("deleteCondition ignores duplicate selected indexes", async () => {
      const { comp, del } = await setup();
      comp.selectedConditions = [1, 2, 2];
      await del(["A", "D"]);
   });
});
