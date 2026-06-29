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
 * SelectWorksheetDialog - single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - selectedSubQuery setter and worksheet selection drive selectedEntry/model
 *   Group 2 [Risk 3] - loadQueryParameters: worksheet-only HTTP GET and queryParams assignment
 *   Group 3 [Risk 2] - getSelectedParamVal and getSelectedInfo mapping paths
 *   Group 4 [Risk 2] - selectItem updates/creates params and closeMenu state
 *   Group 5 [Risk 1] - dropdownMinWidth, ok, clear, cancel, okDisabled
 *
 * Confirmed bugs (it.fails):
 *   Bug - loadQueryParameters post-destroy HTTP callback leak (Group 6): the worksheet-parameter
 *     GET subscription is never torn down, so queryParams can still update after fixture.destroy().
 *
 * Out of scope:
 *   enterSubmit keyboard integration - directive wiring only; the dialog contracts are covered
 *   through direct ok()/cancel() calls and public method assertions.
 *
 * Mocking strategy:
 *   - HttpClient is injected directly, so render helper uses provideHttpClient() + MSW.
 *   - Imported child components/directives are replaced via importOverrides.
 */

import { Component, Directive, EventEmitter, Input, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { SelectWorksheetDialog } from "./select-worksheet-dialog.component";
import { ModalHeaderComponent } from "../../../../../../../../widget/modal-header/modal-header.component";
import { AssetTreeComponent } from "../../../../../../../../widget/asset-tree/asset-tree.component";
import { EnterSubmitDirective } from "../../../../../../../../widget/directive/enter-submit.directive";
import { FixedDropdownDirective } from "../../../../../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { SelectAttributePaneComponent } from "./select-attribute-pane.component";
import { SelectQueryFieldPaneComponent } from "./select-query-field-pane.component";
import { DrillSubQueryModel } from "../../../../../../model/datasources/database/physical-model/logical-model/drill-sub-query-model";
import { EntityModel } from "../../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { AttributeModel } from "../../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { QueryFieldModel } from "../../../../../../model/datasources/database/query/query-field-model";
import { TreeNodeModel } from "../../../../../../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../../../../shared/data/asset-type";

@Component({ selector: "modal-header", template: "", standalone: true })
class ModalHeaderStub {
   @Input() title: string;
   @Input() cshid: string;
   @Output() onCancel = new EventEmitter<void>();
}

@Directive({ selector: "[enterSubmit]", standalone: true })
class EnterSubmitDirectiveStub {
   @Output() onEnter = new EventEmitter<void>();
   @Output() onEsc = new EventEmitter<void>();
}

@Directive({ selector: "[fixedDropdown]", standalone: true })
class FixedDropdownDirectiveStub {
   @Input() fixedDropdown: any;
   @Input() autoClose: boolean;
   @Input() dropdownPlacement: string;
}

@Component({ selector: "asset-tree", template: "", standalone: true })
class AssetTreeStub {
   @Input() datasources: boolean;
   @Input() columns: boolean;
   @Input() viewsheets: boolean;
   @Input() readOnly: boolean;
   @Input() dataSourcePath: string;
   @Input() dataSourceScope: number;
   @Output() nodeSelected = new EventEmitter<TreeNodeModel>();
}

@Component({ selector: "select-attribute-pane", template: "", standalone: true })
class SelectAttributePaneStub {
   @Input() entities: EntityModel[];
   @Input() info: any;
   @Output() onSelectItem = new EventEmitter<string>();
}

@Component({ selector: "select-query-field-pane", template: "", standalone: true })
class SelectQueryFieldPaneStub {
   @Input() fields: QueryFieldModel[];
   @Input() selectedField: string;
   @Output() onSelectField = new EventEmitter<string>();
}

interface RenderOpts {
   entities?: EntityModel[];
   fields?: QueryFieldModel[];
   selectedSubQuery?: DrillSubQueryModel | null;
}

function makeAttribute(overrides: Partial<AttributeModel> = {}): AttributeModel {
   return {
      name: "orderId",
      table: "OrdersEntity",
      qualifiedName: "OrdersEntity.orderId",
      oldName: "orderId",
      baseElement: false,
      elementType: "attribute",
      visible: true,
      ...overrides,
   } as AttributeModel;
}

function makeEntity(overrides: Partial<EntityModel> = {}): EntityModel {
   return {
      name: "OrdersEntity",
      oldName: "OrdersEntity",
      attributes: [makeAttribute()],
      baseElement: false,
      elementType: "entity",
      visible: true,
      expanded: false,
      selected: false,
      ...overrides,
   } as EntityModel;
}

function makeField(overrides: Partial<QueryFieldModel> = {}): QueryFieldModel {
   return {
      name: "fieldA",
      alias: "fieldA",
      ...overrides,
   } as QueryFieldModel;
}

function makeEntry(overrides: Partial<AssetEntry> = {}): AssetEntry {
   return {
      identifier: "ws-1",
      path: "/worksheet/Orders",
      scope: 1,
      type: AssetType.WORKSHEET,
      ...overrides,
   } as AssetEntry;
}

function makeSubQuery(overrides: Partial<DrillSubQueryModel> = {}): DrillSubQueryModel {
   return {
      entry: makeEntry(),
      params: [],
      ...overrides,
   } as DrillSubQueryModel;
}

async function renderComponent(opts: RenderOpts = {}) {
   const onCommitSpy = vi.fn();
   const onCancelSpy = vi.fn();

   const { fixture } = await render(SelectWorksheetDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [provideHttpClient()],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: AssetTreeComponent, with: AssetTreeStub },
         { replace: EnterSubmitDirective, with: EnterSubmitDirectiveStub },
         { replace: FixedDropdownDirective, with: FixedDropdownDirectiveStub },
         { replace: SelectAttributePaneComponent, with: SelectAttributePaneStub },
         { replace: SelectQueryFieldPaneComponent, with: SelectQueryFieldPaneStub },
      ],
      componentInputs: {
         entities: opts.entities ?? [
            makeEntity(),
            makeEntity({
               name: "CustomersEntity",
               oldName: "CustomersEntity",
               attributes: [makeAttribute({
                  name: "customerId",
                  oldName: "customerId",
                  table: "CustomersEntity",
                  qualifiedName: "CustomersEntity.customerId",
               })],
            }),
         ],
         fields: opts.fields ?? [makeField()],
         selectedSubQuery: opts.selectedSubQuery ?? null,
      },
      on: {
         onCommit: onCommitSpy,
         onCancel: onCancelSpy,
      },
   });

   return {
      fixture,
      comp: fixture.componentInstance as SelectWorksheetDialog,
      onCommitSpy,
      onCancelSpy,
   };
}

