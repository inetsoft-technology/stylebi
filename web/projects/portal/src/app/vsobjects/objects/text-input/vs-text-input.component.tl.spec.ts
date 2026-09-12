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
 * VSTextInput - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - validation and submit flow: modal error, pending values, applySelection
 *   Group 2 [Risk 2] - key handling, script execution, and datepicker commit
 *   Group 3 [Risk 2] - submitted observable wiring and selected/model setters
 *   Group 4 [Risk 1] - keyboard navigation and editing helpers
 *
 * Confirmed bugs (it.fails): none
 */

import { Subject, of } from "rxjs";
import { Component, Directive, EventEmitter, Input, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

import { TestUtils } from "../../../common/test/test-utils";
import { ComponentTool } from "../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { VSTextInputModel } from "../../model/vs-text-input-model";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { NavigationKeys } from "../navigation-keys";
import { VSInputLabelWrapper } from "../input-label-wrapper/vs-input-label-wrapper.component";
import { VSTextInput } from "./vs-text-input.component";

@Directive({
   selector: "[VSDataTip]",
   standalone: true,
})
class VSDataTipDirectiveStub {
   @Input() dataTipName: string;
   @Input() popContainerName: string;
   @Input() popZIndex: number;
}

@Directive({
   selector: "[VSPopComponent]",
   standalone: true,
})
class VSPopComponentDirectiveStub {
   @Input() popComponentName: string;
   @Input() popContainerName: string;
   @Input() popZIndex: number;
}

@Component({
   selector: "vs-input-label-wrapper",
   template: "<ng-content></ng-content>",
   standalone: true,
})
class VSInputLabelWrapperStub {
   @Input() labelModel: unknown;
   @Input() labelSelected = false;
   @Input() disabled = false;
   @Input() objectHeight: number | undefined;
   @Input() contentOverflow = "hidden";
   @Output() selectLabel = new EventEmitter<MouseEvent>();
}

afterEach(() => {
   if(vi.isFakeTimers()) {
      vi.runOnlyPendingTimers();
      vi.useRealTimers();
   }
   vi.restoreAllMocks();
});

function makeModel(overrides: Partial<VSTextInputModel> = {}): VSTextInputModel {
   const model = TestUtils.createMockVSTextInputModel("TextInput1");
   model.objectFormat = {
      ...TestUtils.createMockVSFormatModel(),
      font: "12px Arial",
      width: 160,
      height: 28,
      background: "#ffffff",
      foreground: "#111111",
      hAlign: "left",
      wrapping: { whiteSpace: "nowrap", wordWrap: "normal", overflow: "hidden" },
      border: {
         top: "1px solid #111111",
         right: "1px solid #222222",
         bottom: "1px solid #333333",
         left: "1px solid #444444",
      },
   };
   model.text = "";
   model.pattern = "";
   model.message = "";
   model.prompt = "Prompt";
   model.option = "Text";
   model.refresh = true;
   model.multiLine = false;
   model.min = null as any;
   model.max = null as any;
   model.labelSelected = false as any;
   model.editing = false as any;
   model.writeBackDirectly = false as any;
   return Object.assign(model, overrides);
}

function createComponent(opts: {
   model?: VSTextInputModel;
   viewer?: boolean;
   preview?: boolean;
} = {}) {
   vi.useFakeTimers();

   const viewsheetClientService = { sendEvent: vi.fn() };
   const modalService = {};
   const formInputService = {
      addPendingValue: vi.fn(),
   };
   const debounceService = {
      debounce: vi.fn((_key: string, fn: Function, _delay: number, args: unknown[]) => fn(...args)),
   };
   const context = {
      viewer: opts.viewer ?? true,
      preview: opts.preview ?? false,
      binding: false,
      embedAssembly: false,
      vsWizard: false,
      vsWizardPreview: false,
   };
   const dataTipService = { isDataTip: vi.fn(() => false) };
   const firstDayOfWeekService = {
      getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })),
   };
   const zone = { run: (fn: Function) => fn() };

   const comp = new VSTextInput(
      viewsheetClientService as any,
      modalService as any,
      formInputService as any,
      debounceService as any,
      zone as any,
      context as ContextProvider,
      dataTipService as unknown as DataTipService,
      firstDayOfWeekService as any,
   );

   const focus = vi.fn();
   const blur = vi.fn();
   const click = vi.fn();
   comp.textAreaElementRef = { nativeElement: { focus, blur } } as any;
   comp.calendarButton = { nativeElement: { click } } as any;
   comp.dropdown = { close: vi.fn() } as any;
   comp.model = opts.model ?? makeModel();
   comp.ngOnInit();

   return {
      comp,
      viewsheetClientService,
      modalService,
      formInputService,
      debounceService,
      context,
      dataTipService,
      firstDayOfWeekService,
      focus,
      blur,
      click,
   };
}

