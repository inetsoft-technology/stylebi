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
 * SecurityTableViewComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - onDrop(): drag payload must not add duplicate identities and must
 *                      be blocked while the table is read-only.
 *   Group 2 [Risk 3] - openAddDialog(): dialog results must be mapped once and existing
 *                      identities must be filtered before emitting.
 *   Group 3 [Risk 2] - table state: data changes reset selection and editable mode controls
 *                      the selection column.
 *   Group 4 [Risk 1] - organization members: Add is hidden for organization member tables.
 *
 * KEY contracts:
 *   - Duplicate identity checks must use identityID.name + identityID.orgID + type.
 *   - Read-only tables still preventDefault() on drop but must not read or emit payload rows.
 *   - Data-source replacement clears stale row selection.
 */

import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialog } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatTableModule } from "@angular/material/table";
import { MatTooltipModule } from "@angular/material/tooltip";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { of, Subject } from "rxjs";

import { IdentityType } from "../../../../../../shared/data/identity-type";
import {
   COPY_PASTE_CONTEXT_IDENTITY_MEMBERS,
   IdentityClipboardService,
} from "./identity-clipboard.service";
import { IdentityModel } from "./identity-model";
import { SecurityTableViewComponent } from "./security-table-view.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeIdentity(
   name: string,
   type: IdentityType = IdentityType.USER,
   orgID: string | null = "OrgA",
): IdentityModel {
   return { identityID: { name, orgID }, type };
}

function makeDropEvent(models: IdentityModel[]) {
   const dataTransfer = {
      getData: vi.fn().mockReturnValue(JSON.stringify(models)),
   };

   return {
      preventDefault: vi.fn(),
      dataTransfer,
   } as unknown as DragEvent & { dataTransfer: typeof dataTransfer };
}