afterEach(() => vi.restoreAllMocks());

describe("Group 1 - selectedSubQuery and selectWorksheet", () => {
   it("should mirror selectedSubQuery.entry into selectedEntry through the input setter", async () => {
      const subQuery = makeSubQuery({ entry: makeEntry({ path: "/worksheet/A" }) });

      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      expect(comp.selectedSubQuery).toEqual(subQuery);
      expect(comp.selectedEntry).toEqual(subQuery.entry);
   });

   it("should create selectedSubQuery when selecting a worksheet without an existing model", async () => {
      const { comp } = await renderComponent({ selectedSubQuery: null });
      const loadSpy = vi.spyOn(comp, "loadQueryParameters").mockImplementation(() => {});
      const node = { data: makeEntry({ identifier: "ws-2" }) } as TreeNodeModel;

      try {
         comp.selectWorksheet(node);

         expect(comp.selectedEntry).toEqual(node.data);
         expect(comp.selectedSubQuery).toEqual({ entry: node.data });
         expect(loadSpy).toHaveBeenCalledWith(node.data);
      } finally {
         loadSpy.mockRestore();
      }
   });

   it("should update the existing selectedSubQuery entry when selecting another worksheet", async () => {
      const existing = makeSubQuery({ entry: makeEntry({ identifier: "old-ws" }) });
      const { comp } = await renderComponent({ selectedSubQuery: existing });
      const loadSpy = vi.spyOn(comp, "loadQueryParameters").mockImplementation(() => {});
      const node = { data: makeEntry({ identifier: "new-ws" }) } as TreeNodeModel;

      try {
         comp.selectWorksheet(node);

         expect(comp.selectedSubQuery).toBe(existing);
         expect(comp.selectedSubQuery.entry).toEqual(node.data);
         expect(loadSpy).toHaveBeenCalledWith(node.data);
      } finally {
         loadSpy.mockRestore();
      }
   });
});

