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
 * WSCompositeTableFocusPaneComponent — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — unfocusCompositeTable: exits composite view and emits when no crossJoins
 *   Group 2 [Risk 3] — unfocusCompositeTable cancel=true: always exits even with crossJoins
 *   Group 3 [Risk 3] — cross-join confirm dialog: shows dialog when relational crossJoins exist
 *   Group 4 [Risk 2] — cancelCompositeTable: emits onWorksheetCancel then unfocuses
 *   Group 5 [Risk 2] — focusCompositeTable: sets selectedCompositeTable, clears crossJoins, emits
 *   Group 6 [Risk 1] — selectSubtables / selectBreadcrumb / notify: delegation contracts
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { WSCompositeTableFocusPaneComponent } from "./ws-composite-table-focus-pane.component";
import { WSCompositeTableBreadcrumbComponent } from "./ws-composite-table-breadcrumb.component";
import { WSRelationalJoinEditorPaneComponent } from "./schema/ws-relational-join-editor-pane.component";
import { WSMergeJoinEditorPaneComponent } from "./merge/ws-merge-join-editor-pane.component";
import { WSConcatenationEditorPane } from "./concatenation/ws-concatenation-editor-pane.component";
import { ComponentTool } from "../../../../common/util/component-tool";
import { Notification } from "../../../../common/data/notification";

@Component({ selector: "ws-composite-table-breadcrumb", template: "", standalone: true })
class WSCompositeTableBreadcrumbComponentStub {}

@Component({ selector: "ws-relational-join-editor-pane", template: "", standalone: true })
class WSRelationalJoinEditorPaneComponentStub {}

@Component({ selector: "ws-merge-join-editor-pane", template: "", standalone: true })
class WSMergeJoinEditorPaneComponentStub {}

@Component({ selector: "ws-concatenation-editor-pane", template: "", standalone: true })
class WSConcatenationEditorPaneStub {}

const MODAL_MOCK = {};

function makeWorksheet(selectedCompositeTable: any = null) {
   return {
      selectedCompositeTable,
      selectedSubtables: [] as any[],
      compositeViewInfo: { selectedBreadcrumb: null as any },
      exitCompositeView: vi.fn(),
   } as any;
}

async function renderComponent(worksheet: any = makeWorksheet()) {
   const { fixture } = await render(WSCompositeTableFocusPaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: WSCompositeTableBreadcrumbComponent, with: WSCompositeTableBreadcrumbComponentStub },
         { replace: WSRelationalJoinEditorPaneComponent, with: WSRelationalJoinEditorPaneComponentStub },
         { replace: WSMergeJoinEditorPaneComponent, with: WSMergeJoinEditorPaneComponentStub },
         { replace: WSConcatenationEditorPane, with: WSConcatenationEditorPaneStub },
      ],
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      componentProperties: { worksheet, crossJoinEnabled: true },
   });
   return fixture.componentInstance as WSCompositeTableFocusPaneComponent;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: unfocusCompositeTable — no selectedCompositeTable [Risk 3]
// ---------------------------------------------------------------------------

