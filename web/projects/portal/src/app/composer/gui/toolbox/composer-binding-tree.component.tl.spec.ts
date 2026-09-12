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
 * ComposerBindingTree — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit subscription: treeService.bindingTreeChanged() → emits bindingTreeChanged
 *   Group 2 [Risk 3] — ngOnDestroy: unsubscribes so no emit fires after destroy
 *   Group 3 [Risk 2] — hasMenuFunction: returns a function that calls hasMenu
 *
 * Out of scope:
 *   hasMenu() — instantiates VSBindingTreeActions with complex deps; result depends on
 *     runtime action configuration, not testable at unit level without full Angular DI.
 *   openBindingTreeContextmenu() — opens FixedDropdownService overlay; integration-only.
 *   sendRemoveColumnEvent() — complex DnD+WebSocket flow; integration-only.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComposerBindingTree } from "./composer-binding-tree.component";
import { BindingTreeService } from "../../../binding/widget/binding-tree/binding-tree.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ModelService } from "../../../widget/services/model.service";
import { ComposerObjectService } from "../vs/composer-object.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

function makeTreeServiceMock(subject = new Subject<TreeNodeModel>()) {
   return {
      bindingTreeChanged: vi.fn(() => subject.asObservable()),
   };
}

async function renderComponent(treeServiceMock = makeTreeServiceMock()) {
   const { fixture } = await render(ComposerBindingTree, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: BindingTreeService, useValue: treeServiceMock },
         { provide: FixedDropdownService, useValue: { open: vi.fn() } },
         { provide: NgbModal, useValue: {} },
         { provide: ModelService, useValue: { getModel: vi.fn() } },
         { provide: ComposerObjectService, useValue: { removeObjects: vi.fn() } },
      ],
   });
   return { comp: fixture.componentInstance as ComposerBindingTree, fixture };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit subscription [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerBindingTree — ngOnInit subscription", () => {
   // 🔁 Regression-sensitive: bindingTreeChanged must re-emit when treeService fires;
   //    breaking this means the parent composer pane never refreshes the field list.
   it("should emit bindingTreeChanged when treeService fires a new root node", async () => {
      const subject = new Subject<TreeNodeModel>();
      const treeServiceMock = makeTreeServiceMock(subject);
      const { comp } = await renderComponent(treeServiceMock);

      const emitted: TreeNodeModel[] = [];
      comp.bindingTreeChanged.subscribe(n => emitted.push(n));

      const node: TreeNodeModel = { label: "root", data: {}, children: [], leaf: false };
      subject.next(node);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(node);
   });

   it("should subscribe exactly once on init", async () => {
      const treeServiceMock = makeTreeServiceMock();
      await renderComponent(treeServiceMock);
      expect(treeServiceMock.bindingTreeChanged).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnDestroy — subscription cleanup [Risk 3]
// ---------------------------------------------------------------------------

describe("ComposerBindingTree — ngOnDestroy memory leak", () => {
   // 🔁 Regression-sensitive: if ngOnDestroy doesn't unsubscribe, the treeService can emit
   //    after the composer is closed and attempt to write to a destroyed component.
   it("should not emit bindingTreeChanged after the component is destroyed", async () => {
      const subject = new Subject<TreeNodeModel>();
      const treeServiceMock = makeTreeServiceMock(subject);
      const { comp, fixture } = await renderComponent(treeServiceMock);

      const emitted: TreeNodeModel[] = [];
      comp.bindingTreeChanged.subscribe(n => emitted.push(n));

      fixture.destroy();

      const node: TreeNodeModel = { label: "after-destroy", data: {}, children: [], leaf: false };
      subject.next(node);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: hasMenuFunction [Risk 2]
// ---------------------------------------------------------------------------

describe("ComposerBindingTree — hasMenuFunction", () => {
   it("should return a function", async () => {
      const { comp } = await renderComponent();
      const fn = comp.hasMenuFunction();
      expect(typeof fn).toBe("function");
   });
});
