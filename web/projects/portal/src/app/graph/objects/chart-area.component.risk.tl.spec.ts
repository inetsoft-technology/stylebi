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
 * ChartArea — Pass 2: Risk
 *
 * Uses the same direct-instantiation fixtures as Pass 1 (chart-area.component.test-helpers.ts).
 *
 * Scope (per prescan Pass 2 method list, asyncZones=4): model setter rapid-reassignment /
 * subscription leak, clearCanvasSubject flyoverApplied race (Bug #60159), scrollTop/scrollLeft
 * stale-assembly guard, axisLoading/_loadingAxesSet cycle reset (Bug #74260), concurrent
 * axis+plot loading/error state, ngOnDestroy cleanup.
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — model setter: scrollTop/scrollLeft subscriptions are now unsubscribed
 *                       on reassignment, matching the existing clearCanvasSubscription pattern
 *                       (Bug #75575, fixed)
 *   Group 2 [Risk 3] — clearCanvasSubject callback: flyoverApplied guard prevents the mutual-
 *                       flyover double-clear described in Bug #60159
 *   Group 5 [Risk 3] — fireLoading/fireLoaded: axis and plot failures are now tracked in
 *                       separate axisImageError/plotImageError flags and OR'd into imageError,
 *                       so a later success no longer erases an earlier failure (Bug #75575, fixed)
 *   Group 4 [Risk 2] — axisLoading: _loadingAxesSet is only cleared when _axisLoaded is
 *                       currently true (Bug #74260) — verify both branches of that guard
 *   Group 3 [Risk 2] — scrollTop/scrollLeft subscriptions: stale-assembly guard holds across
 *                       multiple model reassignments
 *   Group 6 [Risk 2] — ngOnDestroy: full subscription cleanup, and the pre-ngOnInit crash risk
 *                       from calling ngOnDestroy before devicePixelRatioMedia is ever assigned
 *                       (Bug #75575, fixed)
 *
 * Fixed bugs (Bug #75575):
 *   Bug A — scrollTop/scrollLeft subscription leak (Group 1): every model reassignment used to
 *     create a NEW subscription to pagingControlService.scrollTop()/scrollLeft() without
 *     unsubscribing the previous one. Fix: unsubscribe the previous scrollTopSubscription/
 *     scrollLeftSubscription at the top of the setter, mirroring clearCanvasSubscription.
 *   Bug B — imageError overwritten, not OR'd (Group 5): axisLoaded()/plotLoaded() each used to
 *     unconditionally set `this.imageError = !success` from their own local success flag, so a
 *     later success call silently cleared an earlier failure and onLoad fired despite it. Fix:
 *     track axisImageError/plotImageError separately, OR the failure into each on load
 *     completion, reset each only at the start of its own fresh load cycle, and derive
 *     imageError as axisImageError || plotImageError.
 *   Bug C — ngOnDestroy crashed if called before ngOnInit (Group 6): `devicePixelRatioMedia` is
 *     only assigned inside ngOnInit; ngOnDestroy dereferenced it unconditionally. Fix: use the
 *     optional-chaining guard `this.devicePixelRatioMedia?.removeEventListener(...)`.
 *
 * Out of scope this pass: see chart-area.component.interaction.tl.spec.ts header for the full
 * Pass 1/Pass 3 method split.
 */

import { ChartPlotArea } from "./chart-plot-area.component";
import { createComponent, makeModel } from "./chart-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: model setter — scroll subscription leak on reassignment [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartArea — model setter reassignment (subscription leak)", () => {
   it("should properly unsubscribe the OLD clearCanvasSubscription on reassignment (not leaked)", () => {
      const { comp } = createComponent();
      const firstSubject = comp.model.clearCanvasSubject;
      comp.model = makeModel();
      const clearCanvas = vi.fn();
      comp.chartObjectRef = [{ clearCanvas }] as any;

      firstSubject.next(null);

      expect(clearCanvas).not.toHaveBeenCalled();
   });

   // Bug #75575 (fixed): unlike clearCanvasSubscription (unsubscribed at the top of the setter
   // before creating a new one), scrollTopSubscription/scrollLeftSubscription used to be
   // reassigned with no matching unsubscribe call, so each model reassignment accumulated one
   // more live subscription on the SAME pagingControlService.scrollTop()/scrollLeft()
   // observable. Fix: unsubscribe the previous subscription at the top of the setter.
   it("should invoke the scrollTop sync callback only once per emission after two model reassignments", () => {
      const { comp, scrollTopSubject } = createComponent();
      comp.model = makeModel(); // second assignment — old scrollTop subscription is now cancelled
      const showPagingControlSpy = vi.spyOn(comp, "showPagingControl").mockImplementation(() => {});

      scrollTopSubject.next(77);

      expect(showPagingControlSpy).toHaveBeenCalledTimes(1);
   });

   it("should invoke the scrollLeft sync callback only once per emission after two model reassignments", () => {
      const { comp, scrollLeftSubject } = createComponent();
      comp.model = makeModel();
      const showPagingControlSpy = vi.spyOn(comp, "showPagingControl").mockImplementation(() => {});

      scrollLeftSubject.next(33);

      expect(showPagingControlSpy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 2: clearCanvasSubject callback — flyoverApplied guard (Bug #60159) [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartArea — clearCanvasSubject callback flyoverApplied guard", () => {
   function makePlotAreaChartObject(overrides: Record<string, any> = {}) {
      // Minimal stand-in satisfying `chartObject instanceof ChartPlotArea` via the prototype
      // chain, since the callback branch is gated on that instanceof check.
      return Object.assign(Object.create(ChartPlotArea.prototype), {
         flyover: true, flyOnClick: true, clearSelection: vi.fn(),
         ...overrides,
      });
   }

   it("should clear the plot's own selection and reset flyoverApplied when flyoverApplied is true", () => {
      const { comp } = createComponent();
      const plotArea = makePlotAreaChartObject();
      comp.chartObjectRef = [plotArea] as any;
      (comp as any).flyoverApplied = true;

      comp.model.clearCanvasSubject.next(true);

      expect(plotArea.clearSelection).toHaveBeenCalled();
      expect((comp as any).flyoverApplied).toBe(false);
   });

   it("should NOT clear the plot's own selection when flyoverApplied is false (prevents Bug #60159 double-clear)", () => {
      const { comp } = createComponent();
      const plotArea = makePlotAreaChartObject();
      comp.chartObjectRef = [plotArea] as any;
      (comp as any).flyoverApplied = false;

      comp.model.clearCanvasSubject.next(true);

      expect(plotArea.clearSelection).not.toHaveBeenCalled();
   });

   it("should NOT clear the plot's own selection when clearSelectionFlag is falsy, even if flyoverApplied is true", () => {
      const { comp } = createComponent();
      const plotArea = makePlotAreaChartObject();
      comp.chartObjectRef = [plotArea] as any;
      (comp as any).flyoverApplied = true;

      comp.model.clearCanvasSubject.next(false);

      expect(plotArea.clearSelection).not.toHaveBeenCalled();
      // The general canvas clear still runs for every chart object regardless of the flag.
   });

   it("should always clear the canvas on every chart object regardless of the flyover guard", () => {
      const { comp } = createComponent();
      const plotArea = makePlotAreaChartObject();
      plotArea.clearCanvas = vi.fn();
      comp.chartObjectRef = [plotArea] as any;
      (comp as any).flyoverApplied = false;

      comp.model.clearCanvasSubject.next(true);

      expect(plotArea.clearCanvas).toHaveBeenCalled();
   });

   it("should do nothing when chartObjectRef is not yet populated", () => {
      const { comp } = createComponent();
      comp.chartObjectRef = undefined as any;
      expect(() => comp.model.clearCanvasSubject.next(true)).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 3: scrollTop/scrollLeft stale-assembly guard across model reassignment [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — scroll sync stale-assembly guard", () => {
   it("should NOT sync scrollTop for a different assembly even after a model reassignment", () => {
      const { comp, pagingControlService, scrollTopSubject } = createComponent();
      comp.model = makeModel(); // second assignment — guard must still hold after resubscription
      pagingControlService.getCurrentAssembly.mockReturnValue("OtherChart");
      comp.scrollTop = 5;

      scrollTopSubject.next(999);

      expect(comp.scrollTop).toBe(5);
   });

   it("should re-evaluate getCurrentAssembly() on every emission, not just at subscribe time", () => {
      const { comp, pagingControlService, scrollTopSubject } = createComponent();
      pagingControlService.getCurrentAssembly.mockReturnValue("OtherChart");
      scrollTopSubject.next(1);
      expect(comp.scrollTop).toBe(0);

      pagingControlService.getCurrentAssembly.mockReturnValue("Chart1");
      scrollTopSubject.next(2);
      expect(comp.scrollTop).toBe(2);
   });
});

// ---------------------------------------------------------------------------
// Group 4: axisLoading / _loadingAxesSet cycle reset (Bug #74260) [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — axisLoading loadingAxesSet cycle reset (Bug #74260)", () => {
   it("should clear stale loadingAxesSet entries when starting a fresh cycle (_axisLoaded currently true)", () => {
      const { comp } = createComponent();
      // Simulate a residual entry surviving from an earlier cycle (defensive robustness case
      // described by the Bug #74260 comment) while the component is otherwise idle.
      (comp as any)._loadingAxesSet.add("StaleAxisFromPreviousCycle");
      (comp as any)._axisLoaded = true;

      comp.axisLoading("bottom_x_axis");

      expect(Array.from((comp as any)._loadingAxesSet)).toEqual(["bottom_x_axis"]);
   });

   it("should NOT clear the loadingAxesSet when a second axis starts loading mid-cycle (_axisLoaded already false)", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");

      comp.axisLoading("left_y_axis");

      expect(Array.from((comp as any)._loadingAxesSet).sort()).toEqual(["bottom_x_axis", "left_y_axis"]);
   });

   it("should reach _axisLoaded=true only after every tracked axis in the cycle reports loaded", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.axisLoading("left_y_axis");

      comp.axisLoaded(true, "bottom_x_axis");
      expect((comp as any)._axisLoaded).toBe(false);

      comp.axisLoaded(true, "left_y_axis");
      expect((comp as any)._axisLoaded).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: fireLoading/fireLoaded concurrent axis+plot state [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartArea — concurrent axis+plot loading/error state", () => {
   it("should emit onError immediately when the plot fails, even while the axis is still loading", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.plotLoading();
      const errors: any[] = [];
      comp.onError.subscribe(v => errors.push(v));

      comp.plotLoaded(false);

      expect(errors).toEqual([true]);
   });

   // Bug #75575 (fixed): axisLoaded()/plotLoaded() used to each set `this.imageError = !success`
   // unconditionally from their OWN local success flag, with no OR-with-previous-failure logic,
   // so an axis failure was silently erased by a later, unrelated plot success and onLoad fired
   // as if nothing had gone wrong. Fix: track axisImageError/plotImageError separately and OR
   // the failure into each on completion.
   it("should NOT fire onLoad after an axis failure just because the plot subsequently succeeds", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.plotLoading();
      comp.axisLoaded(false, "bottom_x_axis"); // axis fails; loadingAxesSet empties -> _axisLoaded=true; imageError=true
      const loads: any[] = [];
      comp.onLoad.subscribe(v => loads.push(v));

      comp.plotLoaded(true); // plot succeeds -> imageError reset to false -> both flags true -> onLoad fires

      expect(loads).toHaveLength(0);
   });

   // Bug #75575 follow-up (code review): resetting axisImageError/plotImageError at the start
   // of a fresh cycle must also recompute the public imageError flag immediately — otherwise a
   // stale error banner from the previous cycle keeps showing for the full duration of the new
   // load, only clearing once the matching axisLoaded()/plotLoaded() call resolves.
   it("should clear a stale imageError as soon as a fresh axis cycle starts, not only once it resolves", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.plotLoading();
      comp.axisLoaded(false, "bottom_x_axis"); // axis fails this cycle
      comp.plotLoaded(true); // plot succeeds; imageError stays true because of the axis failure
      expect(comp.imageError).toBe(true);

      comp.axisLoading("bottom_x_axis"); // fresh axis cycle starts (_axisLoaded was true)

      // imageError must already be false here, before axisLoaded() ever resolves the new cycle.
      expect(comp.imageError).toBe(false);
   });

   it("should emit onError (not onLoad) when the axis fails after the plot already succeeded", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.plotLoading();
      comp.plotLoaded(true);
      const errors: any[] = [];
      const loads: any[] = [];
      comp.onError.subscribe(v => errors.push(v));
      comp.onLoad.subscribe(v => loads.push(v));

      comp.axisLoaded(false, "bottom_x_axis");

      expect(errors).toEqual([true]);
      expect(loads).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnDestroy cleanup [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — ngOnDestroy full cleanup", () => {
   let originalMatchMedia: typeof window.matchMedia;

   beforeAll(() => {
      originalMatchMedia = window.matchMedia;
      window.matchMedia = vi.fn().mockReturnValue({
         addEventListener: vi.fn(),
         removeEventListener: vi.fn(),
      }) as any;
   });

   afterAll(() => {
      window.matchMedia = originalMatchMedia;
   });

   it("should unsubscribe clearCanvas, scrollTop, and scrollLeft subscriptions on destroy", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      const clearSub = (comp as any).clearCanvasSubscription;
      const scrollTopSub = (comp as any).scrollTopSubscription;
      const scrollLeftSub = (comp as any).scrollLeftSubscription;
      const clearSpy = vi.spyOn(clearSub, "unsubscribe");
      const topSpy = vi.spyOn(scrollTopSub, "unsubscribe");
      const leftSpy = vi.spyOn(scrollLeftSub, "unsubscribe");

      comp.ngOnDestroy();

      expect(clearSpy).toHaveBeenCalled();
      expect(topSpy).toHaveBeenCalled();
      expect(leftSpy).toHaveBeenCalled();
   });

   it("should remove the devicePixelRatio media-query listener on destroy", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      const media = (comp as any).devicePixelRatioMedia;
      const removeSpy = vi.spyOn(media, "removeEventListener");

      comp.ngOnDestroy();

      expect(removeSpy).toHaveBeenCalledWith("change", expect.any(Function));
   });

   // Bug #75575 (fixed): devicePixelRatioMedia is only assigned inside ngOnInit(). If a
   // component instance is destroyed without ngOnInit ever having run (e.g. removed before
   // Angular's first CD pass), ngOnDestroy() used to dereference it unconditionally and throw.
   // Fix: this.devicePixelRatioMedia?.removeEventListener(...).
   it("should not throw when ngOnDestroy is called without ngOnInit ever having run", () => {
      const { comp } = createComponent();
      expect(() => comp.ngOnDestroy()).not.toThrow();
   });
});
