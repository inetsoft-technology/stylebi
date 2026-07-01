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
 * ReportTabComponent - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - constructor subscriptions and command wiring
 *   Group 2 [Risk 3] - init bootstrap, root loading, and current-user history
 *   Group 3 [Risk 2] - getters and opened-entry helpers
 *   Group 4 [Risk 2] - showEntry / reloadUrl navigation and history persistence
 *   Group 5 [Risk 2] - editViewsheet and collapseTree delegates
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - command stream / route streams -> helper subjects
 *   - ViewChild notifications/mobile view -> minimal helper stubs
 */

import { waitFor } from "@testing-library/angular";
import { NavigationEnd } from "@angular/router";
import { http, HttpResponse } from "msw";

import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { ContextType } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { server } from "@test-mocks/server";
import {
   asReportTabPrivateApi,
   attachReportTabMobileView,
   attachReportTabNotifications,
   createReportTabComponent,
   createReportTabModel,
   createReportTabRootNode,
   makeCurrentUser,
   makeReportEntry,
} from "./report-tab.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("ReportTabComponent - interaction", () => {
   describe("Group 1 - constructor subscriptions", () => {
      it("should connect the viewsheet client, set AI context, and react to route and notification streams", () => {
         const { comp, currentRouteService, notifications$, viewsheetClient, aiAssistantService } =
            createReportTabComponent();
         const notifications = attachReportTabNotifications(comp);
         const mobileView = attachReportTabMobileView(comp);
         const entry = makeReportEntry();

         currentRouteService.repositoryEntry$.next(entry);
         currentRouteService.repositoryUrl$.next("/wizard/path");
         notifications$.next({ type: "danger", content: "boom" });
         currentRouteService.repositoryEntry$.next(null);
         currentRouteService.repositoryUrl$.next("/close");

         expect(viewsheetClient.connect).toHaveBeenCalled();
         expect(aiAssistantService.setContextTypeFieldValue)
            .toHaveBeenCalledWith(ContextType.VIEWSHEET);
         expect(comp.wizardShown).toBe(false);
         expect(comp.selectedEntry).toBeNull();
         expect(mobileView.activePane).toBe("Repository");
         expect(notifications.danger).toHaveBeenCalledWith("boom");
         expect(comp.openedEntrys).toEqual([]);
      });

      it("should dispatch INFO message commands to notifications", () => {
         const { comp, viewsheetClient } = createReportTabComponent();
         const notifications = attachReportTabNotifications(comp);

         viewsheetClient.commands.next({
            type: "MessageCommand",
            assembly: null,
            command: {
               type: "INFO",
               message: "saved",
            } as MessageCommand,
         });

         expect(notifications.info).toHaveBeenCalledWith("saved");
      });

      it("should initialize again on NavigationEnd router events", () => {
         const { comp, router } = createReportTabComponent();
         const privateApi = asReportTabPrivateApi(comp);
         const initSpy = vi.spyOn(privateApi, "init").mockImplementation(() => {});

         router.events.next(new NavigationEnd(1, "/portal/tab/report", "/portal/tab/report"));

         expect(initSpy).toHaveBeenCalled();
      });
   });

   describe("Group 2 - init bootstrap", () => {
      it("ngOnInit should load the root tree and recently viewed entries for the current user", async () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
         vi.spyOn(LocalStorage, "getItem").mockReturnValue(JSON.stringify([makeReportEntry({ path: "Recent" })]));
         server.use(
            http.get("*/api/portal/get-current-user", () =>
               HttpResponse.json({ name: "alice" }),
            ),
         );
         const rootNode = createReportTabRootNode("Folder");
         const { comp, changeRef, route, repositoryTreeService } = createReportTabComponent({
            rootNode,
            reportTabModel: createReportTabModel({ collapseTree: true }),
         });

         comp.ngOnInit();

         await waitFor(() => expect(comp.currentUser?.name).toBe("alice"));
         expect(comp.mobile).toBe(false);
         expect(changeRef.detectChanges).toHaveBeenCalled();
         expect(comp.model.collapseTree).toBe(true);
         expect(comp.treePaneCollapsed).toBe(true);
         expect(repositoryTreeService.getRootFolder).toHaveBeenCalled();
         expect(comp.rootNode.expanded).toBe(true);
         expect(comp.recentlyViewed).toEqual([expect.objectContaining({ path: "Recent" })]);
      });

      it("init should expand the slash child when the service returns a None root", async () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
         vi.spyOn(LocalStorage, "getItem").mockReturnValue("[]");
         server.use(
            http.get("*/api/portal/get-current-user", () =>
               HttpResponse.json({ name: "alice" }),
            ),
         );
         const rootNode = createReportTabRootNode("None");
         const { comp } = createReportTabComponent({ rootNode });

         comp.ngOnInit();

         await waitFor(() => expect(comp.rootNode).toBe(rootNode));
         expect(comp.rootNode.children[0].expanded).toBe(true);
      });

      it("should show a connection error dialog when loading the current user fails", async () => {
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve());
         server.use(
            http.get("*/api/portal/get-current-user", () =>
               HttpResponse.json({}, { status: 500 }),
            ),
         );
         const { comp } = createReportTabComponent();

         comp.ngOnInit();

         await waitFor(() =>
            expect(messageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Error)",
               "_#(js:Connection failed)",
            ),
         );
      });
   });

   describe("Group 3 - getters and opened-entry helpers", () => {
      it("should return true for childRouteShown when selectedEntry is set", () => {
         const { comp } = createReportTabComponent();
         comp.selectedEntry = makeReportEntry();

         expect(comp.childRouteShown).toBe(true);
      });

      it("should return 'list' for viewType when showRepositoryAsList is true", () => {
         const { comp } = createReportTabComponent({
            reportTabModel: createReportTabModel({ showRepositoryAsList: true }),
         });
         comp.model = createReportTabModel({ showRepositoryAsList: true });

         expect(comp.viewType).toBe("list");
      });

      it("should return true for historyBarEnabled from the history bar service", () => {
         const { comp } = createReportTabComponent();

         expect(comp.historyBarEnabled).toBe(true);
      });

      it("should return null from getAssemblyName", () => {
         const { comp } = createReportTabComponent();

         expect(comp.getAssemblyName()).toBeNull();
      });

      it("should detect whether an entry is already open", () => {
         const { comp } = createReportTabComponent();
         const privateApi = asReportTabPrivateApi(comp);
         const entry = makeReportEntry();
         comp.openedEntrys = [entry];

         expect(privateApi.isEntryOpened(entry)).toBe(true);
         expect(privateApi.isEntryOpened(makeReportEntry({ path: "Other" }))).toBe(false);
      });
   });

   describe("Group 4 - showEntry and history", () => {
      it("showEntry should navigate to a viewsheet entry and add it to recently viewed", () => {
         const { comp, router, hideNavService } = createReportTabComponent();
         const entry = makeReportEntry();
         comp.recentlyViewed = [];
         comp.currentUser = makeCurrentUser();
         vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});

         comp.showEntry(entry);

         expect(router.navigate).toHaveBeenCalledWith(
            ["/portal/tab/report/vs/view/vs-sales"],
            { queryParams: { hiddenNav: "true" } },
         );
         expect(hideNavService.appendParameter).toHaveBeenCalled();
         expect(comp.openedEntrys).toEqual([entry]);
         expect(comp.recentlyViewed[0]).toBe(entry);
      });

      it("showEntry should confirm before reloading an actively loading viewsheet", async () => {
         const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
            .mockReturnValue(Promise.resolve("ok"));
         const { comp, router, assetLoadingService } = createReportTabComponent();
         const privateApi = asReportTabPrivateApi(comp);
         const reloadSpy = vi.spyOn(privateApi, "reloadUrl").mockImplementation(() => {});
         const entry = makeReportEntry();
         comp.currentUser = makeCurrentUser();
         comp.recentlyViewed = [];
         comp.selectedEntry = entry;
         vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});
         assetLoadingService.isLoading.mockReturnValue(true);

         comp.showEntry(entry);
         await Promise.resolve();

         expect(confirmSpy).toHaveBeenCalled();
         expect(reloadSpy).toHaveBeenCalledWith(
            "/portal/tab/report/vs/view/vs-sales",
            { queryParams: { hiddenNav: "true" } },
         );
         expect(router.navigate).not.toHaveBeenCalled();
      });

      it("reloadUrl should navigate through the temporary report route before the target route", async () => {
         const { comp, router } = createReportTabComponent();
         const privateApi = asReportTabPrivateApi(comp);

         privateApi.reloadUrl("/portal/tab/report/vs/view/vs-sales", {
            queryParams: { hiddenNav: "true" },
         });
         await Promise.resolve();

         expect(router.navigate).toHaveBeenNthCalledWith(1, ["/portal/tab/report"], {
            queryParams: { hideWelcomePage: "true" },
            skipLocationChange: true,
         });
         expect(router.navigate).toHaveBeenNthCalledWith(2, ["/portal/tab/report/vs/view/vs-sales"], {
            queryParams: { hiddenNav: "true" },
         });
      });

      it("addRecentlyViewed should dedupe entries and trim the list to five items", () => {
         vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});
         const { comp } = createReportTabComponent();
         comp.currentUser = makeCurrentUser();
         comp.recentlyViewed = [
            makeReportEntry({ path: "one" }),
            makeReportEntry({ path: "two" }),
            makeReportEntry({ path: "three" }),
            makeReportEntry({ path: "four" }),
            makeReportEntry({ path: "five" }),
         ];

         comp.addRecentlyViewed(makeReportEntry({ path: "three" }));
         comp.addRecentlyViewed(makeReportEntry({ path: "six" }));

         expect(comp.recentlyViewed.map(entry => entry.path)).toEqual([
            "six",
            "three",
            "one",
            "two",
            "four",
         ]);
         expect(LocalStorage.setItem).toHaveBeenCalled();
      });
   });

   describe("Group 5 - delegate helpers", () => {
      it("editViewsheet should send a composer event when composer is already open", () => {
         const { comp, composerService, viewsheetClient } = createReportTabComponent();
         composerService.composerOpen.next(true);

         comp.editViewsheet(makeReportEntry());

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/composer/editViewsheet",
            expect.objectContaining({ vsId: "vs-sales" }),
         );
      });

      it("editViewsheet should open a composer tab when composer is closed", () => {
         const openSpy = vi.spyOn(GuiTool, "openBrowserTab").mockImplementation(() => {});
         const { comp } = createReportTabComponent();

         comp.editViewsheet(makeReportEntry());

         expect(openSpy).toHaveBeenCalledWith(
            "composer",
            expect.objectContaining({ get: expect.any(Function) }),
         );
      });

      it("collapseTree should persist treePaneCollapsed and notify the service", () => {
         const { comp, collapseTreeService } = createReportTabComponent();

         comp.collapseTree(true);

         expect(comp.treePaneCollapsed).toBe(true);
         expect(collapseTreeService.collapseTree).toHaveBeenCalledWith(true);
      });
   });
});