describe("VSTextInput - legacy DOM compatibility", () => {
   it("should render an input instead of a textarea when multiLine is false", async () => {
      const { fixture } = await render(VSTextInput, {
         providers: [
            { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn() } },
            { provide: FormInputService, useValue: { addPendingValue: vi.fn() } },
            { provide: DebounceService, useValue: { debounce: vi.fn() } },
            { provide: ContextProvider, useValue: { viewer: true, preview: false, binding: false, embedAssembly: false, vsWizard: false, vsWizardPreview: false } },
            { provide: DataTipService, useValue: { isDataTip: vi.fn(() => false) } },
            { provide: NgbModal, useValue: {} },
            { provide: FirstDayOfWeekService, useValue: { getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })) } },
         ],
         componentInputs: {
            model: makeModel({ option: "Text", multiLine: false }),
         },
         importOverrides: [
            { replace: VSDataTipDirective, with: VSDataTipDirectiveStub },
            { replace: VSPopComponentDirective, with: VSPopComponentDirectiveStub },
            { replace: VSInputLabelWrapper, with: VSInputLabelWrapperStub },
         ],
      });

      expect(fixture.nativeElement.querySelector("input")).not.toBeNull();
      expect(fixture.nativeElement.querySelector("textarea")).toBeNull();
   });

   it("should keep manual date input text without showing the validation dialog", async () => {
      const { fixture } = await render(VSTextInput, {
         providers: [
            { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn() } },
            { provide: FormInputService, useValue: { addPendingValue: vi.fn() } },
            { provide: DebounceService, useValue: { debounce: vi.fn() } },
            { provide: ContextProvider, useValue: { viewer: true, preview: false, binding: false, embedAssembly: false, vsWizard: false, vsWizardPreview: false } },
            { provide: DataTipService, useValue: { isDataTip: vi.fn(() => false) } },
            { provide: NgbModal, useValue: {} },
            { provide: FirstDayOfWeekService, useValue: { getFirstDay: vi.fn(() => of({ isoFirstDay: 1 })) } },
         ],
         componentInputs: {
            model: makeModel({ option: "Date", text: "" }),
         },
         importOverrides: [
            { replace: VSDataTipDirective, with: VSDataTipDirectiveStub },
            { replace: VSPopComponentDirective, with: VSPopComponentDirectiveStub },
            { replace: VSInputLabelWrapper, with: VSInputLabelWrapperStub },
         ],
      });
      const showMessageDialogSpy = vi
         .spyOn(ComponentTool, "showMessageDialog")
         .mockReturnValue(Promise.resolve("ok"));
      const dateInput = fixture.nativeElement.querySelector("input");

      dateInput.value = "2017-01-01";
      dateInput.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      expect(showMessageDialogSpy).not.toHaveBeenCalled();
      expect(fixture.componentInstance.model.text).toBe("2017-01-01");
   });
});

describe("VSTextInput - validation and submit flow", () => {
   it("should show the invalid-input modal and reset the field after the dialog resolves", async () => {
      const { comp, formInputService } = createComponent({
         model: makeModel({
            text: "abc",
            option: "Integer",
            absoluteName: "InputA",
            refresh: false,
         }),
      });
      const showMessageDialogSpy = vi
         .spyOn(ComponentTool, "showMessageDialog")
         .mockReturnValue(Promise.resolve("ok"));

      comp.onKey({ keyCode: 65, ctrlKey: false } as any);
      (comp as any).submit();
      await Promise.resolve();

      expect(showMessageDialogSpy).toHaveBeenCalledWith(
         expect.any(Object),
         "_#(js:Warning)",
         expect.stringContaining("InputA"),
      );
      expect(comp.model.text).toBe("");
      expect(comp.validInput).toBe(true);
      expect(formInputService.addPendingValue).toHaveBeenCalledWith("InputA", "abc");
   });

   it("should add a pending value when refresh and writeBackDirectly are both false", () => {
      const { comp, formInputService, debounceService } = createComponent({
         model: makeModel({
            text: "queued",
            refresh: false,
            writeBackDirectly: false as any,
         }),
      });

      (comp as any).submit();

      expect(formInputService.addPendingValue).toHaveBeenCalledWith("TextInput1", "queued");
      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should apply the selection immediately when refresh is enabled", () => {
      const { comp, debounceService } = createComponent({
         model: makeModel({
            text: "live-value",
            refresh: true,
         }),
      });

      (comp as any).submit();

      expect(debounceService.debounce).toHaveBeenCalledWith(
         "InputSelectionEvent.TextInput1",
         expect.any(Function),
         500,
         [expect.objectContaining({ value: "live-value" }), expect.any(Object)],
      );
   });

   it("should apply the queued selection when submitted emits true", () => {
      const { comp, debounceService } = createComponent({
         model: makeModel({ refresh: false }),
      });
      const submitted = new Subject<boolean>();

      comp.submitted = submitted.asObservable();
      comp.ngOnChanges({
         submitted: {
            currentValue: comp.submitted,
            previousValue: null,
            firstChange: true,
            isFirstChange: () => true,
         },
      } as any);
      (comp as any).unappliedSelection = true;
      submitted.next(true);

      expect(debounceService.debounce).toHaveBeenCalledTimes(1);
   });
});

