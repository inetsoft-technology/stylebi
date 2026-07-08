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
 * LogicalModelAttributeDialog - single-pass (+race + memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit: POST payload, tablesRoot assignment, parent defaulting
 *   Group 2 [Risk 2] - newParent setter after init toggles parentControl enabled state
 *   Group 3 [Risk 3] - loadTable success schedules selectColumns through the async callback bridge
 *   Group 4 [Risk 2] - selectColumns re-selects current parent attributes through tree API
 *   Group 5 [Risk 2] - entityChange switches parent and re-runs selection for the new entity
 *   Group 6 [Risk 1] - select() stores exactly the selected nodes array
 *   Group 7 [Risk 3] - ok() emits current entity and only enabled leaf attributes
 *   Group 8 [Risk 1] - cancel() emits "cancel"
 *   Group 9 [Risk 3] - Bug #75599 (fixed): post-destroy loadTable callback no longer runs
 *
 * Fixed bugs:
 *   Bug #75599 - loadTable post-destroy leak (Group 9): loadTable() schedules
 *     `setTimeout(() => this.selectColumns())` after the successful POST callback, and the POST
 *     subscription itself was never stored. The component previously had no ngOnDestroy cleanup,
 *     so selectColumns() could still run after fixture.destroy(). Fixed by implementing
 *     OnDestroy, storing the HTTP Subscription and the setTimeout handle, and clearing both in
 *     ngOnDestroy().
 *
 * Out of scope:
 *   Template focus behavior in ngAfterViewInit - the component calls
 *     `this.selectFocus.nativeElement.focus()`; jsdom focus integration is not the target of this
 *     file and does not affect the attribute-selection or commit contracts covered here.
 *
 * Mocking strategy:
 *   - HttpClient is injected directly, so render helper uses provideHttpClient() + MSW.
 *   - PhysicalTableTreeComponent is import-overridden with a stub that records exact calls.
 *   - ModalHeaderComponent is import-overridden with a minimal standalone stub.
 */

import { Component, EventEmitter, Input, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { EntityModel } from "../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { PhysicalTableTreeComponent } from "./physical-table-tree/physical-table-tree.component";
import { ModalHeaderComponent } from "../../../../../../../widget/modal-header/modal-header.component";
import { LogicalModelAttributeDialog } from "./logical-model-attribute-dialog.component";

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderStub {
   @Input() title: string;
   @Input() cshid: string;
   @Output() onCancel = new EventEmitter<void>();
}

@Component({ selector: "physical-table-tree", template: "", standalone: true })
class PhysicalTableTreeStub {
   @Input() root: TreeNodeModel;
   @Output() nodesSelected = new EventEmitter<TreeNodeModel[]>();
   removeLockedNodes = vi.fn();
   selectAndExpandToNode = vi.fn();
}

interface RenderOpts {
   entities?: EntityModel[];
   parent?: number;
   databaseName?: string;
   physicalModelName?: string;
   logicalModelName?: string;
   parentName?: string;
   additional?: string;
   newParent?: boolean;
}

function makeAttribute(overrides: Partial<AttributeModel> = {}): AttributeModel {
   return {
      name: "attr",
      table: "Orders",
      qualifiedName: "Orders.attr",
      dataType: "string" as any,
      oldName: "attr",
      baseElement: false,
      elementType: "attribute",
      visible: true,
      ...overrides,
   } as AttributeModel;
}

function makeEntity(overrides: Partial<EntityModel> = {}): EntityModel {
   return {
      name: "Entity",
      oldName: "Entity",
      attributes: [],
      baseElement: false,
      elementType: "entity",
      visible: true,
      expanded: false,
      selected: false,
      ...overrides,
   } as EntityModel;
}

function makeTreeRoot(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "root",
      leaf: false,
      expanded: true,
      children: [
         {
            label: "Orders",
            leaf: false,
            expanded: false,
            children: [
               {
                  label: "orderId",
                  leaf: true,
                  data: makeAttribute({
                     name: "orderId",
                     oldName: "orderId",
                     table: "Orders",
                     qualifiedName: "Orders.orderId",
                  }),
               },
               {
                  label: "amount",
                  leaf: true,
                  data: makeAttribute({
                     name: "amount",
                     oldName: "amount",
                     table: "Orders",
                     qualifiedName: "Orders.amount",
                  }),
               },
            ],
         },
         {
            label: "Customers",
            leaf: false,
            expanded: false,
            children: [
               {
                  label: "customerId",
                  leaf: true,
                  data: makeAttribute({
                     name: "customerId",
                     oldName: "customerId",
                     table: "Customers",
                     qualifiedName: "Customers.customerId",
                  }),
               },
            ],
         },
      ],
      ...overrides,
   };
}

function makeEntities(): EntityModel[] {
   return [
      makeEntity({
         name: "OrdersEntity",
         oldName: "OrdersEntity",
         attributes: [
            makeAttribute({
               name: "orderId",
               oldName: "orderId",
               table: "Orders",
               qualifiedName: "Orders.orderId",
            }),
            makeAttribute({
               name: "amount",
               oldName: "amount",
               table: "Orders",
               qualifiedName: "Orders.amount",
            }),
         ],
      }),
      makeEntity({
         name: "CustomersEntity",
         oldName: "CustomersEntity",
         attributes: [
            makeAttribute({
               name: "customerId",
               oldName: "customerId",
               table: "Customers",
               qualifiedName: "Customers.customerId",
            }),
         ],
      }),
   ];
}

async function renderComponent(opts: RenderOpts = {}) {
   const onCommitSpy = vi.fn();
   const onCancelSpy = vi.fn();

   const { fixture } = await render(LogicalModelAttributeDialog, {
      providers: [provideHttpClient()],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: PhysicalTableTreeComponent, with: PhysicalTableTreeStub },
      ],
      componentInputs: {
         entities: opts.entities ?? makeEntities(),
         parent: opts.parent ?? -1,
         databaseName: opts.databaseName ?? "test-db",
         physicalModelName: opts.physicalModelName ?? "physical-model",
         logicalModelName: opts.logicalModelName ?? "logical-model",
         parentName: opts.parentName ?? "parent-folder",
         additional: opts.additional ?? "connection-1",
         newParent: opts.newParent ?? false,
      },
      on: {
         onCommit: onCommitSpy,
         onCancel: onCancelSpy,
      },
   });

   return {
      fixture,
      comp: fixture.componentInstance as LogicalModelAttributeDialog,
      onCommitSpy,
      onCancelSpy,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - ngOnInit", () => {
   it("should POST the exact model payload", async () => {
      let requestBody: any;
      const responseRoot = makeTreeRoot();

      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", async ({ request }) => {
            requestBody = await request.json();
            return MswHttpResponse.json(responseRoot);
         })
      );

      const { comp } = await renderComponent({
         databaseName: "db-A",
         physicalModelName: "pm-A",
         logicalModelName: "lm-A",
         parentName: "folder-A",
         additional: "conn-A",
      });

      await waitFor(() => expect(requestBody).toEqual({
         datasource: "db-A",
         physicalName: "pm-A",
         logicalName: "lm-A",
         parent: "folder-A",
         additional: "conn-A",
      }));
   });

   it("should store the returned tables root after the POST resolves", async () => {
      const responseRoot = makeTreeRoot();

      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", () =>
            MswHttpResponse.json(responseRoot)
         )
      );

      const { comp } = await renderComponent();

      await waitFor(() => expect(comp.tablesRoot).toEqual(responseRoot));
   });

   it("should default parentControl to 0 when input parent is -1 and entities exist", async () => {
      const { comp } = await renderComponent({
         entities: makeEntities(),
         parent: -1,
      });

      expect(comp.parentControl.value).toBe(0);
   });
});

