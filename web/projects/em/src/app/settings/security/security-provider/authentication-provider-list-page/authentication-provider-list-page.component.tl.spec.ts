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
 * AuthenticationProviderViewComponent — Testing Library style
 *
 * Coverage:
 *   Group 1 [Risk 3]  — removeProvider: index re-resolved by name at confirm time; poll cannot shift the target
 *   Group 2 [Risk 2]  — clearProviderCache: formatCacheAgeLabel applied to server response
 *   Group 3 [Risk 2]  — currentProvider: fetched once at init, never refreshed — stale logout warning
 *   Group 4 [Risk 2]  — reorder: out-of-bounds destination fires POST before server-side bounds check
 *   Group 5 [Risk 2]  — copyProvider: duplicate-name guard prevents double-push
 *   Group 6 [Risk 2]  — polling lifecycle: 5 s timer starts on init, stops cleanly on destroy
 *   Group 7 [Risk 3]  — drag to reorder: real DOM drag on the list saves through reorder() (Bug #78723)
 *
 * KEY contracts: DELETE endpoint is /remove-authentication-provider/${index} — positional, not name-based.
 *               Poll fires via timer(0, 5000) + concatMap and replaces the whole authenticationProviders reference.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Router } from "@angular/router";
import { render, waitFor } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { OrganizationDropdownService } from "../../../../navbar/organization-dropdown.service";
import { SecurityProviderStatus } from "../security-provider-model/security-provider-status-list";
import { AuthenticationProviderViewComponent } from "./authentication-provider-list-page.component";

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
   /** Providers returned by the initial poll GET. */
   initialProviders?: SecurityProviderStatus[];
   /** Provider returned by the get-current-authentication-provider GET. */
   currentProvider?: SecurityProviderStatus | null;
}

async function renderComponent(opts: RenderOpts = {}) {
   const dialogSpy = {
      open: vi.fn().mockReturnValue({ afterClosed: () => of(false) }),
   };
   const snackBarSpy = { open: vi.fn() };
   const orgDropdownSpy = { setProvider: vi.fn(), refreshProviders: vi.fn() };
   const appInfoSpy = { setLdapProviderUsed: vi.fn() };
   const windowOpenSpy = vi.spyOn(window, "open").mockImplementation(() => null);

   // Default MSW handlers satisfy the two GETs that ngOnInit fires immediately (timer at t=0).
   server.use(
      http.get("*/api/em/security/configured-authentication-providers", () =>
         HttpResponse.json({ providers: opts.initialProviders ?? [] }),
      ),
      http.get("*/api/em/security/get-current-authentication-provider", () =>
         HttpResponse.json(opts.currentProvider ?? null),
      ),
   );

   const result = await render(AuthenticationProviderViewComponent, {
      imports: [HttpClientModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: MatDialog, useValue: dialogSpy },
         { provide: MatSnackBar, useValue: snackBarSpy },
         { provide: Router, useValue: { navigate: vi.fn() } },
         { provide: AppInfoService, useValue: appInfoSpy },
         { provide: OrganizationDropdownService, useValue: orgDropdownSpy },
      ],
   });

   const comp = result.fixture.componentInstance as AuthenticationProviderViewComponent;
   // Do NOT await fixture.whenStable() — the 5 s repeating timer keeps the zone unstable.

   return { ...result, comp, dialogSpy, snackBarSpy, orgDropdownSpy, appInfoSpy, windowOpenSpy };
}

