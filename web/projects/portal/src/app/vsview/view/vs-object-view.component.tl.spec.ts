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
 * VSObjectView — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — model setter: stores _model and calls actionFactory.createActions
 *   Group 2 [Risk 2] — onChartMaxModeChange: chartMaxMode flag, LocalStorage, emit
 *   Group 3 [Risk 2] — getAssemblyName(): extracts parent path / returns null when no dot
 *   Group 4 [Risk 2] — getFormats() (HostListener click): deferred emit via 250ms timer (+memory leak)
 *   Group 5 [Risk 1] — ngOnDestroy: super.cleanup() called
 *   Group 6 [Risk 1] — showMiniToolbar, onMouseEnter, contextmenuOpened/Closed delegates
 *
 * Confirmed bugs (it.fails):
 *   Bug — getFormats 250ms timer leak (Group 4): getFormats() schedules a 250ms setTimeout that
 *     emits on this.onUpdateData. ngOnDestroy calls super.cleanup() but does NOT cancel the timer,
 *     so onUpdateData fires on the dead component after destroy. Fix: store the timer ID and call
 *     clearTimeout in ngOnDestroy.
 *
 * Out of scope:
 *   resizeModelView() — reads DOM dimensions (offsetWidth/offsetHeight) of parent elements; DOM
 *     layout is not computed in jsdom and the method guards on objectView.nativeElement.
 *   getViewSize() / getTotalWidth() — private helpers for resizeModelView; same constraint.
 *   onResize() — calls VSChart.openMaxMode() on a ViewChild; integration-level.
 *   processRefreshVSObjectCommand / processAddVSObjectCommand — STOMP command handlers that
 *     just re-emit; Risk 1 one-liners.
 *   ngOnInit LocalStorage max-mode branch — calls onChartMaxModeChange and accesses model;
 *     covered transitively via onChartMaxModeChange tests.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { VSObjectView } from "./vs-object-view.component";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../vsobjects/action/assembly-action-factory.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { LocalStorage } from "../../common/util/local-storage.util";

const clientServiceMock = {
   sendEvent: vi.fn(),
   runtimeId: "vs-test",
   addMessageListener: vi.fn(),
   removeMessageListener: vi.fn(),
};
const actionFactoryMock = {
   createActions: vi.fn().mockReturnValue([]),
};
const miniToolbarServiceMock = {
   isMiniToolbarHidden: vi.fn().mockReturnValue(false),
   handleMouseEnter: vi.fn(),
   hiddenFreeze: vi.fn(),
   hiddenUnfreeze: vi.fn(),
};

function makeModel(overrides: Partial<VSObjectModel> = {}): VSObjectModel {
   return {
      absoluteName: "Viewsheet1.Table1",
      objectType: "VSTable",
      objectFormat: { width: 300, height: 200, left: 0, top: 0, zIndex: 0 },
      originalObjectFormat: null,
      ...overrides,
   } as any;
}

async function renderComponent(props: Record<string, any> = {}) {
   const { fixture } = await render(VSObjectView, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ViewsheetClientService, useValue: clientServiceMock },
         { provide: AssemblyActionFactory, useValue: actionFactoryMock },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: MiniToolbarService, useValue: miniToolbarServiceMock },
      ],
      componentProperties: { model: makeModel(), ...props },
   });
   return { comp: fixture.componentInstance as VSObjectView, fixture };
}

beforeEach(() => {
   localStorage.clear();
   actionFactoryMock.createActions.mockClear();
   miniToolbarServiceMock.isMiniToolbarHidden.mockClear();
   miniToolbarServiceMock.handleMouseEnter.mockClear();
   miniToolbarServiceMock.hiddenFreeze.mockClear();
   miniToolbarServiceMock.hiddenUnfreeze.mockClear();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: model setter [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — model setter", () => {
   it("should store the model and call actionFactory.createActions", async () => {
      const { comp } = await renderComponent();
      const newModel = makeModel({ objectType: "VSChart", absoluteName: "VS.Chart1" });
      comp.model = newModel;
      expect(comp.model).toBe(newModel);
      expect(actionFactoryMock.createActions).toHaveBeenCalled();
   });

   it("should not store model when null is passed", async () => {
      const { comp } = await renderComponent();
      const existing = comp.model;
      comp.model = null;
      expect(comp.model).toBe(existing);
   });

   it("should call createActions with the new model", async () => {
      const { comp } = await renderComponent();
      const newModel = makeModel({ absoluteName: "VS.Crosstab1" });
      actionFactoryMock.createActions.mockClear();
      comp.model = newModel;
      expect(actionFactoryMock.createActions).toHaveBeenCalledWith(newModel);
   });
});

// ---------------------------------------------------------------------------
// Group 2: onChartMaxModeChange [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — onChartMaxModeChange", () => {
   it("should set chartMaxMode=true and emit chartMaxModeChange", async () => {
      vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});
      const { comp } = await renderComponent();
      const emitted: any[] = [];
      comp.chartMaxModeChange.subscribe(v => emitted.push(v));

      comp.onChartMaxModeChange({ assembly: "VS.Chart1", maxMode: true });

      expect(comp.chartMaxMode).toBe(true);
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual({ assembly: "VS.Chart1", maxMode: true });
   });

   it("should set chartMaxMode=false and emit on disable", async () => {
      vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});
      const { comp } = await renderComponent();
      comp.chartMaxMode = true;
      const emitted: any[] = [];
      comp.chartMaxModeChange.subscribe(v => emitted.push(v));

      comp.onChartMaxModeChange({ assembly: "VS.Chart1", maxMode: false });

      expect(comp.chartMaxMode).toBe(false);
      expect(emitted[0].maxMode).toBe(false);
   });

   it("should write to LocalStorage when chartMaxMode is enabled", async () => {
      const setItemSpy = vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});
      const { comp } = await renderComponent();
      comp.onChartMaxModeChange({ assembly: "VS.Chart1", maxMode: true });
      expect(setItemSpy).toHaveBeenCalledWith("chart-edit-max-mode", "true");
   });

   it("should clear LocalStorage entry when chartMaxMode is disabled", async () => {
      const setItemSpy = vi.spyOn(LocalStorage, "setItem").mockImplementation(() => {});
      const { comp } = await renderComponent();
      comp.onChartMaxModeChange({ assembly: "VS.Chart1", maxMode: false });
      expect(setItemSpy).toHaveBeenCalledWith("chart-edit-max-mode", "");
   });
});

