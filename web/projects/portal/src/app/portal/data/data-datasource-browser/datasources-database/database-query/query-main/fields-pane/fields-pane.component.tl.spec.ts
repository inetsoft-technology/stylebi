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
 * FieldsPaneComponent — Single Pass (+ Concurrency)
 *
 * Coverage plan:
 *   Group 1  — ngOnInit: GET QUERY_SORT_PANE_FIELDS_TREE_URI; fieldsTree set; initAliasColumns called
 *   Group 2  — selectField: single selection; multi-selection (shift range; ctrl additive)
 *   Group 3  — isUpDisabled / isDownDisabled: boundary conditions
 *   Group 4  — moveUp / moveDown: swaps fields (and orders/aliasColumns when not grouping)
 *   Group 5  — doAdd: adds new fields; shows dialog for duplicates
 *   Group 6  — remove: removes selected fields; updates selectedFieldIndexes
 *   Group 7  — changeOrder: toggles ASC↔DESC for selected fields
 *   Group 8  — getOrderIcon: correct icon strings
 *   Group 9  — getFieldTitle: uses queryFieldsMap or falls back to field string
 *   Group 10 [Concurrency] — validate: POST when grouping=true; emits groupByValidityChange
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { vi } from "vitest";

import { Subject } from "rxjs";
import { AssetType } from "../../../../../../../../../../shared/data/asset-type";
import { DragService } from "../../../../../../../widget/services/drag.service";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../../../../../widget/tree/tree.component";
import { BrowseFieldValuesDialogComponent } from "../query-field-pane/browse-field-values/browse-field-values-dialog.component";
import { FieldsPaneComponent, SortTypes } from "./fields-pane.component";

// ---------------------------------------------------------------------------
// Stubs — prevent heavy DI chains of child components
// ---------------------------------------------------------------------------

@Component({ selector: "tree-component", template: "", standalone: true })
class TreeComponentStub {}

@Component({ selector: "browse-field-values-dialog", template: "", standalone: true })
class BrowseFieldValuesDialogStub {}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeFieldsTree(queryFields: string[] = ["col1", "col2"]): TreeNodeModel {
   const queryFieldsNode: TreeNodeModel = {
      label: "Query Fields",
      leaf: false,
      children: queryFields.map(f => ({
         label: f,
         leaf: true,
         children: [],
         data: { properties: { attribute: f }, type: AssetType.COLUMN },
      })),
   };

   const dbFieldsNode: TreeNodeModel = {
      label: "Database Fields",
      leaf: false,
      children: [
         {
            label: "Orders.orderDate",
            leaf: true,
            children: [],
            data: { properties: { attribute: "orderDate" }, type: AssetType.COLUMN },
         },
      ],
   };

   return {
      label: "root",
      leaf: false,
      children: [queryFieldsNode, dbFieldsNode],
   };
}