/** Wait for ngOnInit's first poll to populate the list; avoids manual assignments being cleared by timer(0). */
async function waitForProviderNames(
   comp: AuthenticationProviderViewComponent,
   names: string[],
): Promise<void> {
   await waitFor(() =>
      expect(comp.authenticationProviders?.map(p => p.name)).toEqual(names),
   );
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — removeProvider: stale-index after poll
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — removeProvider(): stale-index after poll", () => {

   // 🔁 Regression-sensitive: local splice must target the same row the DELETE request encodes
   it("should delete the provider at the given index and remove that entry from the local array", async () => {
      server.use(
         http.delete("*/api/em/security/remove-authentication-provider/1", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B"), makeProvider("C")];
      const { comp, dialogSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B", "C"]);
      comp["currentProvider"] = "";
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });

      comp.removeProvider(1);

      await waitFor(() => expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A", "C"]));
   });

   it("should not trigger logout when a non-current provider is removed", async () => {
      server.use(
         http.delete("*/api/em/security/remove-authentication-provider/1", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp, dialogSpy, windowOpenSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);
      comp["currentProvider"] = "A";
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });

      comp.removeProvider(1);

      await waitFor(() => expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A"]));
      expect(windowOpenSpy).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: same stale-cache contract as copyProvider above -- a removed
   // provider must disappear from the shared list too, otherwise it stays selectable on the
   // Users tab and the tree request 500s
   it("should refresh the shared provider list when a non-current provider is removed", async () => {
      server.use(
         http.delete("*/api/em/security/remove-authentication-provider/1", () => HttpResponse.json({})),
      );

      const { comp, dialogSpy, orgDropdownSpy } = await renderComponent({
         initialProviders: [makeProvider("A"), makeProvider("B")],
      });
      await waitForProviderNames(comp, ["A", "B"]);
      comp["currentProvider"] = "A";
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });
      orgDropdownSpy.refreshProviders.mockClear();

      comp.removeProvider(1);

      await waitFor(() => expect(orgDropdownSpy.refreshProviders).toHaveBeenCalled());
   });

   // 🔁 Regression-sensitive: logout must fire whenever the active provider is deleted
   it("should call window.open to force logout when the currently-active provider is removed", async () => {
      server.use(
         http.delete("*/api/em/security/remove-authentication-provider/1", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp, dialogSpy, windowOpenSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);
      comp["currentProvider"] = "B";
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });

      comp.removeProvider(1);

      await waitFor(() =>
         expect(windowOpenSpy).toHaveBeenCalledWith("../logout?fromEm=true", "_self"),
      );
   });

   it("should show snackBar and preserve the local array when the delete API returns an error", async () => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      server.use(
         http.delete("*/api/em/security/remove-authentication-provider/0", () =>
            HttpResponse.json({}, { status: 500, statusText: "Server Error" }),
         ),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp, dialogSpy, snackBarSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);
      dialogSpy.open.mockReturnValue({ afterClosed: () => of(true) });

      comp.removeProvider(0);

      await waitFor(() => expect(snackBarSpy.open).toHaveBeenCalled());
      expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A", "B"]);

      consoleErrorSpy.mockRestore();
   });

   // 🔁 Regression-sensitive: poll replacing authenticationProviders mid-dialog causes a silent wrong deletion
   // Risk Point/Contract: DELETE encodes the captured index; splice also uses it on the updated (post-poll) array
   // Why High Value: deletes the wrong provider server-side; the intended target remains in the UI with no error
   it("should remove the targeted provider even when poll shifts the array before the user confirms", async () => {
      const confirmSubject = new Subject<boolean>();

      server.use(
         http.delete("*/api/em/security/remove-authentication-provider/2", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B"), makeProvider("C")];
      const { comp, dialogSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B", "C"]);
      comp["currentProvider"] = "";
      dialogSpy.open.mockReturnValue({ afterClosed: () => confirmSubject.asObservable() });

      comp.removeProvider(1); // user targets "B" at index 1

      // Simulate poll replacing the array: "X" inserted at index 1, shifting "B" to index 2
      comp.authenticationProviders = [makeProvider("A"), makeProvider("X"), makeProvider("B"), makeProvider("C")];

      // User confirms the deletion
      confirmSubject.next(true);
      confirmSubject.complete();

      // Before fix: DELETE used stale index 1 and removed "X". After fix: index 2 is resolved by name — "B" must be absent.
      await waitFor(() =>
         expect(comp.authenticationProviders.map(p => p.name)).not.toContain("B"),
      );
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — clearProviderCache: missing formatCacheAgeLabel on response
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — clearProviderCache(): missing cacheAgeLabel format", () => {

   it("should replace the provider entry at the correct index after a successful cache clear", async () => {
      server.use(
         http.get("*/api/em/security/clear-authentication-provider/1", () =>
            HttpResponse.json(makeProvider("B", { cacheAge: 0, cacheAgeLabel: "0:00:00" })),
         ),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);

      comp.clearProviderCache(1);

      await waitFor(() => expect(comp.authenticationProviders[1].cacheAge).toBe(0));
      expect(comp.authenticationProviders[0].name).toBe("A"); // adjacent entry untouched
   });

   // 🔁 Regression-sensitive: poll map() calls formatCacheAgeLabel but clearProviderCache bypasses it
   // Risk Point/Contract: raw SecurityProviderStatus stored directly; cacheAgeLabel is undefined until next poll
   // Why High Value: cache-age column goes blank for up to 5 s after every user-initiated clear
   it("should set cacheAgeLabel on the provider entry returned by clearProviderCache", async () => {
      // Server returns raw status without cacheAgeLabel — as the real API would
      server.use(
         http.get("*/api/em/security/clear-authentication-provider/0", () =>
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

      // Wait for clear response to apply before asserting cacheAgeLabel (avoid false positives on stale values)
      await waitFor(() => expect(comp.authenticationProviders[0].cacheAge).toBe(1800000));
      // Bug: cacheAgeLabel is undefined because formatCacheAgeLabel was not called on the response
      expect(comp.authenticationProviders[0].cacheAgeLabel).toBeDefined();
   });

   it("should show snackBar when the clearProviderCache API fails", async () => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      server.use(
         http.get("*/api/em/security/clear-authentication-provider/0", () =>
            HttpResponse.json({}, { status: 503, statusText: "Service Unavailable" }),
         ),
      );

      const providers = [makeProvider("A", { cacheAge: 3600000, cacheAgeLabel: "1:00:00" })];
      const { comp, snackBarSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A"]);

      comp.clearProviderCache(0);

      await waitFor(() => expect(snackBarSpy.open).toHaveBeenCalled());
      expect(comp.authenticationProviders[0].cacheAge).toBe(3600000);
      expect(comp.authenticationProviders[0].cacheAgeLabel).toBe("1:00:00");

      consoleErrorSpy.mockRestore();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — currentProvider: init-only fetch, never refreshed
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — currentProvider: fetched once at init", () => {

   // 🔁 Regression-sensitive: logout gate in removeProvider reads comp["currentProvider"] directly;
   // it must be populated from the init GET before any remove operation
   it("should set currentProvider from the init GET so the logout path is correctly gated", async () => {
      const { comp } = await renderComponent({
         currentProvider: makeProvider("ProviderA"),
      });

      await waitFor(() => expect(comp["currentProvider"]).toBe("ProviderA"));
   });

   it("should set currentProvider to empty string when the server returns null", async () => {
      const { comp } = await renderComponent({ currentProvider: null });

      await waitFor(() => expect(comp["currentProvider"]).toBe(""));
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — reorder: valid swap and out-of-bounds POST
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — reorder(): valid swap and out-of-bounds POST", () => {

   // 🔁 Regression-sensitive: provider order is the auth fallback chain — a wrong swap breaks auth priority
   it("should swap adjacent providers in the local array and notify orgDropdownService", async () => {
      server.use(
         http.post("*/api/em/security/reorder-authentication-providers", () => HttpResponse.json({})),
      );

      const providers = [makeProvider("A"), makeProvider("B"), makeProvider("C")];
      const { comp, orgDropdownSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B", "C"]);

      comp.reorder([2, 1]);

      await waitFor(() =>
         expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A", "C", "B"]),
      );
      expect(orgDropdownSpy.setProvider).toHaveBeenCalledWith("A");
      expect(orgDropdownSpy.refreshProviders).toHaveBeenCalled();
   });

   // Risk Point/Contract: no pre-POST bounds guard; moveProviderUp(0) sends {source:0, destination:-1}
   // Why High Value: server receives an invalid reorder request; local if-block partially saves the UI
   it("should POST to server with out-of-bounds destination but skip local splice and orgDropdown update", async () => {
      let capturedBody: unknown;
      server.use(
         http.post("*/api/em/security/reorder-authentication-providers", async ({ request }) => {
            capturedBody = await request.json();
            return HttpResponse.json({});
         }),
      );

      const providers = [makeProvider("A"), makeProvider("B")];
      const { comp, orgDropdownSpy } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "B"]);

      comp.reorder([0, -1]); // equivalent to moveProviderUp(0)

      await waitFor(() => expect(capturedBody).toEqual({ source: 0, destination: -1 }));
      expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A", "B"]);
      expect(orgDropdownSpy.setProvider).not.toHaveBeenCalled();
      expect(orgDropdownSpy.refreshProviders).not.toHaveBeenCalled();
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — copyProvider: duplicate-name guard
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — copyProvider(): duplicate-name guard", () => {

   it("should append the copied provider to the list when the server returns a new name", async () => {
      server.use(
         http.get("*/api/em/security/copy-authentication-provider/A", () =>
            HttpResponse.json(makeProvider("A_copy")),
         ),
      );

      const providers = [makeProvider("A")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A"]);

      comp.copyProvider(0);

      await waitFor(() =>
         expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A", "A_copy"]),
      );
   });

   // 🔁 Regression-sensitive: the Users tab and page header read the provider list cached on
   // OrganizationDropdownService, not this page's local array. Without a refresh the copy is
   // invisible there for the rest of the browser session and selecting a stale name makes
   // get-security-tree-root fail (NPE on a null provider).
   it("should refresh the shared provider list after a successful copy", async () => {
      server.use(
         http.get("*/api/em/security/copy-authentication-provider/A", () =>
            HttpResponse.json(makeProvider("A_copy")),
         ),
      );

      const { comp, orgDropdownSpy } = await renderComponent({
         initialProviders: [makeProvider("A")],
      });
      await waitForProviderNames(comp, ["A"]);
      orgDropdownSpy.refreshProviders.mockClear();

      comp.copyProvider(0);

      await waitFor(() => expect(orgDropdownSpy.refreshProviders).toHaveBeenCalled());
   });

   // 🔁 Regression-sensitive: two rows with the same name break reorder and remove index arithmetic
   it("should not push the copy when a provider with the same name already exists in the list", async () => {
      server.use(
         http.get("*/api/em/security/copy-authentication-provider/A", () =>
            HttpResponse.json(makeProvider("A_copy")),
         ),
      );

      const providers = [makeProvider("A"), makeProvider("A_copy")];
      const { comp } = await renderComponent({ initialProviders: providers });
      await waitForProviderNames(comp, ["A", "A_copy"]);

      comp.copyProvider(0);

      await waitFor(() =>
         expect(comp.authenticationProviders.map(p => p.name)).toEqual(["A", "A_copy"]),
      );
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 6 [Risk 2] — polling lifecycle
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — polling lifecycle", () => {

   // 🔁 Regression-sensitive: ldap flag drives LDAP-specific UI; must be set on every successful poll
   it("should update authenticationProviders and set the ldap flag after the first poll response", async () => {
      const ldapProvider = makeProvider("LdapProv", { ldap: true });

      const { comp, appInfoSpy } = await renderComponent({
         initialProviders: [ldapProvider],
      });

      await waitFor(() => expect(comp.authenticationProviders?.[0]?.name).toBe("LdapProv"));
      expect(appInfoSpy.setLdapProviderUsed).toHaveBeenCalledWith(true);
   });

   // 🔁 Regression-sensitive: a leaked poll subscription overwrites state on the page the user navigated to
   // Why High Value: memory leak and stale overwrite of any subsequent page's data
   it("should stop polling on ngOnDestroy so no further HTTP requests are issued", async () => {
      const { comp, fixture } = await renderComponent({
         initialProviders: [makeProvider("A")],
      });
      await waitForProviderNames(comp, ["A"]);

      const destroy$ = comp["destroy$"];
      expect(destroy$.isStopped).toBe(false);

      // Let fixture.destroy trigger ngOnDestroy once; avoid double destroy after spy.next throwing ObjectUnsubscribedError
      fixture.destroy();

      expect(destroy$.isStopped).toBe(true);
   });
});

// ════════════════════════════════════════════════════════════════════════════
// Group 7 [Risk 3] — drag to reorder (Bug #78723), real DOM drag events on the rendered list
// ════════════════════════════════════════════════════════════════════════════

describe("AuthenticationProviderViewComponent — drag to reorder (Bug #78723)", () => {

   const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

   function fire(el: Element, type: string): void {
      const event: any = new Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(event, "dataTransfer", {
         value: { effectAllowed: "", dropEffect: "", setData: () => {}, getData: () => "" },
      });
      el.dispatchEvent(event);
   }

   const rowsOf = (root: Element) => Array.from(root.querySelectorAll("mat-list-item.list-item"));
   const namesOf = (root: Element) =>
      rowsOf(root).map(row => row.querySelector(".provider-name").textContent.trim());

   /** Drag row `from` over the rows in `over` and drop it; dragenter bubbles from the row's label. */
   function drag(root: Element, from: number, over: number[]): void {
      const source = rowsOf(root)[from];
      fire(source, "dragstart");
      over.forEach(i => fire(rowsOf(root)[i].querySelector(".provider-name"), "dragenter"));
      fire(source, "dragend");
   }

   /**
    * Renders the page with a server whose polls return the saved order and whose reorder POST
    * applies the move, as AuthenticationProviderService/AuthorizationProviderService do.
    */
   async function renderWithServer(initial: SecurityProviderStatus[], failPost = false) {
      const state = { list: initial.map(p => ({ ...p })), posts: [] as any[], polls: 0 };
      const result = await renderComponent({ initialProviders: initial });
      server.use(
         http.get("*/api/em/security/configured-authentication-providers", () => {
            state.polls++;
            return HttpResponse.json({ providers: state.list.map(p => ({ ...p })) });
         }),
         http.post("*/api/em/security/reorder-authentication-providers", async ({ request }) => {
            const body: any = await request.json();
            state.posts.push(body);

            if(failPost) {
               return HttpResponse.json({}, { status: 500, statusText: "Server Error" });
            }

            const [moved] = state.list.splice(body.source, 1);
            state.list.splice(body.destination, 0, moved);
            return HttpResponse.json({});
         }),
      );

      const root = result.fixture.nativeElement as Element;
      const model = () => result.comp.authenticationProviders?.map(p => p.name);
      // poll the DOM instead of waitFor; markForCheck because the page updates from async HTTP
      const settle = async (ms = 50) => {
         await sleep(ms);
         result.fixture.componentRef.changeDetectorRef.markForCheck();
         result.fixture.detectChanges();
      };
      const until = async (done: () => boolean, tries = 120) => {
         for(let i = 0; i < tries && !done(); i++) {
            await settle();
         }

         await settle(0);
      };

      await until(() => rowsOf(root).length === initial.length);
      return { ...result, root, model, settle, until, state };
   }

   const providers = () => [
      makeProvider("Primary"),
      makeProvider("Corp LDAP", { cacheEnabled: false, cacheAgeLabel: undefined }),
      makeProvider("Accounts", { label: "Konten" }),
   ];

   // 🔁 Regression-sensitive: the drag moved the row on screen but never saved the order
   it("should save the dragged order through the same reorder POST as Move Down, and keep the rows in the saved order", async () => {
      const { root, model, until, state, fixture } = await renderWithServer(providers());

      drag(root, 0, [1, 2]);
      // until the server accepts, the rows stay in the providers' order
      expect(namesOf(root)).toEqual(["Primary", "Corp LDAP", "Konten"]);

      await until(() => state.posts.length === 1 && model()?.[2] === "Primary");
      expect(state.posts).toEqual([{ source: 0, destination: 2 }]);
      expect(model()).toEqual(["Corp LDAP", "Accounts", "Primary"]);
      expect(namesOf(root)).toEqual(["Corp LDAP", "Konten", "Primary"]);

      // the next poll leaves the rows in the saved order, and Move Up/Down follow it
      const polls = state.polls;
      await until(() => state.polls > polls);
      expect(namesOf(root)).toEqual(["Corp LDAP", "Konten", "Primary"]);
      const upDisabled = rowsOf(root).map(row =>
         (row.querySelector("button[title='_#(Move Up)']") as HTMLButtonElement).disabled);
      expect(upDisabled).toEqual([true, false, false]);

      (rowsOf(root)[0].querySelector("button[title='_#(Move Down)']") as HTMLButtonElement).click();
      await until(() => state.posts.length === 2 && model()?.[0] === "Accounts");
      expect(state.posts[1]).toEqual({ source: 0, destination: 1 });
      expect(namesOf(root)).toEqual(["Konten", "Corp LDAP", "Primary"]);

      fixture.destroy();
   }, 20000);

   it("should not POST when the row is dropped where it started", async () => {
      const { root, settle, state, fixture } = await renderWithServer(providers());

      drag(root, 1, [2, 1]);
      await settle(200);

      expect(state.posts).toEqual([]);
      expect(namesOf(root)).toEqual(["Primary", "Corp LDAP", "Konten"]);

      fixture.destroy();
   });

   it("should leave the rows in the server's order when the reorder POST fails", async () => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      const { root, model, until, settle, state, fixture } = await renderWithServer(providers(), true);

      drag(root, 0, [1, 2]);
      await until(() => state.posts.length === 1);
      await settle(100);

      expect(state.posts).toEqual([{ source: 0, destination: 2 }]);
      expect(model()).toEqual(["Primary", "Corp LDAP", "Accounts"]);
      expect(namesOf(root)).toEqual(["Primary", "Corp LDAP", "Konten"]);

      fixture.destroy();
      consoleErrorSpy.mockRestore();
   });
});
