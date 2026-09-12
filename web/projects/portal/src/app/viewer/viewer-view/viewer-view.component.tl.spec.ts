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
 * ViewerViewComponent — single pass (+race-condition +memory-leak)
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — ngOnInit normal flow: clearTabs + addTab; fields from route data
 *   Group 2  [Risk 3] — ngOnInit returnFromEditor branch: existingTab found → update only;
 *                        no existingTab → fresh tab (race-condition risk)
 *   Group 3  [baseline] — ngOnInit onRefreshPage subscription: runtimeId updated on emit
 *   Group 4  [Risk 2] — ngOnInit getDrillTabsTop: drillTabsTop flag and drillTabsTopPx
 *   Group 5  [baseline] — ngOnDestroy: composite subscription unsubscribed
 *   Group 6  [baseline] — tabs / currentTab: delegate to pageTabService
 *   Group 7  [baseline] — closeCurrentTab: delegates to pageTabService.closeTab
 *   Group 8  [Risk 2] — canDeactivate: no active app → true;
 *                        active app + no form changes + not modified → true
 *   Group 9  [Risk 2] — onViewsheetClosed: inPortal=true clears tabs; inPortal=false no-op
 *   Group 10 [baseline] — isActiveTab: visibility and reference equality conditions
 *   Group 11 [Risk 2] — onToolbarVisibleChange / updateDrillTabsTopPx:
 *                        drillTabsTop=false → null; true+visible → px; true+hidden → 0
 *   Group 12 [baseline] — openViewsheet: inPortal vs !inPortal route targets
 *
 * Out of scope (require full DOM + ViewerAppComponent inputs/outputs):
 *   onEditTable, onEditChart
 *
 * Component-level providers (ShowHyperlinkService, ContextProvider) must be overridden
 * via componentProviders; PageTabService is module-level → use providers.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { firstValueFrom, Observable, of, Subject } from "rxjs";

import { ViewerViewComponent } from "./viewer-view.component";
import { ViewerAppComponent } from "../../vsobjects/viewer-app.component";
import { PageTabComponent } from "./page-tab.component";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { PageTabService, TabInfoModel } from "../services/page-tab.service";
import { ViewDataService } from "../services/view-data.service";
import { ModelService } from "../../widget/services/model.service";
import { HideNavService } from "../../portal/services/hide-nav.service";
import { ViewData } from "../view-data";
import { SetPrincipalCommand } from "../../vsobjects/command/set-principal-command";
import { ViewConstants } from "../view-constants";

// ---------------------------------------------------------------------------
// Stubs for child components declared in ViewerViewComponent.imports[]
// ---------------------------------------------------------------------------

@Component({ selector: "viewer-app", template: "", standalone: true })
class ViewerAppStub {
   @Input() active: boolean;
   @Input() assetId: string;
   @Input() runtimeId: string;
   @Input() queryParameters: Map<string, string[]>;
   @Input() fullScreen: boolean;
   @Output() onEditTable = new EventEmitter<any>();
   @Output() onEditChart = new EventEmitter<any>();
   @Output() closeCurrentTab = new EventEmitter<void>();
   @Output() closeClicked = new EventEmitter<void>();
   @Output() onAnnotationChanged = new EventEmitter<boolean>();
   @Output() onOpenViewsheet = new EventEmitter<string>();
   @Output() toolbarVisibleChange = new EventEmitter<boolean>();
}

@Component({ selector: "page-tab", template: "", standalone: true })
class PageTabStub {}

// ---------------------------------------------------------------------------
// Fake PageTabService — full control over state without HTTP dependency
// ---------------------------------------------------------------------------

class FakePageTabService {
   _tabs: TabInfoModel[] = [];
   currentTab: TabInfoModel = null;
   onRefreshPage = new Subject<TabInfoModel>();

   get tabs(): TabInfoModel[] {
      return this._tabs;
   }

   clearTabs = vi.fn().mockImplementation(() => {
      this._tabs = [];
      this.currentTab = null;
   });

   addTab = vi.fn().mockImplementation((tab: TabInfoModel) => {
      this._tabs.push(tab);
      this.currentTab = tab;
   });

   closeTab = vi.fn();

   getVSTabLabel = vi.fn().mockImplementation((assetId: string) =>
      assetId ? assetId.substring(assetId.lastIndexOf("^") + 1) : ""
   );

   getDrillTabsTop = vi.fn().mockReturnValue(of(false));
}

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const PRINCIPAL_CMD: SetPrincipalCommand = {
   principal: "user1",
   securityEnabled: false,
   viewsheetPermission: true,
   worksheetPermission: true,
   tableStylePermission: true,
   scriptPermission: true,
   aiAssistantPermission: true,
   autoSaveFiles: [],
};

