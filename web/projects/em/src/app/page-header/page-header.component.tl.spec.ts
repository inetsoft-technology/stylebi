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
 * PageHeaderComponent — Angular Testing Library + MSW
 *
 * Covers gap #5 from docs/superpowers/specs/2026-06-25-permission-test-architecture-design.md:
 * the real "switch organization" flow lives here (changeOrg()/onSelectOrg()/the onRefresh-driven
 * external-change branch in refreshModel()), not in OrganizationDropdownService — that service is
 * just an auth-provider-status broadcaster plus a parameterless onOrgChange/notifyOrgChange()
 * signal. This file is the first test coverage for either file.
 *
 * The switcher this component renders (mat-select in the EM top toolbar) is site-admin-only by
 * construction: EmPageHeaderController.getPageHeaderModel() (community/core/.../pageheader/
 * EmPageHeaderController.java) only populates currOrgID/orgIDs when
 * OrganizationManager.isSiteAdmin(principal) is true; for any other caller currOrgID stays null,
 * which makes the component's own orgSelectVisible getter false. The component itself has no
 * "site admin" branch — it just renders whatever EmPageHeaderModel the backend sent — so the
 * "hides when currOrgID is empty" case below is what an org-admin/regular-user response looks
 * like from this component's point of view.
 *
 * mat-select/mat-autocomplete DOM interaction (opening the CDK overlay panel and clicking an
 * option) has no working precedent anywhere in this codebase's *.tl.spec.ts files, so switch
 * behavior is driven directly through the component's public methods (changeOrg()/onSelectOrg())
 * via fixture.componentInstance, matching the pattern already used in
 * audit-required-assets.component.tl.spec.ts. HTTP calls and router side effects are still
 * asserted for real via MSW and a Router useValue mock.
 */