// ---------------------------------------------------------------------------
// Group 3: getAssemblyName [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — getAssemblyName", () => {
   it("should return the parent path when absoluteName contains a dot", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "Viewsheet1.Table1" }) });
      expect(comp.getAssemblyName()).toBe("Viewsheet1");
   });

   it("should return null when absoluteName has no dot (top-level assembly)", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "Table1" }) });
      expect(comp.getAssemblyName()).toBeNull();
   });

   it("should return null when _model is null", async () => {
      const { comp } = await renderComponent();
      (comp as any)._model = null;
      expect(comp.getAssemblyName()).toBeNull();
   });

   it("should extract only the first-level parent for nested paths", async () => {
      const { comp } = await renderComponent({ model: makeModel({ absoluteName: "VS.Container.Table1" }) });
      // lastIndexOf(".") finds the rightmost dot
      expect(comp.getAssemblyName()).toBe("VS.Container");
   });
});

// ---------------------------------------------------------------------------
// Group 4: getFormats() — deferred emit (+memory leak) [Risk 2]
// ---------------------------------------------------------------------------

describe("VSObjectView — getFormats deferred emit (+memory leak)", () => {
   it("should emit getCurrentFormat after 250ms when getFormats is called", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      vi.useFakeTimers();
      comp.getFormats(new MouseEvent("click"));

      expect(emitted).toHaveLength(0); // not yet emitted
      vi.advanceTimersByTime(250);
      expect(emitted).toContain("getCurrentFormat");
      vi.useRealTimers();
   });

   it("should NOT emit before 250ms have elapsed", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      vi.useFakeTimers();
      comp.getFormats(new MouseEvent("click"));
      vi.advanceTimersByTime(249);
      expect(emitted).toHaveLength(0);
      vi.useRealTimers();
   });

   // Bug: getFormats() calls setTimeout(fn, 250) but ngOnDestroy only calls super.cleanup();
   // the timer ID is never stored and never cancelled, so onUpdateData fires on the dead component.
   // Fix: store the timer reference and call clearTimeout in ngOnDestroy.
   it.fails("should not emit onUpdateData after component is destroyed (250ms timer leak)", async () => {
      const { comp, fixture } = await renderComponent();
      const emitted: string[] = [];
      comp.onUpdateData.subscribe(v => emitted.push(v));

      vi.useFakeTimers();
      comp.getFormats(new MouseEvent("click")); // queues 250ms timer
      fixture.destroy();                         // ngOnDestroy → super.cleanup(); timer NOT cancelled
      vi.advanceTimersByTime(300);               // timer fires on dead component

      expect(emitted).toHaveLength(0); // FAILS — "getCurrentFormat" was emitted after destroy
      vi.useRealTimers();
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnDestroy [Risk 1]
// ---------------------------------------------------------------------------

describe("VSObjectView — ngOnDestroy", () => {
   it("should call removeMessageListener during cleanup when destroyed", async () => {
      const { fixture } = await renderComponent();
      fixture.destroy();
      // CommandProcessor.cleanup() calls viewsheetClient.removeMessageListener for each registered listener
      expect(clientServiceMock.removeMessageListener).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: miniToolbar delegates [Risk 1]
// ---------------------------------------------------------------------------

describe("VSObjectView — miniToolbar delegates", () => {
   it("should return true from showMiniToolbar when isMiniToolbarHidden returns false", async () => {
      miniToolbarServiceMock.isMiniToolbarHidden.mockReturnValue(false);
      const { comp } = await renderComponent();
      expect(comp.showMiniToolbar()).toBe(true);
   });

   it("should return false from showMiniToolbar when isMiniToolbarHidden returns true", async () => {
      miniToolbarServiceMock.isMiniToolbarHidden.mockReturnValue(true);
      const { comp } = await renderComponent();
      expect(comp.showMiniToolbar()).toBe(false);
   });

   it("should delegate to miniToolbarService on onMouseEnter", async () => {
      const { comp } = await renderComponent();
      const event = new MouseEvent("mouseenter");
      comp.onMouseEnter(event);
      expect(miniToolbarServiceMock.handleMouseEnter).toHaveBeenCalledWith(
         comp.model?.absoluteName, event
      );
   });

   it("should call hiddenFreeze on contextmenuOpened", async () => {
      const { comp } = await renderComponent();
      comp.contextmenuOpened();
      expect(miniToolbarServiceMock.hiddenFreeze).toHaveBeenCalledWith(comp.model?.absoluteName);
   });

   it("should call hiddenUnfreeze on contextmenuClosed", async () => {
      const { comp } = await renderComponent();
      comp.contextmenuClosed();
      expect(miniToolbarServiceMock.hiddenUnfreeze).toHaveBeenCalledWith(comp.model?.absoluteName);
   });
});
