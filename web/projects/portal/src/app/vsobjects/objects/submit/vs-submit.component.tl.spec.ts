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
 * VSSubmit - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - viewer click flow: global submit, pending value flush, debounced event dispatch
 *   Group 2 [Risk 2] - composer click behavior and refresh-enabled emit contract
 *   Group 3 [Risk 1] - keyboard navigation and focus color derivation
 *
 * Confirmed bugs (it.fails): none
 */

import { Subject } from "rxjs";
import { Directive, Input } from "@angular/core";
import { render } from "@testing-library/angular";

import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { VSSubmitModel } from "../../model/output/vs-submit-model";
import { FormInputService } from "../../util/form-input.service";
import { GlobalSubmitService } from "../../util/global-submit.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { NavigationKeys } from "../navigation-keys";
import { TooltipIfDirective } from "../../../widget/tooltip/tooltip-if.directive";
import { VSSubmit } from "./vs-submit.component";

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

@Directive({
   selector: "[tooltipIf]",
   standalone: true,
})
class TooltipIfDirectiveStub {
   @Input() disableTooltipIf: boolean;
}

afterEach(() => vi.restoreAllMocks());

function makeModel(overrides: Partial<VSSubmitModel> = {}): VSSubmitModel {
   const model = TestUtils.createMockVSSubmitModel("Submit1");
   model.enabled = true;
   model.label = "Submit";
   model.objectFormat = {
      ...TestUtils.createMockVSFormatModel(),
      font: "bold 14px Arial",
      width: 120,
      height: 40,
      background: "",
      foreground: "#111111",
      vAlign: "middle",
      border: {
         top: "1px solid #111111",
         right: "1px solid #222222",
         bottom: "1px solid #333333",
         left: "1px solid #444444",
      },
      wrapping: { whiteSpace: "nowrap", wordWrap: "normal", overflow: "hidden" },
   };
   return Object.assign(model, overrides);
}

function createComponent(opts: { model?: VSSubmitModel; viewer?: boolean; preview?: boolean } = {}) {
   const socket = { sendEvent: vi.fn() };
   const debounceService = {
      debounce: vi.fn((_key: string, fn: Function, _delay: number, args: unknown[]) => fn(...args)),
   };
   const formInputService = {
      getPendingValues: vi.fn(() => [
         { name: "A", value: "1" },
         { name: "B", value: null },
      ]),
      clear: vi.fn(),
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
   const globalSubmitService = { submitGlobal: vi.fn() };
   const zone = { run: (fn: Function) => fn() };

   const comp = new VSSubmit(
      socket as unknown as ViewsheetClientService,
      debounceService as unknown as DebounceService,
      formInputService as unknown as FormInputService,
      zone as any,
      context as ContextProvider,
      dataTipService as unknown as DataTipService,
      globalSubmitService as unknown as GlobalSubmitService,
   );

   const focus = vi.fn();
   const click = vi.fn();
   comp.button = { nativeElement: { focus, click } } as any;
   comp.model = opts.model ?? makeModel();

   return {
      comp,
      socket,
      debounceService,
      formInputService,
      context,
      globalSubmitService,
      focus,
      click,
   };
}

describe("VSSubmit - click behavior", () => {
   it("should flush pending values and send the debounced onclick event in viewer mode", () => {
      const { comp, socket, formInputService, globalSubmitService, debounceService } = createComponent({
         viewer: true,
         model: makeModel({ absoluteName: "SubmitViewer" }),
      });
      const event = { offsetX: 12, offsetY: 34, preventDefault: vi.fn() } as any;

      comp.onClick(event);

      expect(globalSubmitService.submitGlobal).toHaveBeenCalledWith("SubmitViewer");
      expect(formInputService.clear).toHaveBeenCalledTimes(1);
      expect(debounceService.debounce).toHaveBeenCalledWith(
         "vs-submit.click.SubmitViewer",
         expect.any(Function),
         600,
         [event, socket],
      );
      expect(socket.sendEvent).toHaveBeenCalledWith("/events/onclick/SubmitViewer/12/34", expect.any(Object));
      expect(socket.sendEvent.mock.calls[0][1].values).toEqual([{ name: "A", value: "1" }]);
   });

   it("should prevent default outside viewer mode and emit submitClicked when refresh and enabled are true", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ refresh: true, enabled: true }),
      });
      const emitSpy = vi.spyOn(comp.submitClicked, "emit");
      const event = { preventDefault: vi.fn(), offsetX: 0, offsetY: 0 } as any;

      comp.onClick(event);

      expect(event.preventDefault).toHaveBeenCalledTimes(1);
      expect(emitSpy).toHaveBeenCalledWith("Submit1");
   });

   it("should not emit submitClicked when enabled is false even if refresh is true", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ refresh: true, enabled: false }),
      });
      const emitSpy = vi.spyOn(comp.submitClicked, "emit");

      comp.onClick({ preventDefault: vi.fn(), offsetX: 0, offsetY: 0 } as any);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should not emit submitClicked when refresh is false even if enabled is true", () => {
      const { comp } = createComponent({
         viewer: false,
         model: makeModel({ refresh: false, enabled: true }),
      });
      const emitSpy = vi.spyOn(comp.submitClicked, "emit");

      comp.onClick({ preventDefault: vi.fn(), offsetX: 0, offsetY: 0 } as any);

      expect(emitSpy).not.toHaveBeenCalled();
   });
});