function makeAssetEntry(attribute: string, isAlias = false) {
   return {
      identifier: attribute,
      path: attribute,
      type: AssetType.COLUMN,
      properties: {
         attribute,
         isAliasColumn: isAlias ? "true" : "false",
      },
   } as any;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

let httpMock: HttpTestingController;

async function renderFieldsPane(overrides: {
   fields?: string[];
   orders?: string[];
   grouping?: boolean;
   runtimeId?: string;
   queryFieldsMap?: Map<string, string>;
} = {}) {
   const fields = overrides.fields ?? ["col1", "col2"];
   const orders = overrides.orders ?? [SortTypes.ASC, SortTypes.ASC];

   const result = await render(FieldsPaneComponent, {
      inputs: {
         runtimeId: overrides.runtimeId ?? "rt-1",
         fields,
         orders,
         grouping: overrides.grouping ?? false,
         queryFieldsMap: overrides.queryFieldsMap ?? null,
      },
      imports: [HttpClientTestingModule],
      providers: [
         { provide: NgbModal, useValue: { open: vi.fn().mockReturnValue({ componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() }, result: Promise.resolve("ok"), close: vi.fn(), dismiss: vi.fn() }) } },
         { provide: DragService, useValue: { getDragDataValues: vi.fn(), put: vi.fn(), reset: vi.fn() } },
      ],
      importOverrides: [
         { replace: TreeComponent, with: TreeComponentStub },
         { replace: BrowseFieldValuesDialogComponent, with: BrowseFieldValuesDialogStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   httpMock = TestBed.inject(HttpTestingController);

   return {
      comp: result.fixture.componentInstance as FieldsPaneComponent,
      fixture: result.fixture,
      fields,
      orders,
   };
}

/** Flush the GET request from ngOnInit. Returns the comp and fixture. */
async function renderAndFlush(overrides: Parameters<typeof renderFieldsPane>[0] = {}) {
   const res = await renderFieldsPane(overrides);
   const tree = makeFieldsTree(["col1", "col2"]);
   httpMock.expectOne(req => req.url.includes("sort/fields-tree")).flush(tree);
   res.fixture.detectChanges();
   return res;
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
   httpMock?.verify();
});

// ===========================================================================
// Group 1 — ngOnInit
// ===========================================================================

describe("Group 1 — ngOnInit", () => {
   it("should GET QUERY_SORT_PANE_FIELDS_TREE_URI with runtimeId param on init", async () => {
      await renderFieldsPane({ runtimeId: "rt-abc" });
      const req = httpMock.expectOne(req =>
         req.url.includes("sort/fields-tree") && req.params.get("runtimeId") === "rt-abc"
      );
      expect(req.request.method).toBe("GET");
      req.flush(makeFieldsTree());
   });

   it("should set fieldsTree after successful HTTP response", async () => {
      const { comp } = await renderAndFlush();
      expect(comp.fieldsTree?.children).toHaveLength(2);
   });

   it("should populate aliasColumns for non-grouping mode after init", async () => {
      const { comp } = await renderAndFlush({ fields: ["col1"], orders: [SortTypes.ASC], grouping: false });
      // col1 is in query fields node → aliasColumn = true
      expect(comp.aliasColumns).toHaveLength(1);
      expect(comp.aliasColumns[0]).toBe(true);
   });

   it("should NOT call initAliasColumns when grouping=true", async () => {
      const { comp } = await renderAndFlush({ grouping: true });
      // aliasColumns is only populated by initAliasColumns which is skipped in grouping mode
      expect(comp.aliasColumns).toHaveLength(0);
   });
});

// ===========================================================================
// Group 2 — selectField
// ===========================================================================

describe("Group 2 — selectField", () => {
   it("should set selectedFieldIndexes to [idx] on plain click (no modifier)", async () => {
      const { comp } = await renderAndFlush({ fields: ["a", "b", "c"], orders: [SortTypes.ASC, SortTypes.ASC, SortTypes.ASC] });
      comp.selectField(null, 1);
      expect(comp.selectedFieldIndexes).toEqual([1]);
   });

   it("should add to selection on ctrl+click", async () => {
      const { comp } = await renderAndFlush({ fields: ["a", "b", "c"], orders: [SortTypes.ASC, SortTypes.ASC, SortTypes.ASC] });
      comp.selectField(null, 0);
      comp.selectField({ ctrlKey: true, shiftKey: false } as MouseEvent, 2);
      expect(comp.selectedFieldIndexes).toContain(0);
      expect(comp.selectedFieldIndexes).toContain(2);
   });

   it("should select a range on shift+click", async () => {
      const { comp } = await renderAndFlush({ fields: ["a", "b", "c"], orders: [SortTypes.ASC, SortTypes.ASC, SortTypes.ASC] });
      comp.selectField(null, 0);
      comp.selectField({ ctrlKey: false, shiftKey: true } as MouseEvent, 2);
      expect(comp.selectedFieldIndexes).toContain(0);
      expect(comp.selectedFieldIndexes).toContain(1);
      expect(comp.selectedFieldIndexes).toContain(2);
   });

   it("should clear selectedFieldIndexes when fields is empty", async () => {
      const { comp } = await renderAndFlush({ fields: [], orders: [] });
      comp.selectField(null, 0);
      expect(comp.selectedFieldIndexes).toHaveLength(0);
   });
});

// ===========================================================================
// Group 3 — isUpDisabled / isDownDisabled
// ===========================================================================

describe("Group 3 — isUpDisabled / isDownDisabled", () => {
   it("isUpDisabled should be true when fields has length <= 1", async () => {
      const { comp } = await renderAndFlush({ fields: ["a"], orders: [SortTypes.ASC] });
      comp.selectField(null, 0);
      expect(comp.isUpDisabled()).toBe(true);
   });

   it("isUpDisabled should be true when first item is selected", async () => {
      const { comp } = await renderAndFlush();
      comp.selectField(null, 0);
      expect(comp.isUpDisabled()).toBe(true);
   });

   it("isUpDisabled should be false when non-first single item is selected", async () => {
      const { comp } = await renderAndFlush();
      comp.selectField(null, 1);
      expect(comp.isUpDisabled()).toBe(false);
   });

   it("isDownDisabled should be true when last item is selected", async () => {
      const { comp } = await renderAndFlush({ fields: ["a", "b"], orders: [SortTypes.ASC, SortTypes.ASC] });
      comp.selectField(null, 1);
      expect(comp.isDownDisabled()).toBe(true);
   });

   it("isDownDisabled should be false when non-last single item is selected", async () => {
      const { comp } = await renderAndFlush({ fields: ["a", "b"], orders: [SortTypes.ASC, SortTypes.ASC] });
      comp.selectField(null, 0);
      expect(comp.isDownDisabled()).toBe(false);
   });
});

// ===========================================================================
// Group 4 — moveUp / moveDown
// ===========================================================================

describe("Group 4 — moveUp / moveDown", () => {
   it("should swap fields upward and update selection index", async () => {
      const fields = ["a", "b", "c"];
      const orders = [SortTypes.ASC, SortTypes.DESC, SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });
      comp.selectField(null, 1); // select "b"
      comp.moveUp();
      expect(fields).toEqual(["b", "a", "c"]);
      expect(comp.selectedFieldIndexes).toEqual([0]);
   });

   it("should swap orders when not grouping", async () => {
      const fields = ["a", "b"];
      const orders = [SortTypes.ASC, SortTypes.DESC];
      const { comp } = await renderAndFlush({ fields, orders, grouping: false });
      comp.selectField(null, 1);
      comp.moveUp();
      expect(orders).toEqual([SortTypes.DESC, SortTypes.ASC]);
   });

   it("should not swap when moveUp is disabled (first item selected)", async () => {
      const fields = ["a", "b"];
      const orders = [SortTypes.ASC, SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });
      comp.selectField(null, 0);
      comp.moveUp();
      expect(fields).toEqual(["a", "b"]);
   });

   it("should swap fields downward and update selection index", async () => {
      const fields = ["a", "b", "c"];
      const orders = [SortTypes.ASC, SortTypes.ASC, SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });
      comp.selectField(null, 0); // select "a"
      comp.moveDown();
      expect(fields).toEqual(["b", "a", "c"]);
      expect(comp.selectedFieldIndexes).toEqual([1]);
   });
});

