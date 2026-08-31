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
 * VSSpinner — single pass
 *
 * Direct instantiation (not ATL render()), matching the established pattern for this component
 * family (VSTextInput, VSSlider): constructor order is (viewsheetClient, formDataService,
 * formInputService, debounceService, zone, context, dataTipService). debounceService.debounce()
 * is mocked to invoke its callback synchronously so changeValue()'s 200ms debounce doesn't need
 * fake timers; formDataService.checkFormData() is mocked to invoke its success callback
 * synchronously.
 *
 * Risk-first coverage:
 *   Group 4 [Risk 3] — ngOnChanges: viewer+submitted guard, subscribe/unsubscribe, isSubmitted
 *                       && unappliedChange dispatch
 *   Group 6 [Risk 3] — onClick: validate/emit/unappliedChange + refresh||writeBackDirectly
 *                       dispatch vs. pendingChange fallback
 *   Group 8 [Risk 3] — changeValue0 (via debounce): ctrlDown deferral, ovalue no-op guard,
 *                       checkFormData confirmedCallback → sendEvent
 *   Group 14 [Risk 3] — startHold/cancelHold: 300ms initial delay, 60ms repeat interval,
 *                        1500ms acceleration to 20ms, disabled-bound early stop
 *   Group 2 [Risk 2] — updateOnChange setter: pendingChange flush guard
 *   Group 7 [Risk 2] — onBlur: refresh && updateOnChange dispatch vs. pendingChange fallback
 *                       (no writeBackDirectly bypass, unlike onClick)
 *   Group 9 [Risk 2] — onKeyUp/onKeyDown: ctrlDown tracking + deferred pendingChange2 flush
 *   Group 11 [Risk 2] — getTextVerticalPosition(): font/vAlign/padding branch matrix
 *   Group 15 [Risk 2] — onIncrementClick/onDecrementClick: bounds-respecting step + focus
 *   Group 16 [Risk 2] — isIncrementDisabled/isDecrementDisabled: enabled/bounds matrix
 *   Group 17 [Risk 2] — enableEditing(): vizModern gate, viewer guard, focus restore
 *   Group 18 [Risk 2] — isInputDisabled(): vizModern/viewer/selected/editing gate matrix
 *   Group 1 [Risk 1] — selected setter: labelSelected clear guard, editing reset
 *   Group 3 [Risk 1] — ngOnInit: validate() + ovalue capture
 *   Group 5 [Risk 1] — ngOnDestroy: subscription cleanup
 *   Group 10 [Risk 1] — navigate(): UP/DOWN increment stepping
 *   Group 12 [Risk 1] — selectLabel(): preview guard
 *   Group 13 [Risk 1] — clearLabelSelection(): model-presence guard
 *   Group 19 [Risk 1] — onMouseDown(): vizModern-gated stopPropagation
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass:
 *   validate() — private helper with no independent entry point; exercised via ngOnInit
 *     (Group 3) and onClick/onBlur (Groups 6/7).
 *   clearNavSelection() — empty no-op override required by the NavigationComponent abstract
 *     contract; zero observable behavior.
 */

import { NgZone } from "@angular/core";
import { Subject } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { TestUtils } from "../../../common/test/test-utils";
import { VSSpinnerModel } from "../../model/vs-spinner-model";
import { NavigationKeys } from "../navigation-keys";
import { VSSpinner } from "./vs-spinner.component";

function makeModel(overrides: Partial<VSSpinnerModel> = {}): VSSpinnerModel {
   const model = TestUtils.createMockVSNumericRangeModel("VSSpinner", "Spinner1") as VSSpinnerModel;
   (model as any).writeBackDirectly = false;
   (model as any).labelSelected = false;
   return Object.assign(model, overrides);
}