function dataSourceChange(currentValue: IdentityModel[], previousValue: IdentityModel[] = []) {
   return {
      dataSource: new SimpleChange(previousValue, currentValue, previousValue.length === 0),
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   dataSource?: IdentityModel[];
   editable?: boolean;
   label?: string;
   type?: IdentityType;
}

async function renderComponent(opts: RenderOpts = {}) {
   const afterClosed$ = new Subject<unknown>();
   const dialogSpy = {
      open: vi.fn().mockReturnValue({ afterClosed: () => afterClosed$.asObservable() }),
   };
   const clipboardSpy = {
      canPaste: vi.fn().mockReturnValue(false),
      hasContent: vi.fn().mockReturnValue(false),
      copiedCount: vi.fn().mockReturnValue(0),
      copiedTotal: vi.fn().mockReturnValue(0),
      copy: vi.fn(),
      paste: vi.fn().mockReturnValue(null),
   };
   const snackBarSpy = { open: vi.fn() };

   const result = await render(SecurityTableViewComponent, {
      imports: [
         CommonModule,
         NoopAnimationsModule,
         MatButtonModule,
         MatCardModule,
         MatCheckboxModule,
         MatIconModule,
         MatPaginatorModule,
         MatTableModule,
         MatTooltipModule,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: MatDialog, useValue: dialogSpy },
         { provide: MatSnackBar, useValue: snackBarSpy },
         { provide: IdentityClipboardService, useValue: clipboardSpy },
      ],
      componentProperties: {
         dataSource: opts.dataSource ?? [],
         editable: opts.editable ?? true,
         label: opts.label ?? "_#(js:Members)",
         type: opts.type ?? IdentityType.USER,
         showCopyPaste: true,
         copyPasteContext: COPY_PASTE_CONTEXT_IDENTITY_MEMBERS,
      },
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance as SecurityTableViewComponent,
      dialogSpy,
      clipboardSpy,
      snackBarSpy,
      afterClosed$,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - onDrop()
// ---------------------------------------------------------------------------

describe("SecurityTableViewComponent - onDrop(): duplicate and read-only guards", () => {

   // Regression-sensitive: drag/drop is a second add path; it must emit only rows that are not
   // already present or membership tables silently accumulate duplicate identities.
   it("should emit dropped identities that are not already in the table", async () => {
      const bob = makeIdentity("bob");
      const { comp } = await renderComponent({ dataSource: [makeIdentity("alice")] });
      const emitted: IdentityModel[] = [];
      comp.dropOnTable.subscribe(model => emitted.push(model));

      const event = makeDropEvent([bob]);
      comp.onDrop(event);

      expect(event.preventDefault).toHaveBeenCalledTimes(1);
      expect(emitted).toEqual([bob]);
   });

   // Regression-sensitive: JSON parsing recreates identityID objects, so duplicate detection
   // must compare identity values, not object references.
   it("should not emit a dropped identity whose name/org/type already exists", async () => {
      const existing = makeIdentity("alice", IdentityType.USER, "OrgA");
      const sameIdentityFromJson = makeIdentity("alice", IdentityType.USER, "OrgA");
      const { comp } = await renderComponent({ dataSource: [existing] });
      const emitted: IdentityModel[] = [];
      comp.dropOnTable.subscribe(model => emitted.push(model));

      comp.onDrop(makeDropEvent([sameIdentityFromJson]));

      expect(emitted).toHaveLength(0);
   });

   // Regression-sensitive: read-only mode must guard the method itself, not only the disabled
   // button state, because drop events can be fired directly by the browser.
   it("should prevent default but not read or emit the drag payload when editable is false", async () => {
      const { comp } = await renderComponent({ editable: false });
      const emitted: IdentityModel[] = [];
      comp.dropOnTable.subscribe(model => emitted.push(model));

      const event = makeDropEvent([makeIdentity("alice")]);
      comp.onDrop(event);

      expect(event.preventDefault).toHaveBeenCalledTimes(1);
      expect(event.dataTransfer.getData).not.toHaveBeenCalled();
      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] - openAddDialog()
// ---------------------------------------------------------------------------

describe("SecurityTableViewComponent - openAddDialog(): map and dedupe dialog result", () => {

   // Regression-sensitive: the emitted row is the persistence contract for the parent pane.
   // identityIDLabel intentionally carries the selected node organization label.
   it("should map dialog nodes into IdentityModel rows before emitting addIdentities", async () => {
      const { comp, dialogSpy } = await renderComponent({ dataSource: [] });
      const roleNode = {
         identityID: { name: "role1", orgID: "OrgA" },
         organization: "Org A",
         type: IdentityType.ROLE,
      };
      dialogSpy.open.mockReturnValue({ afterClosed: () => of([roleNode]) });
      const emitted: IdentityModel[][] = [];
      comp.addIdentities.subscribe(rows => emitted.push(rows));

      comp.openAddDialog();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual([{
         identityID: roleNode.identityID,
         identityIDLabel: "Org A",
         type: IdentityType.ROLE,
      }]);
   });

   // Regression-sensitive: Add-dialog and drag/drop paths must share duplicate semantics.
   it("should filter out dialog identities that already exist in the table", async () => {
      const existing = makeIdentity("alice", IdentityType.USER, "OrgA");
      const duplicateNode = {
         identityID: { name: "alice", orgID: "OrgA" },
         organization: "Org A",
         type: IdentityType.USER,
      };
      const { comp, dialogSpy } = await renderComponent({ dataSource: [existing] });
      dialogSpy.open.mockReturnValue({ afterClosed: () => of([duplicateNode]) });
      const emitted: IdentityModel[][] = [];
      comp.addIdentities.subscribe(rows => emitted.push(rows));

      comp.openAddDialog();

      expect(emitted).toEqual([[]]);
   });

   // Regression-sensitive: cancelling the add dialog must not emit an empty add event, because
   // parent components treat emissions as user edits.
   it("should not emit addIdentities when the dialog is cancelled", async () => {
      const { comp, dialogSpy } = await renderComponent();
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(null) });
      const emitted: IdentityModel[][] = [];
      comp.addIdentities.subscribe(rows => emitted.push(rows));

      comp.openAddDialog();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - table state
// ---------------------------------------------------------------------------

describe("SecurityTableViewComponent - table state after data/input changes", () => {

   // Regression-sensitive: selection contains row object references. Replacing the data source
   // must clear them or Remove can emit stale rows that are no longer visible.
   it("should clear stale selection and remove the selection column when editable becomes false", async () => {
      const alice = makeIdentity("alice");
      const bob = makeIdentity("bob");
      const { comp } = await renderComponent({ dataSource: [alice], editable: true });
      comp.selection.select(alice);

      comp.editable = false;
      comp.dataSource = [bob];
      comp.ngOnChanges(dataSourceChange([bob], [alice]));

      expect(comp.selection.selected).toHaveLength(0);
      expect(comp.matTableDataSource.data).toEqual([bob]);
      expect(comp.displayColumns).toEqual(["type", "name"]);
   });

   // Regression-sensitive: master checkbox must be a reversible bulk operation over the current
   // MatTableDataSource, not over stale input arrays.
   it("should select all current rows on masterToggle and clear them on the next toggle", async () => {
      const alice = makeIdentity("alice");
      const bob = makeIdentity("bob");
      const { comp } = await renderComponent({ dataSource: [alice, bob] });

      comp.masterToggle();
      expect(comp.selection.selected).toEqual([alice, bob]);
      expect(comp.isAllSelected()).toBe(true);

      comp.masterToggle();
      expect(comp.selection.selected).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4 [Risk 1] - organization member table button visibility
// ---------------------------------------------------------------------------

describe("SecurityTableViewComponent - organization member Add button visibility", () => {

   // Regression-sensitive: organization members are managed through organization-specific flows;
   // exposing Add here creates a second, inconsistent path.
   it("should hide Add for organization member tables and show it for other labels", async () => {
      const { comp } = await renderComponent({
         type: IdentityType.ORGANIZATION,
         label: "_#(js:Members)",
      });

      expect(comp.isAddButtonVisible()).toBe(false);

      comp.label = "_#(js:Roles)";
      expect(comp.isAddButtonVisible()).toBe(true);
   });
});