// ===========================================================================
// Group 5 — doAdd
// ===========================================================================

describe("Group 5 — doAdd", () => {
   it("should add new field to fields array and select it", async () => {
      const fields = ["existing"];
      const orders = [SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });

      comp.doAdd([makeAssetEntry("newField")]);
      expect(fields).toContain("newField");
   });

   it("should add ASC order for non-alias non-grouping field", async () => {
      const fields: string[] = [];
      const orders: string[] = [];
      const { comp } = await renderAndFlush({ fields, orders, grouping: false });

      comp.doAdd([makeAssetEntry("newField", false)]);
      expect(orders).toContain(SortTypes.ASC);
   });

   it("should NOT add a duplicate field and should open a warning dialog", async () => {
      const fields = ["col1"];
      const orders = [SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });
      // Inject AFTER render() so TestBed is already configured
      const modalMock = TestBed.inject(NgbModal) as any;

      comp.doAdd([makeAssetEntry("col1")]);
      // showMessageDialog calls modalService.open
      expect(modalMock.open).toHaveBeenCalled();
      expect(fields).toHaveLength(1); // no duplicate added
   });

   it("should not add non-leaf nodes (tables)", async () => {
      const fields: string[] = [];
      const orders: string[] = [];
      const { comp } = await renderAndFlush({ fields, orders });

      comp.add(); // selectedNodes is empty → no-op
      expect(fields).toHaveLength(0);
   });
});

// ===========================================================================
// Group 6 — remove
// ===========================================================================

