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
 * AuditInactiveUserComponent — Testing Library style
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — fetchParameters: conditional duration update logic (off-by-one boundary)
 *   Group 2 — maxStartDuration / minEndDuration: Math.max/min getter semantics
 *   Group 3 — fetchData: HTTP param construction
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { HttpClientModule, HttpParams } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import { MatSelectStub, makeErrorServiceMock } from "../testing/audit-test-utils";

import { server } from "../../../../../../mocks/server";
import { AuditInactiveUserComponent } from "./audit-inactive-user.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { InactiveUserParameters } from "./inactive-user";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Minimal InactiveUserParameters returned by the parameters API. */
const MOCK_PARAMS: InactiveUserParameters = {
   hosts: ["srv-01"],
   minDuration: 5,
   maxDuration: 20,
   startTime: 0,
   endTime: 0,
};

const EMPTY_ADDITIONAL = {
   selectedHosts: [] as string[],
   minDuration: 0,
   maxDuration: 30,
};


/** Renders the component with NO_ERRORS_SCHEMA so em-audit-table-view is stubbed. */
async function renderComponent(errorService = makeErrorServiceMock()) {
   return render(AuditInactiveUserComponent, {
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
// Group 1: fetchParameters — conditional duration update logic
// ---------------------------------------------------------------------------

describe("AuditInactiveUserComponent — fetchParameters", () => {

   // P0 / Happy
   // The tap() side effect populates hosts and conditionally updates duration bounds.
   // maxDuration condition: this.maxDuration (30) > params.maxDuration + 1 (21)
   // → 30 > 21 is TRUE, and form.maxDuration (30) == this.maxDuration (30) → updates to 21.
   // minDuration condition: this.minDuration (0) < params.minDuration (5)
   // → 0 < 5 is TRUE, and form.minDuration (0) == this.minDuration (0) → updates to 5.
   it("should populate hosts and update duration bounds when API values cross the threshold", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/inactiveUserParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(comp.hosts).toEqual(["srv-01"]);
      // maxDuration updated: params.maxDuration + 1 = 21
      expect(comp.maxDuration).toBe(21);
      expect(comp.form.get("maxDuration").value).toBe(21);
      // minDuration updated: params.minDuration = 5
      expect(comp.minDuration).toBe(5);
      expect(comp.form.get("minDuration").value).toBe(5);
   });

   // P1 / Boundary — form manually changed blocks maxDuration update
   // If the user manually adjusts form.maxDuration (e.g. to 10), then
   // form.maxDuration (10) != this.maxDuration (30) → condition false → no update.
   it("should NOT update maxDuration when form value has been manually changed", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/inactiveUserParameters", () =>
            MswHttpResponse.json(MOCK_PARAMS)
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // Simulate user manually changing the maxDuration slider
      comp.form.get("maxDuration").setValue(10);

      await firstValueFrom(comp.fetchParameters());

      // maxDuration on component should remain 30 (the constructor default)
      expect(comp.maxDuration).toBe(30);
      expect(comp.form.get("maxDuration").value).toBe(10);
   });

   // P1 / Boundary — API maxDuration does NOT trigger update when not above threshold
   // Condition: this.maxDuration (30) > params.maxDuration + 1
   // If API returns maxDuration=30: 30 > 31 is FALSE → no update.
   it("should NOT update maxDuration when API maxDuration + 1 >= this.maxDuration", async () => {
      server.use(
         http.get("*/api/em/monitoring/audit/inactiveUserParameters", () =>
            MswHttpResponse.json({ ...MOCK_PARAMS, maxDuration: 30, minDuration: 0 })
         )
      );

      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      // 30 > 30 + 1 = 31 → false → no update
      expect(comp.maxDuration).toBe(30);
   });

   // P0 / Error
   // catchError feeds empty fallback through tap(). All lists must be empty, durations at 0.
   it("should call errorService.showSnackBar and set empty fallback on API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);

      server.use(
         http.get("*/api/em/monitoring/audit/inactiveUserParameters", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      const comp = fixture.componentInstance;

      await firstValueFrom(comp.fetchParameters());

      expect(errorService.showSnackBar).toHaveBeenCalledTimes(1);
      expect(comp.hosts).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: maxStartDuration / minEndDuration — Math.max/min getter semantics
// ---------------------------------------------------------------------------

describe("AuditInactiveUserComponent — duration getters", () => {

   // P1 / Happy
   // maxStartDuration = Math.max(form.maxDuration || 0, this.maxDuration)
   // When form.maxDuration (50) > this.maxDuration (30) → returns 50.
   it("should return Math.max of form.maxDuration and this.maxDuration for maxStartDuration", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("maxDuration").setValue(50);
      // this.maxDuration is still 30 (default)
      expect(comp.maxStartDuration).toBe(50);
   });

   // P1 / Happy
   // maxStartDuration = Math.max(form.maxDuration || 0, this.maxDuration)
   // When form.maxDuration (10) < this.maxDuration (30) → returns 30.
   it("should return this.maxDuration when it exceeds form.maxDuration in maxStartDuration", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      comp.form.get("maxDuration").setValue(10);
      expect(comp.maxStartDuration).toBe(30);
   });

   // P1 / Happy
   // minEndDuration = Math.min(form.minDuration || 0, this.minDuration)
   // When form.minDuration (0, default) == this.minDuration (0) → returns 0.
   // When form.minDuration set to 5 > this.minDuration (0) → returns 0.
   it("should return Math.min of form.minDuration and this.minDuration for minEndDuration", async () => {
      const { fixture } = await renderComponent();
      const comp = fixture.componentInstance;

      // minDuration defaults: form=0, component.minDuration=0
      expect(comp.minEndDuration).toBe(0);

      // Set form higher than component's minDuration — should still return component's 0
      comp.form.get("minDuration").setValue(5);
      expect(comp.minEndDuration).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: fetchData — HTTP param construction
// ---------------------------------------------------------------------------

describe("AuditInactiveUserComponent — fetchData", () => {

   // P0 / Error
   // fetchData's catchError returns safe empty list.
   it("should call errorService.showSnackBar and return empty rows on data API error", async () => {
      const errorService = makeErrorServiceMock();
      const { fixture } = await renderComponent(errorService);


      server.use(
         http.get("*/api/em/monitoring/audit/inactiveUsers", () =>
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
   // Hosts are appended as separate params (append(), not set()).
   it("should append each selected host as a separate query param entry", async () => {
      const receivedHosts: string[] = [];

      server.use(
         http.get("*/api/em/monitoring/audit/inactiveUsers", ({ request }) => {
            new URL(request.url).searchParams.forEach((value, key) => {
               if(key === "hosts") receivedHosts.push(value);
            });
            return MswHttpResponse.json({ totalRowCount: 0, rows: [] });
         })
      );

      const { fixture } = await renderComponent();
      await firstValueFrom(fixture.componentInstance.fetchData(new HttpParams(), {
         ...EMPTY_ADDITIONAL,
         selectedHosts: ["srv-01", "srv-02"],
      }));

      expect(receivedHosts).toEqual(["srv-01", "srv-02"]);
   });
});
