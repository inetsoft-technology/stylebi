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
 * ComposerSelectionContainerChildren — Pass 2: risk tests
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1] — moveAssembly: emits onMove with the provided event payload
 *
 * Out of scope:
 *   All other methods covered in the P1 spec file
 *   (composer-selection-container-children.component.interaction.tl.spec.ts).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComposerSelectionContainerChildren } from "./composer-selection-container-children.component";
import { VSSelectionContainerChildren } from "../../../../../vsobjects/objects/selection/vs-selection-container-children.component";
import { ViewsheetClientService } from "../../../../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../../../../vsobjects/action/assembly-action-factory.service";
import { SelectionContainerChildrenService } from "../../../../../vsobjects/objects/selection/services/selection-container-children.service";
import { ContextProvider } from "../../../../../vsobjects/context-provider.service";
import { VSTrapService } from "../../../../../vsobjects/util/vs-trap.service";
import { ComposerObjectService } from "../../composer-object.service";
import { DomService } from "../../../../../widget/dom-service/dom.service";
import { DragService } from "../../../../../widget/services/drag.service";
import { ComposerVsSearchService } from "../../composer-vs-search.service";
import { VSObjectModel } from "../../../../../vsobjects/model/vs-object-model";

const domServiceMock = {
   requestRead: (cb: () => void) => { cb(); return 0; },
   requestWrite: (cb: () => void) => { cb(); return 0; },
};

async function renderComponent() {
   // Spy on the base class — child class binding can still be mid-init under ESM order.
   vi.spyOn(VSSelectionContainerChildren.prototype as any, "getBodyHeight").mockReturnValue(120);

   const { fixture } = await render(ComposerSelectionContainerChildren, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ViewsheetClientService, useValue: {
            sendEvent: vi.fn(), runtimeId: "vs-test",
            addMessageListener: vi.fn(), removeMessageListener: vi.fn(),
         }},
         { provide: AssemblyActionFactory, useValue: {
            createActions: vi.fn().mockReturnValue([]),
            createCurrentSelectionActions: vi.fn().mockReturnValue([]),
         }},
         { provide: SelectionContainerChildrenService, useValue: {
            dragModelSubject: new Subject<any>(),
            onChildUpdate: new Subject<any>(),
            onChildModelUpdate: new Subject<any>(),
            pushModel: vi.fn(),
            childWithBorder: -1,
            childDragModel: { dragging: false },
         }},
         { provide: ContextProvider, useValue: { viewer: false, preview: false } },
         { provide: VSTrapService, useValue: { checkTrap: vi.fn() } },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: ComposerObjectService, useValue: { getObjectType: vi.fn() } },
         { provide: DomService, useValue: domServiceMock },
         { provide: DragService, useValue: { getDragData: vi.fn().mockReturnValue({}) } },
         { provide: ComposerVsSearchService, useValue: {
            focusChange: vi.fn().mockReturnValue(new Subject<any>()),
         }},
      ],
      componentProperties: {
         viewsheet: {
            clearFocusedAssemblies: vi.fn(),
            selectAssembly: vi.fn(),
            isAssemblyFocused: vi.fn().mockReturnValue(false),
         } as any,
         // Required: template binds vsObject.objectFormat.left unconditionally on line 20 of the HTML.
         vsObject: {
            absoluteName: "Container1",
            objectType: "VSSelectionContainer",
            objectFormat: {
               top: 10, left: 0, width: 200, height: 150,
               border: { top: null, bottom: null, left: null, right: null },
            },
            titleFormat: { height: 30 },
            outerSelections: [],
            childObjects: [],
            dataRowHeight: 20,
            childrenNames: [],
         } as any,
      },
   });
   return fixture.componentInstance as ComposerSelectionContainerChildren;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: moveAssembly [Risk 1]
// ---------------------------------------------------------------------------

describe("ComposerSelectionContainerChildren — moveAssembly", () => {
   it("should emit onMove with the provided event payload", async () => {
      const comp = await renderComponent();
      const emitted: any[] = [];
      comp.onMove.subscribe(v => emitted.push(v));

      const payload = { event: new MouseEvent("mousemove"), model: {} as VSObjectModel };
      comp.moveAssembly(payload);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(payload);
   });

   it("should not emit onResize when moveAssembly is called", async () => {
      const comp = await renderComponent();
      const emitted: any[] = [];
      comp.onResize.subscribe(v => emitted.push(v));

      comp.moveAssembly({ event: {}, model: {} as VSObjectModel });

      expect(emitted).toHaveLength(0);
   });
});
