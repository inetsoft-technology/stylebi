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
 * PhysicalTableJoinsComponent — Single Pass (+ Memory Leak)
 *
 * Coverage plan:
 *   Group 1  — table setter: builds foreignTableRoot.children; resets selection; calls highlightConnections(null)
 *   Group 2  — ngOnDestroy: calls highlightConnections(null)
 *   Group 3  — selectNode: selection type handling; calls highlightConnections with hInfos
 *   Group 4  — isDeleteDisabled: boundary conditions
 *   Group 5  — isEditJoinDisabled: boundary conditions
 *   Group 6  — editJoinModel getter: single join → join; empty or multiple → null
 *   Group 7  — ngDoCheck / updateForeignTables: detects join array changes via IterableDiffer
 */

import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { vi } from "vitest";

import { HighlightInfo, DataPhysicalModelService } from "../../../../../services/data-physical-model.service";
import { JoinModel } from "../../../../../model/datasources/database/physical-model/join-model";
import { JoinType } from "../../../../../model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../../../../model/datasources/database/physical-model/merging-rule.enum";
import { Cardinality } from "../../../../../model/datasources/database/physical-model/cardinality.enum";
import { PhysicalModelDefinition } from "../../../../../model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../../../../model/datasources/database/physical-model/physical-table-model";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../../../../../widget/tree/tree.component";
import { AddJoinDialog } from "./add-join-dialog/add-join-dialog.component";
import { EditJoinDialog } from "./edit-join-dialog/edit-join-dialog.component";
import { PhysicalTableJoinsComponent } from "./physical-table-joins.component";

// ---------------------------------------------------------------------------
// Stubs — prevent heavy DI chains of child components
// ---------------------------------------------------------------------------

@Component({ selector: "tree-component", template: "", standalone: true })
class TreeComponentStub {}

@Component({ selector: "add-join-dialog", template: "", standalone: true })
class AddJoinDialogStub {}

@Component({ selector: "edit-join-dialog", template: "", standalone: true })
class EditJoinDialogStub {}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeJoin(overrides: Partial<JoinModel> = {}): JoinModel {
   return {
      type: JoinType.EQUAL,
      orderPriority: 0,
      weak: false,
      mergingRule: MergingRule.AND,
      cardinality: Cardinality.ONE_TO_ONE,
      table: "Orders",
      column: "customerId",
      foreignTable: "Customers",
      foreignColumn: "id",
      baseJoin: false,
      ...overrides,
   };
}

function makeTable(overrides: Partial<PhysicalTableModel> = {}): PhysicalTableModel {
   return {
      name: "Orders",
      catalog: "",
      schema: "",
      qualifiedName: "Orders",
      path: "Orders",
      alias: "",
      sql: "",
      type: null,
      joins: [],
      baseTable: true,
      ...overrides,
   };
}