describe("Group 2 - newParent setter", () => {
   it("should disable the parent control when newParent is false after form init", async () => {
      const { comp } = await renderComponent({ newParent: true });

      comp.newParent = false;

      expect(comp.parentControl.disabled).toBe(true);
   });

   it("should enable the parent control when newParent is true after form init", async () => {
      const { comp } = await renderComponent({ newParent: false });

      comp.newParent = true;

      expect(comp.parentControl.enabled).toBe(true);
   });
});

describe("Group 3 - loadTable success schedules selectColumns", () => {
   it("should invoke selectColumns after the successful POST callback resolves", async () => {
      let resolveRequest: ((response: any) => void) | undefined;

      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", () =>
            new Promise<any>((resolve) => {
               resolveRequest = resolve;
            })
         )
      );

      const { comp } = await renderComponent({
         entities: makeEntities(),
         parent: 0,
      });
      const selectSpy = vi.spyOn(comp, "selectColumns").mockImplementation(() => {});

      try {
         resolveRequest!(MswHttpResponse.json(makeTreeRoot()));

         await waitFor(() => expect(selectSpy).toHaveBeenCalledTimes(1));
      } finally {
         selectSpy.mockRestore();
      }
   });
});

// WHY private bypass: ATL + importOverrides does not reliably populate the `tree` ViewChild for
// this child stub. These tests target selectColumns/entityChange behavior, so they inject a stub
// tree instance directly instead of relying on ViewChild wiring.
describe("Group 4 - selectColumns re-selects existing attributes", () => {
   it("should remove locked nodes and re-select every attribute of the current parent entity", async () => {
      const responseRoot = makeTreeRoot();

      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", () =>
            MswHttpResponse.json(responseRoot)
         )
      );

      const { comp } = await renderComponent({
         entities: makeEntities(),
         parent: 0,
      });
      const tree = new PhysicalTableTreeStub();

      await waitFor(() => expect(comp.tablesRoot).toEqual(responseRoot));
      comp.tree = tree as any;
      comp.selectColumns();

      expect(tree.removeLockedNodes).toHaveBeenCalledTimes(1);
      expect(tree.selectAndExpandToNode).toHaveBeenNthCalledWith(1, {
         label: "orderId",
         data: makeAttribute({
            name: "orderId",
            oldName: "orderId",
            table: "Orders",
            qualifiedName: "Orders.orderId",
         }),
         leaf: true,
         disabled: true,
      });
      expect(tree.selectAndExpandToNode).toHaveBeenNthCalledWith(2, {
         label: "amount",
         data: makeAttribute({
            name: "amount",
            oldName: "amount",
            table: "Orders",
            qualifiedName: "Orders.amount",
         }),
         leaf: true,
         disabled: true,
      });
   });
});

