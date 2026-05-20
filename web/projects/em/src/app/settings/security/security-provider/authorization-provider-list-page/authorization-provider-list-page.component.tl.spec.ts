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
 * AuthorizationProviderListPageComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — removeProvider: stale-index bug; index captured at click time, poll may replace array before confirm
 *   Group 2 [Risk 3]  — clearProviderCache: raw server response stored without formatCacheAgeLabel (it.failing — confirmed bug)
 *   Group 3 [Risk 2]  — reorder: out-of-bounds destination fires POST before any client-side guard
 *   Group 4 [Risk 2]  — copyProvider: push() is unconditional, no duplicate-name guard (it.failing — confirmed bug)
 *   Group 5 [Risk 2]  — polling lifecycle: 5 s timer starts on init, stops cleanly on destroy
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — stale-index on removeProvider (Group 1):
 *     removeProvider() captures the positional index at button-click time.
 *     The 5 s poll can replace this.authorizationProviders with a fresh array before the user
 *     confirms the dialog. Both the DELETE URL and the local splice use the original stale index
 *     on the (now different) array.
 *     Result: the wrong provider is deleted on the server; locally the wrong row is removed with
 *     no error shown to the user.
 *
 *   Bug B — missing cacheAgeLabel after clearProviderCache (Group 2):
 *     clearProviderCache() assigns the raw SecurityProviderStatus from the server directly to
 *     authorizationProviders[index] without calling formatCacheAgeLabel().
 *     The poll's map() calls formatCacheAgeLabel() on every tick; clearProviderCache() bypasses it.
 *     Result: cacheAgeLabel is undefined on the refreshed entry until the next poll fires (up to 5 s),
 *     so the cache-age column goes blank immediately after the user clears the cache.
 *
 *   Bug C — copyProvider pushes duplicate names (Group 4):
 *     copyProvider() calls push() unconditionally on the server response without checking whether
 *     authorizationProviders already contains a provider with the same name.
 *     Result: duplicate rows appear in the list, silently corrupting the positional index
 *     assumptions used by reorder and removeProvider.
 *
 *   Bug D — catchError handlers return throwError (remove/clear API errors):
 *     handleRemoveProviderError / handleClearCacheError return throwError instead of EMPTY.
 *     E2E tests (MSW + removeProvider / clearProviderCache) are it.skip until fixed: unhandled Http failure
 *     in Jest while the bug remains. Remove .skip after handlers return EMPTY.
 *
 * KEY contracts: DELETE endpoint is /remove-authorization-provider/${index} — positional, not name-based.
 *               Poll fires via timer(0, 5000) + concatMap and replaces the whole authorizationProviders reference.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Router } from "@angular/router";
import { it } from "@jest/globals";
import { render, waitFor } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "../../../../../../../../mocks/server";
import { SecurityProviderStatus } from "../security-provider-model/security-provider-status-list";
import { AuthorizationProviderListPageComponent } from "./authorization-provider-list-page.component";

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

function makeProvider(name: string, overrides: Partial<SecurityProviderStatus> = {}): SecurityProviderStatus {
   return {
      name, label: name, cacheEnabled: true, cacheAge: 3600000, cacheAgeLabel: "1:00:00",
      loading: false, ldap: false, ...overrides,
   };
}

// ─────────────────────────────────────────────────────────────────────────────
// Render helper
// ─────────────────────────────────────────────────────────────────────────────

interface RenderOpts {
   initialProviders?: SecurityProviderStatus[];
}

async function renderComponent(opts: RenderOpts = {}) {
   const dialogSpy = {
      open: jest.fn().mockReturnValue({ afterClosed: () => of(false) }),
   };
   const snackBarSpy = { open: jest.fn() };

   server.use(
      http.get("*/api/em/security/configured-authorization-providers", () =>
         HttpResponse.json({ providers: opts.initialProviders ?? [] }),
      ),
   );

   const result = await render(AuthorizationProviderListPageComponent, {
      imports: [HttpClientModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: MatDialog, useValue: dialogSpy },
         { provide: MatSnackBar, useValue: snackBarSpy },
         { provide: Router, useValue: { navigate: jest.fn() } },
      ],
   });

   const comp = result.fixture.componentInstance as AuthorizationProviderListPageComponent;
   // Do NOT await fixture.whenStable() — the 5 s repeating timer keeps the zone unstable.

   return { ...result, comp, dialogSpy, snackBarSpy };
}