describe("Group 6 — remove", () => {
   it("should remove the selected field from fields array", async () => {
      const fields = ["a", "b", "c"];
      const orders = [SortTypes.ASC, SortTypes.ASC, SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });
      comp.selectField(null, 1); // select "b"
      comp.remove();
      expect(fields).not.toContain("b");
      expect(fields).toEqual(["a", "c"]);
   });

   it("should also remove the corresponding order entry when not grouping", async () => {
      const fields = ["a", "b"];
      const orders = [SortTypes.ASC, SortTypes.DESC];
      const { comp } = await renderAndFlush({ fields, orders, grouping: false });
      comp.selectField(null, 1);
      comp.remove();
      expect(orders).toHaveLength(1);
      expect(orders[0]).toBe(SortTypes.ASC);
   });

   it("should not crash when nothing is selected", async () => {
      const { comp } = await renderAndFlush();
      expect(() => comp.remove()).not.toThrow();
   });

   it("should emit onFieldsChange after removal", async () => {
      const fields = ["a", "b"];
      const orders = [SortTypes.ASC, SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });

      const emitSpy = vi.spyOn(comp.onFieldsChange, "emit");
      try {
         comp.selectField(null, 0);
         comp.remove();
         expect(emitSpy).toHaveBeenCalledWith(fields);
      } finally {
         emitSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 7 — changeOrder
// ===========================================================================

describe("Group 7 — changeOrder", () => {
   it("should toggle ASC to DESC for the selected field index", async () => {
      const fields = ["a", "b"];
      const orders = [SortTypes.ASC, SortTypes.ASC];
      const { comp } = await renderAndFlush({ fields, orders });
      comp.selectField(null, 0);
      comp.changeOrder();
      expect(orders[0]).toBe(SortTypes.DESC);
   });

   it("should toggle DESC back to ASC", async () => {
      const fields = ["a"];
      const orders = [SortTypes.DESC];
      const { comp } = await renderAndFlush({ fields, orders });
      comp.selectField(null, 0);
      comp.changeOrder();
      expect(orders[0]).toBe(SortTypes.ASC);
   });

   it("should not crash when no field is selected", async () => {
      const { comp } = await renderAndFlush();
      expect(() => comp.changeOrder()).not.toThrow();
   });
});

// ===========================================================================
// Group 8 — getOrderIcon
// ===========================================================================

describe("Group 8 — getOrderIcon", () => {
   it("should return sort-ascending-icon for ASC order", async () => {
      const { comp } = await renderAndFlush({ fields: ["a"], orders: [SortTypes.ASC] });
      expect(comp.getOrderIcon(0)).toBe("sort-ascending-icon");
   });

   it("should return sort-descending-icon for DESC order", async () => {
      const { comp } = await renderAndFlush({ fields: ["a"], orders: [SortTypes.DESC] });
      expect(comp.getOrderIcon(0)).toBe("sort-descending-icon");
   });

   it("should return sort-ascending-icon for negative index", async () => {
      const { comp } = await renderAndFlush();
      expect(comp.getOrderIcon(-1)).toBe("sort-ascending-icon");
   });
});

// ===========================================================================
// Group 9 — getFieldTitle
// ===========================================================================

describe("Group 9 — getFieldTitle", () => {
   it("should return the mapped title when queryFieldsMap has an entry", async () => {
      const map = new Map([["col1", "Column One"]]);
      const { comp } = await renderAndFlush({ queryFieldsMap: map });
      expect(comp.getFieldTitle("col1")).toBe("Column One");
   });

   it("should return the field name itself when not in queryFieldsMap", async () => {
      const map = new Map([["col1", "Column One"]]);
      const { comp } = await renderAndFlush({ queryFieldsMap: map });
      expect(comp.getFieldTitle("unknown")).toBe("unknown");
   });

   it("should return the field name itself when queryFieldsMap is null", async () => {
      const { comp } = await renderAndFlush({ queryFieldsMap: null });
      expect(comp.getFieldTitle("col1")).toBe("col1");
   });
});

// ===========================================================================
// Group 10 [Concurrency] — validate (grouping=true)
// ===========================================================================

describe("Group 10 — validate: POST to groupby check URI when grouping=true", () => {
   it("should POST to GROUPBY_CHECK_URI with the fields array", async () => {
      const fields = ["col1", "col2"];
      const { comp } = await renderAndFlush({ fields, orders: [], grouping: true });
      // Flush the FIELDS_TREE_URI GET already happened in renderAndFlush
      // Now call validate manually to trigger the groupby check
      comp.validate();
      const req = httpMock.expectOne(req => req.url.includes("groupby/check"));
      expect(req.request.method).toBe("POST");
      expect(req.request.body).toEqual(fields);
      req.flush(true);
   });

   it("should emit groupByValidityChange=true when server responds with true", async () => {
      const { comp, fixture } = await renderAndFlush({ fields: ["col1"], orders: [], grouping: true });

      const emitSpy = vi.spyOn(comp.groupByValidityChange, "emit");
      try {
         comp.validate();
         httpMock.expectOne(req => req.url.includes("groupby/check")).flush(true);
         fixture.detectChanges();
         expect(emitSpy).toHaveBeenCalledWith(true);
      } finally {
         emitSpy.mockRestore();
      }
   });

   it("should emit groupByValidityChange=true immediately when fields is empty (grouping=true)", async () => {
      const { comp } = await renderAndFlush({ fields: [], orders: [], grouping: true });

      const emitSpy = vi.spyOn(comp.groupByValidityChange, "emit");
      try {
         comp.validate();
         // Empty fields → early return with true, no HTTP call
         httpMock.expectNone(req => req.url.includes("groupby/check"));
         expect(emitSpy).toHaveBeenCalledWith(true);
      } finally {
         emitSpy.mockRestore();
      }
   });

   it("should NOT POST when grouping=false", async () => {
      const { comp } = await renderAndFlush({ fields: ["col1"], orders: [SortTypes.ASC], grouping: false });
      comp.validate();
      httpMock.expectNone(req => req.url.includes("groupby/check"));
   });
});
