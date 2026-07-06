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
 * RepositoryTreeComponent — Single Pass (+ Concurrency + Memory Leak)
 *
 * Coverage plan:
 *   Group 1  — ngOnInit: connects repository client, subscribes to changes
 *   Group 2  — ngOnDestroy / memory leak: disconnects repository client
 *   Group 3  — selectNode: guard against same node, emit nodeSelected
 *   Group 4  — nodeExpanded: early-exit cases and HTTP fetch
 *   Group 5  — getEntryIcon: classType → CSS class mapping
 *   Group 6  — getCSSIcon: delegates to repositoryTreeService
 *   Group 7  — getEntryLabel: returns HTML string with icon span and label
 *   Group 8  — hasMenu / hasMenuFunction: action visibility
 *   Group 9  — updateScrollPos / ngAfterViewChecked: LocalStorage scroll restore
 *   Group 10 [Concurrency] — refreshTree: searchMode guard + debounce
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { vi } from "vitest";

import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { RepositoryClientService } from "../../common/repository-client/repository-client.service";
import { DomService } from "../dom-service/dom.service";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../services/debounce.service";
import { DragService } from "../services/drag.service";
import { ModelService } from "../services/model.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeComponent } from "../tree/tree.component";
import { RepositoryTreeAction } from "./repository-tree-action.enum";
import { RepositoryTreeComponent } from "./repository-tree.component";
import { RepositoryTreeService } from "./repository-tree.service";

// ---------------------------------------------------------------------------
// Stub — prevents TreeComponent's heavy DI chain from bootstrapping
// ---------------------------------------------------------------------------

@Component({ selector: "tree-component", template: "", standalone: true })
class TreeComponentStub {}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeEntry(overrides: Partial<RepositoryEntry> = {}): RepositoryEntry {
   return {
      name: "TestEntry",
      path: "/TestEntry",
      type: RepositoryEntryType.VIEWSHEET,
      label: "TestEntry",
      owner: null,
      entry: null,
      classType: "ViewsheetEntry",
      htmlType: 0,
      op: [],
      fileFolder: false,
      favoritesUser: false,
      defaultOrgAsset: false,
      ...overrides,
   } as RepositoryEntry;
}

function makeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "Node",
      data: makeEntry(),
      leaf: true,
      children: [],
      ...overrides,
   };
}