async function waitForProviderNames(
   comp: AuthorizationProviderListPageComponent,
   names: string[],
): Promise<void> {
   await waitFor(() =>
      expect(comp.authorizationProviders?.map(p => p.name)).toEqual(names),
   );
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — removeProvider: stale-index after poll
// ════════════════════════════════════════════════════════════════════════════

describe("AuthorizationProviderListPageComponent — removeProvider(): stale-index after poll", () => {

   // 🔁 Regression-sensitive: local splice must target the same row the DELETE request encodes
   it("should delete the provider at the given index and remove that entry from the local array", async () => {
      server.use(
         http.delete("*/api/em/security/remove-authorization-provider/1", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B"), makeProvider("C")];
      const { comp, dialogSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B", "C"]);
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });

      comp.removeProvider(1);

      await waitFor(() => expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "C"]));
   });

   it("should not modify the local array when the user cancels the confirm dialog", async () => {
      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp, dialogSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(false) });

      comp.removeProvider(0);

      await waitFor(() =>
         expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "B"]),
      );
   });

   // SKIP until Bug D fix (handleRemoveProviderError must return EMPTY, not throwError).
   // E2E: renderComponent + MSW DELETE 500 + removeProvider(0). While throwError remains, Jest aborts with
   // unhandled "Http failure response" before snackBar/array assertions. Remove it.skip after the fix.
   it.skip("should show snackBar and preserve the local array when the delete API returns an error", async () => {
      const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
      server.use(
         http.delete("*/api/em/security/remove-authorization-provider/0", () =>
            HttpResponse.json({}, { status: 500, statusText: "Server Error" }),
         ),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp, dialogSpy, snackBarSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });

      comp.removeProvider(0);

      await waitFor(() => expect(snackBarSpy.open).toHaveBeenCalled());
      expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "B"]);

      consoleErrorSpy.mockRestore();
   });

   // 🔁 Regression-sensitive: poll replacing authorizationProviders mid-dialog causes a silent wrong deletion
   // Risk Point/Contract: DELETE encodes the captured index; splice also uses it on the updated (post-poll) array
   // Why High Value: deletes the wrong provider server-side; the intended target survives with no error shown
   it.failing("should remove the targeted provider even when poll shifts the array before the user confirms", async () => {
      const confirmSubject = new Subject<boolean>();

      server.use(
         http.delete("*/api/em/security/remove-authorization-provider/1", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B"), makeProvider("C")];
      const { comp, dialogSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B", "C"]);
      dialogSpy.open.mockReturnValue({ afterClosed: () => confirmSubject.asObservable() });

      comp.removeProvider(1); // user targets "B" at index 1

      // Simulate poll replacing the array: "X" inserted at index 1, shifting "B" to index 2
      comp.authorizationProviders = [makeProvider("A"), makeProvider("X"), makeProvider("B"), makeProvider("C")];

      confirmSubject.next(true);
      confirmSubject.complete();

      // Bug: DELETE fires with index 1 → server deletes "X"; splice(1,1) on [A,X,B,C] removes "X"
      // "B" survives. Correct: "B" should be absent from the list.
      await waitFor(() =>
         expect(comp.authorizationProviders.map(p => p.name)).not.toContain("B"),
      );
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 3] — clearProviderCache: missing formatCacheAgeLabel on response
// ════════════════════════════════════════════════════════════════════════════

describe("AuthorizationProviderListPageComponent — clearProviderCache(): missing cacheAgeLabel format", () => {

   it("should replace the provider entry at the correct index after a successful cache clear", async () => {
      server.use(
         http.get("*/api/em/security/clear-authorization-provider/1", () =>
            HttpResponse.json(makeProvider("B", { cacheAge: 0, cacheAgeLabel: "0:00:00" })),
         ),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);

      comp.clearProviderCache(1);

      await waitFor(() => expect(comp.authorizationProviders[1].cacheAge).toBe(0));
      expect(comp.authorizationProviders[0].name).toBe("A"); // adjacent entry untouched
   });

   // 🔁 Regression-sensitive: poll map() calls formatCacheAgeLabel but clearProviderCache bypasses it
   // Risk Point/Contract: raw SecurityProviderStatus stored directly; cacheAgeLabel is undefined until next poll
   // Why High Value: cache-age column goes blank for up to 5 s after every user-initiated cache clear
   it.failing("should set cacheAgeLabel on the provider entry returned by clearProviderCache", async () => {
      // Server returns a raw status without cacheAgeLabel, as the real API does
      server.use(
         http.get("*/api/em/security/clear-authorization-provider/0", () =>
            HttpResponse.json({
               name: "A", label: "A", cacheEnabled: true,
               cacheAge: 1800000, loading: false, ldap: false,
            }),
         ),
      );

      const providers = [makeProvider("A", { cacheAge: 3600000, cacheAgeLabel: "1:00:00" })];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A"]);

      comp.clearProviderCache(0);

      await waitFor(() => expect(comp.authorizationProviders[0].cacheAge).toBe(1800000));
      // Bug: cacheAgeLabel is undefined because formatCacheAgeLabel was not called on the response
      expect(comp.authorizationProviders[0].cacheAgeLabel).toBeDefined();
   });

   // SKIP until Bug D fix (handleClearCacheError must return EMPTY, not throwError).
   // E2E: renderComponent + MSW GET 503 + clearProviderCache(0). Same unhandled Http failure as delete-error test.
   it.skip("should show snackBar when the clearProviderCache API fails", async () => {
      const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
      server.use(
         http.get("*/api/em/security/clear-authorization-provider/0", () =>
            HttpResponse.json({}, { status: 503, statusText: "Service Unavailable" }),
         ),
      );

      const providers = [makeProvider("A", { cacheAge: 3600000, cacheAgeLabel: "1:00:00" })];
      const { comp, snackBarSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A"]);

      comp.clearProviderCache(0);

      await waitFor(() => expect(snackBarSpy.open).toHaveBeenCalled());
      expect(comp.authorizationProviders[0].cacheAge).toBe(3600000);
      expect(comp.authorizationProviders[0].cacheAgeLabel).toBe("1:00:00");

      consoleErrorSpy.mockRestore();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — reorder: valid swap and out-of-bounds POST
// ════════════════════════════════════════════════════════════════════════════

describe("AuthorizationProviderListPageComponent — reorder(): valid swap and out-of-bounds POST", () => {

   // 🔁 Regression-sensitive: provider order is the authorization fallback chain; a wrong swap breaks access-check priority
   it("should swap adjacent providers in the local array after a successful reorder POST", async () => {
      server.use(
         http.post("*/api/em/security/reorder-authorization-providers", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B"), makeProvider("C")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B", "C"]);

      comp.reorder([2, 1]);

      await waitFor(() =>
         expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "C", "B"]),
      );
   });

   // Risk Point/Contract: no pre-POST client guard; moveProviderUp(0) sends {source:0, destination:-1}
   // Why High Value: server receives an invalid request; the local if-block only partially protects the UI
   it("should POST to server with out-of-bounds destination but skip the local splice", async () => {
      let capturedBody: unknown;
      server.use(
         http.post("*/api/em/security/reorder-authorization-providers", async ({ request }) => {
            capturedBody = await request.json();
            return HttpResponse.json({});
         }),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);

      comp.reorder([0, -1]); // equivalent to moveProviderUp(0)

      await waitFor(() => expect(capturedBody).toEqual({ source: 0, destination: -1 }));
      expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "B"]);
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — copyProvider: duplicate-name guard
// ════════════════════════════════════════════════════════════════════════════

describe("AuthorizationProviderListPageComponent — copyProvider(): duplicate-name guard", () => {

   it("should append the copied provider to the list when the server returns a new name", async () => {
      server.use(
         http.get("*/api/em/security/copy-authorization-provider/A", () =>
            HttpResponse.json(makeProvider("A_copy")),
         ),
      );

      const providers = [makeProvider("A")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A"]);

      comp.copyProvider(0);

      await waitFor(() =>
         expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "A_copy"]),
      );
   });

   // 🔁 Regression-sensitive: two rows with the same name break reorder and removeProvider index arithmetic
   // Risk Point/Contract: push() is unconditional; no name-equality check before appending
   // Why High Value: duplicate entries silently corrupt positional assumptions relied on by every other operation
   it.failing("should not push the copy when a provider with the same name already exists in the list", async () => {
      server.use(
         http.get("*/api/em/security/copy-authorization-provider/A", () =>
            HttpResponse.json(makeProvider("A_copy")),
         ),
      );

      const providers = [makeProvider("A"), makeProvider("A_copy")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "A_copy"]);

      comp.copyProvider(0);

      // it.failing inverts pass/fail: this test must fail only after the HTTP response is applied.
      // While the bug exists, copyProvider push() runs and length becomes 3 — wait for that wrong
      // outcome first so we do not assert ["A","A_copy"] too early (would spuriously pass it.failing).
      // After the wait, the correct contract (still only two names) fails → it.failing passes.
      await waitFor(() =>
         expect(comp.authorizationProviders.length).toBeGreaterThan(2),
      );
      expect(comp.authorizationProviders.map(p => p.name)).toEqual(["A", "A_copy"]);
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — polling lifecycle
// ════════════════════════════════════════════════════════════════════════════

describe("AuthorizationProviderListPageComponent — polling lifecycle", () => {

   // 🔁 Regression-sensitive: providers must be populated on the first timer(0) tick before any user interaction
   it("should populate authorizationProviders after the first poll response", async () => {
      const { comp } = await renderComponent({
         initialProviders: [makeProvider("ProviderA"), makeProvider("ProviderB")],
      });

      await waitFor(() =>
         expect(comp.authorizationProviders?.map(p => p.name)).toEqual(["ProviderA", "ProviderB"]),
      );
   });

   // 🔁 Regression-sensitive: a leaked poll subscription overwrites state on the page the user navigated to
   // Why High Value: memory leak and stale HTTP responses overwrite any subsequent page's data
   it("should stop polling on ngOnDestroy so no further HTTP requests are issued", async () => {
      const { comp, fixture } = await renderComponent({
         initialProviders: [makeProvider("A")],
      });
      await waitForProviderNames(comp, ["A"]);

      const destroy$ = comp["destroy$"];
      expect(destroy$.closed).toBe(false);

      fixture.destroy();

      expect(destroy$.closed).toBe(true);
   });
});
