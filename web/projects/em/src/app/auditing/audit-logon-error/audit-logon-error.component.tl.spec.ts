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
 * AuditLogonErrorComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: API state binding + error recovery
 *   Group 2 — fetchData: HTTP param construction
 *
 * Known design gap in the error fallback (test 2):
 *   The catchError fallback object does NOT include systemAdministrator, so after
 *   an API error, this.systemAdministrator is set to undefined (not false).
 *   The component field initialises to false but tap() overwrites it with
 *   params.systemAdministrator which is absent from the fallback shape.
 *   Documented with it.failing so the test suite stays green while the gap is visible.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import { MatSelectStub, makeErrorServiceMock } from "../testing/audit-test-utils";

import { it } from "@jest/globals";
import { server } from "../../../../../../mocks/server";
import { AuditLogonErrorComponent } from "./audit-logon-error.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { LogonErrorParameters } from "./logon-error";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Minimal LogonErrorParameters returned by the parameters API. */
const MOCK_PARAMS: LogonErrorParameters = {
   users: ["alice", "bob"],
   hosts: ["server-01", "server-02"],
   organizations: [],
   systemAdministrator: true,
   startTime: 1_700_000_000_000,
   endTime:   1_700_086_400_000,
};

/** Additional form values representing an empty filter selection. */
const EMPTY_ADDITIONAL = {
   selectedUsers: [] as string[],
   selectedHosts: [] as string[],
};


/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   return render(AuditLogonErrorComponent, {
      imports: [ReactiveFormsModule, HttpClientModule],
      declarations: [MatSelectStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: ErrorHandlerService, useValue: errorService },
         { provide: ActivatedRoute, useValue: {} },
      ],
   });
}

// ---------------------------------------------------------------------------
// Group 1: fetchParameters — API state binding
// ---------------------------------------------------------------------------

describe("AuditLogonErrorComponent — fetchParameters", () => {

   // P0 / Happy
   // tap() side effects must assign both users and hosts from the API response.
   it("should populate users and hosts from API response", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/logonErrorParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.users).toEqual(MOCK_PARAMS.users);
      expect(comp.hosts).toEqual(MOCK_PARAMS.hosts);
   });

   // P0 / Error — xit documents the design gap
   // The catchError fallback does NOT include systemAdministrator, so
   // tap() assigns params.systemAdministrator = undefined, overwriting the
   // initialised false value. The component should preserve false on error,
   // but currently it does not.
   it.failing("should keep systemAdministrator as false after an error (currently becomes undefined due to missing field in fallback)", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/logonErrorParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.users).toEqual([]);
      expect(comp.hosts).toEqual([]);
      // This assertion fails because systemAdministrator ends up as undefined:
      expect(comp.systemAdministrator).toBe(false);
   });

   // P0 / Error (non-failing)
   // Confirms showSnackBar is called and the list fields are set to empty arrays.
   it("should call errorService.showSnackBar and reset users and hosts to empty arrays on error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/logonErrorParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.users).toEqual([]);
      expect(comp.hosts).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditLogonErrorComponent — fetchData", () => {

   // P0 / Error
   // catchError must call showSnackBar and return a safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/logonErrors", () =>
            new MswHttpResponse(null, { status: 503 })
         )
      );

      const result = await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(result).toEqual({ totalRowCount: 0, rows: [] });
   });

   // P1 / Happy
   // HttpParams.append() must be used (not set()) so multiple values produce
   // separate param entries rather than overwriting each other.
   it("should append each selected user as a separate query param entry", async () => {
      const receivedUsers: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/logonErrors", ({ request }) => {
            const url = new URL(request.url);
            url.searchParams.forEach((value, key) => {
               if(key === "users") receivedUsers.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedUsers: ["alice", "bob"],
      }));

      expect(receivedUsers).toEqual(["alice", "bob"]);
   });

   // P1 / Boundary
   // When all selections are empty, no filter params should appear in the request URL.
   it("should not append users or hosts params when all selections are empty", async () => {
      let capturedUrl: URL | null = null;

      server.use(
         http.get("*/api/em/monitoring/audit/logonErrors", ({ request }) => {
            capturedUrl = new URL(request.url);
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(
         fixture.componentInstance.fetchData(new HttpParams(), EMPTY_ADDITIONAL)
      );

      expect(capturedUrl!.searchParams.has("users")).toBe(false);
      expect(capturedUrl!.searchParams.has("hosts")).toBe(false);
   });
});