const MODEL_SERVICE_MOCK = {
   getModel: vi.fn().mockReturnValue(of(false)),
};

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

const ROUTER_MOCK = {
   navigate: vi.fn().mockResolvedValue(true),
   getCurrentNavigation: vi.fn().mockReturnValue(null),
};

const HIDE_NAV_MOCK = {
   appendParameter: vi.fn().mockImplementation((p: any) => p),
};

const VIEW_DATA_MOCK = {
   data: {} as ViewData,
};

function makeViewData(overrides: Partial<ViewData> = {}): ViewData {
   return {
      runtimeId: "rt-001",
      assetId: "1^128^__NULL__^Sales",
      queryParameters: new Map<string, string[]>(),
      portal: false,
      dashboard: false,
      fullScreen: false,
      dashboardName: null,
      fullScreenId: null,
      hasBaseEntry: false,
      toolbarPermissions: [],
      aiAssistantPermission: false,
      ...overrides,
   };
}

interface RenderOpts {
   viewData?: Partial<ViewData>;
   returnFromEditor?: boolean;
   preExistingTab?: TabInfoModel;
   drillTabsTopValue?: boolean;
   principalCommand?: Partial<SetPrincipalCommand>;
}

async function renderComponent(opts: RenderOpts = {}) {
   ROUTER_MOCK.navigate.mockClear();
   ROUTER_MOCK.getCurrentNavigation.mockReturnValue(
      opts.returnFromEditor ? { extras: { state: { returnFromEditor: true } } } : null
   );
   MODEL_SERVICE_MOCK.getModel.mockClear().mockReturnValue(of(false));
   MODAL_MOCK.open.mockClear();
   HIDE_NAV_MOCK.appendParameter.mockClear().mockImplementation((p: any) => p);
   VIEW_DATA_MOCK.data = {} as ViewData;

   const viewData = makeViewData(opts.viewData);
   const principalCommand = { ...PRINCIPAL_CMD, ...opts.principalCommand };

   const fakePageTab = new FakePageTabService();
   if (opts.preExistingTab) {
      fakePageTab._tabs = [opts.preExistingTab];
      fakePageTab.currentTab = opts.preExistingTab;
   }
   if (opts.drillTabsTopValue !== undefined) {
      fakePageTab.getDrillTabsTop = vi.fn().mockReturnValue(of(opts.drillTabsTopValue));
   }

   const result = await render(ViewerViewComponent, {
      // ShowHyperlinkService and ContextProvider are component-level providers
      componentProviders: [
         { provide: ShowHyperlinkService, useValue: {} },
         { provide: ContextProvider, useValue: {} },
      ],
      providers: [
         { provide: ActivatedRoute, useValue: { data: of({ viewData, principalCommand }) } },
         { provide: Router, useValue: ROUTER_MOCK },
         { provide: PageTabService, useValue: fakePageTab },
         { provide: ViewDataService, useValue: VIEW_DATA_MOCK },
         { provide: ModelService, useValue: MODEL_SERVICE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: HideNavService, useValue: HIDE_NAV_MOCK },
      ],
      importOverrides: [
         { replace: ViewerAppComponent, with: ViewerAppStub },
         { replace: PageTabComponent, with: PageTabStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return { comp: result.fixture.componentInstance, fakePageTab, fixture: result.fixture };
}

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: normal flow
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — ngOnInit() normal flow", () => {
   // 🔁 Regression-sensitive: if clearTabs+addTab are not called for a fresh navigation,
   // stale tabs from a previous viewsheet remain visible in the tab bar.
   it("should clear tabs and add a tab for the incoming viewsheet", async () => {
      const { fakePageTab } = await renderComponent();
      expect(fakePageTab.clearTabs).toHaveBeenCalled();
      expect(fakePageTab.addTab).toHaveBeenCalledWith(
         expect.objectContaining({ id: "1^128^__NULL__^Sales" })
      );
   });

   it("should set runtimeId from viewData on init", async () => {
      const { comp } = await renderComponent();
      expect(comp.runtimeId).toBe("rt-001");
   });

   it("should set principal and securityEnabled from principalCommand on init", async () => {
      const { comp } = await renderComponent({
         principalCommand: { principal: "alice", securityEnabled: true },
      });
      expect(comp.principal).toBe("alice");
      expect(comp.securityEnabled).toBe(true);
   });

   it("should set aiAssistantPermission from principalCommand", async () => {
      const { comp } = await renderComponent({
         principalCommand: { aiAssistantPermission: true },
      });
      expect(comp.aiAssistantPermission).toBe(true);
   });

   it("should set portal, dashboard, and fullScreen flags from viewData", async () => {
      const { comp } = await renderComponent({
         viewData: { portal: true, dashboard: true, fullScreen: true },
      });
      expect(comp.inPortal).toBe(true);
      expect(comp.inDashboard).toBe(true);
      expect(comp.fullScreen).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit: returnFromEditor branch
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — ngOnInit() returnFromEditor branch", () => {
   // 🔁 Race-condition regression: returnFromEditor is captured from getCurrentNavigation()
   // BEFORE the async route.data subscribe fires. If the capture were inside the subscribe
   // callback, getCurrentNavigation() would already have returned null by then and sibling
   // tabs would be incorrectly cleared every time.
   it("should update existing tab runtimeId without clearing tabs when returning from editor", async () => {
      const existing: TabInfoModel = {
         id: "1^128^__NULL__^Sales",
         label: "Sales",
         runtimeId: "old-rt",
         queryParameters: new Map(),
      };
      const { fakePageTab, comp } = await renderComponent({
         returnFromEditor: true,
         preExistingTab: existing,
      });
      // Positive gate first: proves the returnFromEditor branch processed the route data
      expect(existing.runtimeId).toBe("rt-001");
      expect(comp.runtimeId).toBe("rt-001");
      // Negative: meaningful because the positive gate confirms the correct branch executed
      expect(fakePageTab.clearTabs).not.toHaveBeenCalled();
   });

   it("should clear tabs and add fresh tab when returnFromEditor=true but no matching tab exists", async () => {
      const { fakePageTab } = await renderComponent({
         returnFromEditor: true,
         // no preExistingTab — tabs is empty, so existingTab=undefined
      });
      expect(fakePageTab.clearTabs).toHaveBeenCalled();
      expect(fakePageTab.addTab).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — onRefreshPage subscription
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — onRefreshPage subscription", () => {
   it("should update runtimeId when pageTabService.onRefreshPage emits", async () => {
      const { comp, fakePageTab } = await renderComponent();
      expect(comp.runtimeId).toBe("rt-001");
      fakePageTab.onRefreshPage.next({ id: "other", label: "Other", runtimeId: "rt-from-refresh" });
      expect(comp.runtimeId).toBe("rt-from-refresh");
   });
});

// ---------------------------------------------------------------------------
// Group 4 — getDrillTabsTop subscription
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — getDrillTabsTop subscription", () => {
   it("should call getDrillTabsTop during init to set up the subscription", async () => {
      const { fakePageTab } = await renderComponent({ drillTabsTopValue: false });
      expect(fakePageTab.getDrillTabsTop).toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: when drillTabsTop=true and toolbar is visible, the tab bar
   // must be positioned exactly at the toolbar bottom to avoid a visual gap.
   it("should set drillTabsTop=true and compute drillTabsTopPx when service returns true", async () => {
      const { comp } = await renderComponent({ drillTabsTopValue: true });
      expect(comp.drillTabsTop).toBe(true);
      // toolbarVisible defaults to true — non-mobile pixel height expected
      expect(comp.drillTabsTopPx).toBe(ViewConstants.TOOLBAR_HEIGHT_PX);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — ngOnDestroy
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — ngOnDestroy()", () => {
   it("should unsubscribe all subscriptions on destroy", async () => {
      const { comp, fakePageTab } = await renderComponent();
      const initialRuntimeId = comp.runtimeId;
      comp.ngOnDestroy();
      // After destroy, onRefreshPage emissions must not update the component
      fakePageTab.onRefreshPage.next({ id: "x", label: "X", runtimeId: "post-destroy-rt" });
      expect(comp.runtimeId).toBe(initialRuntimeId);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — tabs / currentTab getters
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — tabs / currentTab", () => {
   it("should expose pageTabService.tabs via the tabs getter", async () => {
      const { comp, fakePageTab } = await renderComponent();
      expect(comp.tabs).toBe(fakePageTab.tabs);
   });

   it("should expose pageTabService.currentTab via the currentTab getter", async () => {
      const { comp, fakePageTab } = await renderComponent();
      expect(comp.currentTab).toBe(fakePageTab.currentTab);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — closeCurrentTab
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — closeCurrentTab()", () => {
   it("should call pageTabService.closeTab with the current tab", async () => {
      const { comp, fakePageTab } = await renderComponent();
      const currentTab = fakePageTab.currentTab;
      comp.closeCurrentTab();
      expect(fakePageTab.closeTab).toHaveBeenCalledWith(currentTab);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — canDeactivate
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — canDeactivate()", () => {
   it("should return true immediately when no active viewer app is present", async () => {
      const { comp } = await renderComponent();
      // Bypass: getActiveViewerApp() is private; no public equivalent for test control.
      const spy = vi.spyOn(comp as any, "getActiveViewerApp").mockReturnValue(undefined);
      try {
         expect(comp.canDeactivate()).toBe(true);
      } finally {
         spy.mockRestore();
      }
   });

   it("should emit true when active app has no unsaved form changes and not modified", async () => {
      const { comp } = await renderComponent();
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(false));
      // Bypass: getActiveViewerApp() is private; no public equivalent for test control.
      const spy = vi.spyOn(comp as any, "getActiveViewerApp").mockReturnValue({ runtimeId: "rt-app" });
      try {
         const result = comp.canDeactivate() as Observable<boolean>;
         expect(await firstValueFrom(result)).toBe(true);
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 9 — onViewsheetClosed
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — onViewsheetClosed()", () => {
   it("should clear tabs in-place when inPortal=true", async () => {
      const { comp, fakePageTab } = await renderComponent({
         viewData: { portal: true },
      });
      const tabsRef = fakePageTab.tabs;
      expect(tabsRef.length).toBe(1);
      comp.onViewsheetClosed();
      // splice clears the same array reference
      expect(tabsRef.length).toBe(0);
   });

   it("should leave tabs unchanged when inPortal=false", async () => {
      const { comp, fakePageTab } = await renderComponent({
         viewData: { portal: false },
      });
      const beforeLen = fakePageTab.tabs.length;
      comp.onViewsheetClosed();
      expect(fakePageTab.tabs.length).toBe(beforeLen);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — isActiveTab
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — isActiveTab()", () => {
   it("should return true when visible=true and tab is currentTab", async () => {
      const { comp, fakePageTab } = await renderComponent();
      expect(comp.visible).toBe(true);
      expect(comp.isActiveTab(fakePageTab.currentTab)).toBe(true);
   });

   it("should return false when visible=false even for the current tab", async () => {
      const { comp, fakePageTab } = await renderComponent();
      comp.visible = false;
      expect(comp.isActiveTab(fakePageTab.currentTab)).toBe(false);
   });

   it("should return false for a tab that is not the current tab", async () => {
      const { comp } = await renderComponent();
      const otherTab: TabInfoModel = { id: "other", label: "Other" };
      expect(comp.isActiveTab(otherTab)).toBe(false);
   });

   it("should return false when tab is null", async () => {
      const { comp } = await renderComponent();
      expect(comp.isActiveTab(null)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — onToolbarVisibleChange / updateDrillTabsTopPx
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — onToolbarVisibleChange()", () => {
   it("should keep drillTabsTopPx=null when drillTabsTop=false regardless of visibility", async () => {
      const { comp } = await renderComponent({ drillTabsTopValue: false });
      comp.onToolbarVisibleChange(true);
      expect(comp.drillTabsTopPx).toBeNull();
   });

   it("should set drillTabsTopPx to toolbar height when drillTabsTop=true and toolbar visible", async () => {
      const { comp } = await renderComponent({ drillTabsTopValue: true });
      comp.onToolbarVisibleChange(true);
      expect(comp.drillTabsTopPx).toBe(ViewConstants.TOOLBAR_HEIGHT_PX);
   });

   it("should set drillTabsTopPx to 0 when drillTabsTop=true and toolbar hidden", async () => {
      const { comp } = await renderComponent({ drillTabsTopValue: true });
      comp.onToolbarVisibleChange(false);
      expect(comp.drillTabsTopPx).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — openViewsheet
// ---------------------------------------------------------------------------

describe("ViewerViewComponent — openViewsheet()", () => {
   it("should navigate to portal report route when inPortal=true", async () => {
      const { comp } = await renderComponent({ viewData: { portal: true } });
      comp.openViewsheet("rt-drilled");
      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         expect.arrayContaining([expect.stringContaining("/portal/tab/report/vs/view/rt-drilled")]),
         expect.any(Object)
      );
   });

   it("should navigate to standalone viewer route when inPortal=false", async () => {
      const { comp } = await renderComponent({ viewData: { portal: false } });
      comp.openViewsheet("rt-drilled");
      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         expect.arrayContaining([expect.stringContaining("/viewer/view/rt-drilled")]),
         expect.any(Object)
      );
   });
});