describe("VSTextInput - key handling and date flow", () => {
   it("should submit, prevent default, and send the onclick event on Enter when viewer script is present", async () => {
      const { comp, viewsheetClientService } = createComponent({
         model: makeModel({
            script: "return true;" as any,
            text: "run",
         }),
         viewer: true,
      });
      const event = { keyCode: 13, ctrlKey: false, preventDefault: vi.fn() } as any;

      comp.onKey(event);
      await vi.advanceTimersByTimeAsync(500);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(viewsheetClientService.sendEvent).toHaveBeenCalledWith(
         "/events/onclick/TextInput1/0/0",
      );
   });

   it("should only validate on non-enter keys when focus is none", () => {
      const { comp } = createComponent({
         model: makeModel({ text: "bad", option: "Integer", message: undefined as any }),
      });

      comp.onKey({ keyCode: 65, ctrlKey: false } as any);

      expect(comp.validInput).toBe(false);
      expect(comp.errorString).toBe("_#(js:viewer.viewsheet.textInput.validError2)");
   });

   it("should mark input invalid when the text fails the configured pattern", () => {
      const { comp } = createComponent({
         model: makeModel({ text: "abc", pattern: "^[0-9]+$", message: undefined as any }),
      });
      comp.ngOnChanges({
         model: {
            currentValue: comp.model,
            previousValue: null,
            firstChange: true,
            isFirstChange: () => true,
         },
      } as any);

      comp.onKey({ keyCode: 65, ctrlKey: false } as any);

      expect(comp.validInput).toBe(false);
      expect(comp.errorString).toBe("_#(js:viewer.viewsheet.textInput.validError2)");
   });

   it("should mark input invalid when the value exceeds the configured max", () => {
      const { comp } = createComponent({
         model: makeModel({ text: "20", max: "10", min: null as any }),
      });

      comp.onKey({ keyCode: 65, ctrlKey: false } as any);

      expect(comp.validInput).toBe(false);
   });

   it("should mark input invalid when the value is below the configured min", () => {
      const { comp } = createComponent({
         model: makeModel({ text: "-5", max: null as any, min: "0" }),
      });

      comp.onKey({ keyCode: 65, ctrlKey: false } as any);

      expect(comp.validInput).toBe(false);
   });

   it("should update the text, trigger blur, and close the dropdown for a selected date", () => {
      const { comp } = createComponent({
         model: makeModel({ option: "Date", text: "" }),
      });
      const onBlurSpy = vi.spyOn(comp, "onBlur").mockImplementation(() => {});

      comp.setDateFromDatepicker({ year: 2024, month: 7, day: 2 });

      expect(comp.model.text).toBe("2024-7-2");
      expect(onBlurSpy).toHaveBeenCalledTimes(1);
      expect(comp.dropdown.close).toHaveBeenCalledTimes(1);
   });

   it("should parse the selected date in the model setter for ISO date text", () => {
      const { comp } = createComponent();

      comp.model = makeModel({ option: "Date", text: "2024-07-02" });

      expect(comp.date).toEqual({ year: 2024, month: 7, day: 2 });
   });
});

describe("VSTextInput - setters and navigation helpers", () => {
   it("should clear editing and label selection when selected becomes false", () => {
      const { comp } = createComponent({
         model: makeModel({
            editing: true as any,
            labelSelected: true as any,
         }),
      });

      comp.selected = false;

      expect(comp.model.editing).toBe(false);
      expect(comp.model.labelSelected).toBe(false);
   });

   it("should unsubscribe the previous submitted subscription before replacing it", () => {
      const { comp } = createComponent();
      const previous = { unsubscribe: vi.fn() };
      comp["submittedForm"] = previous as any;
      comp.submitted = new Subject<boolean>().asObservable();

      comp.ngOnChanges({
         submitted: {
            currentValue: comp.submitted,
            previousValue: null,
            firstChange: false,
            isFirstChange: () => false,
         },
      } as any);

      expect(previous.unsubscribe).toHaveBeenCalledTimes(1);
   });

   it("should move focus between input and calendar and click the calendar button on SPACE", () => {
      const { comp, focus, blur, click } = createComponent({
         model: makeModel({ option: "Date" }),
      });

      (comp as any).navigate(NavigationKeys.RIGHT);
      (comp as any).navigate(NavigationKeys.RIGHT);
      (comp as any).navigate(NavigationKeys.SPACE);
      (comp as any).navigate(NavigationKeys.LEFT);

      expect(focus).toHaveBeenCalledTimes(2);
      expect(blur).toHaveBeenCalledTimes(1);
      expect(click).toHaveBeenCalledTimes(1);
      expect(comp.focused).toBe(1);
   });

   it("should enable editing only outside viewer mode and focus the input", () => {
      const { comp, focus } = createComponent({ viewer: false });

      comp.enableEditing();

      expect(comp.model.editing).toBe(true);
      expect(focus).toHaveBeenCalledTimes(1);
      expect(comp.isInputDisabled()).toBe(true);
      comp.selected = true;
      expect(comp.isInputDisabled()).toBe(false);
   });
});