describe("Group 2 - loadQueryParameters", () => {
   it("should GET worksheet query params and store them when the selected entry is a worksheet", async () => {
      let requestUrl = "";
      const entry = makeEntry({ identifier: "worksheet-42", type: AssetType.WORKSHEET });

      server.use(
         http.get("*/api/portal/data/autodrill/worksheet/params", ({ request }) => {
            requestUrl = request.url;
            return MswHttpResponse.json({ queryParams: ["paramA", "paramB"] });
         })
      );

      const { comp } = await renderComponent();

      comp.loadQueryParameters(entry);

      await waitFor(() => expect(comp.queryParams).toEqual(["paramA", "paramB"]));
      expect(requestUrl).toContain("wsIdentifier=worksheet-42");
   });

   it("should not send a GET request when the selected entry is not a worksheet", async () => {
      let requestCount = 0;
      const entry = makeEntry({ type: AssetType.FOLDER });

      server.use(
         http.get("*/api/portal/data/autodrill/worksheet/params", () => {
            requestCount++;
            return MswHttpResponse.json({ queryParams: [] });
         })
      );

      const { comp } = await renderComponent();

      comp.loadQueryParameters(entry);

      await waitFor(() => expect(requestCount).toBe(0));
   });
});

describe("Group 3 - getSelectedParamVal and getSelectedInfo", () => {
   it("should return the selected parameter value for an existing param key", async () => {
      const subQuery = makeSubQuery({
         params: [{ key: "region", value: "OrdersEntity.orderId" }],
      });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      expect(comp.getSelectedParamVal("region")).toBe("OrdersEntity.orderId");
   });

   it("should return null when the parameter key is missing or invalid", async () => {
      const subQuery = makeSubQuery({ params: [{ key: "region", value: "OrdersEntity.orderId" }] });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      expect(comp.getSelectedParamVal("missing")).toBeNull();
      expect(comp.getSelectedParamVal("")).toBeNull();
   });

   it("should map a qualified label to the matching entity and attribute indexes", async () => {
      const subQuery = makeSubQuery({
         params: [{ key: "region", value: "OrdersEntity.orderId" }],
      });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      expect(comp.getSelectedInfo("region")).toEqual({
         expanded: [comp.entities[0]],
         selectedItem: { entity: 0, attribute: 0 },
      });
   });

   it("should return empty expansion and -1 indexes when the label is missing or malformed", async () => {
      const subQuery = makeSubQuery({
         params: [{ key: "region", value: "plainLabel" }],
      });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery, entities: null });

      expect(comp.getSelectedInfo("region")).toEqual({
         expanded: [],
         selectedItem: { entity: -1, attribute: -1 },
      });
      expect(comp.getSelectedInfo("missing")).toEqual({
         expanded: [],
         selectedItem: { entity: -1, attribute: -1 },
      });
   });
});

