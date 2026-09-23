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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { InteractService } from "../../../widget/interact/interact.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { VSRadioButtonModel } from "../../model/vs-radio-button-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSRadioButton } from "./vs-radio-button.component";

const APPLY_URL = "/events/radioButton/applySelection";
const MODEL_URL = "/events/vsview/object/model";

describe("VSRadioButton pending selection (Bug #76959)", () => {
   let fixture: ComponentFixture<VSRadioButton>;
   let radio: VSRadioButton;
   let socket: any;
   let formDataService: any;
   let formInputService: any;
   let context: any;
   // debounced callbacks that have not been fired yet, keyed like DebounceService
   let debounced: Map<string, { fn: Function, args: any[] }>;
   let formCheckResult: "confirm" | "cancel";

   function createModel(selected: string, values: string[] = ["A", "B", "C"]): VSRadioButtonModel {
      const model = TestUtils.createMockVSRadioButtonModel("RadioButton1");
      model.objectFormat = TestUtils.createMockVSFormatModel();
      model.labels = values.slice();
      model.values = values.slice();
      model.selectedObject = selected;
      model.selectedLabel = selected;
      model.dataColCount = 1;
      model.dataRowCount = values.length;
      return model;
   }

   /** Simulates a RefreshVSObjectCommand replacing the viewer's model. */
   function pushModel(selected: string, values?: string[]): void {
      fixture.componentRef.setInput("model", createModel(selected, values));
   }

   /** Fires the pending debounced send, as DebounceService would after 500ms. */
   function flushDebounce(): void {
      const entries = Array.from(debounced.values());
      debounced.clear();
      entries.forEach(({fn, args}) => fn(...args));
   }

   async function checkedIndex(): Promise<number> {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const inputs: HTMLInputElement[] =
         Array.from(fixture.nativeElement.querySelectorAll("input[type=radio]"));
      return inputs.findIndex((input) => input.checked);
   }

   async function click(index: number): Promise<void> {
      const inputs: HTMLInputElement[] =
         Array.from(fixture.nativeElement.querySelectorAll("input[type=radio]"));
      inputs[index].click();
      fixture.detectChanges();
      await fixture.whenStable();
   }

   function sentEvents(url: string): any[] {
      return socket.sendEvent.mock.calls.filter((call) => call[0] === url).map((call) => call[1]);
   }

   beforeEach(async () => {
      vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
      debounced = new Map();
      formCheckResult = "confirm";
      socket = {sendEvent: vi.fn(), runtimeId: "vs1", commands: new Subject<any>()};
      formDataService = {
         checkFormData: vi.fn((runtimeId, name, selection, confirmed, canceled) => {
            if(formCheckResult === "confirm") {
               confirmed();
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
      context = {viewer: true, preview: false};
      const debounceService = {
         debounce: vi.fn((key: string, fn: Function, delay: number, args: any[]) => {
            debounced.set(key, {fn, args});
         }),
         cancel: vi.fn()
      };

      TestBed.configureTestingModule({
         imports: [VSRadioButton],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            PopComponentService,
            {provide: ViewsheetClientService, useValue: socket},
            {provide: CheckFormDataService, useValue: formDataService},
            {provide: FormInputService, useValue: formInputService},
            {provide: DebounceService, useValue: debounceService},
            {provide: ContextProvider, useValue: context},
            {provide: DataTipService, useValue: {isDataTip: vi.fn(), scrolled: new Subject<void>()}},
            {provide: ModelService, useValue: {getModel: vi.fn(), sendModel: vi.fn()}},
            {provide: InteractService, useValue: {
               addInteractable: vi.fn(), notify: vi.fn(), removeInteractable: vi.fn()}}
         ]
      });
      await TestBed.compileComponents();

      fixture = TestBed.createComponent(VSRadioButton);
      radio = fixture.componentInstance;
      fixture.componentRef.setInput("model", createModel("A"));
      expect(await checkedIndex()).toBe(0);
   });

   afterEach(() => {
      fixture.destroy();
      vi.useRealTimers();
   });

   it("(a) ignores a stale model of the previous value while the send is debounced", async () => {
      await click(1);
      expect(await checkedIndex()).toBe(1);
      expect(sentEvents(APPLY_URL).length).toBe(0);

      pushModel("A");

      expect(await checkedIndex()).toBe(1);
      expect(radio.selectIndex).toBe(1);
   });

   it("(b) ignores a stale model of the previous value after the send, before the ack", async () => {
      await click(1);
      flushDebounce();
      expect(sentEvents(APPLY_URL).map((e) => e.value)).toEqual(["B"]);

      pushModel("A");
      pushModel("A");

      expect(await checkedIndex()).toBe(1);
   });

   it("(c) clears the pending selection when the server acknowledges it", async () => {
      await click(1);
      flushDebounce();
      pushModel("A");
      pushModel("B");
      expect(await checkedIndex()).toBe(1);

      // a later server change is no longer ignored
      pushModel("C");
      expect(await checkedIndex()).toBe(2);
   });

   it("(c) does not treat a matching model received before the send as the ack", async () => {
      await click(1);
      pushModel("B");
      flushDebounce();
      pushModel("A");

      expect(await checkedIndex()).toBe(1);
   });

   it("(d) shows a server override once the pending selection times out", async () => {
      await click(1);
      flushDebounce();
      pushModel("C");
      expect(await checkedIndex()).toBe(1);

      vi.advanceTimersByTime(2000);

      expect(await checkedIndex()).toBe(2);
   });

   it("(d) shows a server override back to the previous value once the pending selection times out", async () => {
      await click(1);
      flushDebounce();
      pushModel("A");
      expect(await checkedIndex()).toBe(1);

      vi.advanceTimersByTime(2000);

      expect(await checkedIndex()).toBe(0);
   });

   it("(d) keeps the local selection on timeout when no other model arrived", async () => {
      await click(1);
      flushDebounce();

      vi.advanceTimersByTime(2000);

      expect(await checkedIndex()).toBe(1);
   });

   it("(d) releases the pending selection even if it is never sent", async () => {
      await click(1);
      pushModel("A");
      expect(await checkedIndex()).toBe(1);

      vi.advanceTimersByTime(2500);

      expect(await checkedIndex()).toBe(0);
   });

   it("shows the server value at once when the pending value is no longer an option", async () => {
      await click(1);
      flushDebounce();
      pushModel("A", ["A", "C"]);

      expect(await checkedIndex()).toBe(0);
   });

   it("(e) shows the restored value after the form data check is cancelled", async () => {
      formCheckResult = "cancel";
      await click(1);

      expect(sentEvents(APPLY_URL).length).toBe(0);
      expect(sentEvents(MODEL_URL).length).toBe(1);

      pushModel("A");

      expect(await checkedIndex()).toBe(0);
   });

   it("lets the latest click replace the pending selection", async () => {
      await click(1);
      flushDebounce();
      await click(2);
      flushDebounce();
      expect(sentEvents(APPLY_URL).map((e) => e.value)).toEqual(["B", "C"]);

      pushModel("A");
      pushModel("B");
      expect(await checkedIndex()).toBe(2);

      pushModel("C");
      pushModel("A");
      expect(await checkedIndex()).toBe(0);
   });

   it("does not guard the ctrl-held selection", async () => {
      radio.onKeyDown(<KeyboardEvent> {keyCode: 17});
      await click(1);
      expect(sentEvents(APPLY_URL).length).toBe(0);

      pushModel("A");
      expect(await checkedIndex()).toBe(0);
   });

   it("does not guard the selection outside the viewer", async () => {
      context.viewer = false;
      await click(1);

      pushModel("A");
      expect(await checkedIndex()).toBe(0);
   });

   it("does not guard a selection that is only added to the pending form values", async () => {
      const model = createModel("A");
      model.refresh = false;
      fixture.componentRef.setInput("model", model);
      await click(1);
      expect(formInputService.addPendingValue).toHaveBeenCalledWith("RadioButton1", "B");

      pushModel("A");
      expect(await checkedIndex()).toBe(0);
   });
});