function makePhysicalModel(overrides: Partial<PhysicalModelDefinition> = {}): PhysicalModelDefinition {
   return {
      name: "TestModel",
      folder: "",
      tables: [],
      id: "model-1",
      description: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Mock state
// ---------------------------------------------------------------------------

let physicalModelServiceMock: {
   highlightConnections: ReturnType<typeof vi.fn>;
};

function resetMocks() {
   physicalModelServiceMock = {
      highlightConnections: vi.fn(),
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderJoins(
   tableOverrides: Partial<PhysicalTableModel> = {},
   componentInputs: Record<string, any> = {}
) {
   const table = makeTable(tableOverrides);
   const physicalModel = makePhysicalModel();

   const result = await render(PhysicalTableJoinsComponent, {
      inputs: {
         table,
         physicalModel,
         databaseName: "testDb",
         ...componentInputs,
      },
      imports: [HttpClientTestingModule],
      providers: [
         { provide: DataPhysicalModelService, useValue: physicalModelServiceMock },
         { provide: NgbModal, useValue: { open: vi.fn() } },
      ],
      importOverrides: [
         { replace: TreeComponent, with: TreeComponentStub },
         { replace: AddJoinDialog, with: AddJoinDialogStub },
         { replace: EditJoinDialog, with: EditJoinDialogStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return {
      comp: result.fixture.componentInstance as PhysicalTableJoinsComponent,
      fixture: result.fixture,
      table,
      physicalModel,
   };
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

beforeEach(() => {
   resetMocks();
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 — table setter
// ===========================================================================

describe("Group 1 — table setter / updateForeignTables", () => {
   it("should start with empty foreignTableRoot.children when table has no joins", async () => {
      const { comp } = await renderJoins({ joins: [] });
      expect(comp.foreignTableRoot.children).toHaveLength(0);
   });

   it("should build one TABLE node per unique foreignTable in joins", async () => {
      const join1 = makeJoin({ foreignTable: "Customers", foreignColumn: "id", column: "customerId" });
      const join2 = makeJoin({ foreignTable: "Products", foreignColumn: "id", column: "productId" });
      const { comp } = await renderJoins({ joins: [join1, join2] });
      expect(comp.foreignTableRoot.children).toHaveLength(2);
      expect(comp.foreignTableRoot.children.map(c => c.label)).toContain("Customers");
      expect(comp.foreignTableRoot.children.map(c => c.label)).toContain("Products");
   });

   it("should group multiple joins under the same TABLE node when foreignTable is the same", async () => {
      const join1 = makeJoin({ column: "col1", foreignTable: "Customers", foreignColumn: "id1" });
      const join2 = makeJoin({ column: "col2", foreignTable: "Customers", foreignColumn: "id2" });
      const { comp } = await renderJoins({ joins: [join1, join2] });
      expect(comp.foreignTableRoot.children).toHaveLength(1);
      expect(comp.foreignTableRoot.children[0].children).toHaveLength(2);
   });

   it("should call highlightConnections(null) when table setter fires", async () => {
      await renderJoins({ joins: [] });
      expect(physicalModelServiceMock.highlightConnections).toHaveBeenCalledWith(null);
   });

   it("should reset selectedTableNodes and selectedJoins when table changes", async () => {
      const { comp, fixture } = await renderJoins({ joins: [] });
      comp.selectedJoins = [makeJoin()];
      comp.selectedTableNodes = [{ label: "T", children: [], leaf: false }];

      fixture.componentRef.setInput("table", makeTable({ name: "NewTable", joins: [] }));
      fixture.detectChanges();

      expect(comp.selectedJoins).toHaveLength(0);
      expect(comp.selectedTableNodes).toHaveLength(0);
   });
});

// ===========================================================================
// Group 2 — ngOnDestroy / memory-leak
// ===========================================================================

describe("Group 2 — ngOnDestroy / memory-leak", () => {
   it("should call highlightConnections(null) on destroy", async () => {
      const { fixture } = await renderJoins({ joins: [] });
      physicalModelServiceMock.highlightConnections.mockClear();
      fixture.destroy();
      expect(physicalModelServiceMock.highlightConnections).toHaveBeenCalledWith(null);
   });
});

// ===========================================================================
// Group 3 — selectNode
// ===========================================================================

describe("Group 3 — selectNode", () => {
   it("should populate selectedJoins when JOIN nodes are selected", async () => {
      const join1 = makeJoin({ column: "col1", foreignTable: "Customers", foreignColumn: "id" });
      const { comp } = await renderJoins({ joins: [join1] });

      const tableNode = comp.foreignTableRoot.children[0];
      const joinNode = tableNode.children[0];

      comp.selectNode([joinNode]);

      expect(comp.selectedJoins).toHaveLength(1);
      expect(comp.selectedJoins[0]).toBe(join1);
   });

   it("should populate selectedTableNodes when TABLE nodes are selected", async () => {
      const join1 = makeJoin({ foreignTable: "Customers", foreignColumn: "id", column: "col1" });
      const { comp } = await renderJoins({ joins: [join1] });

      const tableNode = comp.foreignTableRoot.children[0];
      comp.selectNode([tableNode]);

      expect(comp.selectedTableNodes).toHaveLength(1);
      expect(comp.selectedJoins).toHaveLength(0);
   });

   it("should call highlightConnections with hInfos for each selected JOIN", async () => {
      const join1 = makeJoin({ column: "col1", foreignTable: "Customers", foreignColumn: "id" });
      const { comp } = await renderJoins({ joins: [join1] });

      physicalModelServiceMock.highlightConnections.mockClear();
      const joinNode = comp.foreignTableRoot.children[0].children[0];
      comp.selectNode([joinNode]);

      expect(physicalModelServiceMock.highlightConnections).toHaveBeenCalledWith(
         expect.arrayContaining([
            expect.objectContaining<Partial<HighlightInfo>>({ sourceTable: "Orders", targetTable: "Customers" }),
         ])
      );
   });

   it("should reset when called with empty nodes array", async () => {
      const { comp } = await renderJoins({ joins: [makeJoin()] });
      comp.selectNode([]);
      // No selection changes when empty — selectedNodes becomes []
      expect(comp.selectedNodes).toHaveLength(0);
   });

   it("should reset joins when a mixed TABLE+JOIN selection resets type", async () => {
      const join1 = makeJoin({ column: "col1", foreignTable: "Customers", foreignColumn: "id" });
      const join2 = makeJoin({ column: "col2", foreignTable: "Customers", foreignColumn: "id2" });
      const { comp } = await renderJoins({ joins: [join1, join2] });

      const tableNode = comp.foreignTableRoot.children[0];
      const joinNode = tableNode.children[0];
      const joinNode2 = tableNode.children[1];

      // Select TABLE node first, then a JOIN — type changes reset the lists
      comp.selectNode([tableNode, joinNode]);
      // After mixed selection, second group (JOIN) takes precedence
      expect(comp.selectedJoins).toHaveLength(1);
   });
});

// ===========================================================================
// Group 4 — isDeleteDisabled
// ===========================================================================

describe("Group 4 — isDeleteDisabled", () => {
   it("should be true when disabled=true", async () => {
      const { comp } = await renderJoins({ joins: [] }, { disabled: true });
      expect(comp.isDeleteDisabled()).toBe(true);
   });

   it("should be true when selectedTableNodes is empty", async () => {
      const { comp } = await renderJoins({ joins: [] });
      // No nodes selected by default
      expect(comp.isDeleteDisabled()).toBe(true);
   });

   it("should be false when a non-base join is selected", async () => {
      const join1 = makeJoin({ baseJoin: false });
      const { comp } = await renderJoins({ joins: [join1] });

      const tableNode = comp.foreignTableRoot.children[0];
      const joinNode = tableNode.children[0];
      comp.selectNode([joinNode]);

      expect(comp.isDeleteDisabled()).toBe(false);
   });

   it("should be true when the only selected join is a base join", async () => {
      const join1 = makeJoin({ baseJoin: true });
      const { comp } = await renderJoins({ joins: [join1] });

      const tableNode = comp.foreignTableRoot.children[0];
      const joinNode = tableNode.children[0];
      comp.selectNode([joinNode]);

      expect(comp.isDeleteDisabled()).toBe(true);
   });
});

// ===========================================================================
// Group 5 — isEditJoinDisabled
// ===========================================================================

describe("Group 5 — isEditJoinDisabled", () => {
   it("should be true when disabled=true", async () => {
      const { comp } = await renderJoins({ joins: [] }, { disabled: true });
      expect(comp.isEditJoinDisabled()).toBe(true);
   });

   it("should be true when no join is selected", async () => {
      const { comp } = await renderJoins({ joins: [] });
      expect(comp.isEditJoinDisabled()).toBe(true);
   });

   it("should be false when exactly one non-base join is selected", async () => {
      const join1 = makeJoin({ baseJoin: false });
      const { comp } = await renderJoins({ joins: [join1] });

      const joinNode = comp.foreignTableRoot.children[0].children[0];
      comp.selectNode([joinNode]);

      expect(comp.isEditJoinDisabled()).toBe(false);
   });

   it("should be true when more than one join is selected", async () => {
      const join1 = makeJoin({ column: "col1", foreignTable: "Customers", foreignColumn: "id1", baseJoin: false });
      const join2 = makeJoin({ column: "col2", foreignTable: "Customers", foreignColumn: "id2", baseJoin: false });
      const { comp } = await renderJoins({ joins: [join1, join2] });

      const tableNode = comp.foreignTableRoot.children[0];
      comp.selectNode([tableNode.children[0], tableNode.children[1]]);

      expect(comp.isEditJoinDisabled()).toBe(true);
   });

   it("should be true when the selected join is a base join", async () => {
      const join1 = makeJoin({ baseJoin: true });
      const { comp } = await renderJoins({ joins: [join1] });

      const joinNode = comp.foreignTableRoot.children[0].children[0];
      comp.selectNode([joinNode]);

      expect(comp.isEditJoinDisabled()).toBe(true);
   });
});

// ===========================================================================
// Group 6 — editJoinModel getter
// ===========================================================================

describe("Group 6 — editJoinModel getter", () => {
   it("should return null when no joins are selected", async () => {
      const { comp } = await renderJoins({ joins: [] });
      expect(comp.editJoinModel).toBeNull();
   });

   it("should return the join when exactly one join is selected", async () => {
      const join1 = makeJoin();
      const { comp } = await renderJoins({ joins: [join1] });

      const joinNode = comp.foreignTableRoot.children[0].children[0];
      comp.selectNode([joinNode]);

      expect(comp.editJoinModel).toBe(join1);
   });

   it("should return null when two joins are selected", async () => {
      const join1 = makeJoin({ column: "col1", foreignTable: "Customers", foreignColumn: "id1" });
      const join2 = makeJoin({ column: "col2", foreignTable: "Customers", foreignColumn: "id2" });
      const { comp } = await renderJoins({ joins: [join1, join2] });

      const tableNode = comp.foreignTableRoot.children[0];
      comp.selectNode([tableNode.children[0], tableNode.children[1]]);

      expect(comp.editJoinModel).toBeNull();
   });
});

// ===========================================================================
// Group 7 — ngDoCheck / updateForeignTables re-run
// ===========================================================================

describe("Group 7 — ngDoCheck: IterableDiffer detects join array changes", () => {
   it("should rebuild foreignTableRoot when a join is pushed to table.joins", async () => {
      const { comp, fixture, table } = await renderJoins({ joins: [] });
      expect(comp.foreignTableRoot.children).toHaveLength(0);

      // Mutate the joins array (as the IterableDiffer tracks)
      table.joins.push(makeJoin({ foreignTable: "Customers", column: "col", foreignColumn: "id" }));
      fixture.detectChanges(); // triggers ngDoCheck

      expect(comp.foreignTableRoot.children).toHaveLength(1);
   });
});