import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { Router } from "@angular/router";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { render, screen, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { EMPTY, Subject, of } from "rxjs";

import { server } from "@test-mocks/server";
import { PageHeaderComponent } from "./page-header.component";
import { EmPageHeaderModel } from "./em-page-header-model";
import { OrganizationDropdownService } from "../navbar/organization-dropdown.service";
import { PageHeaderService } from "./page-header.service";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import { CustomRouteReuseStrategy } from "../custom-route-reuse-strategy";

const GET_MODEL_URL = "*/api/em/pageheader/get-pageheader-model";
const POST_ORG_URL = "*/api/em/pageheader/organization";

// EmPageHeaderModel.isMultiTenant is typed as `string` even though the backend sends a real JSON
// boolean (inetsoft.web.admin.pageheader.EmPageHeaderModel#isMultiTenant is `boolean`) and the
// frontend only ever uses it in a truthiness check (`model?.isMultiTenant && ...`). Accepting a
// real boolean here and casting keeps call sites readable without touching the (pre-existing,
// out of scope) frontend model type.
function makeModel(overrides: Omit<Partial<EmPageHeaderModel>, "isMultiTenant"> & { isMultiTenant?: boolean } = {}): EmPageHeaderModel {
   const { isMultiTenant = true, ...rest } = overrides;

   return {
      orgs: ["Acme", "Globex"],
      orgIDs: ["acme", "globex"],
      currOrgID: "acme",
      providerName: "",
      isMultiTenant: isMultiTenant as unknown as EmPageHeaderModel["isMultiTenant"],
      enterprise: true,
      ...rest,
   };
}

type OnRefreshEvent = { provider?: string; providerChanged?: boolean; renameOnly?: boolean };

interface RenderOptions {
   modelProvider: () => EmPageHeaderModel;
   orgVisible?: boolean;
   isEnterprise?: boolean;
   initialUrl?: string;
}

async function renderComponent(options: RenderOptions) {
   const { modelProvider, orgVisible = true, isEnterprise = true,
           initialUrl = "/settings/content/repository" } = options;

   server.use(
      http.get(GET_MODEL_URL, () => MswHttpResponse.json(modelProvider()))
   );

   const notifyOrgChange = vi.fn();
   const onRefreshSubject = new Subject<OnRefreshEvent>();
   const routerMock = {
      url: initialUrl,
      navigate: vi.fn(),
      navigateByUrl: vi.fn().mockResolvedValue(true),
      events: EMPTY,
   };
   const pageHeaderService = { title: "", currentOrgId: null as string | null, orgVisible };

   const { fixture } = await render(PageHeaderComponent, {
      componentInputs: { title: "" },
      providers: [
         provideHttpClient(),
         provideNoopAnimations(),
         { provide: Router, useValue: routerMock },
         { provide: AppInfoService, useValue: { isEnterprise: () => of(isEnterprise) } },
         { provide: PageHeaderService, useValue: pageHeaderService },
         {
            provide: OrganizationDropdownService,
            useValue: {
               getProvider: () => undefined,
               onRefresh: onRefreshSubject.asObservable(),
               notifyOrgChange,
            },
         },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   await fixture.whenStable();

   return {
      fixture,
      comp: fixture.componentInstance as PageHeaderComponent,
      routerMock,
      notifyOrgChange,
      onRefreshSubject,
      pageHeaderService,
   };
}

afterEach(() => {
   CustomRouteReuseStrategy.forceNoReuse = false;
});

describe("PageHeaderComponent — org switcher visibility (orgSelectVisible)", () => {

   it("shows the Select-Organization label and the org combobox when multiTenant + enterprise + orgVisible + currOrgID are all true", async () => {
      await renderComponent({ modelProvider: () => makeModel() });

      expect(await screen.findByText("_#(Select Organization):")).toBeTruthy();
      expect(await screen.findByRole("combobox")).toBeTruthy();
   });

   it("hides the org switcher when isMultiTenant is false", async () => {
      const { comp } = await renderComponent({ modelProvider: () => makeModel({ isMultiTenant: false }) });
      await waitFor(() => expect(comp.model).toBeTruthy());

      expect(screen.queryByText("_#(Select Organization):")).toBeNull();
      expect(screen.queryByRole("combobox")).toBeNull();
   });

   // This is what an org-admin/regular-user EmPageHeaderModel looks like: EmPageHeaderController
   // never assigns currOrgID for non-site-admins, so it stays empty/null and the switcher hides.
   it("hides the org switcher when currOrgID is empty", async () => {
      const { comp } = await renderComponent({ modelProvider: () => makeModel({ currOrgID: "" }) });
      await waitFor(() => expect(comp.model).toBeTruthy());

      expect(screen.queryByText("_#(Select Organization):")).toBeNull();
   });

   it("hides the org switcher when showOrgs()/pageTitle.orgVisible is false", async () => {
      const { comp } = await renderComponent({ modelProvider: () => makeModel(), orgVisible: false });
      await waitFor(() => expect(comp.model).toBeTruthy());

      expect(screen.queryByText("_#(Select Organization):")).toBeNull();
   });

   it("hides the org switcher when isEnterprise is false", async () => {
      const { comp } = await renderComponent({ modelProvider: () => makeModel(), isEnterprise: false });
      await waitFor(() => expect(comp.model).toBeTruthy());

      expect(screen.queryByText("_#(Select Organization):")).toBeNull();
   });
});

describe("PageHeaderComponent — changeOrg(): HTTP POST, optimistic update, notify, forced route reload", () => {

   it("POSTs the model with the newly selected currOrgID and optimistically updates pageTitle.currentOrgId before the request resolves", async () => {
      let capturedBody: EmPageHeaderModel | null = null;
      server.use(
         http.post(POST_ORG_URL, async ({ request }) => {
            capturedBody = await request.json() as EmPageHeaderModel;
            return MswHttpResponse.json({});
         })
      );

      const { comp, pageHeaderService } = await renderComponent({ modelProvider: () => makeModel() });
      await waitFor(() => expect(comp.model).toBeTruthy());

      comp.model.currOrgID = "globex";
      comp.changeOrg();

      // Optimistic: pageTitle.currentOrgId is set synchronously, before the POST resolves.
      expect(pageHeaderService.currentOrgId).toBe("globex");
      await waitFor(() => expect(capturedBody?.currOrgID).toBe("globex"));
   });

   it("after the POST resolves, notifies org change and force-reloads the current route", async () => {
      server.use(http.post(POST_ORG_URL, () => MswHttpResponse.json({})));

      const { comp, notifyOrgChange, routerMock } = await renderComponent({
         modelProvider: () => makeModel(),
         initialUrl: "/settings/content/repository",
      });
      await waitFor(() => expect(comp.model).toBeTruthy());

      comp.model.currOrgID = "globex";
      comp.changeOrg();

      await waitFor(() => expect(notifyOrgChange).toHaveBeenCalledTimes(1));
      expect(routerMock.navigateByUrl).toHaveBeenCalledWith("/", { skipLocationChange: true });
      await waitFor(() => expect(routerMock.navigate).toHaveBeenCalledWith(["/settings/content/repository"]));
   });

   it("preserves the URL fragment via {fragment, replaceUrl:true} when the current route has one", async () => {
      server.use(http.post(POST_ORG_URL, () => MswHttpResponse.json({})));

      const { comp, routerMock } = await renderComponent({
         modelProvider: () => makeModel(),
         initialUrl: "/settings/content/repository#tab2",
      });
      await waitFor(() => expect(comp.model).toBeTruthy());

      comp.model.currOrgID = "globex";
      comp.changeOrg();

      await waitFor(() => expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/settings/content/repository"], { fragment: "tab2", replaceUrl: true }
      ));
   });

   it("sets CustomRouteReuseStrategy.forceNoReuse before re-navigating, forcing the destination route to be recreated", async () => {
      server.use(http.post(POST_ORG_URL, () => MswHttpResponse.json({})));

      const { comp, routerMock } = await renderComponent({ modelProvider: () => makeModel() });
      await waitFor(() => expect(comp.model).toBeTruthy());

      expect(CustomRouteReuseStrategy.forceNoReuse).toBe(false);

      comp.model.currOrgID = "globex";
      comp.changeOrg();

      await waitFor(() => expect(routerMock.navigate).toHaveBeenCalled());
      expect(CustomRouteReuseStrategy.forceNoReuse).toBe(true);
   });
});

describe("PageHeaderComponent — onSelectOrg(): search-result selection guard", () => {

   it("selecting a different org from search results updates currOrgID, triggers changeOrg(), and resets the search box", async () => {
      let capturedBody: EmPageHeaderModel | null = null;
      server.use(
         http.post(POST_ORG_URL, async ({ request }) => {
            capturedBody = await request.json() as EmPageHeaderModel;
            return MswHttpResponse.json({});
         })
      );

      const { comp } = await renderComponent({ modelProvider: () => makeModel({ currOrgID: "acme" }) });
      await waitFor(() => expect(comp.model).toBeTruthy());

      comp.searchOpen = true;
      comp.searchQuery = "glob";
      comp.searchResults = [{ id: "globex", name: "Globex" }];

      comp.onSelectOrg({ option: { value: "Globex" } });

      expect(comp.model.currOrgID).toBe("globex");
      expect(comp.searchOpen).toBe(false);
      expect(comp.searchQuery).toBe("");
      await waitFor(() => expect(capturedBody?.currOrgID).toBe("globex"));
   });

   it("selecting the already-active org does not trigger a redundant POST, but still closes the search box", async () => {
      let postCallCount = 0;
      server.use(
         http.post(POST_ORG_URL, () => {
            postCallCount++;
            return MswHttpResponse.json({});
         })
      );

      const { comp, fixture } = await renderComponent({ modelProvider: () => makeModel({ currOrgID: "acme" }) });
      await waitFor(() => expect(comp.model).toBeTruthy());

      comp.searchOpen = true;
      comp.searchResults = [{ id: "acme", name: "Acme" }];
      comp.onSelectOrg({ option: { value: "Acme" } });

      await fixture.whenStable();
      expect(postCallCount).toBe(0);
      expect(comp.searchOpen).toBe(false);
      expect(comp.searchQuery).toBe("");
   });
});

describe("PageHeaderComponent — externally-detected org change via orgDropdownService.onRefresh", () => {

   it("when a refresh reports a different currOrgID (not a rename), it re-fetches, notifies org change, and force-reloads the route", async () => {
      let getCallCount = 0;
      const { comp, notifyOrgChange, routerMock, onRefreshSubject } = await renderComponent({
         modelProvider: () => {
            getCallCount++;
            return makeModel({ currOrgID: getCallCount === 1 ? "acme" : "globex" });
         },
      });
      await waitFor(() => expect(comp.model?.currOrgID).toBe("acme"));

      onRefreshSubject.next({ provider: "prov2", providerChanged: true, renameOnly: false });

      await waitFor(() => expect(comp.model?.currOrgID).toBe("globex"));
      await waitFor(() => expect(notifyOrgChange).toHaveBeenCalledTimes(1));
      expect(routerMock.navigateByUrl).toHaveBeenCalledWith("/", { skipLocationChange: true });
      await waitFor(() => expect(routerMock.navigate).toHaveBeenCalledWith(["/settings/content/repository"]));
   });

   it("when renameOnly is true, the model still refreshes but no notify/reload happens", async () => {
      let getCallCount = 0;
      const { comp, notifyOrgChange, routerMock, onRefreshSubject } = await renderComponent({
         modelProvider: () => {
            getCallCount++;
            return makeModel({ currOrgID: getCallCount === 1 ? "acme" : "globex" });
         },
      });
      await waitFor(() => expect(comp.model?.currOrgID).toBe("acme"));

      onRefreshSubject.next({ provider: "prov2", providerChanged: true, renameOnly: true });

      await waitFor(() => expect(comp.model?.currOrgID).toBe("globex"));
      expect(notifyOrgChange).not.toHaveBeenCalled();
      expect(routerMock.navigateByUrl).not.toHaveBeenCalled();
   });

   it("when the org did not actually change, no notify/reload happens", async () => {
      const { comp, notifyOrgChange, routerMock, onRefreshSubject, fixture } = await renderComponent({
         modelProvider: () => makeModel({ currOrgID: "acme" }),
      });
      await waitFor(() => expect(comp.model?.currOrgID).toBe("acme"));

      onRefreshSubject.next({ provider: "prov2", providerChanged: true, renameOnly: false });
      await fixture.whenStable();

      expect(notifyOrgChange).not.toHaveBeenCalled();
      expect(routerMock.navigateByUrl).not.toHaveBeenCalled();
   });
});
