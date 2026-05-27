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
 * ResourcePermissionComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — pastePermissions(): validate-then-confirm flow must correctly
 *                       filter missing identities and update model only after confirmation.
 *   Group 2 [Risk 3] — removePermission(): org-admin (non-siteAdmin) must be blocked from
 *                       removing global-role permissions (identityID.orgID == null).
 *   Group 3 [Risk 2] — ngOnInit: siteAdmin and isOrgAdminOnly flags are loaded via HTTP
 *                       and reflect the actual API response.
 *   Group 4 [Risk 2] — onTableDataChange(): must update model.permissions, set
 *                       model.hasOrgEdited and model.changed, then emit permissionChanged.
 *   Group 5 [Risk 2] — derivePermissionChange(): both true/false paths set model.permissions
 *                       and always emit null (not the permission array) via permissionChanged.
 *   Group 6 [Risk 2] — addPermission(): dialog result is mapped and forwarded to the table;
 *                       cancelling the dialog does not call table.receiveSelection.
 *   Group 7 [Risk 2] — pasteBadgeLabel: "(N)", "(N of M)", or "" depending on count/total.
 *   Group 8 [Risk 2] — copyPermissions(): should copy selected rows if present, otherwise copy
 *                       all current permissions; must include provider + context when copying.
 *
 * KEY contracts:
 *   - derivePermissionChange() always calls permissionChanged.emit(null), never with the array.
 *   - pastePermissions() missing-key formula: `${name}:${orgID ?? ""}:${type}`.
 *   - removePermission() blocks removal when any selected row has identityID.orgID == null AND
 *     siteAdmin is false; siteAdmin defaults to true before ngOnInit fires.
 *   - pasteBadgeLabel returns "" (empty string, not " (0)") when pasteCount is 0.
 *   - addPermission() maps dialog results to {identityID, type} only — actions are omitted so the
 *     table assigns displayActions defaults.
 *   - copyPermissions() copies selection if non-empty, otherwise copies model.permissions.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import { FormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { it } from "@jest/globals";
import { render, waitFor } from "@testing-library/angular";
import { of, Subject } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "../../../../../../../mocks/server";
import { ResourcePermissionComponent } from "./resource-permission.component";
import { ResourcePermissionModel } from "./resource-permission-model";
import { ResourcePermissionTableModel } from "./resource-permission-table-model";
import { PermissionClipboardService } from "./permission-clipboard.service";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { ResourceAction } from "../../../../../../shared/util/security/resource-permission/resource-action.enum";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { COPY_PASTE_CONTEXT_REPOSITORY } from "./copy-paste-context";

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

const makeModel = (overrides: Partial<ResourcePermissionModel> = {}): ResourcePermissionModel => ({
   permissions: [],
   displayActions: [ResourceAction.READ, ResourceAction.WRITE],
   hasOrgEdited: false,
   securityEnabled: true,
   requiresBoth: false,
   derivePermissionLabel: "Derive permissions from parent",
   grantReadToAll: false,
   grantReadToAllVisible: false,
   grantReadToAllLabel: "Grant read to all",
   changed: false,
   ...overrides,
});

const makePermission = (
   name: string,
   orgID: string | null = "DefaultOrg",
   type: IdentityType = IdentityType.USER,
): ResourcePermissionTableModel => ({
   identityID: { name, orgID },
   type,
   actions: [ResourceAction.READ, ResourceAction.WRITE],
});

/** Minimal stub — mirrors only the methods called by ResourcePermissionComponent */
const makeMockTable = (selected: ResourcePermissionTableModel[] = []) => ({
   selection: { selected },
   receiveSelection: jest.fn(),
   sendSelection: jest.fn(),
});

// ─────────────────────────────────────────────────────────────────────────────
// Render helper
// ─────────────────────────────────────────────────────────────────────────────

interface RenderOpts {
   model?: ResourcePermissionModel;
   copyPasteContext?: typeof COPY_PASTE_CONTEXT_REPOSITORY | null;
   validateIdentitiesUrl?: string | null;
   /** What dialog.afterClosed() emits. Default: true (user confirmed). */
   dialogClosesWith?: unknown;
   clipboardCanPaste?: boolean;
   clipboardCount?: number;
   clipboardTotal?: number;
   clipboardPasteResult?: { permissions: ResourcePermissionTableModel[]; requiresBoth: boolean } | null;
}

async function renderComponent(opts: RenderOpts = {}) {
   const dialogSpy = {
      open: jest.fn().mockReturnValue({
         afterClosed: () =>
            of(opts.dialogClosesWith !== undefined ? opts.dialogClosesWith : true),
      }),
   };

   const clipboardSpy = {
      canPaste: jest.fn().mockReturnValue(opts.clipboardCanPaste ?? false),
      copiedCount: jest.fn().mockReturnValue(opts.clipboardCount ?? 0),
      copiedTotal: jest.fn().mockReturnValue(opts.clipboardTotal ?? 0),
      copy: jest.fn(),
      paste: jest.fn().mockReturnValue(opts.clipboardPasteResult ?? null),
   };

   const snackBarSpy = { open: jest.fn() };

   const orgDropdownSpy = {
      onRefresh: new Subject<any>(),
      onOrgChange: new Subject<void>().asObservable(),
      getProvider: jest.fn().mockReturnValue("TestProvider"),
   };

   const result = await render(ResourcePermissionComponent, {
      imports: [HttpClientModule, FormsModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: MatDialog, useValue: dialogSpy },
         { provide: MatSnackBar, useValue: snackBarSpy },
         { provide: PermissionClipboardService, useValue: clipboardSpy },
         { provide: OrganizationDropdownService, useValue: orgDropdownSpy },
      ],
      componentProperties: {
         model: opts.model ?? makeModel(),
         showCopyPaste: true,
         showRadioButtons: false, // avoids NG01203: mat-radio-group [(ngModel)] needs MatRadioModule
         copyPasteContext: opts.copyPasteContext ?? COPY_PASTE_CONTEXT_REPOSITORY,
         validateIdentitiesUrl: opts.validateIdentitiesUrl ?? null,
      },
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance as ResourcePermissionComponent,
      dialogSpy,
      snackBarSpy,
      clipboardSpy,
   };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — pastePermissions(): validate-then-confirm
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — pastePermissions(): validate-then-confirm", () => {

   const VALIDATE_URL = "../api/em/test/validate-permissions"; // passed to the component
   const VALIDATE_MSW = "*/api/em/test/validate-permissions";  // MSW handler pattern (resolved URL)

   // 🔁 Regression-sensitive: pasteCount=0 is caught INSIDE pastePermissions(), not at the button.
   // If the inner guard is removed, mismatched clipboard data silently overwrites the model.
   // Risk Point/Contract: permissionChanged must NOT emit when the incompatible path is taken.
   it("should show incompatible snackbar and not emit permissionChanged when pasteCount is 0", async () => {
      const { comp, snackBarSpy, clipboardSpy } = await renderComponent({
         model: makeModel({ permissions: [] }),
         clipboardCanPaste: true,
         clipboardCount: 0,
      });

      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.pastePermissions();

      expect(snackBarSpy.open).toHaveBeenCalledWith(
         "_#(js:em.security.pastePermissions.incompatible)",
         null,
         expect.any(Object),
      );
      expect(clipboardSpy.paste).not.toHaveBeenCalled();
      expect(emitted).toHaveLength(0); // model not touched
   });

   // Risk Point/Contract: model.permissions AND model.requiresBoth must both be updated from the
   // paste result. Missing either means the next save sends stale data to the server.
   it("should update model.permissions and requiresBoth after confirmation with no validate URL", async () => {
      const pastedPerms = [makePermission("user1"), makePermission("user2")];
      const { comp, fixture } = await renderComponent({
         model: makeModel({ permissions: [] }),
         validateIdentitiesUrl: null,
         clipboardCanPaste: true,
         clipboardCount: 2,
         clipboardPasteResult: { permissions: pastedPerms, requiresBoth: true },
         dialogClosesWith: true,
      });

      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.pastePermissions();
      await fixture.whenStable();

      expect(comp.model.permissions).toHaveLength(2);
      expect(comp.model.requiresBoth).toBe(true);
      expect(comp.model.hasOrgEdited).toBe(true);
      expect(comp.model.changed).toBe(true);
      expect(emitted).toHaveLength(1);
   });

   // 🔁 Regression-sensitive: When ALL identities are missing the flow returns early without
   // opening a confirmation dialog. If this guard is weakened, pasting produces an empty
   // permission list and silently wipes the table.
   it("should show allMissing snackbar and NOT open confirm dialog when every pasted identity is missing", async () => {
      const pastedPerms = [makePermission("gone1"), makePermission("gone2")];
      server.use(
         http.post(VALIDATE_MSW, () =>
            HttpResponse.json([...pastedPerms]),
         ),
      );

      const { comp, snackBarSpy, dialogSpy } = await renderComponent({
         model: makeModel({ permissions: [] }),
         validateIdentitiesUrl: VALIDATE_URL,
         clipboardCanPaste: true,
         clipboardCount: 2,
         clipboardPasteResult: { permissions: pastedPerms, requiresBoth: false },
      });

      comp.pastePermissions();
      await waitFor(() =>
         expect(snackBarSpy.open).toHaveBeenCalledWith(
            "_#(js:em.security.pastePermissions.allMissing)",
            null,
            expect.any(Object),
         ),
      );
      expect(dialogSpy.open).not.toHaveBeenCalled();
   });

   // Risk Point/Contract: missing-key formula is `${name}:${orgID ?? ""}:${type}` — only rows
   // whose key matches a missing row are filtered. Wrong formula silently drops valid permissions
   // or keeps invalid ones.
   it("should filter only the missing identities from pasted permissions after confirm", async () => {
      const user1 = makePermission("user1", "DefaultOrg", IdentityType.USER);
      const user2 = makePermission("user2", "DefaultOrg", IdentityType.USER); // will be missing
      const user3 = makePermission("user3", "DefaultOrg", IdentityType.USER);
      server.use(
         http.post(VALIDATE_MSW, () =>
            HttpResponse.json([user2]),
         ),
      );

      const { comp } = await renderComponent({
         model: makeModel({ permissions: [] }),
         validateIdentitiesUrl: VALIDATE_URL,
         clipboardCanPaste: true,
         clipboardCount: 3,
         clipboardPasteResult: { permissions: [user1, user2, user3], requiresBoth: false },
         dialogClosesWith: true,
      });

      comp.pastePermissions();
      await waitFor(() => expect(comp.model.permissions).toHaveLength(2));

      expect(comp.model.permissions.map(p => p.identityID.name)).not.toContain("user2");
      expect(comp.model.permissions.map(p => p.identityID.name)).toContain("user1");
      expect(comp.model.permissions.map(p => p.identityID.name)).toContain("user3");
   });

   // Risk Point/Contract: When some (not all) identities are missing, the confirm dialog content
   // must include their names so the user can make an informed decision before accepting.
   it("should include missing identity names in confirm dialog content when some are missing", async () => {
      const alice = makePermission("alice", "DefaultOrg", IdentityType.USER);
      const bob   = makePermission("bob",   "DefaultOrg", IdentityType.USER); // missing
      server.use(
         http.post(VALIDATE_MSW, () =>
            HttpResponse.json([bob]),
         ),
      );

      const { comp, dialogSpy } = await renderComponent({
         model: makeModel({ permissions: [] }),
         validateIdentitiesUrl: VALIDATE_URL,
         clipboardCanPaste: true,
         clipboardCount: 2,
         clipboardPasteResult: { permissions: [alice, bob], requiresBoth: false },
         dialogClosesWith: false, // user cancels — we only need to inspect the open() call
      });

      comp.pastePermissions();
      await waitFor(() =>
         expect(dialogSpy.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({
               data: expect.objectContaining({
                  content: expect.stringContaining("bob"),
               }),
            }),
         ),
      );
   });

   // 🔁 Regression-sensitive: Cancel must be a hard no-op. It's easy to accidentally move the
   // model mutation outside the "confirmed" branch during refactoring, which would apply changes
   // even when the user clicks Cancel.
   // Risk Point/Contract: model and permissionChanged must remain unchanged when confirm is cancelled.
   it("should NOT update model or emit permissionChanged when user cancels the confirm dialog", async () => {
      const alice = makePermission("alice", "DefaultOrg", IdentityType.USER);
      const bob   = makePermission("bob",   "DefaultOrg", IdentityType.USER); // missing
      server.use(
         http.post(VALIDATE_MSW, () => HttpResponse.json([bob])),
      );

      const model = makeModel({ permissions: [] });
      const { comp, fixture } = await renderComponent({
         model,
         validateIdentitiesUrl: VALIDATE_URL,
         clipboardCanPaste: true,
         clipboardCount: 2,
         clipboardPasteResult: { permissions: [alice, bob], requiresBoth: true },
         dialogClosesWith: false, // user cancels
      });

      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.pastePermissions();
      await fixture.whenStable();

      expect(comp.model.permissions).toEqual([]);
      expect(comp.model.requiresBoth).toBe(false);
      expect(comp.model.hasOrgEdited).toBe(false);
      expect(comp.model.changed).toBe(false);
      expect(emitted).toHaveLength(0);
   });

   // Why High Value: A validate API error must not silently swallow the paste. The error handler
   // falls back to openPasteConfirmDialog() — a simple confirm with no filtering — so the user
   // can still apply all permissions. If the error path is removed, a transient API failure
   // silently blocks every paste in that session.
   it("should fall back to simple confirm (no filtering) and apply all permissions when validate API errors", async () => {
      const pastedPerms = [makePermission("user1"), makePermission("user2")];
      server.use(
         http.post(VALIDATE_MSW, () => HttpResponse.error()),
      );

      const { comp, dialogSpy } = await renderComponent({
         model: makeModel({ permissions: [] }),
         validateIdentitiesUrl: VALIDATE_URL,
         clipboardCanPaste: true,
         clipboardCount: 2,
         clipboardPasteResult: { permissions: pastedPerms, requiresBoth: false },
         dialogClosesWith: true,
      });

      comp.pastePermissions();
      await waitFor(() => expect(dialogSpy.open).toHaveBeenCalledTimes(1));
      // all permissions applied — no filtering in error path
      expect(comp.model.permissions).toHaveLength(2);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 3] — removePermission(): org-admin global-role guard
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — removePermission(): org-admin global-role guard", () => {

   // 🔁 Regression-sensitive: This is the only UI-layer barrier preventing an org-admin from
   // removing site-wide role grants. Deleting the `!this.siteAdmin` check exposes a privilege
   // escalation that is invisible to the server until a full audit.
   // Risk Point/Contract: snackbar must be shown AND sendSelection must NOT be called.
   it("should show denied snackbar and NOT call sendSelection when non-siteAdmin selects a global role (orgID=null)", async () => {
      const { comp, snackBarSpy } = await renderComponent({ model: makeModel() });
      comp.siteAdmin = false; // simulate org-admin after ngOnInit

      const globalRole = makePermission("globalRole", null, IdentityType.ROLE);
      const table = makeMockTable([globalRole]);

      comp.removePermission(table as any);

      expect(snackBarSpy.open).toHaveBeenCalledWith(
         "_#(js:em.security.orgAdmin.removeGlobalRolePermissionDenied)",
         null,
         expect.any(Object),
      );
      expect(table.sendSelection).not.toHaveBeenCalled();
   });

   // Why High Value: siteAdmin=true must bypass the guard entirely — blocking removal
   // for site admins would prevent legitimate permission cleanup.
   it("should call sendSelection for siteAdmin even when the selected row has orgID=null", async () => {
      const { comp } = await renderComponent({ model: makeModel() });
      // siteAdmin defaults to true — no override needed

      const globalRole = makePermission("globalRole", null, IdentityType.ROLE);
      const table = makeMockTable([globalRole]);

      comp.removePermission(table as any);

      expect(table.sendSelection).toHaveBeenCalledTimes(1);
   });

   // Boundary: non-siteAdmin with org-scoped role (orgID non-null) must be allowed.
   // The guard checks strict null (== null), not general falsy — empty string must not trigger it.
   it("should call sendSelection for non-siteAdmin when all selected roles have a non-null orgID", async () => {
      const { comp, snackBarSpy } = await renderComponent({ model: makeModel() });
      comp.siteAdmin = false;

      const orgRole = makePermission("orgRole", "DefaultOrg", IdentityType.ROLE);
      const table = makeMockTable([orgRole]);

      comp.removePermission(table as any);

      expect(snackBarSpy.open).not.toHaveBeenCalled();
      expect(table.sendSelection).toHaveBeenCalledTimes(1);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — ngOnInit: HTTP flag loading
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — ngOnInit: HTTP flag loading", () => {

   // Risk Point/Contract: siteAdmin and isOrgAdminOnly must reflect the actual API response.
   // If the subscriptions are removed, both stay at their class defaults (true), silently granting
   // every user site-admin behaviour in the UI.
   // Note: extra detectChanges+whenStable cycle lets MSW's async HTTP response propagate
   // through Angular's zone before assertions are made.
   it("should set siteAdmin=true and isOrgAdminOnly=false from the API response on init", async () => {
      server.use(
         http.get("*/api/em/navbar/isSiteAdmin",    () => HttpResponse.json(true)),
         http.get("*/api/em/navbar/isOrgAdminOnly", () => HttpResponse.json(false)),
      );

      const { comp } = await renderComponent({ model: makeModel() });

      await waitFor(() => {
         expect(comp.siteAdmin).toBe(true);
         expect(comp.isOrgAdminOnly).toBe(false);
      });
   });

   it("should set siteAdmin=false and isOrgAdminOnly=true from the API response on init", async () => {
      server.use(
         http.get("*/api/em/navbar/isSiteAdmin",    () => HttpResponse.json(false)),
         http.get("*/api/em/navbar/isOrgAdminOnly", () => HttpResponse.json(true)),
      );

      const { comp } = await renderComponent({ model: makeModel() });

      await waitFor(() => {
         expect(comp.siteAdmin).toBe(false);
         expect(comp.isOrgAdminOnly).toBe(true);
      });
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — onTableDataChange(): model mutation and event emission
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — onTableDataChange(): model mutation", () => {

   // Risk Point/Contract: All three model flags must be updated atomically. If changed=false is
   // left, the parent never persists. If hasOrgEdited=false, the table hides itself on the next
   // change-detection cycle, discarding the just-edited data.
   it("should update permissions, set hasOrgEdited and changed, and emit permissionChanged", async () => {
      const model = makeModel({ permissions: [] });
      const { comp } = await renderComponent({ model });

      const newPerms = [makePermission("user1"), makePermission("user2")];
      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.onTableDataChange(newPerms);

      expect(comp.model.permissions).toHaveLength(2);
      expect(comp.model.hasOrgEdited).toBe(true);
      expect(comp.model.changed).toBe(true);
      expect(emitted).toHaveLength(1);
      expect((emitted[0] as ResourcePermissionTableModel[]).length).toBe(2);
   });

   // Boundary: empty array is a valid explicit clear and must NOT be coerced to null.
   // null permissions triggers derive-permission semantics in the template, which is wrong here.
   it("should set model.permissions to [] (not null) and emit [] when table data is cleared", async () => {
      const model = makeModel({ permissions: [makePermission("existing")] });
      const { comp } = await renderComponent({ model });

      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.onTableDataChange([]);

      expect(comp.model.permissions).toEqual([]);
      expect(comp.model.permissions).not.toBeNull();
      expect(emitted[0]).toEqual([]);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — derivePermissionChange(): null vs empty semantics
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — derivePermissionChange(): checkbox semantics", () => {

   // 🔁 Regression-sensitive: derivePermissionChange(true) must set both permissions=null AND
   // hasOrgEdited=false together. The template gates table visibility on
   // `permissions != null || hasOrgEdited` — if hasOrgEdited stays true, the table renders
   // with a null dataSource and crashes the child component.
   it("should set permissions=null and hasOrgEdited=false and emit null when setToNull=true", async () => {
      const model = makeModel({ permissions: [makePermission("user1")] });
      const { comp } = await renderComponent({ model });

      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.derivePermissionChange(true);

      expect(comp.model.permissions).toBeNull();
      expect(comp.model.hasOrgEdited).toBe(false);
      expect(comp.model.changed).toBe(true);
      // Contract: always emits null — not the current permissions array
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBeNull();
   });

   // Risk Point/Contract: derivePermissionChange(false) must produce [] (not null) to signal
   // "explicit empty override", and must still emit null via permissionChanged — not the array.
   it("should set permissions=[] and hasOrgEdited=true and emit null when setToNull=false", async () => {
      const model = makeModel({ permissions: null, hasOrgEdited: false });
      const { comp } = await renderComponent({ model });

      const emitted: unknown[] = [];
      comp.permissionChanged.subscribe(v => emitted.push(v));

      comp.derivePermissionChange(false);

      expect(comp.model.permissions).toEqual([]);
      expect(comp.model.hasOrgEdited).toBe(true);
      expect(comp.model.changed).toBe(true);
      expect(emitted[0]).toBeNull(); // always null, regardless of path
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 6 [Risk 2] — addPermission(): dialog result forwarded to table
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — addPermission(): dialog result forwarded to table", () => {

   // Risk Point/Contract: Dialog results are mapped to {identityID, type} ONLY — actions are
   // intentionally excluded so the table assigns displayActions defaults. If the mapping changes
   // to include actions, newly added rows bypass the table's action-assignment logic.
   it("should call table.receiveSelection with mapped {identityID, type} when dialog returns a selection", async () => {
      const { comp, dialogSpy } = await renderComponent({ model: makeModel() });

      const dialogSelection = [
         { identityID: { name: "role1", orgID: "Org" }, type: IdentityType.ROLE, actions: [ResourceAction.ADMIN] },
      ];
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(dialogSelection) });

      const table = makeMockTable();
      comp.addPermission(table as any);

      expect(table.receiveSelection).toHaveBeenCalledTimes(1);
      const received: ResourcePermissionTableModel[] = table.receiveSelection.mock.calls[0][0];
      expect(received[0].identityID.name).toBe("role1");
      expect(received[0].type).toBe(IdentityType.ROLE);
      // actions must NOT be forwarded — the table assigns them
      expect((received[0] as any).actions).toBeUndefined();
   });

   // Risk Point/Contract: A falsy dialog result (cancel) must be a no-op.
   // Calling receiveSelection(undefined) would push undefined into dataSource, breaking the table.
   it("should NOT call table.receiveSelection when the dialog is cancelled (result is null)", async () => {
      const { comp, dialogSpy } = await renderComponent({ model: makeModel() });

      dialogSpy.open.mockReturnValue({ afterClosed: () => of(null) });
      const table = makeMockTable();
      comp.addPermission(table as any);

      expect(table.receiveSelection).not.toHaveBeenCalled();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 7 [Risk 2] — pasteBadgeLabel: format contract
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — pasteBadgeLabel: format contract", () => {

   // Risk Point/Contract: must return "" (empty string, not " (0)") when count=0 so the
   // template renders "_#(Paste Permissions)" without a trailing space or badge.
   it("should return empty string when pasteCount is 0", async () => {
      const { comp } = await renderComponent({
         model: makeModel(),
         clipboardCanPaste: true,
         clipboardCount: 0,
      });

      expect(comp.pasteBadgeLabel).toBe("");
   });

   it("should return \" (N)\" when pasteCount equals pasteTotal", async () => {
      const { comp } = await renderComponent({
         model: makeModel(),
         clipboardCanPaste: true,
         clipboardCount: 3,
         clipboardTotal: 3,
      });

      expect(comp.pasteBadgeLabel).toBe(" (3)");
   });

   // Why High Value: "(N of M)" alerts the user that action-filtering dropped some rows.
   // If total > count comparison is inverted, users see "(3 of 3)" instead of "(3)" — or
   // never see "of M" at all — and don't know rows were silently dropped.
   it("should return \" (N of M)\" when pasteCount is less than pasteTotal", async () => {
      const { comp } = await renderComponent({
         model: makeModel(),
         clipboardCanPaste: true,
         clipboardCount: 2,
         clipboardTotal: 5,
      });

      expect(comp.pasteBadgeLabel).toBe(" (2 of 5)");
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 8 [Risk 2] — copyPermissions(): selected vs all + provider/context
// ════════════════════════════════════════════════════════════════════════════

describe("ResourcePermissionComponent — copyPermissions(): selected vs all", () => {

   // Risk Point/Contract: Must copy the selection if present; otherwise copy full model.permissions.
   // Why High Value: Wrong source silently copies the wrong access rules, causing incorrect pastes
   // and mistaken security edits.
   it("should copy selected rows when selection is non-empty and show snackbar", async () => {
      const selected = [makePermission("sel1"), makePermission("sel2")];
      const model = makeModel({ permissions: [makePermission("other")] });
      const { comp, clipboardSpy, snackBarSpy } = await renderComponent({ model });

      const table = makeMockTable(selected);
      comp.copyPermissions(table as any);

      expect(clipboardSpy.copy).toHaveBeenCalledWith(
         selected,
         false,
         "TestProvider",
         COPY_PASTE_CONTEXT_REPOSITORY,
      );
      expect(snackBarSpy.open).toHaveBeenCalledWith(
         "_#(js:em.security.permissionsCopied)",
         null,
         expect.any(Object),
      );
   });

   it("should copy all model.permissions when selection is empty", async () => {
      const perms = [makePermission("p1"), makePermission("p2")];
      const model = makeModel({ permissions: perms });
      const { comp, clipboardSpy } = await renderComponent({ model });

      const table = makeMockTable([]); // nothing selected
      comp.copyPermissions(table as any);

      expect(clipboardSpy.copy).toHaveBeenCalledWith(
         perms,
         false,
         "TestProvider",
         COPY_PASTE_CONTEXT_REPOSITORY,
      );
   });

});

