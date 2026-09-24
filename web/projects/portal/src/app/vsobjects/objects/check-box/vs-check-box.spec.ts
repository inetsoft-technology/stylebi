/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { TimerService } from "../data-tip/timer.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { NavigationKeys } from "../navigation-keys";
import { VSCheckBoxModel } from "../../model/vs-check-box-model";
import { VSCheckBox } from "./vs-check-box.component";

describe("vs check box component unit case", () => {
   let fixture: ComponentFixture<VSCheckBox>;
   let vsCheckBox: VSCheckBox;
   let socket: any;
   let interactService: any;
   let debounceService: any;
   let dataTipService: any;
   let dialogService: any;
   let modelService: any;
   let ssoHeartbeatService: any;
   let timerService: any;

   beforeEach(() => {
      socket = {sendEvent: vi.fn()};
      interactService = {
         addInteractable: vi.fn(),
         notify: vi.fn(),
         removeInteractable: vi.fn()
      };
      const formDataService = {
         checkFormData: vi.fn(),
         removeObject: vi.fn(),
         addObject: vi.fn(),
         replaceObject: vi.fn()
      };
      debounceService = {debounce: vi.fn((key, fn, delay, args) => fn(...args))};
      dataTipService = {isDataTip: vi.fn(), scrolled: new Subject<void>()};
      const contextProvider = {};
      dialogService = {open: vi.fn()};
      modelService = {getModel: vi.fn()};
      ssoHeartbeatService = {heartbeat: vi.fn()};
      timerService = {
         defer: vi.fn((fn) => {
            fn();
         })
      };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule, VSCheckBox, VSPopComponentDirective, InteractableDirective, DefaultFocusDirective, SafeFontDirective],
         
         schemas: [
            NO_ERRORS_SCHEMA
         ],
         providers: [
            FormInputService,
            ViewsheetClientService,
            StompClientService,
            PopComponentService,
            {provide: ContextProvider, useValue: contextProvider},
            {provide: InteractService, useValue: interactService},
            {provide: CheckFormDataService, useValue: formDataService},
            {provide: DebounceService, useValue: debounceService},
            {provide: DataTipService, useValue: dataTipService},
            {provide: DialogService, useValue: dialogService},
            {provide: ModelService, useValue: modelService},
            {provide: SsoHeartbeatService, useValue: ssoHeartbeatService},
            {provide: TimerService, useValue: timerService},
         ]
      }).compileComponents();

      fixture = TestBed.createComponent(VSCheckBox);
   });

   //Bug #18435 should apply H aligment
   //Bug #18926 should apply V aligment
   it("should apply H|V aligment", () => {
      let checkBoxModel = TestUtils.createMockVSCheckBoxModel("checkbox1");
      let vsformat = TestUtils.createMockVSFormatModel();
      vsformat.hAlign = "right";
      vsformat.width = 50;
      vsformat.height = 18;
      vsformat.vAlign = "bottom";
      checkBoxModel.objectFormat = vsformat;
      checkBoxModel.detailFormat = vsformat;
      checkBoxModel.labels = ["AAA", "BBB"];

      vsCheckBox = <VSCheckBox>fixture.componentInstance;
      vsCheckBox.model = checkBoxModel;
      fixture.detectChanges();
      let check = fixture.nativeElement.querySelector("div.vs-check-box");
      let checkItem = fixture.nativeElement.querySelectorAll("div.checkbox")[0];
      expect(check.style["text-align"]).toEqual("right");
      expect(checkItem.style["padding-left"]).not.toBeNull();
      expect(check.style["vertical-align"]).toEqual("bottom");
      //expect(checkItem.style["align-items"]).toBe("flex-end");
   });
});

const APPLY_URL = "/events/checkBox/applySelection";
const MODEL_URL = "/events/vsview/object/model";

