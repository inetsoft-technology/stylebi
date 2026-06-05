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
 * EditIdentityPaneComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnChanges(): selected identity type must map to the exact encoded API URL.
 *   Group 2 [Risk 2] - load errors: invalid organization shows server message; other failures show
 *                      the permission-denied fallback and emit null to the child view.
 *   Group 3 [Risk 2] - page edit proxy: child pageChanged event must be forwarded unchanged.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - Identity key format is convertToKey(identityID), then Tool.byteEncodeURLComponent().
 *   - USER/GROUP/ROLE endpoints are plural; ORGANIZATION endpoint is singular "organization".
 *   - handled HTTP load errors emit null through editModel$ after opening the error dialog.
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { MatDialog } from "@angular/material/dialog";
import { render } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { firstValueFrom, Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialogType } from "../../../../common/util/message-dialog";
import { SecurityTreeNode } from "../../security-tree-view/security-tree-node";
import { SecurityBusyService } from "../security-busy.service";
import { convertToKey, IdentityId } from "../identity-id";
import {
   EditGroupPaneModel,
   EditIdentityPaneModel,
   EditOrganizationPaneModel,
   EditRolePaneModel,
   EditUserPaneModel,
} from "./edit-identity-pane.model";
import { EditIdentityPaneComponent } from "./edit-identity-pane.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const makeIdentityId = (overrides: Partial<IdentityId> = {}): IdentityId => ({
   name: "Identity One",
   orgID: "Org/One",
   ...overrides,
});

const makeBaseModel = (
   overrides: Partial<EditIdentityPaneModel> = {},
): EditIdentityPaneModel => ({
   name: "Identity One",
   organization: "Org/One",
   root: false,
   identityNames: [],
   members: [],
   roles: [],
   permittedIdentities: [],
   editable: true,
   ...overrides,
});

const makeUserModel = (): EditUserPaneModel => ({
   ...makeBaseModel(),
   status: true,
   currentUser: false,
   localesList: [],
});

const makeGroupModel = (): EditGroupPaneModel => makeBaseModel();

const makeRoleModel = (): EditRolePaneModel => ({
   ...makeBaseModel(),
   defaultRole: false,
   isSysAdmin: false,
   isOrgAdmin: false,
});

const makeOrganizationModel = (): EditOrganizationPaneModel => ({
   ...makeBaseModel(),
   id: "org-one",
   properties: [],
   localesList: [],
   currentUserName: "admin",
});

async function renderComponent() {
   const dialogSpy = { open: vi.fn() };
   const busySpy = { orgLoading$: new Subject<boolean>() };

   const result = await render(EditIdentityPaneComponent, {
      providers: [
         provideHttpClient(),
         { provide: MatDialog, useValue: dialogSpy },
         { provide: SecurityBusyService, useValue: busySpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = result.fixture.componentInstance as EditIdentityPaneComponent;
   return { ...result, comp, dialogSpy, busySpy };
}

function loadIdentity(
   comp: EditIdentityPaneComponent,
   node: SecurityTreeNode,
   provider = "Provider/One",
): void {
   comp.provider = provider;
   comp.identityEditable = new Subject<boolean>();
   comp.selectedIdentity = node;
   comp.ngOnChanges({
      selectedIdentity: new SimpleChange(null, node, true),
   });
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - ngOnChanges URL contract
// ---------------------------------------------------------------------------

describe("EditIdentityPaneComponent - ngOnChanges(): API URL mapping", () => {

   // Regression-sensitive: each identity type maps to a different endpoint segment.
   // A plural/singular mismatch loads the wrong backend handler or returns 404.
   it.each([
      { label: "user", type: IdentityType.USER, segment: "users", model: makeUserModel() },
      { label: "group", type: IdentityType.GROUP, segment: "groups", model: makeGroupModel() },
      { label: "role", type: IdentityType.ROLE, segment: "roles", model: makeRoleModel() },
      { label: "organization", type: IdentityType.ORGANIZATION, segment: "organization", model: makeOrganizationModel() },
   ] satisfies Array<{
      label: string;
      type: IdentityType;
      segment: string;
      model: EditIdentityPaneModel;
   }>)("should load the encoded %s endpoint and expose the returned model", async ({ type, segment, model }) => {
      let capturedPath = "";
      server.use(
         http.get("*", ({ request }) => {
            capturedPath = new URL(request.url).pathname;
            return HttpResponse.json(model);
         }),
      );

      const { comp } = await renderComponent();
      const identityID = makeIdentityId();
      const provider = "Provider/One";

      loadIdentity(comp, new SecurityTreeNode(identityID, type), provider);
      const emitted = await firstValueFrom(comp.editModel$);

      const encodedProvider = Tool.byteEncodeURLComponent(provider);
      const encodedIdentity = Tool.byteEncodeURLComponent(convertToKey(identityID));
      expect(capturedPath).toBe(
         `/api/em/security/providers/${encodedProvider}/${segment}/${encodedIdentity}/`,
      );
      expect(emitted).toEqual(model);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] - load errors
// ---------------------------------------------------------------------------

describe("EditIdentityPaneComponent - load error dialog handling", () => {

   // Regression-sensitive: InvalidOrgException carries a user-actionable server message.
   // Replacing it with the generic permission message hides the real organization problem.
   it("should show the InvalidOrgException message and emit null when the load fails", async () => {
      server.use(
         http.get("*", () =>
            HttpResponse.json(
               { type: "InvalidOrgException", message: "Organization no longer exists" },
               { status: 400 },
            ),
         ),
      );

      const { comp, dialogSpy } = await renderComponent();
      loadIdentity(comp, new SecurityTreeNode(makeIdentityId(), IdentityType.USER));

      const emitted = await firstValueFrom(comp.editModel$);

      expect(emitted).toBeNull();
      expect(dialogSpy.open).toHaveBeenCalledTimes(1);
      expect(dialogSpy.open.mock.calls[0][1].data).toEqual(expect.objectContaining({
         title: "_#(js:Error)",
         content: "Organization no longer exists",
         type: MessageDialogType.ERROR,
      }));
   });

   // Regression-sensitive: non-org failures must not leak arbitrary server details into
   // this permission dialog; the UI contract is the localized permission-denied message.
   it("should show the permission fallback for non-InvalidOrg load errors", async () => {
      server.use(
         http.get("*", () =>
            HttpResponse.json(
               { type: "AccessDeniedException", message: "raw backend detail" },
               { status: 403 },
            ),
         ),
      );

      const { comp, dialogSpy } = await renderComponent();
      loadIdentity(comp, new SecurityTreeNode(makeIdentityId(), IdentityType.GROUP));

      const emitted = await firstValueFrom(comp.editModel$);

      expect(emitted).toBeNull();
      expect(dialogSpy.open).toHaveBeenCalledTimes(1);
      expect(dialogSpy.open.mock.calls[0][1].data.content)
         .toBe("_#(js:em.security.orgAdmin.identityPermissionDenied)");
      expect(dialogSpy.open.mock.calls[0][1].data.content).not.toBe("raw backend detail");
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - page edit proxy
// ---------------------------------------------------------------------------

describe("EditIdentityPaneComponent - page edit proxy", () => {

   // Regression-sensitive: parent dirty-state guards depend on the exact child value.
   // Dropping the false event leaves the parent thinking there are unsaved changes after save/reset.
   it("should forward onPageChanged values through pageEdited", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.pageEdited.subscribe(value => emitted.push(value));

      comp.onPageChanged();
      comp.onPageChanged(false);

      expect(emitted).toEqual([true, false]);
   });
});