describe("WSCompositeTableFocusPaneComponent — unfocusCompositeTable (no selected table)", () => {
   // 🔁 Regression-sensitive: when no composite table is selected, exitCompositeView must
   //    be called immediately; missing call leaves the user stuck in composite view.
   it("should call exitCompositeView when there is no selectedCompositeTable", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      const spy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(spy);

      comp.unfocusCompositeTable(false);

      expect(worksheet.exitCompositeView).toHaveBeenCalled();
      expect(spy).toHaveBeenCalled();
   });

   it("should exit and emit when selectedCompositeTable is set but crossJoins is empty", async () => {
      const table = { tableClassType: "RelationalJoinTableAssembly" };
      const worksheet = makeWorksheet(table);
      const comp = await renderComponent(worksheet);
      // crossJoins defaults to []
      const spy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(spy);

      comp.unfocusCompositeTable(false);

      expect(worksheet.exitCompositeView).toHaveBeenCalled();
      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: unfocusCompositeTable cancel=true bypasses dialog [Risk 3]
// ---------------------------------------------------------------------------

describe("WSCompositeTableFocusPaneComponent — unfocusCompositeTable cancel=true", () => {
   // 🔁 Regression-sensitive: cancel must bypass the cross-join confirmation dialog even
   //    when crossJoins exist; failure would block cancel navigation.
   it("should exit without dialog when cancel=true even if crossJoins are present", async () => {
      const table = { tableClassType: "RelationalJoinTableAssembly" };
      const worksheet = makeWorksheet(table);
      const comp = await renderComponent(worksheet);
      comp.crossJoins = [["table1", "table2"]];
      const dialogSpy = vi.spyOn(ComponentTool, "showConfirmDialog");
      const spy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(spy);

      comp.unfocusCompositeTable(true);

      expect(dialogSpy).not.toHaveBeenCalled();
      expect(worksheet.exitCompositeView).toHaveBeenCalled();
      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: cross-join confirm dialog [Risk 3]
// ---------------------------------------------------------------------------

describe("WSCompositeTableFocusPaneComponent — cross-join confirm dialog", () => {
   // 🔁 Regression-sensitive: cross-join confirmation must fire when relational joins are
   //    pending; missing dialog would silently create invalid cross joins.
   it("should show confirm dialog when relational crossJoins exist and cancel=false", async () => {
      const table = { tableClassType: "RelationalJoinTableAssembly" };
      const worksheet = makeWorksheet(table);
      const comp = await renderComponent(worksheet);
      comp.crossJoins = [["a", "b"]];
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      comp.unfocusCompositeTable(false);
      await new Promise(r => setTimeout(r, 0));

      expect(ComponentTool.showConfirmDialog).toHaveBeenCalled();
      expect(worksheet.exitCompositeView).toHaveBeenCalled();
   });

   it("should NOT exit when user cancels the cross-join confirm dialog", async () => {
      const table = { tableClassType: "RelationalJoinTableAssembly" };
      const worksheet = makeWorksheet(table);
      const comp = await renderComponent(worksheet);
      comp.crossJoins = [["a", "b"]];
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

      comp.unfocusCompositeTable(false);
      await new Promise(r => setTimeout(r, 0));

      expect(worksheet.exitCompositeView).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: cancelCompositeTable [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableFocusPaneComponent — cancelCompositeTable", () => {
   // 🔁 Regression-sensitive: onWorksheetCancel must fire before unfocus; parent listens
   //    to the cancel event to restore state.
   it("should emit onWorksheetCancel", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      const cancelSpy = vi.fn();
      comp.onWorksheetCancel.subscribe(cancelSpy);

      comp.cancelCompositeTable();

      expect(cancelSpy).toHaveBeenCalled();
   });

   it("should call exitCompositeView (unfocuses with cancel=true)", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);

      comp.cancelCompositeTable();

      expect(worksheet.exitCompositeView).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: focusCompositeTable [Risk 2]
// ---------------------------------------------------------------------------

describe("WSCompositeTableFocusPaneComponent — focusCompositeTable", () => {
   // 🔁 Regression-sensitive: crossJoins must be cleared when focusing a new table;
   //    stale crossJoins from a previous table would trigger spurious dialogs.
   it("should set worksheet.selectedCompositeTable to the given table", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      const table = { tableClassType: "RelationalJoinTableAssembly" } as any;

      comp.focusCompositeTable(table);

      expect(worksheet.selectedCompositeTable).toBe(table);
   });

   it("should clear crossJoins array when focusing a new composite table", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      comp.crossJoins = [["a", "b"]];
      const table = { tableClassType: "RelationalJoinTableAssembly" } as any;

      comp.focusCompositeTable(table);

      expect(comp.crossJoins).toHaveLength(0);
   });

   it("should emit onWorksheetCompositionChanged after focusing", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      const spy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(spy);
      const table = { tableClassType: "RelationalJoinTableAssembly" } as any;

      comp.focusCompositeTable(table);

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: selectSubtables / selectBreadcrumb / notify [Risk 1]
// ---------------------------------------------------------------------------

describe("WSCompositeTableFocusPaneComponent — delegation methods", () => {
   it("should set worksheet.selectedSubtables via selectSubtables", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      const subtables = [{ name: "Table1" } as any];

      comp.selectSubtables(subtables);

      expect(worksheet.selectedSubtables).toBe(subtables);
   });

   it("should set compositeViewInfo.selectedBreadcrumb and emit via selectBreadcrumb", async () => {
      const worksheet = makeWorksheet(null);
      const comp = await renderComponent(worksheet);
      const spy = vi.fn();
      comp.onWorksheetCompositionChanged.subscribe(spy);
      const breadcrumb = { label: "crumb" } as any;

      comp.selectBreadcrumb(breadcrumb);

      expect(worksheet.compositeViewInfo.selectedBreadcrumb).toBe(breadcrumb);
      expect(spy).toHaveBeenCalled();
   });

   it("should emit via onNotification when notify is called", async () => {
      const comp = await renderComponent();
      const spy = vi.fn();
      comp.onNotification.subscribe(spy);
      const notification: Notification = { message: "Error occurred", type: "danger" };

      comp.notify(notification);

      expect(spy).toHaveBeenCalledWith(notification);
   });
});
