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
 * Shared test helpers for AssetTreePane P1 (interaction) and P2 (risk) test suites.
 *
 * Both passes use the same renderComponent configuration, so this file centralises
 * the fixture setup to avoid drift.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject } from "rxjs";

import { AssetTreePane } from "./asset-tree-pane.component";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetConstants } from "../../../common/data/asset-constants";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../widget/services/drag.service";
import { DomService } from "../../../widget/dom-service/dom.service";
import { ComposerRecentService } from "../composer-recent.service";
import { TreeView } from "../../../widget/tree/tree.component";

// ---------------------------------------------------------------------------
// Module-level mock singletons shared across all importing test suites.
// Spy methods are reset via mockClear()/mockReset() in each suite's beforeEach.
// recentChange$ is a BehaviorSubject, not a spy: each beforeEach calls
// recentChange$.next([]) to flush the stored value. Subscribers that
// complete/error inside a test will not receive future emissions, but this is
// safe because renderComponent() creates a fresh component instance per test.
// ---------------------------------------------------------------------------

export const recentChange$ = new BehaviorSubject<any[]>([]);

export const RECENT_SERVICE_MOCK = {
   addRecentlyViewed: vi.fn(),
   removeRecentlyViewed: vi.fn(),
   removeNonExistItems: vi.fn(),
   recentlyViewedChange: vi.fn().mockReturnValue(recentChange$.asObservable()),
};

export const DROPDOWN_MOCK = {
   open: vi.fn().mockReturnValue({
      componentInstance: { sourceEvent: null, actions: null },
   }),
};

export const DRAG_SERVICE_MOCK = {
   getDragData: vi.fn().mockReturnValue({}),
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: Promise.resolve("ok"),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export function makeAssetTreeMock() {
   return {
      selectNodes: vi.fn(),
      refreshView: vi.fn(),
      findAssetTreeNodeParentFromIdentifier: vi.fn().mockReturnValue(null),
      getParentNode: vi.fn().mockReturnValue(null),
      getNodeByData: vi.fn().mockReturnValue(null),
      selectedNodes: [],
      root: {},
      virtualScrollTree: { tree: { expandNode: vi.fn() } },
   };
}

export async function renderComponent(): Promise<AssetTreePane> {
   const { fixture } = await render(AssetTreePane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: FixedDropdownService, useValue: DROPDOWN_MOCK },
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
         { provide: DomService, useValue: {} },
         { provide: ComposerRecentService, useValue: RECENT_SERVICE_MOCK },
      ],
   });
   const comp = fixture.componentInstance as AssetTreePane;
   (comp as any).assetTree = makeAssetTreeMock();
   comp.openedSheets = [];
   comp.opendTabs = [];
   // Wait for ngOnInit's tabularDataSourceTypes HTTP load to complete
   await waitFor(() => expect(comp.tabularDataSourceTypes).toBeDefined());
   return comp;
}

// ---------------------------------------------------------------------------
// Factory helpers for test data
// ---------------------------------------------------------------------------

export function makeEntry(type: AssetType, overrides: Partial<any> = {}): any {
   return {
      scope: AssetConstants.GLOBAL_SCOPE,
      type,
      path: `User Workspace/${type}/Entry`,
      identifier: `1^${type}^__NULL__^User Workspace/${type}/Entry`,
      properties: {},
      folder: false,
      alias: null,
      organization: null,
      user: null,
      ...overrides,
   };
}

export function makeNode(entry: any, overrides: Partial<any> = {}): any {
   return {
      label: "Entry",
      data: entry,
      leaf: true,
      treeView: TreeView.FULL_VIEW,
      ...overrides,
   };
}
