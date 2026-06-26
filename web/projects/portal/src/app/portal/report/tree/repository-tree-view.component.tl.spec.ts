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
 * RepositoryTreeViewComponent — single pass (+race-condition + memory-leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnDestroy: subscriptions.unsubscribe() stops further callbacks
 *   Group 2 [Risk 3] — search(): only fires when searchString is non-empty/non-blank;
 *                       on success sets searchRootNode and searchMode=true; error → dialog
 *   Group 3 [Risk 2] — showFavoritesTree(): HTTP GET sets favoritesRootNode.expanded=true;
 *                       error → ComponentTool.showHttpError
 *   Group 4 [Risk 2] — selectedEntry setter → selectEntry: calls repositoryTree.selectAndExpandToPath
 *                       when repositoryTree+rootNode available; deselectAllNodes on null entry
 *   Group 5 [Risk 1] — ngOnInit: reads localStorage favorites-tree-mode to set favoritesMode
 *   Group 6 [Risk 1] — currentRootNode getter: three-branch dispatch (search / favorites / regular)
 *   Group 7 [Risk 1] — nodeSelected: sets _selectedEntry and emits entryOpened
 *   Group 8 [Risk 1] — clickFavoritesBtn: toggles favoritesMode, clears searchMode, saves localStorage
 *   Group 9 [Risk 1] — resetSearchMode: clears searchString and searchMode
 *   Group 10 [Risk 1] — searchStringChanged: calls resetSearchMode when searchString is falsy
 *   Group 11 [Risk 1] — updateRootNode: routes to favoritesRootNode or _rootNode based on mode
 *   Group 12 [Risk 1] — rootNode setter: sets loading flag correctly
 *   Group 13 [Risk 1] — refreshTree: dispatches to correct handler (favorites/search/repositoryTree)
 *
 * Suspected bugs (header only):
 *   Suspicion A — selectEntry: `this.currOrgID != entry.entry.organization` can be wrong when
 *     currOrgID is null and organization is also null; null != null is false, but this is likely
 *     the intended fallback behavior. No confirmed defect.
 *
 * Out of scope:
 *   ngAfterViewInit — calls selectEntry(this._selectedEntry) but repositoryTree ViewChild is not
 *     populated with NO_ERRORS_SCHEMA; same path tested via selectedEntry setter in Group 4.
 *   private selectEntry path via pageTabService.onRefreshPage — exercised in Group 1 subscription test.
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { Subject, of } from "rxjs";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../common/util/component-tool";
import { RepositoryTreeViewComponent } from "./repository-tree-view.component";
import { RepositoryTreeComponent } from "../../../widget/repository-tree/repository-tree.component";
import { PageTabService } from "../../../viewer/services/page-tab.service";
import { CurrentUserService } from "../../../../../../shared/util/current-user.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { RepositoryEntry } from "../../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { ReportTabModel } from "../report-tab-model";

// Stub cuts the RepositoryClientService → StompClientService → SsoHeartbeatService DI chain
@Component({ selector: "repository-tree", template: "", standalone: true })
class RepositoryTreeComponentStub {}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

const refreshPage$ = new Subject<any>();
const PAGE_TAB_SERVICE_MOCK = {
   onRefreshPage: refreshPage$,
};

const CURRENT_USER_SERVICE_MOCK = {
   getPortalCurrentUser: vi.fn().mockReturnValue(
      of({ name: { name: "admin", orgID: "host_org" } })
   ),
};

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const REPORT_MODEL: ReportTabModel = {
   expandAllNodes: false,
   showRepositoryAsList: false,
   searchEnabled: true,
   welcomePageUri: "",
   licensedComponentMsg: "",
   dragAndDrop: false,
};

const ROOT_NODE: TreeNodeModel = {
   label: "Repository",
   children: [],
   leaf: false,
   expanded: true,
   data: { path: "" },
};

function makeEntry(path: string, owner: string = null, orgID = "host_org"): RepositoryEntry {
   return {
      name: "entry",
      type: RepositoryEntryType.FOLDER,
      path,
      label: path,
      owner,
      entry: { identifier: "id1", organization: orgID } as any,
      htmlType: 0,
   } as any;
}

function makeRepoTreeMock() {
   return {
      selectAndExpandToPath: vi.fn(),
      refreshTree: vi.fn(),
      deselectAllNodes: vi.fn(),
   };
}

async function renderComponent(inputs: Partial<any> = {}) {
   const { fixture } = await render(RepositoryTreeViewComponent, {
      providers: [
         provideHttpClient(),
         { provide: PageTabService, useValue: PAGE_TAB_SERVICE_MOCK },
         { provide: CurrentUserService, useValue: CURRENT_USER_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      importOverrides: [
         { replace: RepositoryTreeComponent, with: RepositoryTreeComponentStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         model: REPORT_MODEL,
         openedEntrys: [],
         isMobile: false,
         ...inputs,
      },
   });
   return fixture.componentInstance as RepositoryTreeViewComponent;
}

beforeEach(() => {
   CURRENT_USER_SERVICE_MOCK.getPortalCurrentUser.mockClear();
   Object.values(MODAL_MOCK).forEach(m => typeof (m as any).mockClear === "function" && (m as any).mockClear());
});

afterEach(() => {
   vi.restoreAllMocks();
   localStorage.removeItem("__inetsoft__favorites-tree-mode");
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnDestroy: subscription cleanup
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnDestroy: unsubscribes all constructor subscriptions", () => {
   // 🔁 Regression-sensitive: two constructor subscriptions (currentUserService + pageTabService);
   //    both must stop firing after destroy to avoid writing to a stale component instance.
   it("should stop updating currOrgID after destroy — currentUserService subscription cleaned up", async () => {
      const userSubject = new Subject<any>();
      const dynamicUserMock = { getPortalCurrentUser: vi.fn().mockReturnValue(userSubject.asObservable()) };
      const { fixture } = await render(RepositoryTreeViewComponent, {
         providers: [
            provideHttpClient(),
            { provide: PageTabService, useValue: PAGE_TAB_SERVICE_MOCK },
            { provide: CurrentUserService, useValue: dynamicUserMock },
            { provide: NgbModal, useValue: MODAL_MOCK },
         ],
         importOverrides: [
            { replace: RepositoryTreeComponent, with: RepositoryTreeComponentStub },
         ],
         schemas: [NO_ERRORS_SCHEMA],
         componentInputs: { model: REPORT_MODEL, openedEntrys: [] },
      });
      const comp = fixture.componentInstance as RepositoryTreeViewComponent;

      // push first value before destroy
      userSubject.next({ name: { name: "alice", orgID: "org_a" } });
      expect((comp as any).currOrgID).toBe("org_a");

      fixture.destroy();  // triggers ngOnDestroy via Angular lifecycle (C6: never call ngOnDestroy directly)

      // push second value after destroy — must be ignored; comp reference is still valid in memory
      userSubject.next({ name: { name: "bob", orgID: "org_b" } });
      expect((comp as any).currOrgID).toBe("org_a");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — search(): conditional HTTP GET
// ---------------------------------------------------------------------------

describe("Group 2 — search(): fires only for non-blank searchString", () => {
   // 🔁 Regression-sensitive: empty-string search must not send a request — the guard
   //    is `!!this.searchString && this.searchString.trim()`.
   it("should set searchRootNode and searchMode=true on successful GET", async () => {
      const searchNode: TreeNodeModel = { label: "Results", children: [], leaf: true, data: {} };
      server.use(
         http.get("*/api/portal/tree/search", () => MswHttpResponse.json(searchNode))
      );
      const comp = await renderComponent();
      comp.searchString = "Sales";

      comp.search();

      await waitFor(() => expect(comp.searchMode).toBe(true));
      expect(comp.searchRootNode).toEqual(searchNode);
   });

   it("should NOT send a request when searchString is empty string", async () => {
      let requestFired = false;
      server.use(
         http.get("*/api/portal/tree/search", () => {
            requestFired = true;
            return MswHttpResponse.json({});
         })
      );
      const comp = await renderComponent();
      comp.searchString = "";

      comp.search();

      await Promise.resolve();
      expect(requestFired).toBe(false);
      expect(comp.searchMode).toBe(false);
   });

   it("should NOT send a request when searchString is whitespace only", async () => {
      let requestFired = false;
      server.use(
         http.get("*/api/portal/tree/search", () => {
            requestFired = true;
            return MswHttpResponse.json({});
         })
      );
      const comp = await renderComponent();
      comp.searchString = "   ";

      comp.search();

      await Promise.resolve();
      expect(requestFired).toBe(false);
   });

   it("should call showHttpError when search GET fails", async () => {
      server.use(
         http.get("*/api/portal/tree/search", () => new MswHttpResponse(null, { status: 500 }))
      );
      const errorSpy = vi.spyOn(ComponentTool, "showHttpError").mockImplementation(() => {});
      const comp = await renderComponent();
      comp.searchString = "Report";

      comp.search();

      await waitFor(() => expect(errorSpy).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 3 — showFavoritesTree(): HTTP GET
// ---------------------------------------------------------------------------

describe("Group 3 — showFavoritesTree(): fetches tree and sets expanded=true", () => {
   // 🔁 Regression-sensitive: the expanded flag must be forced to true after the HTTP response
   //    because the server may return expanded=false.
   it("should set favoritesRootNode and force expanded=true on successful GET", async () => {
      const favNode: TreeNodeModel = { label: "Favorites", children: [], leaf: false, expanded: false, data: {} };
      server.use(
         http.get("*/api/portal/tree", () => MswHttpResponse.json(favNode))
      );
      const comp = await renderComponent();

      comp.showFavoritesTree();

      await waitFor(() => expect(comp.favoritesRootNode).toBeTruthy());
      expect(comp.favoritesRootNode!.expanded).toBe(true);
   });

   it("should call showHttpError when favorites GET fails", async () => {
      server.use(
         http.get("*/api/portal/tree", () => new MswHttpResponse(null, { status: 500 }))
      );
      const errorSpy = vi.spyOn(ComponentTool, "showHttpError").mockImplementation(() => {});
      const comp = await renderComponent();

      comp.showFavoritesTree();

      await waitFor(() => expect(errorSpy).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 4 — selectedEntry setter: selectEntry dispatch
// ---------------------------------------------------------------------------

// WHY private bypass: `repositoryTree` is a @ViewChild not populated under NO_ERRORS_SCHEMA + stub;
// `_rootNode` is the backing field for the rootNode @Input setter (setter also triggers selectEntry).
// Both must be set directly to exercise the selectEntry guard `if(this.repositoryTree && this.rootNode)`.
describe("Group 4 — selectedEntry setter: calls repositoryTree methods", () => {
   it("should call selectAndExpandToPath when entry is set and repositoryTree+rootNode are available", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      (comp as any)._rootNode = ROOT_NODE;
      const entry = makeEntry("Reports/Sales");

      comp.selectedEntry = entry;

      expect(repoTree.selectAndExpandToPath).toHaveBeenCalledWith(
         "Reports/Sales",
         ROOT_NODE,
         expect.any(Boolean)
      );
   });

   it("should call deselectAllNodes when entry is null", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      (comp as any)._rootNode = ROOT_NODE;

      comp.selectedEntry = null;

      expect(repoTree.deselectAllNodes).toHaveBeenCalled();
      expect((comp as any)._selectedEntry).toBeNull();
   });

   it("should prepend MY_REPORTS prefix to path when entry has an owner and path does not start with it", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      (comp as any)._rootNode = ROOT_NODE;
      const entry = makeEntry("Sales", "alice");

      comp.selectedEntry = entry;

      const calledPath: string = repoTree.selectAndExpandToPath.mock.calls[0][0];
      expect(calledPath).toContain("My Dashboards");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — ngOnInit: localStorage favorites-tree-mode
// ---------------------------------------------------------------------------

describe("Group 5 — ngOnInit: reads localStorage to set favoritesMode", () => {
   it("should set favoritesMode=true and call showFavoritesTree when localStorage flag is 'true'", async () => {
      localStorage.setItem("__inetsoft__favorites-tree-mode", "true");

      server.use(
         http.get("*/api/portal/tree", () =>
            MswHttpResponse.json({ label: "Favorites", children: [], leaf: false, expanded: false, data: {} })
         )
      );
      const comp = await renderComponent();

      expect(comp.favoritesMode).toBe(true);
      await waitFor(() => expect(comp.favoritesRootNode).toBeTruthy());
   });

   it("should leave favoritesMode=false when localStorage flag is absent", async () => {
      const comp = await renderComponent();
      expect(comp.favoritesMode).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — currentRootNode getter: three-branch dispatch
// ---------------------------------------------------------------------------

describe("Group 6 — currentRootNode getter: returns correct node for each mode", () => {
   it("should return searchRootNode when searchMode=true", async () => {
      const comp = await renderComponent();
      const searchNode: TreeNodeModel = { label: "Search", children: [], data: {} };
      comp.searchRootNode = searchNode;
      comp.searchMode = true;

      expect(comp.currentRootNode).toBe(searchNode);
   });

   it("should return favoritesRootNode when favoritesMode=true (and not searchMode)", async () => {
      const comp = await renderComponent();
      const favNode: TreeNodeModel = { label: "Favorites", children: [], data: {} };
      comp.favoritesRootNode = favNode;
      comp.favoritesMode = true;
      comp.searchMode = false;

      expect(comp.currentRootNode).toBe(favNode);
   });

   it("should return rootNode when neither searchMode nor favoritesMode", async () => {
      const comp = await renderComponent({ rootNode: ROOT_NODE });
      comp.searchMode = false;
      comp.favoritesMode = false;

      expect(comp.currentRootNode).toBe(ROOT_NODE);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — nodeSelected: emits entryOpened
// ---------------------------------------------------------------------------

describe("Group 7 — nodeSelected: sets _selectedEntry and emits entryOpened", () => {
   it("should set _selectedEntry and emit entryOpened with the node data", async () => {
      const comp = await renderComponent();
      const entry = makeEntry("/reports/sales");
      const emitSpy = vi.spyOn(comp.entryOpened, "emit");

      comp.nodeSelected({ label: "Sales", data: entry, children: [] });

      expect((comp as any)._selectedEntry).toBe(entry);
      expect(emitSpy).toHaveBeenCalledWith(entry);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — clickFavoritesBtn: toggle, clear searchMode, persist localStorage
// ---------------------------------------------------------------------------

describe("Group 8 — clickFavoritesBtn: toggles favoritesMode and persists to localStorage", () => {
   it("should toggle favoritesMode from false to true and save to localStorage", async () => {
      const comp = await renderComponent();
      // Spy prevents HTTP call when the setTimeout fires; drain the queue inside the test
      // so the timer doesn't outlive the injector and cause an unhandled error.
      vi.spyOn(comp, "refreshTree").mockImplementation(() => {});
      expect(comp.favoritesMode).toBe(false);

      comp.clickFavoritesBtn();
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp.favoritesMode).toBe(true);
      expect(localStorage.getItem("__inetsoft__favorites-tree-mode")).toBe("true");
   });

   it("should clear searchMode when favorites button is clicked", async () => {
      const comp = await renderComponent();
      vi.spyOn(comp, "refreshTree").mockImplementation(() => {});
      comp.searchMode = true;

      comp.clickFavoritesBtn();
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp.searchMode).toBe(false);
   });

   it("should toggle favoritesMode from true to false and save 'false' to localStorage", async () => {
      const comp = await renderComponent();
      vi.spyOn(comp, "refreshTree").mockImplementation(() => {});
      comp.favoritesMode = true;

      comp.clickFavoritesBtn();
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp.favoritesMode).toBe(false);
      expect(localStorage.getItem("__inetsoft__favorites-tree-mode")).toBe("false");
   });

   it("should call showFavoritesTree after setTimeout when toggling on", async () => {
      server.use(
         http.get("*/api/portal/tree", () =>
            MswHttpResponse.json({ label: "Favorites", children: [], leaf: false, expanded: false, data: {} })
         )
      );
      const comp = await renderComponent();

      comp.clickFavoritesBtn();  // favoritesMode=true → setTimeout → showFavoritesTree

      await waitFor(() => expect(comp.favoritesRootNode).toBeTruthy());
      expect(comp.favoritesRootNode!.expanded).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — resetSearchMode: clears searchString and searchMode
// ---------------------------------------------------------------------------

describe("Group 9 — resetSearchMode: clears search state", () => {
   it("should set searchString=null and searchMode=false", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      // Drain setTimeout inside the test so it doesn't outlive the injector.
      vi.spyOn(comp, "refreshTree").mockImplementation(() => {});
      comp.searchString = "Report";
      comp.searchMode = true;

      comp.resetSearchMode();
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp.searchString).toBeNull();
      expect(comp.searchMode).toBe(false);
   });

   it("should call repositoryTree.refreshTree via setTimeout after clearing search mode", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      comp.searchMode = false;
      comp.favoritesMode = false;

      comp.resetSearchMode();

      await waitFor(() => expect(repoTree.refreshTree).toHaveBeenCalled());
   });
});

// ---------------------------------------------------------------------------
// Group 10 — searchStringChanged: conditional resetSearchMode call
// ---------------------------------------------------------------------------

describe("Group 10 — searchStringChanged: delegates to resetSearchMode when empty", () => {
   it("should call resetSearchMode when searchString is falsy", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      const spy = vi.spyOn(comp, "resetSearchMode");
      comp.searchString = null;

      comp.searchStringChanged();

      expect(spy).toHaveBeenCalled();
   });

   it("should NOT call resetSearchMode when searchString has content", async () => {
      const comp = await renderComponent();
      const spy = vi.spyOn(comp, "resetSearchMode");
      comp.searchString = "Report";

      comp.searchStringChanged();

      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 11 — updateRootNode: routes to favoritesRootNode or _rootNode
// ---------------------------------------------------------------------------

describe("Group 11 — updateRootNode: updates the correct node based on mode", () => {
   it("should update favoritesRootNode when favoritesMode=true", async () => {
      const comp = await renderComponent();
      comp.favoritesMode = true;
      const favNode: TreeNodeModel = { label: "Fav", children: [], data: {} };

      comp.updateRootNode(favNode);

      expect(comp.favoritesRootNode).toBe(favNode);
      expect((comp as any)._rootNode).not.toBe(favNode);
   });

   it("should update _rootNode when favoritesMode=false", async () => {
      const comp = await renderComponent();
      comp.favoritesMode = false;
      const regularNode: TreeNodeModel = { label: "Root", children: [], data: {} };

      comp.updateRootNode(regularNode);

      expect((comp as any)._rootNode).toBe(regularNode);
      expect(comp.favoritesRootNode).not.toBe(regularNode);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — rootNode setter: loading flag
// ---------------------------------------------------------------------------

describe("Group 12 — rootNode setter: loading state", () => {
   it("should set loading=false when a node is assigned", async () => {
      const comp = await renderComponent();
      comp.loading = true;

      comp.rootNode = ROOT_NODE;

      expect(comp.loading).toBe(false);
   });

   it("should set loading=true when null is assigned", async () => {
      const comp = await renderComponent();
      comp.loading = false;

      comp.rootNode = null;

      expect(comp.loading).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — refreshTree: dispatches to correct handler
// ---------------------------------------------------------------------------

describe("Group 13 — refreshTree: dispatches based on current mode", () => {
   it("should call showFavoritesTree when favoritesMode=true", async () => {
      const comp = await renderComponent();
      comp.favoritesMode = true;
      const spy = vi.spyOn(comp, "showFavoritesTree").mockImplementation(() => {});

      comp.refreshTree();

      expect(spy).toHaveBeenCalled();
   });

   it("should call search when searchMode=true (and favoritesMode=false)", async () => {
      const comp = await renderComponent();
      comp.favoritesMode = false;
      comp.searchMode = true;
      comp.searchString = "Report";
      const spy = vi.spyOn(comp, "search").mockImplementation(() => {});

      comp.refreshTree();

      expect(spy).toHaveBeenCalled();
   });

   it("should call repositoryTree.refreshTree when neither favoritesMode nor searchMode", async () => {
      const comp = await renderComponent();
      const repoTree = makeRepoTreeMock();
      (comp as any).repositoryTree = repoTree;
      comp.favoritesMode = false;
      comp.searchMode = false;

      comp.refreshTree();

      expect(repoTree.refreshTree).toHaveBeenCalled();
   });
});
