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
 * DatabasePhysicalModelComponent - Angular Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - onKeyDown: keyboard save path must honor the same guards as the toolbar
 *   Group 2 [Risk 3] - isDuplicateTableName: optional backend fields must not crash duplicate checks
 *   Group 3 [Risk 3] - refreshWarnings0: warning confirmation must persist warning state and callback ownership
 *   Group 4 [Risk 2] - showTreeContextMenu: context menu must keep the original DOM source event
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed Issue #75158):
 *
 *   Bug A - Ctrl+S bypasses toolbar guards (Group 1):
 *     The toolbar disables/hides save when not modified or join editing, but the keyboard handler always calls save().
 *
 *   Bug B - optional autoAliases crashes duplicate check (Group 2):
 *     Backend models can omit optional autoAliases, but isDuplicateTableName calls .some() unguarded.
 *
 *   Bug C - warning state assignment typo (Group 3):
 *     The canContinue confirmation path uses this.warning == data instead of this.warning = data.
 *
 *   Bug D - context menu source event is dropped (Group 4):
 *     showTreeContextMenu receives an object but assigns contextmenu.sourceEvent = event[0].
 *
 * KEY contracts:
 *   Keyboard save must not create a second path around visible toolbar guards.
 *   Duplicate detection must consider qualifiedName, alias, and selected auto aliases without crashing.
 *   Warning refresh is runtimeId-owned; empty runtimeId must be a no-op.
 */

import { CommonModule } from "@angular/common";
import { provideHttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { EMPTY, Subject, of } from "rxjs";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { DataModelNameChangeService } from "../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../services/folder-change.service";
import { DataPhysicalModelService } from "../../../services/data-physical-model.service";
import { PhysicalModelDefinition } from "../../../model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../../model/datasources/database/physical-model/physical-table-model";
import { PhysicalTableType } from "../../../model/datasources/database/physical-model/physical-table-type.enum";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DatabasePhysicalModelComponent } from "./database-physical-model.component";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

@Component({
   selector: "split-pane",
   template: "<ng-content></ng-content>",
})
class StubSplitPaneComponent {
   @Input() direction: string;
   @Input() sizes: number[];
   @Input() gutterSize: number;
   @Input() minSize: number;
   @Input() snapOffset: number;
   @Input() fullWidth: boolean;
   @Output() onDrag = new EventEmitter<void>();

   private currentSizes: number[] = [55, 45];

   getSizes(): number[] {
      return this.currentSizes;
   }

   setSizes(sizes: number[]): void {
      this.currentSizes = sizes;
   }

   collapse(index: number): void {
      this.currentSizes = index === 0 ? [0, 100] : [100, 0];
   }
}

function createTable(overrides: Partial<PhysicalTableModel> = {}): PhysicalTableModel {
   return {
      name: "Orders",
      catalog: null,
      schema: "PUBLIC",
      qualifiedName: "Orders",
      path: "SalesDB/Orders",
      alias: null,
      sql: null,
      type: PhysicalTableType.PHYSICAL,
      joins: [],
      autoAliases: [],
      autoAliasesEnabled: false,
      baseTable: false,
      ...overrides,
   };
}

function createPhysicalModel(overrides: Partial<PhysicalModelDefinition> = {}): PhysicalModelDefinition {
   return {
      name: "PhysicalModel",
      folder: "",
      description: "",
      tables: [],
      id: null,
      ...overrides,
   };
}

function createPhysicalService(model: PhysicalModelDefinition) {
   const modelChange = new Subject<boolean>();
   const onNotification = new Subject<any>();
   const onRefreshWarning = new Subject<string>();
   const onFullScreen = new Subject<boolean>();

   return {
      physicalModel: model,
      database: "SalesDB",
      parent: null,
      loadingModel: false,
      aliasValidators: [],
      aliasValidatorMessages: [],
      modelChange: modelChange.asObservable(),
      onNotification,
      onRefreshWarning,
      onFullScreen,
      refreshModel: vi.fn(() => Promise.resolve(null)),
      resetModel: vi.fn(),
      openPhysicalModel: vi.fn(() => of(model)),
      createPhysicalModel: vi.fn(() => of(model)),
      emitModelChange: vi.fn(),
   };
}

