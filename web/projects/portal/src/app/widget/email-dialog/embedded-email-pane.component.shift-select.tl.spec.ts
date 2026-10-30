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
 * EmbeddedEmailPane - shift-click range selection followed by Remove (Bug #79751).
 *
 * The shuffle-list stub used here projects [shuffleButtons] and [shuffleRight], so the real
 * recipient rows and the real Remove button are rendered and clicked.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { provideHttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { fireEvent, render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { IdentityType } from "../../../../../shared/data/identity-type";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ShuffleListComponent } from "../shuffle-list/shuffle-list.component";
import { IdentityTreeComponent } from "../identity-tree/identity-tree.component";
import { EnterClickDirective } from "../directive/enter-click.directive";
import { ScrollableTableDirective } from "../scrollable-table/scrollable-table.directive";
import { EmbeddedEmailPane } from "./embedded-email-pane.component";
import {
   EnterClickStub,
   IdentityTreeStub,
   MODAL_MOCK,
   makeEmailDialogModel,
   makeTreeNode,
   ScrollableTableStub,
} from "./embedded-email-pane.component.test-helpers";

@Component({
   selector: "w-shuffle-list",
   template: `<ng-content select="[shuffleButtons]"></ng-content>
              <ng-content select="[shuffleRight]"></ng-content>`,
   standalone: true
})
class RenderingShuffleListStub {}

beforeEach(() => {
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
});

afterEach(() => vi.restoreAllMocks());

async function renderPane(embeddedOnly: boolean) {
   const { fixture, container } = await render(EmbeddedEmailPane, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [provideHttpClient(), { provide: NgbModal, useValue: MODAL_MOCK }],
      importOverrides: [
         { replace: ShuffleListComponent, with: RenderingShuffleListStub },
         { replace: IdentityTreeComponent, with: IdentityTreeStub },
         { replace: EnterClickDirective, with: EnterClickStub },
         { replace: ScrollableTableDirective, with: ScrollableTableStub },
      ],
      componentInputs: {
         addresses: "",
         embeddedOnly,
         showRoot: true,
         model: makeEmailDialogModel(),
         emailForm: new UntypedFormGroup({}),
      },
   });
   const comp = fixture.componentInstance as EmbeddedEmailPane;
   const root = container as HTMLElement;

   return {
      comp, fixture,
      rows: () => Array.from(root.querySelectorAll<HTMLElement>(
         embeddedOnly ? "[role=listitem]" : "tbody tr")),
      remove: () => (root.querySelector(".remove_btn_id") as HTMLElement).click()
   };
}

function userNode(name: string): TreeNodeModel {
   return makeTreeNode({ label: name, type: String(IdentityType.USER), data: name + "@x" });
}

function groupNode(name: string, parentNode: string = "/"): TreeNodeModel {
   return makeTreeNode({
      label: name, type: String(IdentityType.GROUP), data: { parentNode }, leaf: false
   });
}

function addNodes(comp: EmbeddedEmailPane, ...nodes: TreeNodeModel[]) {
   comp.selectedNodes = nodes;
   comp.addIdentities();
}

const names = (comp: EmbeddedEmailPane) => comp.addedIdentities.map((i) => i.identityID.name);
const selectedNames = (comp: EmbeddedEmailPane) =>
   comp.selectedAddedIdentities.map((i) => i.identityID.name);

describe.each([true, false])("shift-click range then Remove (embeddedOnly=%s)", (embeddedOnly) => {
   const NAMES = ["uA", "uB", "uC", "uD"];

   async function setup() {
      const ctx = await renderPane(embeddedOnly);
      addNodes(ctx.comp, ...NAMES.map(userNode));
      ctx.fixture.detectChanges();
      expect(ctx.rows()).toHaveLength(NAMES.length);
      return ctx;
   }

   it("click A, shift-click C, Remove removes only A, B and C", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[0]);
      fireEvent.click(rows()[2], { shiftKey: true });
      expect(selectedNames(comp)).toEqual(["uA", "uB", "uC"]);
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uD"]);
      expect(rows()).toHaveLength(1);

      if(!embeddedOnly) {
         expect(comp.addedEmails).toEqual(["uD@x"]);
      }
   });

   it("click B, shift-click C leaves A and D", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[1]);
      fireEvent.click(rows()[2], { shiftKey: true });
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uA", "uD"]);

      if(!embeddedOnly) {
         expect(comp.addedEmails).toEqual(["uA@x", "uD@x"]);
      }
   });

   it("range ending at the last row, anchor not on the first row, keeps the first row", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[1]);
      fireEvent.click(rows()[3], { shiftKey: true });
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uA"]);
   });

   it("range over the whole list removes everything", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[0]);
      fireEvent.click(rows()[3], { shiftKey: true });
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual([]);
   });

   it("shift-click on the anchor row itself does not duplicate it or remove another row", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[1]);
      fireEvent.click(rows()[1], { shiftKey: true });
      expect(comp.selectedAddedIdentities).toHaveLength(1);
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uA", "uC", "uD"]);
   });

   it("chained shift-clicks keep the selection free of duplicates", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[0]);
      fireEvent.click(rows()[1], { shiftKey: true });
      fireEvent.click(rows()[2], { shiftKey: true });
      expect(selectedNames(comp)).toEqual(["uA", "uB", "uC"]);
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uD"]);
   });

   it("ctrl-click selection removes exactly the selected rows", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[0]);
      fireEvent.click(rows()[2], { ctrlKey: true });
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uB", "uD"]);
   });

   it("ctrl-click after a shift range deselects the row (no leftover duplicate)", async () => {
      const { comp, rows } = await setup();
      fireEvent.click(rows()[1]);
      fireEvent.click(rows()[2], { shiftKey: true });
      fireEvent.click(rows()[1], { ctrlKey: true });
      expect(selectedNames(comp)).toEqual(["uC"]);
   });

   it("upward shift-click keeps its behavior and loses no data", async () => {
      const { comp, fixture, rows, remove } = await setup();
      fireEvent.click(rows()[2]);
      fireEvent.click(rows()[0], { shiftKey: true });
      expect(selectedNames(comp)).toEqual(["uC", "uA"]);
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uB", "uD"]);
   });
});

describe("Remove with a stale selection entry", () => {
   it("a selected subgroup dropped by adding its parent group does not remove another row", async () => {
      const { comp, fixture, rows, remove } = await renderPane(true);
      addNodes(comp, groupNode("g2", "g1"), userNode("uZ"));
      fixture.detectChanges();
      fireEvent.click(rows().find((r) => r.textContent.includes("g2")));
      expect(selectedNames(comp)).toEqual(["g2"]);

      addNodes(comp, groupNode("g1"));
      fixture.detectChanges();
      expect(names(comp).sort()).toEqual(["g1", "uZ"]);

      remove();
      fixture.detectChanges();
      expect(names(comp).sort()).toEqual(["g1", "uZ"]);
   });
});

describe("group removal in embedded mode", () => {
   it("removing a group leaves the user rows and the email list intact", async () => {
      const { comp, fixture, rows, remove } = await renderPane(true);
      addNodes(comp, groupNode("gA"), userNode("uB"));
      fixture.detectChanges();
      expect(comp.addedEmails).toEqual(["uB@x"]);

      fireEvent.click(rows().find((r) => r.textContent.includes("gA")));
      remove();
      fixture.detectChanges();
      expect(names(comp)).toEqual(["uB"]);
      // addedEmails is not index-aligned with addedIdentities in embeddedOnly mode
      expect(comp.addedEmails).toEqual(["uB@x"]);
   });
});