function createComponent(opts: {
   model?: VSSpinnerModel;
   viewer?: boolean;
   preview?: boolean;
} = {}) {
   const viewsheetClient = { sendEvent: vi.fn(), runtimeId: "vs-test" };
   const formDataService = {
      checkFormData: vi.fn().mockImplementation(
         (_rid: any, _name: any, _selection: any, success: any, _fail?: any) => success()
      ),
   };
   const formInputService = { addPendingValue: vi.fn() };
   const debounceService = { debounce: vi.fn((_key: string, fn: Function) => fn()) };
   const context = {
      viewer: opts.viewer ?? true,
      preview: opts.preview ?? false,
      binding: false,
      embedAssembly: false,
   };
   const dataTipService = { isDataTip: vi.fn(() => false) };
   const zone = { run: (fn: Function) => fn() };

   const comp = new VSSpinner(
      viewsheetClient as any,
      formDataService as any,
      formInputService as any,
      debounceService as any,
      zone as unknown as NgZone,
      context as ContextProvider,
      dataTipService as unknown as DataTipService,
   );
   comp.model = opts.model ?? makeModel();

   return { comp, viewsheetClient, formDataService, formInputService, debounceService, context, dataTipService };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: selected setter [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — selected setter", () => {
   it("should clear model.labelSelected when deselected", () => {
      const { comp } = createComponent({ model: makeModel({ labelSelected: true } as any) });
      comp.selected = false;
      expect((comp.model as any).labelSelected).toBe(false);
      expect(comp.selected).toBe(false);
   });

   it("should NOT touch model.labelSelected when selected", () => {
      const { comp } = createComponent({ model: makeModel({ labelSelected: true } as any) });
      comp.selected = true;
      expect((comp.model as any).labelSelected).toBe(true);
      expect(comp.selected).toBe(true);
   });

   it("should not throw when deselected before a model is assigned", () => {
      const { comp } = createComponent();
      (comp as any)._model = undefined;
      expect(() => comp.selected = false).not.toThrow();
   });

   it("should clear model.editing when deselected", () => {
      const { comp } = createComponent({ model: makeModel({ editing: true } as any) });
      comp.selected = false;
      expect(comp.model.editing).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateOnChange setter [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — updateOnChange setter", () => {
   it("should flush a pending change when transitioning from false to true", () => {
      const { comp, debounceService } = createComponent();
      comp.updateOnChange = false;
      (comp as any).pendingChange = true;
      debounceService.debounce.mockClear();

      comp.updateOnChange = true;

      expect(debounceService.debounce).toHaveBeenCalled();
      expect(comp.updateOnChange).toBe(true);
   });

   it("should NOT flush when transitioning from false to true with no pending change", () => {
      const { comp, debounceService } = createComponent();
      comp.updateOnChange = false;
      (comp as any).pendingChange = false;
      debounceService.debounce.mockClear();

      comp.updateOnChange = true;

      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should NOT flush when already true (no false→true transition)", () => {
      const { comp, debounceService } = createComponent();
      comp.updateOnChange = true;
      (comp as any).pendingChange = true;
      debounceService.debounce.mockClear();

      comp.updateOnChange = true;

      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should always reset pendingChange to false after the setter runs", () => {
      const { comp } = createComponent();
      (comp as any).pendingChange = true;
      comp.updateOnChange = false;
      expect((comp as any).pendingChange).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — ngOnInit", () => {
   it("should clamp an out-of-range value and capture it as ovalue", () => {
      const { comp } = createComponent({ model: makeModel({ value: 500, max: 100, min: 0 }) });
      comp.ngOnInit();
      expect(comp.model.value).toBe(100);
      expect((comp as any).ovalue).toBe(100);
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnChanges [Risk 3]
// ---------------------------------------------------------------------------

describe("VSSpinner — ngOnChanges", () => {
   it("should NOT subscribe when not a viewer", () => {
      const { comp } = createComponent({ viewer: false });
      comp.submitted = new Subject<boolean>();
      comp.ngOnChanges({});
      expect(comp.submittedForm).toBeFalsy();
   });

   it("should NOT subscribe when submitted is not provided", () => {
      const { comp } = createComponent({ viewer: true });
      comp.submitted = undefined;
      comp.ngOnChanges({});
      expect(comp.submittedForm).toBeFalsy();
   });

   it("should subscribe to submitted when viewer and submitted are both present", () => {
      const { comp } = createComponent({ viewer: true });
      comp.submitted = new Subject<boolean>();
      comp.ngOnChanges({});
      expect(comp.submittedForm).toBeTruthy();
   });

   it("should call changeValue via debounce when isSubmitted and there is an unapplied change", () => {
      const { comp, debounceService } = createComponent({ viewer: true });
      const submitted = new Subject<boolean>();
      comp.submitted = submitted;
      comp.ngOnChanges({});
      (comp as any).unappliedChange = true;

      submitted.next(true);

      expect(debounceService.debounce).toHaveBeenCalled();
   });

   it("should NOT call changeValue when isSubmitted but there is no unapplied change", () => {
      const { comp, debounceService } = createComponent({ viewer: true });
      const submitted = new Subject<boolean>();
      comp.submitted = submitted;
      comp.ngOnChanges({});
      (comp as any).unappliedChange = false;

      submitted.next(true);

      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should NOT call changeValue when there is an unapplied change but isSubmitted is false", () => {
      const { comp, debounceService } = createComponent({ viewer: true });
      const submitted = new Subject<boolean>();
      comp.submitted = submitted;
      comp.ngOnChanges({});
      (comp as any).unappliedChange = true;

      submitted.next(false);

      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should unsubscribe the previous submittedForm before creating a new one", () => {
      const { comp } = createComponent({ viewer: true });
      comp.submitted = new Subject<boolean>();
      comp.ngOnChanges({});
      const firstForm = comp.submittedForm;
      const unsubSpy = vi.spyOn(firstForm, "unsubscribe");

      comp.submitted = new Subject<boolean>();
      comp.ngOnChanges({});

      expect(unsubSpy).toHaveBeenCalled();
      expect(comp.submittedForm).not.toBe(firstForm);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnDestroy [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — ngOnDestroy", () => {
   it("should unsubscribe submittedForm when present", () => {
      const { comp } = createComponent({ viewer: true });
      comp.submitted = new Subject<boolean>();
      comp.ngOnChanges({});
      const unsubSpy = vi.spyOn(comp.submittedForm, "unsubscribe");

      comp.ngOnDestroy();

      expect(unsubSpy).toHaveBeenCalled();
   });

   it("should not throw when submittedForm was never created", () => {
      const { comp } = createComponent();
      expect(() => comp.ngOnDestroy()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 6: onClick [Risk 3]
// ---------------------------------------------------------------------------

describe("VSSpinner — onClick", () => {
   it("should stop propagation, validate, emit spinnerClicked, and mark an unapplied change", () => {
      // refresh=false + writeBackDirectly=false takes the deferred pendingChange path, so
      // changeValue0() (which resets unappliedChange back to false — see Group 8) never runs
      // and this test can observe the flag onClick() itself sets in isolation.
      const { comp } = createComponent({
         model: makeModel({ value: 500, max: 100, refresh: false, writeBackDirectly: false } as any)
      });
      const emitted: string[] = [];
      comp.spinnerClicked.subscribe(v => emitted.push(v));
      const event = new MouseEvent("click");
      const stopSpy = vi.spyOn(event, "stopPropagation");

      comp.onClick(event);

      expect(stopSpy).toHaveBeenCalled();
      expect(comp.model.value).toBe(100); // validated/clamped
      expect(emitted).toEqual([comp.model.absoluteName]);
      expect((comp as any).unappliedChange).toBe(true);
   });

   it("should flush immediately when refresh=true and updateOnChange=true", () => {
      const { comp, debounceService } = createComponent({
         model: makeModel({ refresh: true } as any)
      });
      comp.updateOnChange = true;

      comp.onClick(new MouseEvent("click"));

      expect(debounceService.debounce).toHaveBeenCalled();
   });

   it("should flush immediately when writeBackDirectly=true, even if refresh=false", () => {
      const { comp, debounceService } = createComponent({
         model: makeModel({ refresh: false, writeBackDirectly: true } as any)
      });
      comp.updateOnChange = false;

      comp.onClick(new MouseEvent("click"));

      expect(debounceService.debounce).toHaveBeenCalled();
   });

   it("should defer via pendingChange and record the pending value when neither condition applies", () => {
      const { comp, debounceService, formInputService } = createComponent({
         model: makeModel({ refresh: false, writeBackDirectly: false, value: 5 } as any)
      });
      comp.updateOnChange = false;

      comp.onClick(new MouseEvent("click"));

      expect(debounceService.debounce).not.toHaveBeenCalled();
      expect((comp as any).pendingChange).toBe(true);
      expect(formInputService.addPendingValue).toHaveBeenCalledWith(comp.model.absoluteName, 5);
   });
});

// ---------------------------------------------------------------------------
// Group 7: onBlur [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — onBlur", () => {
   it("should flush immediately when refresh=true and updateOnChange=true", () => {
      const { comp, debounceService } = createComponent({
         model: makeModel({ refresh: true } as any)
      });
      comp.updateOnChange = true;

      comp.onBlur(new MouseEvent("blur"));

      expect(debounceService.debounce).toHaveBeenCalled();
   });

   it("should defer via pendingChange when refresh=true but updateOnChange=false", () => {
      const { comp, debounceService, formInputService } = createComponent({
         model: makeModel({ refresh: true, value: 9 } as any)
      });
      comp.updateOnChange = false;

      comp.onBlur(new MouseEvent("blur"));

      expect(debounceService.debounce).not.toHaveBeenCalled();
      expect((comp as any).pendingChange).toBe(true);
      expect(formInputService.addPendingValue).toHaveBeenCalledWith(comp.model.absoluteName, 9);
   });

   it("should defer via pendingChange when refresh=false, regardless of writeBackDirectly", () => {
      // Unlike onClick, onBlur has no writeBackDirectly bypass — this is the asymmetry to guard.
      const { comp, debounceService, formInputService } = createComponent({
         model: makeModel({ refresh: false, writeBackDirectly: true, value: 7 } as any)
      });
      comp.updateOnChange = false;

      comp.onBlur(new MouseEvent("blur"));

      expect(debounceService.debounce).not.toHaveBeenCalled();
      expect((comp as any).pendingChange).toBe(true);
      expect(formInputService.addPendingValue).toHaveBeenCalledWith(comp.model.absoluteName, 7);
   });

   it("should mark an unapplied change and emit spinnerClicked", () => {
      // refresh=false takes the deferred pendingChange path, so changeValue0() (which resets
      // unappliedChange back to false — see Group 8) never runs in this test.
      const { comp } = createComponent({ model: makeModel({ refresh: false } as any) });
      const emitted: string[] = [];
      comp.spinnerClicked.subscribe(v => emitted.push(v));

      comp.onBlur(new MouseEvent("blur"));

      expect(emitted).toEqual([comp.model.absoluteName]);
      expect((comp as any).unappliedChange).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: changeValue / changeValue0 (via debounce) [Risk 3]
// ---------------------------------------------------------------------------

describe("VSSpinner — changeValue0", () => {
   it("should defer via pendingChange2 and send nothing while ctrlDown is true", () => {
      const { comp, viewsheetClient } = createComponent({
         model: makeModel({ refresh: true, value: 42 } as any)
      });
      comp.updateOnChange = true;
      (comp as any).ctrlDown = true;

      comp.onClick(new MouseEvent("click"));

      expect((comp as any).pendingChange2).toBe(true);
      expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
   });

   it("should send a changeValue event and update ovalue when the value actually changed", () => {
      const { comp, viewsheetClient } = createComponent({
         model: makeModel({ refresh: true, value: 42 } as any)
      });
      (comp as any).ovalue = 0;
      comp.updateOnChange = true;

      comp.onClick(new MouseEvent("click"));

      expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/vsSpinner/changeValue",
         expect.objectContaining({ value: 42 })
      );
      expect((comp as any).ovalue).toBe(42);
      expect((comp as any).unappliedChange).toBe(false);
   });

   it("should NOT send an event when the value is unchanged from ovalue", () => {
      const { comp, viewsheetClient } = createComponent({
         model: makeModel({ refresh: true, value: 42 } as any)
      });
      (comp as any).ovalue = 42;
      comp.updateOnChange = true;

      comp.onClick(new MouseEvent("click"));

      expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 9: onKeyUp / onKeyDown (HostListener) [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — onKeyUp / onKeyDown", () => {
   it("should set ctrlDown=true on keydown with keyCode 17", () => {
      const { comp } = createComponent();
      comp.onKeyDown({ keyCode: 17 } as KeyboardEvent);
      expect((comp as any).ctrlDown).toBe(true);
   });

   it("should ignore keydown with a different keyCode", () => {
      const { comp } = createComponent();
      comp.onKeyDown({ keyCode: 65 } as KeyboardEvent);
      expect((comp as any).ctrlDown).toBe(false);
   });

   it("should clear ctrlDown on keyup with keyCode 17 and flush a pending change", () => {
      const { comp, debounceService } = createComponent();
      (comp as any).ctrlDown = true;
      (comp as any).pendingChange2 = true;

      comp.onKeyUp({ keyCode: 17 } as KeyboardEvent);

      expect((comp as any).ctrlDown).toBe(false);
      expect((comp as any).pendingChange2).toBe(false);
      expect(debounceService.debounce).toHaveBeenCalled();
   });

   it("should clear ctrlDown on keyup with keyCode 17 without flushing when nothing is pending", () => {
      const { comp, debounceService } = createComponent();
      (comp as any).ctrlDown = true;
      (comp as any).pendingChange2 = false;

      comp.onKeyUp({ keyCode: 17 } as KeyboardEvent);

      expect((comp as any).ctrlDown).toBe(false);
      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should ignore keyup with a different keyCode", () => {
      const { comp } = createComponent();
      (comp as any).ctrlDown = true;

      comp.onKeyUp({ keyCode: 65 } as KeyboardEvent);

      expect((comp as any).ctrlDown).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 10: navigate() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — navigate", () => {
   it("should decrement the value by increment on DOWN", () => {
      const { comp } = createComponent({ model: makeModel({ value: 40, increment: 20 }) });
      (comp as any).navigate(NavigationKeys.DOWN);
      expect(comp.model.value).toBe(20);
   });

   it("should increment the value by increment on UP", () => {
      const { comp } = createComponent({ model: makeModel({ value: 40, increment: 20 }) });
      (comp as any).navigate(NavigationKeys.UP);
      expect(comp.model.value).toBe(60);
   });

   it("should do nothing for other keys", () => {
      const { comp } = createComponent({ model: makeModel({ value: 40, increment: 20 }) });
      (comp as any).navigate(NavigationKeys.LEFT);
      expect(comp.model.value).toBe(40);
   });
});

// ---------------------------------------------------------------------------
// Group 11: getTextVerticalPosition() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — getTextVerticalPosition", () => {
   it("should return an empty string when no font is set", () => {
      const { comp } = createComponent();
      comp.model.objectFormat.font = "";
      expect(comp.getTextVerticalPosition()).toBe("");
   });

   it("should return an empty string when vAlign is middle, even with a font set", () => {
      const { comp } = createComponent();
      comp.model.objectFormat.font = "bold 12px Arial";
      comp.model.objectFormat.vAlign = "middle";
      expect(comp.getTextVerticalPosition()).toBe("");
   });

   it("should return an empty string when the computed padding is not positive", () => {
      const { comp } = createComponent();
      comp.model.objectFormat.font = "bold 12px Arial";
      comp.model.objectFormat.vAlign = "top";
      comp.model.objectFormat.height = 14; // 14 - 12 - 3 = -1 <= 0
      expect(comp.getTextVerticalPosition()).toBe("");
   });

   it("should return top padding shorthand when vAlign is top with positive padding", () => {
      const { comp } = createComponent();
      comp.model.objectFormat.font = "bold 12px Arial";
      comp.model.objectFormat.vAlign = "top";
      comp.model.objectFormat.height = 30; // 30 - 12 - 3 = 15
      expect(comp.getTextVerticalPosition()).toBe("0px 4px 15px 4px");
   });

   it("should return bottom padding shorthand when vAlign is not top or middle with positive padding", () => {
      const { comp } = createComponent();
      comp.model.objectFormat.font = "bold 12px Arial";
      comp.model.objectFormat.vAlign = "bottom";
      comp.model.objectFormat.height = 30; // 30 - 12 - 3 = 15
      expect(comp.getTextVerticalPosition()).toBe("15px 4px 0px 4px");
   });
});

// ---------------------------------------------------------------------------
// Group 12: selectLabel() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — selectLabel", () => {
   it("should NOT select the label in a preview context", () => {
      const { comp } = createComponent({ preview: true });
      comp.selectLabel(new MouseEvent("click"));
      expect((comp.model as any).labelSelected).toBe(false);
   });

   it("should select the label outside of a preview context", () => {
      const { comp } = createComponent({ preview: false });
      comp.selectLabel(new MouseEvent("click"));
      expect((comp.model as any).labelSelected).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 13: clearLabelSelection() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — clearLabelSelection", () => {
   it("should clear labelSelected when a model is present", () => {
      const { comp } = createComponent({ model: makeModel({ labelSelected: true } as any) });
      comp.clearLabelSelection();
      expect((comp.model as any).labelSelected).toBe(false);
   });

   it("should not throw when no model is present", () => {
      const { comp } = createComponent();
      (comp as any)._model = undefined;
      expect(() => comp.clearLabelSelection()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 14: startHold / cancelHold (hold-to-repeat) [Risk 3]
// ---------------------------------------------------------------------------

describe("VSSpinner — startHold / cancelHold", () => {
   beforeEach(() => vi.useFakeTimers());
   afterEach(() => vi.useRealTimers());

   it("should NOT step before the initial 300ms delay elapses", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20 }) });
      comp.startHold(1);
      vi.advanceTimersByTime(299);
      expect(comp.model.value).toBe(0);
   });

   it("should step once, emit, and mark an unapplied change after the initial delay", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20 }) });
      const emitted: string[] = [];
      comp.spinnerClicked.subscribe(v => emitted.push(v));

      comp.startHold(1);
      vi.advanceTimersByTime(300);

      expect(comp.model.value).toBe(20);
      expect(emitted).toEqual([comp.model.absoluteName]);
      expect((comp as any).unappliedChange).toBe(true);
   });

   it("should repeat stepping every 60ms after the initial delay", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20, max: 1000 }) });
      comp.startHold(1);
      vi.advanceTimersByTime(300); // step 1 -> 20
      vi.advanceTimersByTime(60); // step 2 -> 40
      vi.advanceTimersByTime(60); // step 3 -> 60
      expect(comp.model.value).toBe(60);
   });

   it("should decrement on repeat when held in the negative direction", () => {
      const { comp } = createComponent({ model: makeModel({ value: 100, increment: 20, min: -1000 }) });
      comp.startHold(-1);
      vi.advanceTimersByTime(300); // step 1 -> 80
      vi.advanceTimersByTime(60); // step 2 -> 60
      expect(comp.model.value).toBe(60);
   });

   it("should accelerate the repeat interval to 20ms after 1500ms of continuous holding", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 1, max: 100000 }) });
      comp.startHold(1);
      vi.advanceTimersByTime(300 + 1500);
      expect((comp as any).holdRepeatInterval).toBe(20);
   });

   it("should stop repeating once the increment bound is reached", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20, max: 40 }) });
      comp.startHold(1);
      vi.advanceTimersByTime(300); // step -> 20
      vi.advanceTimersByTime(60); // step -> 40 (== max, now disabled)
      vi.advanceTimersByTime(60); // guard should cancel the hold instead of stepping again

      expect(comp.model.value).toBe(40);
      expect((comp as any).holdRepeatTimer).toBeNull();
   });

   it("should stop repeating once the decrement bound is reached", () => {
      const { comp } = createComponent({ model: makeModel({ value: 40, increment: 20, min: 0 }) });
      comp.startHold(-1);
      vi.advanceTimersByTime(300); // step -> 20
      vi.advanceTimersByTime(60); // step -> 0 (== min, now disabled)
      vi.advanceTimersByTime(60); // guard should cancel the hold instead of stepping again

      expect(comp.model.value).toBe(0);
      expect((comp as any).holdRepeatTimer).toBeNull();
   });

   it("should stop all further stepping once cancelHold is called", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20, max: 1000 }) });
      comp.startHold(1);
      vi.advanceTimersByTime(300); // step -> 20

      comp.cancelHold();
      vi.advanceTimersByTime(500);

      expect(comp.model.value).toBe(20);
   });

   it("should clear the init timer before it fires when cancelHold is called immediately", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20 }) });
      comp.startHold(1);
      comp.cancelHold();
      vi.advanceTimersByTime(1000);
      expect(comp.model.value).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 15: onIncrementClick / onDecrementClick [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — onIncrementClick / onDecrementClick", () => {
   it("should step up by increment, emit, and mark an unapplied change", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20, max: 100 }) });
      const emitted: string[] = [];
      comp.spinnerClicked.subscribe(v => emitted.push(v));

      comp.onIncrementClick();

      expect(comp.model.value).toBe(20);
      expect(emitted).toEqual([comp.model.absoluteName]);
      expect((comp as any).unappliedChange).toBe(true);
   });

   it("should do nothing when isIncrementDisabled is true", () => {
      const { comp } = createComponent({ model: makeModel({ value: 100, increment: 20, max: 100 }) });
      comp.onIncrementClick();
      expect(comp.model.value).toBe(100);
   });

   it("should step down by increment when not disabled", () => {
      const { comp } = createComponent({ model: makeModel({ value: 100, increment: 20, min: 0 }) });
      comp.onDecrementClick();
      expect(comp.model.value).toBe(80);
   });

   it("should do nothing when isDecrementDisabled is true", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, increment: 20, min: 0 }) });
      comp.onDecrementClick();
      expect(comp.model.value).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 16: isIncrementDisabled / isDecrementDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — isIncrementDisabled / isDecrementDisabled", () => {
   it("should disable increment when the assembly is not enabled", () => {
      const { comp } = createComponent({ model: makeModel({ enabled: false, value: 0, max: 100 } as any) });
      expect(comp.isIncrementDisabled).toBe(true);
   });

   it("should disable increment when value has reached max", () => {
      const { comp } = createComponent({ model: makeModel({ value: 100, max: 100 }) });
      expect(comp.isIncrementDisabled).toBe(true);
   });

   it("should NOT disable increment when enabled and below max", () => {
      const { comp } = createComponent({ model: makeModel({ value: 50, max: 100 }) });
      expect(comp.isIncrementDisabled).toBe(false);
   });

   it("should disable decrement when the assembly is not enabled", () => {
      const { comp } = createComponent({ model: makeModel({ enabled: false, value: 0, min: 0 } as any) });
      expect(comp.isDecrementDisabled).toBe(true);
   });

   it("should disable decrement when value has reached min", () => {
      const { comp } = createComponent({ model: makeModel({ value: 0, min: 0 }) });
      expect(comp.isDecrementDisabled).toBe(true);
   });

   it("should NOT disable decrement when enabled and above min", () => {
      const { comp } = createComponent({ model: makeModel({ value: 50, min: 0 }) });
      expect(comp.isDecrementDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 17: enableEditing() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — enableEditing", () => {
   it("should NOT set model.editing for a legacy (non-modern) spinner", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ vizModern: false } as any)
      });
      comp.enableEditing();
      expect(comp.model.editing).toBeUndefined();
   });

   it("should set model.editing=true for a modern spinner in the composer", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ vizModern: true } as any)
      });
      comp.enableEditing();
      expect(comp.model.editing).toBe(true);
   });

   it("should set model.editing=false for a modern spinner in the viewer", () => {
      const { comp } = createComponent({
         viewer: true,
         model: makeModel({ vizModern: true } as any)
      });
      comp.enableEditing();
      expect(comp.model.editing).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 18: isInputDisabled() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSSpinner — isInputDisabled", () => {
   it("should always be false for a legacy (non-modern) spinner", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ vizModern: false } as any)
      });
      comp.selected = false;
      expect(comp.isInputDisabled()).toBe(false);
   });

   it("should be false in the viewer, regardless of selection/editing state", () => {
      const { comp } = createComponent({
         viewer: true,
         model: makeModel({ vizModern: true } as any)
      });
      expect(comp.isInputDisabled()).toBe(false);
   });

   it("should be true in the composer when not selected", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ vizModern: true } as any)
      });
      comp.selected = false;
      expect(comp.isInputDisabled()).toBe(true);
   });

   it("should be true in the composer when selected but not yet editing", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ vizModern: true, editing: false } as any)
      });
      comp.selected = true;
      expect(comp.isInputDisabled()).toBe(true);
   });

   it("should be false in the composer when selected and editing", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ vizModern: true, editing: true } as any)
      });
      comp.selected = true;
      expect(comp.isInputDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 19: onMouseDown() [Risk 1]
// ---------------------------------------------------------------------------

describe("VSSpinner — onMouseDown", () => {
   it("should stop propagation for a legacy (non-modern) spinner", () => {
      const { comp } = createComponent({ model: makeModel({ vizModern: false } as any) });
      const event = new MouseEvent("mousedown");
      const stopSpy = vi.spyOn(event, "stopPropagation");

      comp.onMouseDown(event);

      expect(stopSpy).toHaveBeenCalled();
   });

   it("should NOT stop propagation for a modern spinner", () => {
      const { comp } = createComponent({ model: makeModel({ vizModern: true } as any) });
      const event = new MouseEvent("mousedown");
      const stopSpy = vi.spyOn(event, "stopPropagation");

      comp.onMouseDown(event);

      expect(stopSpy).not.toHaveBeenCalled();
   });
});
