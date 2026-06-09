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
 * ComposerAppComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnDestroy: dragService.removeListeners(document) and
 *                        resizeHandlerService.removeListeners() called; not called before destroy
 *   Group 2 [Risk 2]  — constructor: route.data → principal + composerRecentService.currentUser;
 *                        missing setPrincipalCommand → currentUser is undefined
 *   Group 3 [Risk 2]  — ngOnInit: vsId/wsId → initialSheet; vsId priority over wsId;
 *                        wsWizard "true" string → boolean true; absent baseDataSourceType → -1
 *   Group 4 [Risk 2]  — ngOnInit: firstDayOfWeekService response sets ngbDatepickerConfig.firstDayOfWeek
 *   Group 5 [Risk 2]  — close(identifier): null → window.close(); non-null → parent.postMessage
 *                        with composerClose:true and correct created value
 *
 * Suspected bugs (header only):
 *   Suspicion A — firstDayOfWeek subscription (ngOnInit): no takeUntil / stored subscription
 *     unsubscribed in ngOnDestroy. In practice getFirstDay() is an HTTP observable that completes
 *     after one emission, so no persistent leak at runtime. Low practical impact; flagged for hygiene.
 *
 *   Group 6 [baseline] — ngOnInit: dragService.initListeners(document) and
 *                        resizeHandlerService.initListeners() called on init
 *   Group 7 [baseline] — constructor: ngbDatepickerConfig.minDate/maxDate set to 1900/2099 range
 *   Group 8 [baseline] — downloadStarted: opens info dialog via ComponentTool.showMessageDialog
 *
 * Out of scope this pass: none (single pass)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbDatepickerConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Title } from "@angular/platform-browser";
import { Observable, Subject, of } from "rxjs";

import { ComposerAppComponent } from "./app.component";
import { ComponentTool } from "../common/util/component-tool";
import { DragService } from "../widget/services/drag.service";
import { ResizeHandlerService } from "./gui/resize-handler.service";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { ComposerRecentService } from "./gui/composer-recent.service";
import { GuiTool } from "../common/util/gui-tool";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const MOCK_PRINCIPAL_COMMAND = { principal: "admin" } as any;
const EMPTY_QUERY_PARAMS = new Map<string, string[]>();

async function renderComponent(overrides: {
   dragService?: any;
   resizeHandlerService?: any;
   firstDay$?: Subject<{ isoFirstDay: number }> | Observable<any>;
   recentService?: any;
   routeData?: Observable<any>;
   config?: any;
} = {}) {
   const config = overrides.config
      ?? ({ minDate: null, maxDate: null, firstDayOfWeek: 0 } as unknown as NgbDatepickerConfig);
   const dragService = overrides.dragService
      ?? { initListeners: vi.fn(), removeListeners: vi.fn() };
   const resizeHandlerService = overrides.resizeHandlerService
      ?? { initListeners: vi.fn(), removeListeners: vi.fn() };
   const firstDayService = {
      getFirstDay: vi.fn().mockReturnValue(overrides.firstDay$ ?? of({ isoFirstDay: 1 })),
   };
   const recentService = overrides.recentService ?? { currentUser: null };
   const routeData = overrides.routeData ?? of({ setPrincipalCommand: MOCK_PRINCIPAL_COMMAND });

   const result = await render(ComposerAppComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      // Clear heavy child-component imports so Angular does not attempt to
      // instantiate ComposerMainComponent and its deep transitive dependencies.
      componentImports: [],
      providers: [
         { provide: DragService, useValue: dragService },
         { provide: ResizeHandlerService, useValue: resizeHandlerService },
         { provide: FirstDayOfWeekService, useValue: firstDayService },
         { provide: ComposerRecentService, useValue: recentService },
         { provide: ActivatedRoute, useValue: { data: routeData } },
         { provide: Router, useValue: { navigate: vi.fn() } },
         { provide: Title, useValue: { setTitle: vi.fn() } },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: NgbDatepickerConfig, useValue: config },
      ],
   });

   return {
      fixture: result.fixture,
      comp: result.fixture.componentInstance as ComposerAppComponent,
      dragService,
      resizeHandlerService,
      recentService,
      config,
   };
}

let queryParamsSpy: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
   queryParamsSpy = vi.spyOn(GuiTool, "getQueryParameters").mockReturnValue(EMPTY_QUERY_PARAMS);
});

afterEach(() => {
   vi.restoreAllMocks();
   document.body.className = document.body.className.replace(/\bapp-loaded\b/g, "").trim();
});