describe.each([
   ["portal viewer", {viewer: true, preview: false}],
   ["composer preview", {viewer: false, preview: true}],
   ["composer edit mode", {viewer: false, preview: false}]
])("VSCheckBox pending selection in %s (Bug #76959)", (contextName, contextFlags) => {
   let fixture: ComponentFixture<VSCheckBox>;
   let checkBox: VSCheckBox;
   let socket: any;
   let formInputService: any;
   // debounced callbacks that have not been fired yet, keyed like DebounceService
   let debounced: Map<string, { fn: Function, args: any[] }>;
   let formCheckResult: "confirm" | "cancel" | "defer";
   // the confirm callback of a form data check that is still waiting for the user
   let deferredConfirm: Function;

   function createModel(selected: string[], values: string[] = ["A", "B", "C"]): VSCheckBoxModel {
      const model = TestUtils.createMockVSCheckBoxModel("CheckBox1");
      model.objectFormat = TestUtils.createMockVSFormatModel();
      model.labels = values.slice();
      model.values = values.slice();
      model.selectedObjects = selected.slice();
      model.selectedLabels = selected.slice();
      model.dataColCount = 1;
      model.dataRowCount = values.length;
      return model;
   }

   /** Simulates a RefreshVSObjectCommand replacing the model. */
   function pushModel(selected: string[], values?: string[]): VSCheckBoxModel {
      const model = createModel(selected, values);
      fixture.componentRef.setInput("model", model);
      return model;
   }

   /** Fires the pending debounced send, as DebounceService would after 500ms. */
   function flushDebounce(): void {
      const entries = Array.from(debounced.values());
      debounced.clear();
      entries.forEach(({fn, args}) => fn(...args));
   }

   function inputs(): HTMLInputElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll("input[type=checkbox]"));
   }

   async function checked(): Promise<boolean[]> {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      return inputs().map((input) => input.checked);
   }

   async function click(index: number): Promise<void> {
      inputs()[index].click();
      fixture.detectChanges();
      await fixture.whenStable();
   }

   function sentValues(): any[] {
      return socket.sendEvent.mock.calls.filter((call) => call[0] === APPLY_URL)
         .map((call) => JSON.parse(JSON.stringify(call[1].value)));
   }

   beforeEach(async () => {
      vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
      debounced = new Map();
      formCheckResult = "confirm";
      deferredConfirm = null;
      socket = {sendEvent: vi.fn(), runtimeId: "vs1", commands: new Subject<any>()};
      const formDataService = {
         checkFormData: vi.fn((runtimeId, name, selection, confirmed, canceled) => {
            if(formCheckResult === "confirm") {
               confirmed();
            }
            else if(formCheckResult === "defer") {
               deferredConfirm = confirmed;
            }
            else {
               canceled();
            }
         }),
         removeObject: vi.fn(),
         addObject: vi.fn(),
         replaceObject: vi.fn()
      };
      formInputService = {addPendingValue: vi.fn()};
      const debounceService = {
         debounce: vi.fn((key: string, fn: Function, delay: number, args: any[]) => {
            debounced.set(key, {fn, args});
         }),
         cancel: vi.fn()
      };

      TestBed.configureTestingModule({
         imports: [VSCheckBox],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            PopComponentService,
            {provide: ViewsheetClientService, useValue: socket},
            {provide: CheckFormDataService, useValue: formDataService},
            {provide: FormInputService, useValue: formInputService},
            {provide: DebounceService, useValue: debounceService},
            {provide: ContextProvider, useValue: Object.assign({}, contextFlags)},
            {provide: DataTipService, useValue: {isDataTip: vi.fn(), scrolled: new Subject<void>()}},
            {provide: ModelService, useValue: {getModel: vi.fn(), sendModel: vi.fn()}},
            {provide: InteractService, useValue: {
               addInteractable: vi.fn(), notify: vi.fn(), removeInteractable: vi.fn()}}
         ]
      });
      await TestBed.compileComponents();

      fixture = TestBed.createComponent(VSCheckBox);
      checkBox = fixture.componentInstance;
      pushModel(["A"]);
      expect(await checked()).toEqual([true, false, false]);
   });

   afterEach(() => {
      fixture.destroy();
      vi.useRealTimers();
   });

   it("ignores a stale model of the previous selection while the send is debounced", async () => {
      await click(1);
      expect(await checked()).toEqual([true, true, false]);
      expect(sentValues().length).toBe(0);

      const stale = pushModel(["A"]);

      expect(await checked()).toEqual([true, true, false]);
      // the incoming model is not modified
      expect(stale.selectedObjects).toEqual(["A"]);

      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"]]);
   });

   it("ignores a stale model of the previous selection after the send, before the ack", async () => {
      await click(1);
      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"]]);

      pushModel(["A"]);
      pushModel(["A"]);

      expect(await checked()).toEqual([true, true, false]);
   });

   it("clears the pending selection when the server acknowledges it in any order", async () => {
      await click(1);
      flushDebounce();
      pushModel(["A"]);
      pushModel(["B", "A"]);
      expect(await checked()).toEqual([true, true, false]);

      // a later server change is no longer ignored
      pushModel(["C"]);
      expect(await checked()).toEqual([false, false, true]);
   });

   it("does not treat a matching model received before the send as the ack", async () => {
      await click(1);
      pushModel(["A", "B"]);
      flushDebounce();
      pushModel(["A"]);

      expect(await checked()).toEqual([true, true, false]);
   });

   it("toggles the displayed selection when clicked again while a stale model is shown", async () => {
      await click(1);
      flushDebounce();
      pushModel(["A"]);

      await click(2);
      expect(await checked()).toEqual([true, true, true]);
      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"], ["A", "B", "C"]]);

      // the ack of the first click is stale for the second one
      pushModel(["A", "B"]);
      expect(await checked()).toEqual([true, true, true]);

      pushModel(["C", "B", "A"]);
      pushModel(["A"]);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("keeps the displayed selection over quick add B, add C, remove B with stale models between", async () => {
      await click(1);
      pushModel(["A"]);
      expect(await checked()).toEqual([true, true, false]);
      flushDebounce();
      pushModel(["A"]);

      await click(2);
      pushModel(["A", "B"]);
      pushModel(["A"]);
      expect(await checked()).toEqual([true, true, true]);
      flushDebounce();
      pushModel(["A", "B"]);
      expect(await checked()).toEqual([true, true, true]);

      await click(1);
      expect(await checked()).toEqual([true, false, true]);
      pushModel(["A", "B", "C"]);
      pushModel(["A", "B"]);
      expect(await checked()).toEqual([true, false, true]);
      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"], ["A", "B", "C"], ["A", "C"]]);
      pushModel(["A", "B", "C"]);
      expect(await checked()).toEqual([true, false, true]);

      pushModel(["C", "A"]);
      expect(await checked()).toEqual([true, false, true]);
      // released by the ack, a later server change (e.g. by script) is shown
      pushModel(["B"]);
      expect(await checked()).toEqual([false, true, false]);
   });

   it("does not guard the selection applied when ctrl is released", async () => {
      checkBox.onKeyDown(<KeyboardEvent> {keyCode: 17});
      await click(1);
      checkBox.onKeyUp(<KeyboardEvent> {keyCode: 17});
      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"]]);

      pushModel(["A"]);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("unchecks a value from the displayed selection while a stale model is shown", async () => {
      await click(1);
      flushDebounce();
      pushModel(["A"]);

      await click(1);
      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"], ["A"]]);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("shows a server override once the pending selection times out", async () => {
      await click(1);
      flushDebounce();
      pushModel(["C"]);
      expect(await checked()).toEqual([true, true, false]);

      vi.advanceTimersByTime(2000);

      expect(await checked()).toEqual([false, false, true]);
   });

   it("shows a server override back to the previous selection once the pending selection times out", async () => {
      await click(1);
      flushDebounce();
      pushModel(["A"]);

      vi.advanceTimersByTime(1999);
      expect(await checked()).toEqual([true, true, false]);

      vi.advanceTimersByTime(1);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("releases the pending selection even if it is never sent", async () => {
      await click(1);
      pushModel(["A"]);

      vi.advanceTimersByTime(2500);

      expect(await checked()).toEqual([true, false, false]);
   });

   it("shows the latest server model once the absolute limit after the send is reached", async () => {
      await click(1);
      flushDebounce();

      for(let t = 0; t < 10000; t += 1000) {
         pushModel(["A"]);
         expect(await checked()).toEqual([true, true, false]);
         vi.advanceTimersByTime(1000);
      }

      expect(await checked()).toEqual([true, false, false]);
   });

   it("shows the server selection at once when a pending value is no longer an option", async () => {
      await click(1);
      flushDebounce();
      pushModel(["A"], ["A", "C"]);

      expect(await checked()).toEqual([true, false]);
   });

   it("keeps the selection while the form data check waits for the user", async () => {
      formCheckResult = "defer";
      await click(1);
      pushModel(["A"]);
      vi.advanceTimersByTime(5000);
      expect(await checked()).toEqual([true, true, false]);

      deferredConfirm();
      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"]]);
   });

   it("shows the restored selection after the form data check is cancelled", async () => {
      formCheckResult = "cancel";
      await click(1);

      expect(sentValues().length).toBe(0);
      expect(socket.sendEvent.mock.calls.filter((call) => call[0] === MODEL_URL).length).toBe(1);

      pushModel(["A"]);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("clears the pending timeout when the component is destroyed", async () => {
      await click(1);
      flushDebounce();
      const clear = vi.spyOn((<any> checkBox).pendingSelection, "clear");

      fixture.destroy();
      clear.mockClear();
      vi.advanceTimersByTime(15000);

      expect(clear).not.toHaveBeenCalled();
   });

   it("does not guard the ctrl-held selection", async () => {
      checkBox.onKeyDown(<KeyboardEvent> {keyCode: 17});
      await click(1);
      expect(sentValues().length).toBe(0);

      pushModel(["A"]);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("does not guard a selection that is only added to the pending form values", async () => {
      const model = createModel(["A"]);
      model.refresh = false;
      fixture.componentRef.setInput("model", model);
      await click(1);
      expect(formInputService.addPendingValue).toHaveBeenCalledWith("CheckBox1", ["A", "B"]);

      pushModel(["A"]);
      expect(await checked()).toEqual([true, false, false]);
   });

   it("toggles the focused option when space is pressed", async () => {
      checkBox.selectedCells = [1];
      checkBox["navigate"](NavigationKeys.SPACE);
      expect(await checked()).toEqual([true, true, false]);

      flushDebounce();
      expect(sentValues()).toEqual([["A", "B"]]);
   });

   it("does not report an option as selected when the model has no values", () => {
      checkBox.model.values = null;
      expect(checkBox.isSelected(0)).toBe(false);
   });
});