describe("Group 4 - selectItem", () => {
   it("should create the params array and close the menu when selecting a new field", async () => {
      const subQuery = makeSubQuery({ params: null });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      comp.selectItem("OrdersEntity.orderId", "region");

      expect(comp.selectedSubQuery.params).toEqual([{ key: "region", value: "OrdersEntity.orderId" }]);
      expect(comp.closeMenu).toBe(true);
   });

   it("should append a new param when the key does not yet exist", async () => {
      const subQuery = makeSubQuery({
         params: [{ key: "existing", value: "A" }],
      });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      comp.selectItem("OrdersEntity.orderId", "region");

      expect(comp.selectedSubQuery.params).toEqual([
         { key: "existing", value: "A" },
         { key: "region", value: "OrdersEntity.orderId" },
      ]);
   });

   it("should replace the existing param value when the key already exists", async () => {
      const subQuery = makeSubQuery({
         params: [{ key: "region", value: "old.value" }],
      });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      comp.selectItem("CustomersEntity.customerId", "region");

      expect(comp.selectedSubQuery.params).toEqual([
         { key: "region", value: "CustomersEntity.customerId" },
      ]);
      expect(comp.closeMenu).toBe(true);
   });

   it("should leave the menu open when the selected field is falsy", async () => {
      const subQuery = makeSubQuery({ params: [] });
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      comp.selectItem("", "region");

      expect(comp.closeMenu).toBe(false);
      expect(comp.selectedSubQuery.params).toEqual([]);
   });
});

describe("Group 5 - view helpers and actions", () => {
   it("should expose dropdownMinWidth from dropdownBody.offsetWidth", async () => {
      const { comp } = await renderComponent();
      comp.dropdownBody = { nativeElement: { offsetWidth: 240 } } as any;

      expect(comp.dropdownMinWidth).toBe(240);
   });

   it("should return null dropdownMinWidth when dropdownBody is absent", async () => {
      const { comp } = await renderComponent();

      expect(comp.dropdownMinWidth).toBeNull();
   });

   it("should emit the selectedSubQuery on ok()", async () => {
      const subQuery = makeSubQuery();
      const { comp, onCommitSpy } = await renderComponent({ selectedSubQuery: subQuery });

      comp.ok();

      expect(onCommitSpy).toHaveBeenCalledWith(subQuery);
   });

   it("should clear both selectedSubQuery and selectedEntry on clear()", async () => {
      const subQuery = makeSubQuery();
      const { comp } = await renderComponent({ selectedSubQuery: subQuery });

      comp.clear();

      expect(comp.selectedSubQuery).toBeNull();
      expect(comp.selectedEntry).toBeNull();
   });

   it("should emit cancel on cancel()", async () => {
      const { comp, onCancelSpy } = await renderComponent();

      comp.cancel();

      expect(onCancelSpy).toHaveBeenCalled();
   });

   it("should disable OK only when the selected entry exists and is not a worksheet", async () => {
      const { comp } = await renderComponent();
      comp.selectedEntry = makeEntry({ type: AssetType.FOLDER });
      expect(comp.okDisabled()).toBe(true);

      comp.selectedEntry = makeEntry({ type: AssetType.WORKSHEET });
      expect(comp.okDisabled()).toBe(false);

      comp.selectedEntry = null;
      expect(comp.okDisabled()).toBe(false);
   });
});

describe("Group 6 - Confirmed bug (it.fails): post-destroy loadQueryParameters callback", () => {
   // Expected failure: `expect(comp.queryParams).toBeUndefined()` fails because the worksheet
   // parameter GET subscription remains active after fixture.destroy() and still writes the
   // resolved queryParams array back onto the dead component instance.
   it.fails("should not update queryParams after fixture.destroy()", async () => {
      let resolveRequest: ((response: any) => void) | undefined;

      server.use(
         http.get("*/api/portal/data/autodrill/worksheet/params", () =>
            new Promise<any>((resolve) => {
               resolveRequest = resolve;
            })
         )
      );

      const { comp, fixture } = await renderComponent();

      comp.loadQueryParameters(makeEntry({ identifier: "worksheet-leak" }));
      fixture.destroy();
      resolveRequest!(MswHttpResponse.json({ queryParams: ["late-param"] }));

      await waitFor(() => expect(comp.queryParams).toEqual(["late-param"]));
      expect(comp.queryParams).toBeUndefined();
   });
});