// ---------------------------------------------------------------------------
// Group 1: ngOnDestroy — listener cleanup
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: dragService attaches listeners to document on init; leaving them
   // attached after composer closes intercepts drag events in the portal shell indefinitely.
   it("should call dragService.removeListeners(document) on destroy", async () => {
      const { fixture, dragService } = await renderComponent();
      fixture.destroy();
      expect(dragService.removeListeners).toHaveBeenCalledWith(document);
   });

   // 🔁 Regression-sensitive: resize listeners accumulate across open/close cycles unless
   // explicitly removed; multiple handlers corrupt layout calculations.
   it("should call resizeHandlerService.removeListeners() on destroy", async () => {
      const { fixture, resizeHandlerService } = await renderComponent();
      fixture.destroy();
      expect(resizeHandlerService.removeListeners).toHaveBeenCalledTimes(1);
   });

   it("should not remove listeners before the component is destroyed", async () => {
      const { dragService, resizeHandlerService } = await renderComponent();
      expect(dragService.removeListeners).not.toHaveBeenCalled();
      expect(resizeHandlerService.removeListeners).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: constructor — route.data → principal binding
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — constructor: principal binding", () => {
   it("should assign principal from route data setPrincipalCommand", async () => {
      const { comp } = await renderComponent();
      expect(comp.principal).toBe(MOCK_PRINCIPAL_COMMAND);
   });

   // 🔁 Regression-sensitive: currentUser gates recent-asset filtering; wrong value causes all
   // users to share the same recently-opened assets list.
   it("should set composerRecentService.currentUser to principal.principal", async () => {
      const recentService = { currentUser: null as any };
      await renderComponent({ recentService });
      expect(recentService.currentUser).toBe(MOCK_PRINCIPAL_COMMAND.principal);
   });

   it("should set composerRecentService.currentUser to undefined when setPrincipalCommand is null", async () => {
      const recentService = { currentUser: "stale" as any };
      await renderComponent({ recentService, routeData: of({ setPrincipalCommand: null }) });
      expect(recentService.currentUser).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit — URL param parsing
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — ngOnInit: URL param parsing", () => {
   it("should set initialSheet from vsId query param", async () => {
      queryParamsSpy.mockReturnValue(new Map([["vsId", ["vs-abc"]]]));
      const { comp } = await renderComponent();
      expect(comp.initialSheet).toBe("vs-abc");
   });

   it("should fall back to wsId when vsId is absent", async () => {
      queryParamsSpy.mockReturnValue(new Map([["wsId", ["ws-abc"]]]));
      const { comp } = await renderComponent();
      expect(comp.initialSheet).toBe("ws-abc");
   });

   // 🔁 Regression-sensitive: vsId must win over wsId — a viewsheet URL must not silently
   // load the worksheet instead.
   it("should prefer vsId over wsId when both params are present", async () => {
      queryParamsSpy.mockReturnValue(new Map([["vsId", ["vs-wins"]], ["wsId", ["ws-loses"]]]));
      const { comp } = await renderComponent();
      expect(comp.initialSheet).toBe("vs-wins");
   });

   it("should parse wsWizard as boolean true when the param value is the string 'true'", async () => {
      queryParamsSpy.mockReturnValue(new Map([["wsWizard", ["true"]]]));
      const { comp } = await renderComponent();
      expect(comp.wsWizard).toBe(true);
   });

   it("should default baseDataSourceType to -1 when the param is absent", async () => {
      const { comp } = await renderComponent();
      expect(comp.baseDataSourceType).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnInit — firstDayOfWeek subscription
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — ngOnInit: firstDayOfWeek", () => {
   it("should update ngbDatepickerConfig.firstDayOfWeek from the service response", async () => {
      const firstDay$ = new Subject<{ isoFirstDay: number }>();
      const config = { minDate: null, maxDate: null, firstDayOfWeek: 0 };
      await renderComponent({ firstDay$, config });
      firstDay$.next({ isoFirstDay: 3 });
      expect(config.firstDayOfWeek).toBe(3);
   });
});

// ---------------------------------------------------------------------------
// Group 5: close(identifier) — null vs non-null
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — close", () => {
   it("should call window.close() when identifier is null", async () => {
      const closeSpy = vi.spyOn(window, "close").mockImplementation(() => {});
      const { comp } = await renderComponent();
      comp.close(null!);
      expect(closeSpy).toHaveBeenCalledTimes(1);
   });

   // 🔁 Regression-sensitive: the postMessage payload is consumed by the host page that opened
   // composer in an iframe; wrong keys or a missing created value silently breaks the close
   // handshake and the opener's onmessage callback never fires.
   it("should call postMessage with composerClose:true and created set to the identifier", async () => {
      const postMessageSpy = vi.spyOn(window, "postMessage").mockImplementation(() => {});
      const { comp } = await renderComponent();
      comp.close("sheet-xyz");
      expect(postMessageSpy).toHaveBeenCalledWith(
         { composerClose: true, created: "sheet-xyz" },
         "*",
      );
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnInit — listener init (baseline)
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — ngOnInit: listener init", () => {
   it("should call dragService.initListeners(document) on init", async () => {
      const { dragService } = await renderComponent();
      expect(dragService.initListeners).toHaveBeenCalledWith(document);
   });

   it("should call resizeHandlerService.initListeners() on init", async () => {
      const { resizeHandlerService } = await renderComponent();
      expect(resizeHandlerService.initListeners).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 7: constructor — ngbDatepickerConfig date range (baseline)
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — constructor: ngbDatepickerConfig date range", () => {
   // 🔁 Regression-sensitive: without an explicit range the datepicker defaults to ±20 years,
   // which makes historical and far-future dates inaccessible in the composer.
   it("should set minDate to 1900/1/1 and maxDate to 2099/12/31", async () => {
      const config = { minDate: null as any, maxDate: null as any, firstDayOfWeek: 0 };
      await renderComponent({ config });
      expect(config.minDate).toEqual({ year: 1900, month: 1, day: 1 });
      expect(config.maxDate).toEqual({ year: 2099, month: 12, day: 31 });
   });
});

// ---------------------------------------------------------------------------
// Group 8: downloadStarted — baseline
// ---------------------------------------------------------------------------

describe("ComposerAppComponent — downloadStarted", () => {
   it("should open an info dialog when a download starts", async () => {
      const showDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");
      const { comp } = await renderComponent();

      comp.downloadStarted("/api/export/file.zip");

      expect(showDialogSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Info)",
         "_#(js:common.downloadStart)",
      );
   });
});