describe("VSSubmit - layout and navigation", () => {
   it("should apply the fade-assembly class when the button is disabled", async () => {
      const { fixture } = await render(VSSubmit, {
         providers: [
            { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn() } },
            { provide: DebounceService, useValue: { debounce: vi.fn() } },
            { provide: FormInputService, useValue: { getPendingValues: vi.fn(() => []), clear: vi.fn() } },
            { provide: ContextProvider, useValue: { viewer: true, preview: false, binding: false, embedAssembly: false, vsWizard: false, vsWizardPreview: false } },
            { provide: DataTipService, useValue: { isDataTip: vi.fn(() => false) } },
            { provide: GlobalSubmitService, useValue: { submitGlobal: vi.fn() } },
         ],
         componentInputs: {
            model: makeModel({ enabled: false }),
         },
         importOverrides: [
            { replace: VSDataTipDirective, with: VSDataTipDirectiveStub },
            { replace: VSPopComponentDirective, with: VSPopComponentDirectiveStub },
            { replace: TooltipIfDirective, with: TooltipIfDirectiveStub },
         ],
      });
      const button = fixture.nativeElement.querySelector("button.submit-button");

      expect(button.getAttribute("class")).toContain("fade-assembly");
   });

   it("should calculate bottom padding for top-aligned text and initialize focusColor from the border", () => {
      const { comp } = createComponent({
         model: makeModel({
            objectFormat: {
               ...makeModel().objectFormat,
               vAlign: "top",
               height: 54,
               border: {
                  top: "2px solid #102030",
                  right: "",
                  bottom: "",
                  left: "",
               },
            },
         }),
      });

      comp.ngOnChanges({
         model: {
            currentValue: comp.model,
            previousValue: null,
            firstChange: true,
            isFirstChange: () => true,
         },
      } as any);

      expect(comp.vAlign).toBe("32px");
      expect(comp.focusColor).toBe("rgba(16, 32, 48, 0.5)");
   });

   it("should derive focusColor from the background when no border color is available", () => {
      const model = makeModel({
         objectFormat: {
            ...makeModel().objectFormat,
            background: "#abcdef",
            border: { top: "", right: "", bottom: "", left: "" },
         },
      });
      const { comp } = createComponent({ model });

      comp.ngOnChanges({
         model: {
            currentValue: model,
            previousValue: null,
            firstChange: true,
            isFirstChange: () => true,
         },
      } as any);

      expect(comp.focusColor).toBe("rgba(171, 205, 239, 0.5)");
   });

   it("should focus the button and click it on SPACE navigation", () => {
      const { comp, focus, click } = createComponent();

      (comp as any).navigate(NavigationKeys.SPACE);

      expect(focus).toHaveBeenCalledTimes(1);
      expect(click).toHaveBeenCalledTimes(1);
   });

   it("should report hyperlinks when the model exposes a non-empty hyperlinks array", () => {
      const { comp } = createComponent({
         model: makeModel({ hyperlinks: [{ link: "/detail" } as any] as any } as any),
      });

      expect(comp.hasHyperlinks).toBe(true);
   });

   it("should report no hyperlinks when the model does not expose a hyperlinks array", () => {
      const { comp } = createComponent();

      expect(comp.hasHyperlinks).toBe(false);
   });
});