// WHY private bypass: ATL + importOverrides does not reliably populate the `tree` ViewChild for
// this child stub. These tests target selectColumns/entityChange behavior, so they inject a stub
// tree instance directly instead of relying on ViewChild wiring.
describe("Group 5 - entityChange", () => {
   it("should update parent and re-select the new parent entity attributes", async () => {
      const { comp } = await renderComponent({
         entities: makeEntities(),
         parent: 0,
      });
      const tree = new PhysicalTableTreeStub();
      comp.tree = tree as any;

      comp.entityChange(1);

      expect(comp.parent).toBe(1);
      expect(tree.removeLockedNodes).toHaveBeenCalledTimes(1);
      expect(tree.selectAndExpandToNode).toHaveBeenCalledWith({
         label: "customerId",
         data: makeAttribute({
            name: "customerId",
            oldName: "customerId",
            table: "Customers",
            qualifiedName: "Customers.customerId",
         }),
         leaf: true,
         disabled: true,
      });
   });
});

describe("Group 6 - select()", () => {
   it("should store exactly the selected nodes array", async () => {
      const { comp } = await renderComponent();
      const nodes: TreeNodeModel[] = [
         {
            label: "A",
            leaf: true,
            data: makeAttribute({
               name: "A",
               oldName: "A",
               table: "Orders",
               qualifiedName: "Orders.A",
            }),
         },
         {
            label: "B",
            leaf: true,
            data: makeAttribute({
               name: "B",
               oldName: "B",
               table: "Orders",
               qualifiedName: "Orders.B",
            }),
         },
      ];

      comp.select(nodes);

      expect(comp.selectedColumns).toEqual(nodes);
   });
});

describe("Group 7 - ok()", () => {
   it("should emit the current parent entity and only enabled leaf attribute nodes", async () => {
      const entities = makeEntities();
      const { comp, onCommitSpy } = await renderComponent({
         entities,
         parent: 1,
      });
      const enabledLeaf = {
         label: "customerId",
         leaf: true,
         disabled: false,
         data: makeAttribute({
            name: "customerId",
            oldName: "customerId",
            table: "Customers",
            qualifiedName: "Customers.customerId",
         }),
      } as TreeNodeModel;
      const disabledLeaf = {
         label: "amount",
         leaf: true,
         disabled: true,
         data: makeAttribute({
            name: "amount",
            oldName: "amount",
            table: "Orders",
            qualifiedName: "Orders.amount",
         }),
      } as TreeNodeModel;
      const nonLeaf = {
         label: "Customers",
         leaf: false,
         disabled: false,
         children: [],
         data: { path: "Customers" },
      } as TreeNodeModel;
      comp.parentControl.setValue(1);
      comp.select([enabledLeaf, disabledLeaf, nonLeaf]);

      comp.ok();

      expect(onCommitSpy).toHaveBeenCalledWith({
         entity: entities[1],
         attributes: [makeAttribute({
            name: "customerId",
            oldName: "customerId",
            table: "Customers",
            qualifiedName: "Customers.customerId",
         })],
      });
   });
});

describe("Group 8 - cancel()", () => {
   it("should emit 'cancel'", async () => {
      const { comp, onCancelSpy } = await renderComponent();

      comp.cancel();

      expect(onCancelSpy).toHaveBeenCalledWith("cancel");
   });
});

describe("Group 9 - Bug #75599 (fixed): post-destroy loadTable callback", () => {
   // Bug #75599 (fixed): the successful POST callback used to schedule
   // `setTimeout(() => this.selectColumns())` without any destroy cleanup, so selectColumns()
   // would still run after fixture.destroy(). The component now implements OnDestroy, storing
   // the HTTP Subscription and the setTimeout handle and clearing both in ngOnDestroy(), so
   // selectColumns() is never invoked after destroy.
   it("should not run selectColumns after fixture.destroy()", async () => {
      let resolveRequest: ((response: any) => void) | undefined;
      server.use(
         http.post("*/api/data/logicalModel/tables/nodes", () =>
            new Promise<any>((resolve) => {
               resolveRequest = resolve;
            })
         )
      );

      const { comp, fixture } = await renderComponent();
      const selectSpy = vi.spyOn(comp, "selectColumns").mockImplementation(() => {});

      try {
         fixture.destroy();
         resolveRequest!(MswHttpResponse.json(makeTreeRoot()));

         // Give the resolved response and any (would-be) timers a chance to run. There is no
         // affirmative event to wait for here - a fixed settle delay is used to prove absence.
         await new Promise<void>(resolve => setTimeout(resolve, 0));
         await new Promise<void>(resolve => setTimeout(resolve, 0));

         expect(selectSpy).not.toHaveBeenCalled();
      } finally {
         selectSpy.mockRestore();
      }
   });
});