function keyEvent(ctrlKey: boolean, key: string): KeyboardEvent {
   return {
      ctrlKey,
      key,
      stopPropagation: vi.fn(),
      preventDefault: vi.fn(),
   } as unknown as KeyboardEvent;
}

async function renderPhysical(model: PhysicalModelDefinition = createPhysicalModel()) {
   server.use(
      http.delete("*/api/data/physicalmodel/destroy", () => MswHttpResponse.json({}))
   );

   const service = createPhysicalService(model);
   const router = { navigate: vi.fn() };
   const route = { paramMap: EMPTY };
   const contextmenu = {};
   const dropdownService = {
      open: vi.fn(() => ({ componentInstance: contextmenu })),
   };
   const result = await render(DatabasePhysicalModelComponent, {
      imports: [CommonModule, FormsModule],
      declarations: [StubSplitPaneComponent],
      providers: [
         provideHttpClient(),
         { provide: DataModelNameChangeService, useValue: { nameChangeObservable: EMPTY } },
         { provide: FolderChangeService, useValue: { emitFolderChange: vi.fn() } },
         { provide: DataPhysicalModelService, useValue: service },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: FixedDropdownService, useValue: dropdownService },
         { provide: ActivatedRoute, useValue: route },
         { provide: Router, useValue: router },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   result.fixture.componentInstance.tableTree = {
      selectedNodes: [],
      selectNode: vi.fn(),
   } as any;

   return { ...result, service, router, route, dropdownService, contextmenu };
}

// ---------------------------------------------------------------------------
// Group 1 - onKeyDown: toolbar guard parity [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabasePhysicalModelComponent - onKeyDown - save guard parity [Group 1, Risk 3]", () => {

   // Regression-sensitive: Ctrl+S is the keyboard equivalent of the toolbar save action.
   it("should call save, stop propagation, and prevent default for Ctrl+S when saving is allowed", async () => {
      const { fixture } = await renderPhysical();
      const comp = fixture.componentInstance;
      comp.isModified = true;
      comp.joinEditing = false;
      const saveSpy = vi.spyOn(comp, "save").mockImplementation(() => undefined);
      const event = keyEvent(true, "s");

      comp.onKeyDown(event);

      expect(saveSpy).toHaveBeenCalledTimes(1);
      expect(event.stopPropagation).toHaveBeenCalledTimes(1);
      expect(event.preventDefault).toHaveBeenCalledTimes(1);
   });

   // Regression-sensitive: keyboard path must not bypass the toolbar's [disabled]="!isModified" guard.
   // Risk Point/Contract: unmodified models should not send save requests from any path.
   it("should ignore Ctrl+S when the model is not modified", async () => {
      const { fixture } = await renderPhysical();
      const comp = fixture.componentInstance;
      comp.isModified = false;
      comp.joinEditing = false;
      const saveSpy = vi.spyOn(comp, "save").mockImplementation(() => undefined);
      const event = keyEvent(true, "s");

      comp.onKeyDown(event);

      expect(saveSpy).not.toHaveBeenCalled();
      expect(event.stopPropagation).not.toHaveBeenCalled();
      expect(event.preventDefault).not.toHaveBeenCalled();
   });

   // Regression-sensitive: join editor hides the toolbar save button, so Ctrl+S must not mutate the model in that mode.
   // Risk Point/Contract: hidden/disabled mouse action and keyboard action must share the same validation guard.
   it("should ignore Ctrl+S while join editing is active", async () => {
      const { fixture } = await renderPhysical();
      const comp = fixture.componentInstance;
      comp.isModified = true;
      comp.joinEditing = true;
      const saveSpy = vi.spyOn(comp, "save").mockImplementation(() => undefined);
      const event = keyEvent(true, "s");

      comp.onKeyDown(event);

      expect(saveSpy).not.toHaveBeenCalled();
      expect(event.stopPropagation).not.toHaveBeenCalled();
      expect(event.preventDefault).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 - isDuplicateTableName: optional fields [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabasePhysicalModelComponent - isDuplicateTableName - optional backend fields [Group 2, Risk 3]", () => {

   // Regression-sensitive: duplicate table names must still be caught before creating aliases or adding tables.
   it("should detect duplicates by qualifiedName, alias, and selected auto alias", async () => {
      const { fixture } = await renderPhysical(createPhysicalModel({
         tables: [
            createTable({ qualifiedName: "Orders" }),
            createTable({ qualifiedName: "Customers", alias: "CustomersAlias" }),
            createTable({
               qualifiedName: "Products",
               autoAliases: [{ alias: "ProductsAuto", selected: true } as any],
            }),
         ],
      }));
      const comp = fixture.componentInstance;

      expect(comp.isDuplicateTableName("Orders")).toBe(true);
      expect(comp.isDuplicateTableName("CustomersAlias")).toBe(true);
      expect(comp.isDuplicateTableName("ProductsAuto")).toBe(true);
      expect(comp.isDuplicateTableName("NotUsed")).toBe(false);
   });

   // Regression-sensitive: backend may omit optional autoAliases; duplicate checks must degrade to false, not crash.
   // Risk Point/Contract: optional array fields from the server require a null-safe check before .some().
   it("should not throw when a physical table omits autoAliases", async () => {
      const { fixture } = await renderPhysical(createPhysicalModel({
         tables: [
            createTable({ qualifiedName: "Orders", autoAliases: undefined }),
         ],
      }));
      const comp = fixture.componentInstance;

      expect(comp.isDuplicateTableName("Customers")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 - refreshWarnings0: callback/warning state [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabasePhysicalModelComponent - refreshWarnings0 - warning confirmation state [Group 3, Risk 3]", () => {

   // Regression-sensitive: empty runtime IDs occur before model creation; this must remain a no-op.
   it("should no-op when runtimeId is empty", async () => {
      const { fixture } = await renderPhysical();
      const comp = fixture.componentInstance;
      const callback = vi.fn();

      (comp as any).refreshWarnings0("", callback, true);

      expect(callback).not.toHaveBeenCalled();
      expect(comp.warning).toBeUndefined();
   });

   // Regression-sensitive: confirmed warnings must leave visible warning state after continuing.
   // Risk Point/Contract: the confirm path must assign this.warning, not compare it.
   it("should call callback and persist warning after user confirms a canContinue warning", async () => {
      const { fixture } = await renderPhysical();
      const comp = fixture.componentInstance;
      const warning = { message: "Join warning", canContinue: true };
      const callback = vi.fn();
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("ok" as any);

      server.use(
         http.get("*/api/data/physicalmodel/warnings/runtime1", () =>
            MswHttpResponse.json(warning))
      );

      (comp as any).refreshWarnings0("runtime1", callback, true);

      try {
         await waitFor(() => expect(callback).toHaveBeenCalledTimes(1));
         expect(comp.warning).toEqual(warning);
      }
      finally {
         confirmSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 4 - showTreeContextMenu: source event ownership [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabasePhysicalModelComponent - showTreeContextMenu - source event ownership [Group 4, Risk 2]", () => {

   // Regression-sensitive: downstream dropdown positioning/focus handling depends on the original DOM event.
   // Risk Point/Contract: input is an object {node, event}; the DOM event lives at event.event, not event[0].
   it("should pass the original DOM event into the opened context menu component", async () => {
      const { fixture, dropdownService, contextmenu } = await renderPhysical();
      const comp = fixture.componentInstance;
      const domEvent = new MouseEvent("contextmenu", { clientX: 10, clientY: 20 });
      const node = {
         type: "table",
         data: {
            selected: true,
            baseTable: false,
         },
      };

      comp.showTreeContextMenu({ node, event: domEvent } as any);

      expect(dropdownService.open).toHaveBeenCalledTimes(1);
      expect(contextmenu["sourceEvent"]).toBe(domEvent);
      expect(contextmenu["actions"].length).toBeGreaterThan(0);
   });
});