function makeFolder(children: TreeNodeModel[] = [], overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "Root",
      data: makeEntry({ type: RepositoryEntryType.FOLDER, classType: "RepletFolderEntry", path: "/" }),
      leaf: false,
      expanded: false,
      children,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Mock state — reset before each test
// ---------------------------------------------------------------------------

let repositoryChangedSubject: Subject<any>;
let repositoryClientMock: {
   connect: ReturnType<typeof vi.fn>;
   disconnect: ReturnType<typeof vi.fn>;
   repositoryChanged: ReturnType<Subject<any>["asObservable"]>;
};
let repositoryTreeServiceMock: {
   getFolder: ReturnType<typeof vi.fn>;
   getCSSIcon: ReturnType<typeof vi.fn>;
};
let debounceServiceMock: { debounce: ReturnType<typeof vi.fn> };

function resetMocks() {
   repositoryChangedSubject = new Subject<any>();
   repositoryClientMock = {
      connect: vi.fn(),
      // Complete the Subject so the component's subscription is torn down after destroy.
      disconnect: vi.fn().mockImplementation(() => repositoryChangedSubject.complete()),
      repositoryChanged: repositoryChangedSubject.asObservable(),
   };
   repositoryTreeServiceMock = {
      getFolder: vi.fn().mockReturnValue(of({ children: [], data: { path: "/" } })),
      getCSSIcon: vi.fn().mockReturnValue("viewsheet-icon"),
   };
   debounceServiceMock = {
      debounce: vi.fn(),
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderRepositoryTree(inputs: Record<string, any> = {}) {
   const result = await render(RepositoryTreeComponent, {
      inputs: {
         root: makeFolder([makeNode()]),
         ...inputs,
      },
      componentProviders: [
         { provide: RepositoryClientService, useValue: repositoryClientMock },
      ],
      providers: [
         { provide: RepositoryTreeService, useValue: repositoryTreeServiceMock },
         { provide: DragService, useValue: { getDragData: vi.fn().mockReturnValue({}), reset: vi.fn(), put: vi.fn() } },
         { provide: DebounceService, useValue: debounceServiceMock },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: ModelService, useValue: { getModel: vi.fn().mockReturnValue(of({})), putModel: vi.fn().mockReturnValue(of({})), sendModel: vi.fn().mockReturnValue(of({})) } },
         { provide: Router, useValue: { navigate: vi.fn(), events: of(), routerState: { root: {} } } },
         { provide: FixedDropdownService, useValue: { open: vi.fn().mockReturnValue({ componentInstance: {} }) } },
         { provide: DomService, useValue: {} },
      ],
      importOverrides: [
         { replace: TreeComponent, with: TreeComponentStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return {
      comp: result.fixture.componentInstance as RepositoryTreeComponent,
      fixture: result.fixture,
   };
}

beforeEach(() => {
   resetMocks();
   window.localStorage.clear();
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 — ngOnInit
// ===========================================================================

describe("Group 1 — ngOnInit", () => {
   it("should call repositoryClient.connect() on init", async () => {
      await renderRepositoryTree();
      expect(repositoryClientMock.connect).toHaveBeenCalledTimes(1);
   });

   it("should call debounceService.debounce (refreshTree) when autoRefreshEnabled=true and repositoryChanged fires", async () => {
      await renderRepositoryTree({ autoRefreshEnabled: true });
      repositoryChangedSubject.next({});
      // refreshTree() → debounceService.debounce("refreshTree", ...)
      expect(debounceServiceMock.debounce).toHaveBeenCalledWith(
         "refreshTree", expect.any(Function), expect.any(Number), []
      );
   });

   it("should emit autoRefreshTriggered when autoRefreshEnabled=false and repositoryChanged fires", async () => {
      const { comp } = await renderRepositoryTree({ autoRefreshEnabled: false });
      const emitSpy = vi.spyOn(comp.autoRefreshTriggered, "emit");
      try {
         repositoryChangedSubject.next({});
         expect(emitSpy).toHaveBeenCalled();
      } finally {
         emitSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 2 — ngOnDestroy / memory leak
// ===========================================================================

describe("Group 2 — ngOnDestroy / memory leak", () => {
   it("should call repositoryClient.disconnect() on destroy", async () => {
      const { fixture } = await renderRepositoryTree();
      fixture.destroy();
      expect(repositoryClientMock.disconnect).toHaveBeenCalledTimes(1);
   });

   it("should not call autoRefreshTriggered after destroy because disconnect completes the subscription", async () => {
      // disconnect mock calls repositoryChangedSubject.complete() → subscription torn down
      const { comp, fixture } = await renderRepositoryTree({ autoRefreshEnabled: false });
      fixture.destroy(); // → ngOnDestroy → repositoryClient.disconnect() → Subject.complete()
      const emitSpy = vi.spyOn(comp.autoRefreshTriggered, "emit");
      try {
         repositoryChangedSubject.next({});
         // Subject is completed; callback should not fire
         expect(emitSpy).not.toHaveBeenCalled();
      } finally {
         emitSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 3 — selectNode
// ===========================================================================

describe("Group 3 — selectNode", () => {
   it("should do nothing when the same node is selected again", async () => {
      const node = makeNode();
      const { comp } = await renderRepositoryTree();
      comp.selectNode(node);
      const emitSpy = vi.spyOn(comp.nodeSelected, "emit");
      try {
         comp.selectNode(node); // same node → should be no-op
         expect(emitSpy).not.toHaveBeenCalled();
      } finally {
         emitSpy.mockRestore();
      }
   });

   it("should set selectedNode and emit nodeSelected for a new node", async () => {
      const node = makeNode({ label: "New" });
      const { comp } = await renderRepositoryTree();
      const emitSpy = vi.spyOn(comp.nodeSelected, "emit");
      try {
         comp.selectNode(node);
         expect(comp.selectedNode).toBe(node);
         expect(emitSpy).toHaveBeenCalledWith(node);
      } finally {
         emitSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 4 — nodeExpanded
// ===========================================================================

describe("Group 4 — nodeExpanded", () => {
   it("should not fetch when node equals root", async () => {
      const root = makeFolder([makeNode()]);
      const { comp } = await renderRepositoryTree({ root });
      comp.nodeExpanded(root);
      expect(repositoryTreeServiceMock.getFolder).not.toHaveBeenCalled();
   });

   it("should not fetch when node is a leaf", async () => {
      const leaf = makeNode();
      const { comp } = await renderRepositoryTree();
      comp.nodeExpanded(leaf);
      expect(repositoryTreeServiceMock.getFolder).not.toHaveBeenCalled();
   });

   it("should not fetch when node already has children loaded", async () => {
      const child = makeNode({ label: "Child" });
      const folder = makeFolder([child], { label: "HasChildren" });
      folder.children = [child];
      const { comp } = await renderRepositoryTree({ root: makeFolder([folder]) });
      comp.nodeExpanded(folder);
      expect(repositoryTreeServiceMock.getFolder).not.toHaveBeenCalled();
   });

   it("should fetch folder data for a non-root, non-leaf, empty-children node", async () => {
      const emptyFolder = makeFolder([], { label: "EmptyFolder", children: [] });
      const root = makeFolder([emptyFolder]);
      repositoryTreeServiceMock.getFolder.mockReturnValueOnce(of({
         children: [makeNode({ label: "Loaded" })],
         data: { path: "/EmptyFolder" },
      }));
      const { comp } = await renderRepositoryTree({ root });
      comp.nodeExpanded(emptyFolder);
      expect(repositoryTreeServiceMock.getFolder).toHaveBeenCalled();
      expect(emptyFolder.children).toHaveLength(1);
      expect(emptyFolder.loading).toBe(false);
   });
});

// ===========================================================================
// Group 5 — getEntryIcon
// ===========================================================================

describe("Group 5 — getEntryIcon", () => {
   it("should return folder-icon for RepletFolderEntry", async () => {
      const { comp } = await renderRepositoryTree();
      expect(comp.getEntryIcon("RepletFolderEntry")).toBe("folder-icon");
   });

   it("should return viewsheet-icon for ViewsheetEntry", async () => {
      const { comp } = await renderRepositoryTree();
      expect(comp.getEntryIcon("ViewsheetEntry")).toBe("viewsheet-icon");
   });

   it("should return report-icon for RepletEntry", async () => {
      const { comp } = await renderRepositoryTree();
      expect(comp.getEntryIcon("RepletEntry")).toBe("report-icon");
   });

   it("should return archive-icon for any other classType", async () => {
      const { comp } = await renderRepositoryTree();
      expect(comp.getEntryIcon("SomeOtherType")).toBe("archive-icon");
   });
});

// ===========================================================================
// Group 6 — getCSSIcon
// ===========================================================================

describe("Group 6 — getCSSIcon", () => {
   it("should delegate to repositoryTreeService.getCSSIcon and return the CSS class", async () => {
      repositoryTreeServiceMock.getCSSIcon.mockReturnValueOnce("folder-open-icon");
      const node = makeNode({ expanded: true });
      const { comp } = await renderRepositoryTree();
      const result = comp.getCSSIcon(node);
      expect(repositoryTreeServiceMock.getCSSIcon).toHaveBeenCalledWith(node.data, node.expanded);
      expect(result).toBe("folder-open-icon");
   });
});

// ===========================================================================
// Group 7 — getEntryLabel
// ===========================================================================

describe("Group 7 — getEntryLabel", () => {
   it("should return HTML containing the icon CSS class and the entry label", async () => {
      const { comp } = await renderRepositoryTree();
      const entry = { label: "My Dashboard", classType: "ViewsheetEntry" };
      const html = comp.getEntryLabel(entry);
      expect(html).toContain("viewsheet-icon");
      expect(html).toContain("My Dashboard");
   });
});

// ===========================================================================
// Group 8 — hasMenu / hasMenuFunction
// ===========================================================================

describe("Group 8 — hasMenu / hasMenuFunction", () => {
   it("should return a function from hasMenuFunction()", async () => {
      const { comp } = await renderRepositoryTree();
      const fn = comp.hasMenuFunction();
      expect(typeof fn).toBe("function");
   });

   it("should return true for a VIEWSHEET node with visible actions", async () => {
      const node = makeNode({
         data: makeEntry({
            type: RepositoryEntryType.VIEWSHEET,
            path: "/MyDashboard",
            op: [RepositoryTreeAction.RENAME, RepositoryTreeAction.DELETE],
         }),
      });
      const { comp } = await renderRepositoryTree();
      // VIEWSHEET always gets createOpenInNewTabAction (visible: true)
      expect(comp.hasMenu(node)).toBe(true);
   });

   it("should return false for a FOLDER node when no op and isFavoritesTree=true", async () => {
      // All folder actions are guarded by !isFavoritesTree;
      // only removeFavorites remains (visible only when favoritesUser=true, which it isn't)
      const node = makeNode({
         data: makeEntry({
            type: RepositoryEntryType.FOLDER,
            classType: "RepletFolderEntry",
            path: "/SomeFolder",
            op: [],
            favoritesUser: false,
         }),
      });
      const { comp } = await renderRepositoryTree({ isFavoritesTree: true });
      expect(comp.hasMenu(node)).toBe(false);
   });
});

// ===========================================================================
// Group 9 — updateScrollPos / ngAfterViewChecked scroll restore
// ===========================================================================

describe("Group 9 — updateScrollPos / ngAfterViewChecked", () => {
   it("should save scrollTop to LocalStorage when scroll event fires", async () => {
      const { fixture } = await renderRepositoryTree();
      const setItemSpy = vi.spyOn(Storage.prototype, "setItem");
      try {
         // Simulate the HostListener("scroll") by triggering a scroll event on the host element
         fixture.nativeElement.dispatchEvent(new Event("scroll"));
         expect(setItemSpy).toHaveBeenCalledWith(
            expect.stringContaining("repository-tree-scroll-position"),
            expect.any(String)
         );
      } finally {
         setItemSpy.mockRestore();
      }
   });

   it("should restore scrollTop from LocalStorage on ngAfterViewChecked when a non-zero value is stored", async () => {
      // Pre-populate LocalStorage with the prefixed key before render
      window.localStorage.setItem("__inetsoft__repository-tree-scroll-position", "42");
      const { fixture } = await renderRepositoryTree();
      // ngAfterViewChecked runs during init and restores scroll
      expect(fixture.nativeElement.scrollTop).toBe(42);
   });
});

// ===========================================================================
// Group 10 [Concurrency] — refreshTree
// ===========================================================================

describe("Group 10 [Concurrency] — refreshTree", () => {
   it("should emit autoRefreshTriggered immediately when searchMode=true", async () => {
      const { comp } = await renderRepositoryTree({ searchMode: true });
      const emitSpy = vi.spyOn(comp.autoRefreshTriggered, "emit");
      try {
         comp.refreshTree();
         expect(emitSpy).toHaveBeenCalled();
         expect(debounceServiceMock.debounce).not.toHaveBeenCalled();
      } finally {
         emitSpy.mockRestore();
      }
   });

   it("should call debounceService.debounce with key 'refreshTree' when not in searchMode", async () => {
      const { comp } = await renderRepositoryTree({ searchMode: false });
      comp.refreshTree();
      expect(debounceServiceMock.debounce).toHaveBeenCalledWith(
         "refreshTree", expect.any(Function), expect.any(Number), []
      );
   });
});
